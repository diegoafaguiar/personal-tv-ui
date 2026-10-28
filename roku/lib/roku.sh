#!/usr/bin/env bash
# roku/lib/roku.sh — funções compartilhadas pelos scripts da Roku TV.
#
# A Roku não tem adb: tudo passa pelo ECP (External Control Protocol), uma API HTTP
# na porta 8060 da TV. Ela permite LER (aparelho, canais, app aberto) e CONTROLAR
# (teclas do controle, abrir canal, digitar). Não permite instalar, remover nem
# desativar nada: isso é feito pelo controle (veja roku/docs/02-limpeza.md).
#
# Requisito na TV: Configurações › Sistema › Configurações avançadas do sistema ›
# Controle por apps móveis › Acesso à rede › "Permissivo". Em "Limitado" a TV recusa
# a maior parte das consultas.
#
# Convenções (as mesmas do lado Android):
#   - Nunca chame curl direto nos scripts. Use ecp_get (só lê) e ecp_post (muda a TV;
#     obedece DRY_RUN).

set -euo pipefail

# --------------------------------------------------------------------------
# Caminhos
# --------------------------------------------------------------------------
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# roku/ (scripts, config) e a raiz do repositório (backups, reports, devices)
ROKU_ROOT="$(cd "$LIB_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ROKU_ROOT/.." && pwd)"
REPORTS_DIR="${REPORTS_DIR:-$REPO_ROOT/reports}"
export LIB_DIR ROKU_ROOT REPO_ROOT REPORTS_DIR

# --------------------------------------------------------------------------
# Logging (cores só quando há um terminal)
# --------------------------------------------------------------------------
if [[ -t 2 ]]; then
  C_RESET=$'\e[0m' C_RED=$'\e[31m' C_GREEN=$'\e[32m' C_YELLOW=$'\e[33m' C_BLUE=$'\e[34m' C_BOLD=$'\e[1m'
else
  C_RESET='' C_RED='' C_GREEN='' C_YELLOW='' C_BLUE='' C_BOLD=''
fi

log_step() { printf '%s==> %s%s\n' "$C_BOLD$C_BLUE" "$*" "$C_RESET" >&2; }
log_info() { printf '    %s\n' "$*" >&2; }
log_ok()   { printf '    %s✔ %s%s\n' "$C_GREEN" "$*" "$C_RESET" >&2; }
log_warn() { printf '    %s⚠ %s%s\n' "$C_YELLOW" "$*" "$C_RESET" >&2; }
log_err()  { printf '    %s✖ %s%s\n' "$C_RED" "$*" "$C_RESET" >&2; }
die()      { log_err "$*"; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Comando '$1' não encontrado. $2"
}

timestamp() { date +%Y%m%d-%H%M%S; }

# --------------------------------------------------------------------------
# Configuração
# --------------------------------------------------------------------------
# Ordem de precedência: variável de ambiente > devices/$TV.env (TV=nome) > roku/config/roku.env > erro.
# A chave é TV_HOST, a mesma das fichas em devices/.
load_config() {
  local env_file="${TV_ENV:-}"
  if [[ -z "$env_file" && -n "${TV:-}" ]]; then
    env_file="$REPO_ROOT/devices/$TV.env"
    [[ -f "$env_file" ]] || die "Ficha da TV não encontrada: devices/$TV.env (veja devices/README.md)"
  fi
  env_file="${env_file:-$ROKU_ROOT/config/roku.env}"
  local env_host="${TV_HOST:-}" env_dry="${DRY_RUN:-}"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$env_file"
    set +a
  fi
  [[ -n "$env_host" ]] && TV_HOST="$env_host"
  [[ -n "$env_dry" ]] && DRY_RUN="$env_dry"
  DRY_RUN="${DRY_RUN:-0}"
  TV_HOST="${TV_HOST:-}"
  [[ -n "$TV_HOST" ]] || die "TV_HOST não definido. Rode roku/scripts/00-connect.sh <ip-da-tv> (ou TV=nome com uma ficha em devices/)."
  # A porta do ECP é sempre 8060; aceita "ip" ou "ip:8060"
  ECP_URL="http://${TV_HOST%%:*}:8060"
  export TV_HOST DRY_RUN ECP_URL
}

# --------------------------------------------------------------------------
# ECP
# --------------------------------------------------------------------------
# GET em um caminho do ECP (ex.: /query/device-info). Só lê; sempre executa.
ecp_get() {
  local out
  out="$(curl -s -m "${ECP_TIMEOUT:-5}" "$ECP_URL$1")" || die "A TV não respondeu em $ECP_URL (está ligada e na mesma rede?)"
  if [[ "$out" == *"not allowed in Limited mode"* ]]; then
    die "A TV está em modo \"Limitado\". Na TV: Configurações › Sistema › Configurações avançadas do sistema › Controle por apps móveis › Acesso à rede › Permissivo."
  fi
  printf '%s\n' "$out"
}

# POST em um caminho do ECP (tecla, abrir canal). Muda a TV: obedece DRY_RUN.
ecp_post() {
  if [[ "$DRY_RUN" == "1" ]]; then
    log_info "[dry-run] POST $ECP_URL$1"
    return 0
  fi
  curl -s -m "${ECP_TIMEOUT:-5}" -X POST -d '' "$ECP_URL$1" >/dev/null || die "A TV recusou: POST $1"
}

# Valor de uma tag simples do XML do ECP: xml_tag model-name <<< "$xml"
xml_tag() { sed -n "s:.*<$1>\(.*\)</$1>.*:\1:p" | head -n1; }

# Canais instalados como "id<TAB>tipo<TAB>versão<TAB>nome", a partir de /query/apps.
# Desfaz as entidades XML que a Roku usa nos nomes (&amp; etc.).
parse_apps() {
  sed -n 's:.*<app id="\([^"]*\)" type="\([^"]*\)" version="\([^"]*\)">\(.*\)</app>.*:\1\t\2\t\3\t\4:p' \
    | sed -e 's/&amp;/\&/g' -e 's/&apos;/'"'"'/g' -e 's/&quot;/"/g' -e 's/&lt;/</g' -e 's/&gt;/>/g'
}

# Nota de um canal (roku/config/canais.tsv): "id<TAB>nota". Vazio se não houver.
channel_note() { awk -F'\t' -v id="$1" '$1==id {print $2; exit}' "$ROKU_ROOT/config/canais.tsv"; }

# Codifica um texto para URL (para /keypress/Lit_<letra> e /search).
urlencode() {
  # LC_ALL=C: percorre bytes, então acentos viram os bytes UTF-8 (%C3%A9), como a Roku espera
  local LC_ALL=C s="$1" out="" c i
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) out+="$(printf '%%%02X' "'$c")" ;;
    esac
  done
  printf '%s' "$out"
}
