#!/usr/bin/env bash
# 59-launcher-rescue.sh — tela preta após ligar a TV: diagnostica e recupera.
#
# Uso:
#   android-tv/scripts/59-launcher-rescue.sh                 # diagnóstico + tenta abrir o launcher + libera auto-start
#   android-tv/scripts/59-launcher-rescue.sh --tvdetox       # Home volta para o TV Detox (sem reabilitar a fábrica)
#   android-tv/scripts/59-launcher-rescue.sh --stock         # Home volta para a tela inicial do Google TV
#
# O que ele olha: quem é o Home, o que está na tela, se o boot terminou, o
# buffer de crashes (logcat -b crash) e o log do launcher; depois tenta abrir
# o launcher e, se a TCL estiver bloqueando o auto-start, libera com appops
# (APP_AUTO_START + write-settings, senão o reboot perde a mudança).

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

PKG="io.github.diegoafaguiar.sintonia"
ACT="$PKG/.MainActivity"

main() {
  local mode="${1:-}"
  load_config
  ensure_connected
  case "$mode" in
    --tvdetox)
      log_step "Home → TV Detox"
      tv_run pm enable --user 0 com.ansub.tvhome || true
      tv_run cmd package set-home-activity com.ansub.tvhome/.MainActivity
      tv_run input keyevent KEYCODE_HOME; sleep 2
      log_ok "HOME abre: $(home_owner)"; exit 0 ;;
    --stock)
      exec "$ANDROID_ROOT/scripts/50-launcher.sh" restore ;;
  esac

  log_step "1. Estado"
  log_info "boot_completed : $(tv_prop sys.boot_completed)   uptime: $(tv_shell cat /proc/uptime | tr -d '\r' | cut -d. -f1)s"
  log_info "HOME abre      : $(home_owner)"
  log_info "launcher       : $PKG = $(pkg_state "$PKG")"
  log_info "na tela        : $(tv_shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -oE 'topResumedActivity=[^}]*' | head -n1)"
  log_info "auto-start TCL : $(tv_shell appops get "$PKG" APP_AUTO_START 2>/dev/null | tr -d '\r' | head -n1)"

  log_step "2. Crashes recentes (logcat -b crash)"
  tv_shell logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -A25 -iE "tvhome|$PKG" | tail -n 40 | sed 's/^/    /' >&2 || true
  log_step "3. Últimas linhas do launcher e do sistema sobre ele"
  tv_shell logcat -d 2>/dev/null | tr -d '\r' | grep -iE "TvHome|$PKG|FATAL|ANR in|not allowed to start|AppBoot" | tail -n 30 | sed 's/^/    /' >&2 || true

  log_step "4. Liberando auto-start no firmware da TCL (inofensivo se não existir)"
  tv_run appops set "$PKG" APP_AUTO_START allow || true
  tv_run appops write-settings || true

  log_step "5. Abrindo o launcher"
  tv_run am start -n "$ACT"
  sleep 3
  local top; top="$(tv_shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -oE 'topResumedActivity=[^}]*' | head -n1)"
  log_info "na tela agora: $top"
  if [[ "$top" == *"$PKG"* ]]; then
    log_ok "O launcher abriu. Se ao religar a TV a tela voltar a ficar preta, é o auto-start; o passo 4 já ficou gravado. Teste: android-tv/scripts/95-finish.sh"
  else
    log_warn "Não abriu. Veja os crashes acima. Para voltar a ter uma home agora:"
    log_warn "  android-tv/scripts/59-launcher-rescue.sh --tvdetox    (TV Detox)   ou   --stock (Google TV)"
    tv_shell logcat -d -b crash 2>/dev/null | tr -d '\r' | tail -n 60 | sed 's/^/    /' >&2 || true
  fi
}

main "$@"
