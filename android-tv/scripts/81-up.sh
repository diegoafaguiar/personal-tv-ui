#!/usr/bin/env bash
# 81-up.sh — um comando para ver o launcher rodando no Mac: prepara o que faltar, liga o
# emulador Android TV, compila, instala e abre o "Sintonia".
#
# Uso:
#   android-tv/scripts/81-up.sh          (ou: make up, ou dois cliques em rodar-mac.command)
#
# Idempotente: na primeira vez instala JDK 17, SDK e a imagem Android TV (uns 10 min, ~6 GB);
# depois só liga o emulador (se estiver parado) e recompila (~20 s). Nunca mexe nas TVs da casa:
# o alvo é sempre o emulador.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

main() {
  case "${1:-}" in -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
  # O alvo é o emulador, mesmo que o terminal tenha DEV_SERIAL de uma TV
  unset DEV_SERIAL
  local s="$ANDROID_ROOT/scripts"

  android_sdk_env
  if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" || ! -d "$ANDROID_HOME/platforms/android-34" ]] \
     || ! command -v emulator >/dev/null 2>&1 || ! avdmanager list avd -c 2>/dev/null | grep -qx "${AVD_NAME:-tvhome}"; then
    log_step "Primeira vez: preparando JDK, SDK e o emulador Android TV"
    "$s/80-emulator.sh" setup
  fi
  "$s/80-emulator.sh" start
  "$s/85-launcher-dev.sh" run
  "$s/85-launcher-dev.sh" home

  log_step "Pronto. Na janela do emulador:"
  log_info "setas = controle, Enter = OK, Esc = Voltar, clique do mouse = foca e abre"
  log_info "Editou o código? make dev (recompila em ~20 s).  Logs: make dev-logs.  Desligar: make emulator-stop"
}

main "$@"
