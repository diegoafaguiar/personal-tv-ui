#!/usr/bin/env bash
# 85-launcher-dev.sh — ciclo local do launcher: compila no Mac, roda no emulador, só depois manda para a TV.
#
# Uso:
#   scripts/85-launcher-dev.sh build            # ./gradlew assembleDebug (APK em launcher/app/build/outputs/apk/debug)
#   scripts/85-launcher-dev.sh run              # build + instala no emulador + concede READ_TV_LISTINGS + abre
#   scripts/85-launcher-dev.sh run --no-build   # só reinstala o último APK compilado
#   scripts/85-launcher-dev.sh home             # torna o launcher o HOME do emulador (botão Home abre ele)
#   scripts/85-launcher-dev.sh logs             # logcat filtrado pelo processo do launcher (Ctrl+C sai)
#   scripts/85-launcher-dev.sh remote           # controle remoto no terminal: setas, Enter, Backspace, h, m, 0-9, s, q
#   scripts/85-launcher-dev.sh key up|down|left|right|ok|back|home|menu|0-9 [n]   # uma tecla (n vezes)
#   scripts/85-launcher-dev.sh tap X Y          # toque/clique numa coordenada (1080p: 0-1919 x 0-1079)
#   scripts/85-launcher-dev.sh screenshot [arquivo.png]   # captura do emulador (padrão: reports/)
#   scripts/85-launcher-dev.sh push [--release] # instala o APK local na TV (config/tv.env) via 57-launcher-apk.sh
#
# Precisa do emulador no ar para run/home/logs/key/screenshot: scripts/80-emulator.sh start.
# Com a TV conectada em vez do emulador, exporte DEV_SERIAL=ip:5555 para usar run/logs/key nela.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
(( BASH_VERSINFO[0] >= 4 )) || die "Este script precisa de bash 4+ (no macOS: brew install bash). Você está no bash $BASH_VERSION."
android_sdk_env

LAUNCHER_DIR="$REPO_ROOT/launcher"
PKG="tech.futureme.tvhome"
ACTIVITY="$PKG/.MainActivity"
DEBUG_APK="$LAUNCHER_DIR/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="$LAUNCHER_DIR/app/build/outputs/apk/release/app-release.apk"

usage() { sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

# Alvo do adb: DEV_SERIAL explícito, senão o emulador. Reaproveita tv_adb/tv_shell de common.sh.
pick_device() {
  TV_SERIAL="${DEV_SERIAL:-$(emulator_serial)}"
  [[ -n "$TV_SERIAL" ]] || die "Nenhum emulador no ar. Rode scripts/80-emulator.sh start (ou exporte DEV_SERIAL=ip:5555)."
  export TV_SERIAL
  [[ "$(tv_shell echo ok | tr -d '\r')" == "ok" ]] || die "$TV_SERIAL não responde ao adb."
}

cmd_build() {
  local task="${1:-assembleDebug}"
  [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] || die "JDK 17 não encontrado. Rode scripts/80-emulator.sh setup"
  [[ -d "$ANDROID_HOME/platforms/android-34" ]] || die "SDK sem API 34 em $ANDROID_HOME. Rode scripts/80-emulator.sh setup"
  [[ -f "$LAUNCHER_DIR/local.properties" ]] || printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$LAUNCHER_DIR/local.properties"
  log_step "./gradlew $task (primeira vez baixa o Gradle 8.9 e as dependências; depois é rápido)"
  (cd "$LAUNCHER_DIR" && ./gradlew --console=plain -q "$task")
  local apk="$DEBUG_APK"; [[ "$task" == assembleRelease ]] && apk="$RELEASE_APK"
  [[ -f "$apk" ]] || die "APK não gerado: $apk"
  log_ok "$apk ($(du -h "$apk" | cut -f1))"
}

cmd_run() {
  local build=1
  [[ "${1:-}" == "--no-build" ]] && build=0
  (( build )) && cmd_build
  [[ -f "$DEBUG_APK" ]] || die "Sem APK: rode sem --no-build."
  pick_device
  log_step "Instalando em $TV_SERIAL"
  local out; out="$(tv_adb install -r -t "$DEBUG_APK" 2>&1 || true)"
  [[ "$out" == *Success* ]] || die "Instalação falhou: $out"
  tv_shell pm grant "$PKG" android.permission.READ_TV_LISTINGS 2>/dev/null || log_warn "READ_TV_LISTINGS não concedida (aceite na tela)"
  tv_shell am force-stop "$PKG" >/dev/null 2>&1 || true
  tv_shell am start -n "$ACTIVITY" >/dev/null
  log_ok "Launcher aberto. Logs: scripts/85-launcher-dev.sh logs"
}

cmd_home() {
  pick_device
  log_step "Tornando $PKG o HOME de $TV_SERIAL"
  # No emulador o launcher de fábrica pode ficar ligado: o role HOME decide quem atende o botão.
  tv_shell cmd role add-role-holder android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
  tv_shell cmd package set-home-activity "$ACTIVITY" >/dev/null 2>&1 || true
  local owner
  owner="$(tv_shell cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN | tr -d '\r' | sed -n 's/.*packageName=//p' | head -n1 || true)"
  if [[ "$owner" != "$PKG" && "$TV_SERIAL" == emulator-* && "$owner" == com.google.android.tvlauncher ]]; then
    # Só no emulador (AVD descartável): desliga a home de fábrica, como o set-default faz na TV.
    log_info "Role HOME não bastou; desligando a home de fábrica do emulador ($owner)"
    tv_shell pm disable-user --user 0 "$owner" >/dev/null 2>&1 || true
    owner="$(tv_shell cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN | tr -d '\r' | sed -n 's/.*packageName=//p' | head -n1 || true)"
  fi
  if [[ "$owner" == "$PKG" ]]; then
    log_ok "Botão Home abre o launcher"
  else
    log_warn "HOME ainda é $owner. Na TV use: ALLOW_STOCK_LAUNCHER_DISABLE=1 scripts/50-launcher.sh set-default $PKG"
  fi
  tv_shell input keyevent KEYCODE_HOME >/dev/null
}

cmd_logs() {
  pick_device
  log_step "logcat de $PKG em $TV_SERIAL (Ctrl+C sai)"
  tv_adb logcat -c </dev/null || true
  local pid; pid="$(tv_shell pidof "$PKG" | tr -d '\r' || true)"
  if [[ -n "$pid" ]]; then
    log_info "pid $pid (se o app reiniciar, rode logs de novo)"
    exec adb -s "$TV_SERIAL" logcat -v time --pid="$pid"
  else
    log_warn "App não está rodando: mostrando só avisos do sistema e crashes."
    exec adb -s "$TV_SERIAL" logcat -v time "*:W" "TvHome:V" "AndroidRuntime:E"
  fi
}

cmd_key() {
  pick_device
  local name="${1:-}" n="${2:-1}" code
  case "$name" in
    up) code=KEYCODE_DPAD_UP ;; down) code=KEYCODE_DPAD_DOWN ;;
    left) code=KEYCODE_DPAD_LEFT ;; right) code=KEYCODE_DPAD_RIGHT ;;
    ok|enter|center) code=KEYCODE_DPAD_CENTER ;; back) code=KEYCODE_BACK ;;
    home) code=KEYCODE_HOME ;; menu) code=KEYCODE_MENU ;;
    [0-9]) code="KEYCODE_$name" ;;
    *) die "Tecla desconhecida: $name (up|down|left|right|ok|back|home|menu|0-9)" ;;
  esac
  local i; for ((i=0; i<n; i++)); do tv_shell input keyevent "$code" >/dev/null; done
}

cmd_tap() {
  pick_device
  [[ "${1:-}" =~ ^[0-9]+$ && "${2:-}" =~ ^[0-9]+$ ]] || die "Uso: tap X Y (pixels, tela 1920x1080)"
  tv_shell input tap "$1" "$2" >/dev/null
}

# Controle remoto no terminal: lê uma tecla por vez e manda o keyevent. Sem eco, sem Enter.
cmd_remote() {
  pick_device
  [[ -t 0 ]] || die "remote precisa de um terminal interativo."
  log_step "Controle remoto em $TV_SERIAL"
  log_info "setas = D-pad · Enter = OK · Backspace ou Esc = Voltar · h = Home · m = Menu · 0-9 = dígitos"
  log_info "s = screenshot em reports/ · q = sair"
  local k rest code
  while IFS= read -rsn1 k; do
    code=""
    case "$k" in
      $'\e')
        # Seta = ESC [ A/B/C/D chegando junto; ESC sozinho = Voltar.
        if IFS= read -rsn2 -t 0.05 rest; then
          case "$rest" in
            '[A') code=KEYCODE_DPAD_UP ;; '[B') code=KEYCODE_DPAD_DOWN ;;
            '[C') code=KEYCODE_DPAD_RIGHT ;; '[D') code=KEYCODE_DPAD_LEFT ;;
          esac
        else
          code=KEYCODE_BACK
        fi ;;
      ''|$'\n'|$'\r') code=KEYCODE_DPAD_CENTER ;;
      $'\x7f'|$'\b') code=KEYCODE_BACK ;;
      h|H) code=KEYCODE_HOME ;;
      m|M) code=KEYCODE_MENU ;;
      [0-9]) code="KEYCODE_$k" ;;
      s|S) cmd_screenshot; continue ;;
      q|Q) log_ok "Saindo"; return 0 ;;
      *) continue ;;
    esac
    [[ -n "$code" ]] && tv_shell input keyevent "$code" >/dev/null
  done
}

cmd_screenshot() {
  pick_device
  local out="${1:-$REPORTS_DIR/emulator-$(timestamp).png}"
  mkdir -p "$(dirname "$out")"
  tv_adb exec-out screencap -p > "$out"
  log_ok "$out"
}

cmd_push() {
  local variant=debug
  [[ "${1:-}" == "--release" ]] && variant=release
  local task=assembleDebug; [[ "$variant" == release ]] && task=assembleRelease
  cmd_build "$task"
  local apk="$DEBUG_APK"; [[ "$variant" == release ]] && apk="$RELEASE_APK"
  log_step "Enviando o $variant para a TV (config/tv.env)"
  exec "$REPO_ROOT/scripts/57-launcher-apk.sh" --file "$apk"
}

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in
    build) cmd_build "$@" ;;
    run) cmd_run "$@" ;;
    home) cmd_home ;;
    logs) cmd_logs ;;
    key) cmd_key "$@" ;;
    tap) cmd_tap "$@" ;;
    remote) cmd_remote ;;
    screenshot) cmd_screenshot "$@" ;;
    push) cmd_push "$@" ;;
    -h|--help|"") usage; [[ -n "$cmd" ]] || exit 1 ;;
    *) die "Comando desconhecido: $cmd" ;;
  esac
}

main "$@"
