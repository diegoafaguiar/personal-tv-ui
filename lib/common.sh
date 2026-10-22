#!/usr/bin/env bash
# lib/common.sh — funções compartilhadas por todos os scripts.
#
# Como ler este arquivo: cada script em scripts/ começa com
#   source "$(dirname "$0")/../lib/common.sh"
# e passa a ter logging, carregamento de config, wrappers de adb, leitura de
# listas de pacotes e as verificações de segurança (never-disable, HOME).
#
# Convenções:
#   - Nunca chame "adb" direto nos scripts. Use tv_adb / tv_shell: eles
#     respeitam DRY_RUN e sempre apontam para a TV configurada (-s).
#   - Funções que MUDAM a TV passam por tv_run (obedece DRY_RUN).
#   - Funções que só LEEM a TV usam tv_shell (sempre executam).

set -euo pipefail

# --------------------------------------------------------------------------
# Caminhos
# --------------------------------------------------------------------------
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$LIB_DIR/.." && pwd)"
PROFILES_DIR="$REPO_ROOT/config/profiles"
BACKUPS_DIR="${BACKUPS_DIR:-$REPO_ROOT/backups}"
REPORTS_DIR="${REPORTS_DIR:-$REPO_ROOT/reports}"
export LIB_DIR REPO_ROOT PROFILES_DIR BACKUPS_DIR REPORTS_DIR

# --------------------------------------------------------------------------
# Logging (cores só quando há um terminal)
# --------------------------------------------------------------------------
if [[ -t 2 ]]; then
  C_RESET=$'\e[0m' C_DIM=$'\e[2m' C_RED=$'\e[31m' C_GREEN=$'\e[32m'
  C_YELLOW=$'\e[33m' C_BLUE=$'\e[34m' C_BOLD=$'\e[1m'
else
  C_RESET='' C_DIM='' C_RED='' C_GREEN='' C_YELLOW='' C_BLUE='' C_BOLD=''
fi

log_step() { printf '%s==> %s%s\n' "$C_BOLD$C_BLUE" "$*" "$C_RESET" >&2; }
log_info() { printf '    %s\n' "$*" >&2; }
log_ok()   { printf '    %s✔ %s%s\n' "$C_GREEN" "$*" "$C_RESET" >&2; }
log_warn() { printf '    %s⚠ %s%s\n' "$C_YELLOW" "$*" "$C_RESET" >&2; }
log_err()  { printf '    %s✖ %s%s\n' "$C_RED" "$*" "$C_RESET" >&2; }
log_dim()  { printf '    %s%s%s\n' "$C_DIM" "$*" "$C_RESET" >&2; }
die()      { log_err "$@"; exit 1; }

# --------------------------------------------------------------------------
# Configuração
# --------------------------------------------------------------------------
# Ordem de precedência: variável de ambiente > config/tv.env > erro.
load_config() {
  local env_file="$REPO_ROOT/config/tv.env"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$env_file"
    set +a
  fi
  DRY_RUN="${DRY_RUN:-0}"
  TV_HOST="${TV_HOST:-}"
  if [[ -z "$TV_HOST" ]]; then
    die "TV_HOST não definido. Copie config/tv.env.example para config/tv.env ou exporte TV_HOST=ip-da-tv."
  fi
  # O exemplo traz um IP fictício; se ainda for ele, a pessoa esqueceu de editar.
  local example_host
  example_host="$(sed -n 's/^TV_HOST=//p' "$REPO_ROOT/config/tv.env.example" 2>/dev/null | head -n1)"
  if [[ -n "$example_host" && "$TV_HOST" == "$example_host" && "${ALLOW_EXAMPLE_HOST:-0}" != "1" ]]; then
    log_err "TV_HOST=$TV_HOST é o valor de exemplo. Edite config/tv.env com o IP real da TV"
    log_err "(Configurações › Rede e Internet › sua rede) ou descubra com: scripts/00-connect.sh --scan"
    exit 1
  fi
  # adb identifica dispositivos de rede como ip:porta
  if [[ "$TV_HOST" != *:* ]]; then
    TV_SERIAL="$TV_HOST:5555"
  else
    TV_SERIAL="$TV_HOST"
  fi
  export DRY_RUN TV_HOST TV_SERIAL
  [[ "$DRY_RUN" == "1" ]] && log_warn "DRY_RUN=1: nada será alterado na TV."
  return 0
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Comando '$1' não encontrado. $2"
}

# --------------------------------------------------------------------------
# Wrappers de ADB
# --------------------------------------------------------------------------
# Leitura: sempre executa.
# "</dev/null" é essencial: adb repassa o stdin do processo para a TV e, dentro
# de um loop "while read", isso engole o resto da lista que está sendo lida.
tv_adb()   { adb -s "$TV_SERIAL" "$@" </dev/null; }
tv_shell() { adb -s "$TV_SERIAL" shell "$@" </dev/null; }

# Escrita: respeita DRY_RUN. Use para tudo que muda estado na TV.
tv_run() {
  if [[ "${DRY_RUN:-0}" == "1" ]]; then
    log_dim "[dry-run] adb shell $*"
    return 0
  fi
  log_dim "adb shell $*"
  adb -s "$TV_SERIAL" shell "$@" </dev/null
}

# Conecta (idempotente) e garante que a TV responde a "shell echo".
ensure_connected() {
  require_cmd adb "Instale o Android platform-tools: https://developer.android.com/tools/releases/platform-tools"
  adb start-server >/dev/null 2>&1 </dev/null || true   # evita o ruído "daemon not running" na primeira chamada
  local out
  out="$(adb connect "$TV_SERIAL" 2>&1 </dev/null || true)"
  case "$out" in
    *"connected to"*|*"already connected"*) ;;
    *)
      log_err "Falha ao conectar em $TV_SERIAL: $out"
      log_info "Confira o IP na TV (Configurações › Rede e Internet), se a Depuração por rede está ligada,"
      log_info "ou procure a TV na rede: scripts/00-connect.sh --scan   (docs/02-conexao-adb.md)"
      exit 1 ;;
  esac
  local state
  state="$(adb -s "$TV_SERIAL" get-state 2>&1 </dev/null || true)"
  case "$state" in
    device) ;;
    *unauthorized*) die "TV respondeu 'unauthorized'. Aceite o prompt de depuração na tela da TV (marque 'sempre permitir').";;
    *offline*) die "TV está 'offline' para o adb. Tente: adb disconnect; adb connect $TV_SERIAL";;
    *) die "Estado inesperado do adb: $state";;
  esac
  [[ "$(tv_shell echo ok | tr -d '\r')" == "ok" ]] || die "A TV não respondeu a 'adb shell echo'."
}

# --------------------------------------------------------------------------
# Informações do dispositivo
# --------------------------------------------------------------------------
tv_prop() { tv_shell getprop "$1" | tr -d '\r'; }

# Nome curto e seguro para usar em nomes de pasta: ex. "TCL_G08_4K_GB"
device_slug() {
  local brand model
  brand="$(tv_prop ro.product.brand)"
  model="$(tv_prop ro.product.model)"
  printf '%s_%s' "$brand" "$model" | tr -c 'A-Za-z0-9_.-' '_' | sed 's/_*$//'
}

# Detecta qual launcher de fábrica existe (Google TV vs Android TV).
stock_launcher_pkg() {
  if pkg_is_installed com.google.android.apps.tv.launcherx; then
    echo com.google.android.apps.tv.launcherx
  elif pkg_is_installed com.google.android.tvlauncher; then
    echo com.google.android.tvlauncher
  else
    echo ""
  fi
}

# Candidatos a HOME como "pacote/Classe", em ordem de prioridade. O dump traz
# "name=<classe completa>" e, logo abaixo, "packageName=<pacote>"; pareamos os dois.
home_candidates() {
  tv_shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME" \
    | tr -d '\r' \
    | awk '/^[[:space:]]+name=/ {sub(/^[[:space:]]+name=/, ""); n=$0}
           /^[[:space:]]+packageName=/ {sub(/^[[:space:]]+packageName=/, ""); print $0 "/" n}' || true
}

# Quem responde ao botão HOME hoje. Primeira entrada = quem o Android abre.
home_owner() { home_candidates | head -n1; }

# Dono da rede Wi-Fi salva (o pacote que criou a configuração). Importa porque
# se for o setupwraith, desabilitá-lo derruba a rede.
wifi_owner() {
  # Android 12-13 imprime "cname=<pkg>"; Android 14 também "creatorName=<pkg>".
  tv_shell "dumpsys wifi" 2>/dev/null | tr -d '\r' | grep -oE '(cname|creatorName)=[^ ,]*' | head -n1 | sed -E 's/^(cname|creatorName)=//' || true
}

# --------------------------------------------------------------------------
# Pacotes
# --------------------------------------------------------------------------
# Listas da TV, normalizadas (sem "package:" e sem \r).
pm_list() { tv_shell pm list packages "$@" | tr -d '\r' | sed 's/^package://' | sort; }

pkg_is_installed() { pm_list | grep -qx "$1"; }
pkg_is_disabled()  { pm_list -d | grep -qx "$1"; }
# Instalado em algum momento (inclui os "desinstalados para o usuário 0").
pkg_is_known()     { pm_list -u | grep -qx "$1"; }

# Estado único e legível para relatórios.
pkg_state() {
  local pkg="$1"
  if pkg_is_installed "$pkg"; then
    if pkg_is_disabled "$pkg"; then echo disabled; else echo enabled; fi
  elif pkg_is_known "$pkg"; then
    echo uninstalled   # removido com "pm uninstall -k --user 0"; volta com install-existing
  else
    echo absent
  fi
}

# Lê um arquivo .list: ignora comentários e linhas vazias, devolve só pacotes.
read_list() {
  local file="$1"
  [[ -f "$file" ]] || die "Lista não encontrada: $file"
  sed -E 's/[[:space:]]*#.*$//; s/^[[:space:]]+//; s/[[:space:]]+$//' "$file" | grep -v '^$' || true
}

# Nível de risco declarado no cabeçalho ("# risk: safe|optional|launcher|keep").
list_risk() {
  sed -nE 's/^# risk:[[:space:]]*([a-z]+).*$/\1/p' "$1" | head -n1
}

# Testa um pacote contra os globs de never-disable.list. Retorna 0 se protegido.
is_protected() {
  local pkg="$1" pattern
  while IFS= read -r pattern; do
    # shellcheck disable=SC2053  # queremos glob matching de propósito
    [[ "$pkg" == $pattern ]] && return 0
  done < <(read_list "$PROFILES_DIR/never-disable.list")
  return 1
}

# Resolve um nome de perfil ("tcl-telemetry" ou caminho) para o arquivo .list.
resolve_profile() {
  local name="$1"
  if [[ -f "$name" ]]; then
    echo "$name"
  elif [[ -f "$PROFILES_DIR/$name.list" ]]; then
    echo "$PROFILES_DIR/$name.list"
  elif [[ -f "$PROFILES_DIR/$name" ]]; then
    echo "$PROFILES_DIR/$name"
  else
    die "Perfil não encontrado: $name (veja config/profiles/)"
  fi
}

# --------------------------------------------------------------------------
# Settings (chaves que os scripts alteram e o rollback restaura)
# --------------------------------------------------------------------------
# Formato: namespace:chave. Mantenha em sincronia com docs/06-rollback.md.
# shellcheck disable=SC2034  # usado por 10-backup.sh e 90-rollback.sh
TRACKED_SETTINGS=(
  global:window_animation_scale
  global:transition_animation_scale
  global:animator_duration_scale
  global:private_dns_mode
  global:private_dns_specifier
  secure:screensaver_enabled
  secure:screensaver_components
  secure:screensaver_activate_on_sleep
  secure:enabled_accessibility_services
  secure:accessibility_enabled
  system:screen_off_timeout
)

settings_get() { tv_shell settings get "$1" "$2" | tr -d '\r'; }

# --------------------------------------------------------------------------
# Memória e espaço
# --------------------------------------------------------------------------
# MemAvailable do kernel, em MB: o número honesto de "memória livre" no Android.
mem_available_mb() {
  tv_shell cat /proc/meminfo | tr -d '\r' | awk '/MemAvailable/ {printf "%d", $2/1024}'
}
mem_total_mb() {
  tv_shell cat /proc/meminfo | tr -d '\r' | awk '/MemTotal/ {printf "%d", $2/1024}'
}
# Memória que o pacote ocupa AGORA (PSS total), em MB. 0 se não está rodando.
pkg_pss_mb() {
  local v
  v="$(tv_shell dumpsys meminfo "$1" 2>/dev/null | tr -d '\r' | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL +[0-9]/ {print $2; exit}')"
  [[ -n "$v" ]] && awk -v k="$v" 'BEGIN {printf "%d", k/1024}' || echo 0
}
# Espaço do APK (e libs) em MB. Para apps de /system isso NÃO é liberado ao
# desabilitar; só apps de /data liberam espaço de verdade.
pkg_apk_mb() {
  local path dir
  path="$(tv_shell pm path "$1" 2>/dev/null | tr -d '\r' | head -n1 | sed 's/^package://')"
  [[ -n "$path" ]] || { echo 0; return; }
  dir="$(dirname "$path")"
  tv_shell du -sk "$dir" 2>/dev/null | tr -d '\r' | awk '{printf "%d", $1/1024}'
}
pkg_is_system() {
  local path
  path="$(tv_shell pm path "$1" 2>/dev/null | tr -d '\r' | head -n1 | sed 's/^package://')"
  [[ "$path" == /system/* || "$path" == /product/* || "$path" == /vendor/* || "$path" == /odm/* || "$path" == /apex/* ]]
}

# --------------------------------------------------------------------------
# Nomes amigáveis (config/app-names.tsv)
# --------------------------------------------------------------------------
app_friendly_name() { awk -F'\t' -v p="$1" '$1==p {print $2; exit}' "$REPO_ROOT/config/app-names.tsv"; }
app_description()   { awk -F'\t' -v p="$1" '$1==p {print $3; exit}' "$REPO_ROOT/config/app-names.tsv"; }

# "null" é como o Android responde para chave inexistente.
settings_put() {
  local ns="$1" key="$2" value="$3"
  if [[ "$value" == "null" || -z "$value" ]]; then
    tv_run settings delete "$ns" "$key"
  else
    tv_run settings put "$ns" "$key" "$value"
  fi
}

# --------------------------------------------------------------------------
# Backups
# --------------------------------------------------------------------------
latest_backup_dir() {
  local pointer="$BACKUPS_DIR/LATEST"
  if [[ -f "$pointer" ]]; then
    local dir
    dir="$(cat "$pointer")"
    [[ -d "$dir" ]] && { echo "$dir"; return 0; }
    [[ -d "$BACKUPS_DIR/$dir" ]] && { echo "$BACKUPS_DIR/$dir"; return 0; }
  fi
  # Fallback: pasta mais recente
  find "$BACKUPS_DIR" -mindepth 1 -maxdepth 1 -type d | sort | tail -n1
}

require_backup() {
  local dir
  dir="$(latest_backup_dir)"
  [[ -n "$dir" && -f "$dir/packages-enabled.txt" ]] \
    || die "Nenhum backup encontrado em $BACKUPS_DIR. Rode scripts/10-backup.sh antes de alterar a TV."
  echo "$dir"
}

# --------------------------------------------------------------------------
# Interação
# --------------------------------------------------------------------------
confirm() {
  local prompt="${1:-Continuar?}"
  if [[ "${ASSUME_YES:-0}" == "1" ]]; then return 0; fi
  if [[ ! -t 0 ]]; then
    log_warn "Sem terminal interativo e sem --yes: abortando por segurança."
    return 1
  fi
  read -r -p "    $prompt [s/N] " answer
  [[ "$answer" =~ ^[sSyY]$ ]]
}

timestamp() { date +%Y%m%d-%H%M%S; }
