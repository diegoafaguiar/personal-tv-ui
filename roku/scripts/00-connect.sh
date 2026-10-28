#!/usr/bin/env bash
# roku/scripts/00-connect.sh — confere a conexão com a Roku TV pelo ECP (porta 8060).
#
# Uso:
#   roku/scripts/00-connect.sh              usa TV_HOST (roku/config/roku.env ou TV=nome)
#   roku/scripts/00-connect.sh 192.168.0.50 testa esse IP e o salva em roku/config/roku.env
#   roku/scripts/00-connect.sh --scan       procura Roku na rede (porta 8060)
#
# Não muda nada na TV. Mostra modelo, versão do Roku OS e o modo do controle pela rede.
source "$(dirname "${BASH_SOURCE[0]}")/../lib/roku.sh"

usage() { sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

scan() {
  local subnet="${TV_SUBNET:-}" ip found=0
  if [[ -z "$subnet" ]]; then
    # Subrede da interface padrão (macOS e Linux)
    ip="$( (ipconfig getifaddr en0 || hostname -I | awk '{print $1}') 2>/dev/null | head -n1)"
    subnet="${ip%.*}"
  fi
  [[ -n "$subnet" ]] || die "Não descobri a subrede. Exporte TV_SUBNET=192.168.0"
  log_step "Procurando Roku em $subnet.0/24 (porta 8060)"
  local tmp; tmp="$(mktemp -d)"
  for i in $(seq 1 254); do
    (curl -s -m 2 "http://$subnet.$i:8060/query/device-info" 2>/dev/null | grep -q '<device-info>' && echo "$subnet.$i" > "$tmp/$i") &
  done
  wait
  for f in "$tmp"/*; do
    [[ -f "$f" ]] || continue
    found=1; log_ok "Roku em $(cat "$f")"
  done
  rm -rf "$tmp"
  [[ "$found" == 1 ]] || log_warn "Nenhuma Roku respondeu. Ela está ligada e na mesma rede?"
}

main() {
  require_cmd curl "Instale o curl."
  case "${1:-}" in
    -h|--help) usage; exit 0 ;;
    --scan) scan; exit 0 ;;
    "") ;;
    *)
      export TV_HOST="$1"
      if [[ ! -f "$ROKU_ROOT/config/roku.env" && -z "${TV:-}" ]]; then
        printf 'TV_HOST=%s\n' "$1" > "$ROKU_ROOT/config/roku.env"
        log_ok "Salvei TV_HOST=$1 em roku/config/roku.env"
      fi ;;
  esac
  load_config

  log_step "Conectando em $ECP_URL"
  local info; info="$(ecp_get /query/device-info)"
  log_ok "$(xml_tag friendly-model-name <<<"$info") ($(xml_tag model-name <<<"$info"))"
  log_info "Nome: $(xml_tag user-device-name <<<"$info")"
  log_info "Roku OS $(xml_tag software-version <<<"$info") build $(xml_tag software-build <<<"$info")"
  log_info "Ligada: $(xml_tag power-mode <<<"$info")"
  local mode; mode="$(xml_tag ecp-setting-mode <<<"$info")"
  case "$mode" in
    permissive|enabled|default|"") log_ok "Controle pela rede: ${mode:-padrão}" ;;
    *) log_warn "Controle pela rede: $mode. Para inventário e controle, mude para Permissivo (veja roku/docs/01-primeiros-passos.md)." ;;
  esac
}

main "$@"
