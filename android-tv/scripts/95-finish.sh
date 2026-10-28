#!/usr/bin/env bash
# 95-finish.sh — encerramento seguro: espera 30 s, reinicia a TV uma vez,
# confirma que ela voltou normal, confere o debloat e mostra memória antes/depois.
#
# Uso:
#   android-tv/scripts/95-finish.sh                 # usa o backup mais recente como "antes"
#   android-tv/scripts/95-finish.sh --no-reboot     # só verifica
#
# O que é "voltou normal": adb reconecta, sys.boot_completed=1, HOME abre o
# mesmo app de antes (ou o novo launcher), e 35-verify.sh não acusa pacote
# que voltou.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

wait_for_tv() {
  local deadline=$(( $(date +%s) + 240 )) booted
  log_info "Esperando a TV voltar (até 4 min)..."
  while [[ $(date +%s) -lt $deadline ]]; do
    if adb connect "$TV_SERIAL" >/dev/null 2>&1 </dev/null && [[ "$(adb -s "$TV_SERIAL" get-state 2>/dev/null </dev/null)" == "device" ]]; then
      booted="$(tv_prop sys.boot_completed 2>/dev/null || true)"
      [[ "$booted" == "1" ]] && return 0
    fi
    sleep 5
  done
  return 1
}

main() {
  local do_reboot=1
  [[ "${1:-}" == "--no-reboot" ]] && do_reboot=0
  load_config
  ensure_connected
  local backup; backup="$(require_backup)"

  local mem_before="?" mem_now home_before
  [[ -f "$backup/proc-meminfo.txt" ]] && mem_before="$(awk '/MemAvailable/ {printf "%d", $2/1024}' "$backup/proc-meminfo.txt" 2>/dev/null)"
  [[ -z "$mem_before" ]] && mem_before="?"
  home_before="$(home_owner)"
  mem_now="$(mem_available_mb)"
  log_step "Antes do reinício"
  log_info "Memória livre no backup: ${mem_before} MB   agora (sem reiniciar): ${mem_now} MB"
  log_info "HOME abre: $home_before"

  if [[ "$do_reboot" == "1" ]]; then
    if [[ "$DRY_RUN" == "1" ]]; then log_warn "dry-run: esperaria 30 s e reiniciaria."; exit 0; fi
    log_step "Esperando 30 s para o sistema gravar as mudanças"
    sleep 30
    log_step "Reiniciando a TV"
    tv_run reboot || true
    sleep 20
    wait_for_tv || die "A TV não voltou em 4 min. Veja android-tv/docs/07-troubleshooting.md (tela preta / não conecta)."
    log_ok "TV ligou: boot_completed=1"
    log_info "Esperando 60 s para os serviços subirem antes de medir..."
    sleep 60
  fi

  log_step "Depois"
  local home_now; home_now="$(home_owner)"
  log_info "HOME abre: $home_now"
  [[ "$home_now" == "$home_before" ]] || log_warn "HOME mudou após o reinício (antes: $home_before)."
  log_info "Pacotes desabilitados: $(pm_list -d | wc -l | tr -d ' ')"
  log_info "Memória livre: ${mem_before} MB (backup) → $(mem_available_mb) MB (agora), de $(mem_total_mb) MB"
  if ls "$REPORTS_DIR"/*-debloat.log >/dev/null 2>&1; then
    "$ANDROID_ROOT/scripts/35-verify.sh" || log_warn "Algum pacote voltou; veja acima."
  fi
  log_ok "Pronto. Para desligar a depuração por rede: android-tv/scripts/00-connect.sh --disconnect e desligue na TV."
}

main "$@"
