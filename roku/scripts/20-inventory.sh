#!/usr/bin/env bash
# roku/scripts/20-inventory.sh — inventário da Roku TV em reports/.
#
# Uso: roku/scripts/20-inventory.sh
#
# Gera em reports/:
#   <data>_<marca>_<modelo>-inventory.md   aparelho, privacidade e canais, com notas
#   <data>_<marca>_<modelo>-device-info.xml e -apps.xml   respostas cruas do ECP
#
# Não muda nada na TV. A Roku não tem backup nem desativação de apps de fábrica:
# o inventário serve de registro do "antes" e de roteiro para a limpeza manual.
source "$(dirname "${BASH_SOURCE[0]}")/../lib/roku.sh"

main() {
  case "${1:-}" in -h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
  require_cmd curl "Instale o curl."
  load_config

  log_step "Lendo a TV em $ECP_URL"
  local info apps active
  info="$(ecp_get /query/device-info)"
  apps="$(ecp_get /query/apps)"
  active="$(ecp_get /query/active-app | sed -n 's:.*<app[^>]*>\(.*\)</app>.*:\1:p' | head -n1)"

  local vendor model base
  vendor="$(xml_tag vendor-name <<<"$info")"; model="$(xml_tag model-name <<<"$info")"
  base="$REPORTS_DIR/$(timestamp)_${vendor:-Roku}_${model:-TV}"
  mkdir -p "$REPORTS_DIR"
  printf '%s\n' "$info" > "$base-device-info.xml"
  printf '%s\n' "$apps" > "$base-apps.xml"

  local list n_apps n_inputs
  list="$(parse_apps <<<"$apps")"
  n_apps="$(awk -F'\t' '$2=="appl"' <<<"$list" | grep -c . || true)"
  n_inputs="$(awk -F'\t' '$2=="tvin"' <<<"$list" | grep -c . || true)"

  yn() { case "$1" in true) echo sim ;; false) echo não ;; *) echo "${1:-?}" ;; esac; }
  {
    echo "# Inventário: $(xml_tag user-device-name <<<"$info")"
    echo
    echo "Gerado em $(date '+%d/%m/%Y %H:%M') a partir de $ECP_URL (ECP, só leitura)."
    echo
    echo "## Aparelho"
    echo
    echo "| | |"
    echo "|---|---|"
    echo "| Marca / modelo | $vendor $model ($(xml_tag model-number <<<"$info")) |"
    echo "| Roku OS | $(xml_tag software-version <<<"$info") build $(xml_tag software-build <<<"$info") |"
    echo "| Resolução da interface | $(xml_tag ui-resolution <<<"$info") |"
    echo "| Sintonizador | $(xml_tag tuner-type <<<"$info") |"
    echo "| Rede | $(xml_tag network-type <<<"$info") |"
    echo "| Idioma / país | $(xml_tag language <<<"$info") / $(xml_tag country <<<"$info") |"
    echo "| Controle pela rede (ECP) | $(xml_tag ecp-setting-mode <<<"$info") |"
    echo "| Modo de desenvolvedor | $(yn "$(xml_tag developer-enabled <<<"$info")") |"
    echo "| Busca por voz | $(yn "$(xml_tag voice-search-enabled <<<"$info")") |"
    echo "| App aberto agora | ${active:-?} |"
    echo
    echo "## Canais instalados ($n_apps) e entradas ($n_inputs)"
    echo
    echo "Para remover um canal: na tela inicial, selecione-o, aperte ✱ no controle › Remover canal."
    echo "Entradas (TV ao vivo, HDMI) não se removem; dá para escondê-las em Configurações › Tela inicial."
    echo
    echo "| Canal | ID | Versão | Nota |"
    echo "|---|---|---|---|"
    local id type ver name note
    while IFS=$'\t' read -r id type ver name; do
      [[ -n "$id" ]] || continue
      note="$(channel_note "$id")"
      [[ "$type" == tvin ]] && note="entrada da TV${note:+; $note}"
      echo "| $name | $id | $ver | ${note:-} |"
    done <<<"$list"
    echo
    echo "## Próximo passo"
    echo
    echo "Limpeza e privacidade pelo controle: roku/docs/02-limpeza.md."
  } > "$base-inventory.md"

  log_ok "$n_apps canais e $n_inputs entradas"
  log_ok "Relatório: ${base#"$REPO_ROOT"/}-inventory.md"
}

main "$@"
