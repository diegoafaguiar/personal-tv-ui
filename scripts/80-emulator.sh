#!/usr/bin/env bash
# 80-emulator.sh — emulador Android TV local para testar o launcher antes de mandar para a TV.
#
# Uso:
#   scripts/80-emulator.sh setup     # instala JDK 17, SDK, imagem Android TV e cria o AVD "tvhome" (uma vez)
#   scripts/80-emulator.sh start     # abre a janela do emulador e espera o boot (idempotente)
#   scripts/80-emulator.sh stop      # desliga o emulador
#   scripts/80-emulator.sh status    # JDK, SDK, AVD e se o emulador está no ar
#   scripts/80-emulator.sh wipe      # apaga os dados do AVD (próximo start = TV recém-ligada)
#
# Na janela do emulador: setas = D-pad, Enter = OK, Esc = Voltar, Home = botão Home.
# Variáveis: AVD_NAME (tvhome), AVD_IMAGE (system-images;android-34;android-tv;arm64-v8a),
# EMULATOR_OPTS (extras para o binário emulator), ANDROID_HOME, JAVA_HOME.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
android_sdk_env

AVD_NAME="${AVD_NAME:-tvhome}"
AVD_IMAGE="${AVD_IMAGE:-system-images;android-34;android-tv;arm64-v8a}"
AVD_DEVICE="${AVD_DEVICE:-tv_1080p}"
SDK_PACKAGES=("cmdline-tools;latest" platform-tools "platforms;android-34" "build-tools;34.0.0" emulator "$AVD_IMAGE")
BOOT_TIMEOUT="${BOOT_TIMEOUT:-180}"

usage() { sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

have_avd() { avdmanager list avd -c 2>/dev/null | grep -qx "$AVD_NAME"; }

# Define (ou substitui) chave=valor no config.ini do AVD.
avd_set() {
  local ini="$HOME/.android/avd/$AVD_NAME.avd/config.ini" key="$1" value="$2"
  if grep -q "^$key=" "$ini"; then
    sed -i '' "s|^$key=.*|$key=$value|" "$ini"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ini"
  fi
}

# O perfil tv_1080p vem sem teclado, sem toque e sem GPU. Ajusta para:
# teclado do Mac = D-pad (setas/Enter/Esc), clique do mouse = toque,
# mais RAM/heap que o padrão (512 MB) e GPU do host (Compose for TV fluido).
# Idempotente: roda a cada setup, vale para AVDs antigos.
tune_avd_ini() {
  avd_set hw.ramSize 2048
  avd_set vm.heapSize 256
  avd_set hw.gpu.enabled yes
  avd_set hw.gpu.mode auto
  avd_set hw.keyboard yes
  avd_set hw.dPad yes
  avd_set hw.screen touch
  log_ok "config.ini: teclado, D-pad, toque (mouse), GPU, 2 GB RAM"
}

cmd_setup() {
  if ! command -v brew >/dev/null 2>&1; then
    die "Homebrew não encontrado. Instale JDK 17 e o Android SDK à mão, ou https://brew.sh"
  fi
  log_step "JDK 17"
  if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    brew install openjdk@17
    android_sdk_env
  fi
  log_ok "$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1)"

  log_step "Android command-line tools"
  if ! command -v sdkmanager >/dev/null 2>&1; then
    brew install --cask android-commandlinetools
    android_sdk_env
  fi
  require_cmd sdkmanager "brew install --cask android-commandlinetools"

  log_step "Pacotes do SDK em $ANDROID_HOME (platform-tools, API 34, build-tools, emulador, imagem Android TV)"
  mkdir -p "$ANDROID_HOME"
  yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
  sdkmanager --sdk_root="$ANDROID_HOME" "${SDK_PACKAGES[@]}" 2>&1 \
    | grep -vE '^\[=*|^Loading|deprecated|android.*binary|To learn more|^Tip:' || true
  [[ "${PIPESTATUS[0]}" == "0" ]] || die "sdkmanager falhou. Veja a saída acima (rede? licenças?)."
  android_sdk_env   # cmdline-tools;latest dentro do SDK: só assim o avdmanager enxerga as imagens
  log_ok "SDK pronto"

  log_step "AVD $AVD_NAME ($AVD_DEVICE, $AVD_IMAGE)"
  if have_avd; then
    log_ok "já existe"
  else
    echo no | avdmanager create avd -n "$AVD_NAME" -k "$AVD_IMAGE" -d "$AVD_DEVICE" >/dev/null
    log_ok "criado"
  fi
  tune_avd_ini

  # Gradle acha o SDK por aqui (local.properties é ignorado pelo git).
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$REPO_ROOT/launcher/local.properties"
  log_ok "launcher/local.properties aponta para o SDK"
  log_info "Próximo: scripts/80-emulator.sh start && scripts/85-launcher-dev.sh run"
}

cmd_start() {
  require_cmd emulator "Rode scripts/80-emulator.sh setup"
  have_avd || die "AVD $AVD_NAME não existe. Rode scripts/80-emulator.sh setup"
  local serial; serial="$(emulator_serial)"
  if [[ -n "$serial" ]]; then
    log_ok "Emulador já está no ar ($serial)"
    return 0
  fi
  log_step "Abrindo o emulador $AVD_NAME (a janela demora alguns segundos)"
  # shellcheck disable=SC2086  # EMULATOR_OPTS é lista de flags
  nohup emulator -avd "$AVD_NAME" -gpu auto -no-boot-anim -netdelay none -netspeed full \
    ${EMULATOR_OPTS:-} >"${TMPDIR:-/tmp}/tvhome-emulator.log" 2>&1 </dev/null &
  log_step "Esperando o boot (até ${BOOT_TIMEOUT}s)"
  local t=0
  while (( t < BOOT_TIMEOUT )); do
    serial="$(emulator_serial)"
    if [[ -n "$serial" ]] && [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null </dev/null | tr -d '\r')" == "1" ]]; then
      log_ok "Pronto: $serial"
      log_info "Log do emulador: ${TMPDIR:-/tmp}/tvhome-emulator.log"
      return 0
    fi
    sleep 3; t=$((t+3))
  done
  die "O emulador não terminou o boot em ${BOOT_TIMEOUT}s. Veja ${TMPDIR:-/tmp}/tvhome-emulator.log"
}

cmd_stop() {
  local serial; serial="$(emulator_serial)"
  [[ -n "$serial" ]] || { log_ok "Nenhum emulador rodando"; return 0; }
  adb -s "$serial" emu kill </dev/null >/dev/null 2>&1 || true
  log_ok "Emulador $serial desligado"
}

cmd_status() {
  log_step "Ferramentas"
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then log_ok "JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n1)"; else log_err "JDK 17 não encontrado"; fi
  if [[ -d "$ANDROID_HOME/platforms/android-34" ]]; then log_ok "SDK: $ANDROID_HOME"; else log_err "SDK sem platforms/android-34 em $ANDROID_HOME"; fi
  if command -v emulator >/dev/null 2>&1; then log_ok "emulator: $(emulator -version 2>/dev/null | head -n1)"; else log_err "emulator não encontrado"; fi
  log_step "AVD"
  if have_avd; then log_ok "$AVD_NAME existe"; else log_err "AVD $AVD_NAME não existe (setup)"; fi
  local serial; serial="$(emulator_serial)"
  if [[ -n "$serial" ]]; then
    log_ok "Rodando: $serial (Android $(adb -s "$serial" shell getprop ro.build.version.release </dev/null | tr -d '\r'))"
    local home; home="$(adb -s "$serial" shell cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN </dev/null | tr -d '\r' | sed -n 's/.*name=//p' | head -n1 || true)"
    log_info "HOME: ${home:-?}"
  else
    log_info "Emulador parado"
  fi
}

cmd_wipe() {
  cmd_stop
  confirm "Apagar os dados do AVD $AVD_NAME (apps instalados, ajustes do launcher)?" || die "Cancelado."
  rm -f "$HOME/.android/avd/$AVD_NAME.avd/"*.img "$HOME/.android/avd/$AVD_NAME.avd/"*.qcow2 2>/dev/null || true
  rm -rf "$HOME/.android/avd/$AVD_NAME.avd/snapshots"
  log_ok "AVD limpo"
}

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in
    setup) cmd_setup ;;
    start) cmd_start ;;
    stop) cmd_stop ;;
    status) cmd_status ;;
    wipe) cmd_wipe ;;
    -h|--help|"") usage; [[ -n "$cmd" ]] || exit 1 ;;
    *) die "Comando desconhecido: $cmd" ;;
  esac
}

main "$@"
