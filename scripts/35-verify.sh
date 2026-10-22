#!/usr/bin/env bash
# 35-verify.sh — confere, depois de um reboot, se o que o debloat fez continua valendo.
#
# Uso:
#   scripts/35-verify.sh                 # usa todos os reports/*-debloat.log
#   scripts/35-verify.sh --fix           # reaplica o que voltou (pergunta uma vez) e espera a gravação
#   scripts/35-verify.sh reports/20261022-011002_..-debloat.log
#
# Por que existe: o Android grava o estado dos pacotes com alguns segundos de
# atraso; um reboot logo após "pm disable-user" perde a mudança (causa real do
# "tudo voltou" na unidade de referência). Mais raro: firmwares que reabilitam
# pacotes no boot (com.tcl.esticker num BeyondTV2). Em ambos os casos, --fix
# reaplica e aguarda 15 s antes de devolver o prompt.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

main() {
  local fix=0
  local -a logs=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --fix) fix=1; shift ;;
      --yes|-y) export ASSUME_YES=1; shift ;;
      *) logs+=("$1"); shift ;;
    esac
  done
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
  local -a reverted_list=()
  for log in "${logs[@]}"; do
    log_step "$(basename "$log")"
    while read -r action pkg; do
      [[ -z "$action" || "$action" == \#* ]] && continue
      state="$(pkg_state "$pkg")"
      case "$action:$state" in
        disable:disabled|uninstall:uninstalled|uninstall:absent) ok=$((ok+1)); log_ok "$pkg ($state)" ;;
        *) reverted=$((reverted+1)); reverted_list+=("$action $pkg"); log_warn "$pkg VOLTOU: esperado $action, está $state" ;;
      esac
    done < "$log"
  done
  echo >&2
  log_info "Persistiram: $ok   Voltaram: $reverted   (desabilitados agora: $(pm_list -d | wc -l | tr -d ' '))"
  if [[ "$reverted" -gt 0 ]]; then
    if [[ "$fix" == "1" ]]; then
      log_step "Reaplicando ${#reverted_list[@]} mudanças"
      confirm "Reaplicar agora?" || die "Cancelado."
      local entry
      for entry in "${reverted_list[@]}"; do
        action="${entry%% *}"; pkg="${entry#* }"
        if [[ "$action" == "uninstall" ]]; then tv_run pm uninstall -k --user 0 "$pkg" || true
        else tv_run pm disable-user --user 0 "$pkg" || true; fi
        log_ok "$pkg → $(pkg_state "$pkg")"
      done
      log_step "Aguardando ${PM_WRITE_WAIT:-15} s para o Android gravar o estado (não reinicie antes)"
      [[ "$DRY_RUN" == "1" ]] || sleep "${PM_WRITE_WAIT:-15}"
      log_info "Agora reinicie com scripts/95-finish.sh (ele espera mais 30 s) e rode 35-verify.sh de novo."
      exit 0
    fi
    log_warn "Mudanças perdidas (reboot cedo demais) ou reabilitadas pelo firmware. Reaplique com: scripts/35-verify.sh --fix"
    exit 2
  fi
}

main "$@"
