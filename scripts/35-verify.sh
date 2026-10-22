#!/usr/bin/env bash
# 35-verify.sh — confere, depois de um reboot, se o que o debloat fez continua valendo.
#
# Uso:
#   scripts/35-verify.sh                 # usa todos os reports/*-debloat.log
#   scripts/35-verify.sh reports/20261022-011002_..-debloat.log
#
# Por que existe: alguns firmwares TCL reabilitam pacotes no boot (visto com
# com.tcl.esticker num BeyondTV2). Se algo "voltou", este script lista, e a
# alternativa é "30-debloat.sh --uninstall" para esses pacotes, que usa outro
# mecanismo (desinstalação para o usuário 0) e costuma persistir.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

main() {
  local -a logs=("$@")
  if [[ ${#logs[@]} -eq 0 ]]; then
    mapfile -t logs < <(find "$REPORTS_DIR" -name '*-debloat.log' | sort)
  fi
  [[ ${#logs[@]} -gt 0 ]] || die "Nenhum log de debloat em $REPORTS_DIR."

  load_config
  ensure_connected
  local uptime
  uptime="$(tv_shell cat /proc/uptime | tr -d '\r' | cut -d. -f1)"
  log_info "Uptime da TV: ${uptime}s $( [[ "$uptime" -lt 120 ]] && echo '(ainda inicializando; resultados podem ser parciais)' )"

  local action pkg state ok=0 reverted=0 log
  for log in "${logs[@]}"; do
    log_step "$(basename "$log")"
    while read -r action pkg; do
      [[ -z "$action" || "$action" == \#* ]] && continue
      state="$(pkg_state "$pkg")"
      case "$action:$state" in
        disable:disabled|uninstall:uninstalled|uninstall:absent) ok=$((ok+1)); log_ok "$pkg ($state)" ;;
        *) reverted=$((reverted+1)); log_warn "$pkg VOLTOU: esperado $action, está $state" ;;
      esac
    done < "$log"
  done
  echo >&2
  log_info "Persistiram: $ok   Voltaram: $reverted   (desabilitados agora: $(pm_list -d | wc -l | tr -d ' '))"
  if [[ "$reverted" -gt 0 ]]; then
    log_warn "O firmware reabilitou pacotes no boot. Veja docs/07-troubleshooting.md ('voltou sozinho')."
    exit 2
  fi
}

main "$@"
