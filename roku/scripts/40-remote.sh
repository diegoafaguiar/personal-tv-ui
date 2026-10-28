#!/usr/bin/env bash
# roku/scripts/40-remote.sh — controle remoto da Roku TV pelo terminal (ECP).
#
# Uso:
#   roku/scripts/40-remote.sh key Home Down Down Select   aperta teclas, em ordem
#   roku/scripts/40-remote.sh home                        volta à tela inicial
#   roku/scripts/40-remote.sh launch Netflix              abre um canal (nome ou ID)
#   roku/scripts/40-remote.sh type "texto"                digita num campo de texto
#   roku/scripts/40-remote.sh apps                        lista canais (ID e nome)
#   roku/scripts/40-remote.sh active                      mostra o app aberto
#
# Teclas: Home Back Select Up Down Left Right Play Rev Fwd InstantReplay Info Search
# Backspace Enter VolumeUp VolumeDown VolumeMute PowerOff InputTuner InputHDMI1..4.
# DRY_RUN=1 mostra o que faria sem mexer na TV.
source "$(dirname "${BASH_SOURCE[0]}")/../lib/roku.sh"

usage() { sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

# Pausa entre teclas: a interface da Roku perde toques muito rápidos
KEY_DELAY="${KEY_DELAY:-0.4}"

press() {
  local k
  for k in "$@"; do
    [[ "$k" =~ ^[A-Za-z0-9_]+$ ]] || die "Tecla inválida: $k"
    ecp_post "/keypress/$k"
    [[ "$DRY_RUN" == 1 ]] || sleep "$KEY_DELAY"
  done
}

# Nome ou ID → ID do canal (nome sem diferenciar maiúsculas; o primeiro que contém o texto)
app_id() {
  local want="$1" list
  list="$(ecp_get /query/apps | parse_apps)"
  awk -F'\t' -v w="$want" '$1==w {print $1; exit}' <<<"$list" | grep . && return 0
  awk -F'\t' -v w="$(tr '[:upper:]' '[:lower:]' <<<"$want")" 'index(tolower($4), w) {print $1; exit}' <<<"$list" | grep . && return 0
  die "Canal não encontrado: $want (veja: roku/scripts/40-remote.sh apps)"
}

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in -h|--help|"") usage; [[ -n "$cmd" ]] || exit 1; exit 0 ;; esac
  require_cmd curl "Instale o curl."
  load_config
  case "$cmd" in
    key) [[ $# -gt 0 ]] || die "Informe ao menos uma tecla."; press "$@" ;;
    home) press Home ;;
    launch)
      [[ $# -eq 1 ]] || die "Uso: launch <nome ou ID>"
      local id; id="$(app_id "$1")"
      ecp_post "/launch/$(urlencode "$id")"
      log_ok "Abrindo $1 ($id)" ;;
    type)
      [[ $# -eq 1 ]] || die "Uso: type \"texto\""
      local text="$1" i
      for ((i = 0; i < ${#text}; i++)); do ecp_post "/keypress/Lit_$(urlencode "${text:i:1}")"; done ;;
    apps) ecp_get /query/apps | parse_apps | awk -F'\t' '{printf "%-14s %s\n", $1, $4}' ;;
    active) ecp_get /query/active-app | sed -n 's:.*<app[^>]*>\(.*\)</app>.*:\1:p' | head -n1 ;;
    *) die "Comando desconhecido: $cmd (veja --help)" ;;
  esac
}

main "$@"
