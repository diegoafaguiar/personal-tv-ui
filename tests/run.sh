#!/usr/bin/env bash
# tests/run.sh — testa os scripts contra um adb falso (tests/fake-adb/adb).
# Cobre o fluxo inteiro: backup → inventário → debloat → tune → launcher → rollback,
# e as proteções (never-disable, HOME guard, Wi-Fi do setup wizard).
# shellcheck disable=SC2016,SC2034  # as asserções são strings avaliadas por "check" via eval
set -euo pipefail
exec </dev/null   # stdin fechado: o adb falso (como o real) consome stdin, e aqui não há terminal
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

export FAKE_ADB_STATE="$WORK/state"
export BACKUPS_DIR="$WORK/backups" REPORTS_DIR="$WORK/reports"
export PATH="$ROOT/tests/fake-adb:$PATH"
export TV_HOST=10.0.0.9 ASSUME_YES=1
mkdir -p "$FAKE_ADB_STATE" "$BACKUPS_DIR" "$REPORTS_DIR"

pass=0; fail=0
ok()   { pass=$((pass+1)); printf '  ✔ %s\n' "$1"; }
bad()  { fail=$((fail+1)); printf '  ✖ %s\n' "$1"; }
check(){ if eval "$2"; then ok "$1"; else bad "$1"; fi; }
has()  { grep -qx "$2" "$FAKE_ADB_STATE/$1"; }

reset_state() {
  cat > "$FAKE_ADB_STATE/installed" <<'L'
android
com.android.vending
com.google.android.gms
com.google.android.apps.tv.launcherx
com.google.android.tungsten.setupwraith
com.tcl.tv
com.tcl.tvinput
com.tcl.ttvs
com.tcl.logkit
com.tcl.usercenter
com.tcl.waterfall.overseas
com.tcl.exhibit
com.tcl.smartalexa
com.spocky.projengmenu
com.android.dreams.basic
org.example.userapp
L
  printf 'com.tcl.exhibit\n' > "$FAKE_ADB_STATE/disabled"
  { cat "$FAKE_ADB_STATE/installed"; echo com.tcl.esticker; } > "$FAKE_ADB_STATE/known"   # esticker: "desinstalado" (user 0)
  printf 'com.spocky.projengmenu\norg.example.userapp\n' > "$FAKE_ADB_STATE/third"
  cat > "$FAKE_ADB_STATE/settings" <<'L'
global:window_animation_scale=1.0
global:transition_animation_scale=1.0
global:animator_duration_scale=1.0
secure:screensaver_enabled=1
secure:screensaver_components=com.google.android.apps.tv.dreamx/.Dream
L
  printf 'com.google.android.apps.tv.launcherx/.Home\ncom.google.android.tungsten.setupwraith/.RecoveryActivity\ncom.spocky.projengmenu/.Main\n' > "$FAKE_ADB_STATE/home"
  cat > "$FAKE_ADB_STATE/props" <<'L'
[ro.product.brand]: [TCL]
[ro.product.model]: [Smart TV Pro]
[ro.product.device]: [G08]
[ro.build.version.release]: [14]
[ro.build.version.sdk]: [34]
[ro.build.display.id]: [UTT2.250416.001]
[ro.build.fingerprint]: [TCL/G08/G08:14/UTT2/1:user/release-keys]
[ro.board.platform]: [mt5896]
[ro.product.cpu.abi]: [armeabi-v7a]
L
  echo com.android.tv.settings > "$FAKE_ADB_STATE/wifi-owner"
  : > "$FAKE_ADB_STATE/calls.log"
}

echo "== 00-connect"
reset_state
out="$("$ROOT/scripts/00-connect.sh" 2>&1)"
check "conecta e identifica o launcher" '[[ "$out" == *"launcherx"* && "$out" == *"TCL Smart TV Pro"* ]]'

echo "== 30-debloat sem backup"
out="$("$ROOT/scripts/30-debloat.sh" --dry-run tcl-telemetry 2>&1 || true)"
check "recusa rodar sem backup" '[[ "$out" == *"Nenhum backup"* ]]'

echo "== 10-backup"
"$ROOT/scripts/10-backup.sh" >/dev/null 2>&1
bk="$(cat "$BACKUPS_DIR/LATEST")"
check "cria pasta e LATEST" '[[ -d "$BACKUPS_DIR/$bk" ]]'
check "registra pacotes habilitados" 'grep -qx com.tcl.ttvs "$BACKUPS_DIR/$bk/packages-enabled.txt"'
check "registra pacotes desabilitados de fábrica" 'grep -qx com.tcl.exhibit "$BACKUPS_DIR/$bk/packages-disabled.txt"'
check "registra settings rastreadas" 'grep -q "^global:window_animation_scale=1.0" "$BACKUPS_DIR/$bk/settings.env"'
check "registra dono do HOME" '[[ "$(cat "$BACKUPS_DIR/$bk/home-owner.txt")" == "com.google.android.apps.tv.launcherx/.Home" ]]'

echo "== 20-inventory"
"$ROOT/scripts/20-inventory.sh" >/dev/null 2>&1
csv="$(ls "$REPORTS_DIR"/*-inventory.csv)"
check "com.tcl.tv é PRECISA" 'grep -q "^com.tcl.tv,\"TV ao vivo / HDMI\",ligado,PRECISA" "$csv"'
check "com.tcl.ttvs é PODE com RAM/APK medidos" 'grep -q "^com.tcl.ttvs,\"TCL TTVS\",ligado,PODE,tcl-telemetry,20,12,sim" "$csv"'
check "com.tcl.smartalexa é PODE" 'grep -q "^com.tcl.smartalexa,\"Alexa\",ligado,PODE" "$csv"'
check "launcherx é PRECISA (regra: nunca desligar)" 'grep -q "^com.google.android.apps.tv.launcherx,.*,ligado,PRECISA" "$csv"'
check "app do usuário é USA e fora de /system" 'grep -q "^org.example.userapp,\"\",ligado,USA,,20,12,nao" "$csv"'
check "esticker aparece como desinstalado" 'grep -q "^com.tcl.esticker,.*,desinstalado" "$csv"'
check "markdown tem memória livre" 'grep -q "Memória livre agora: 585 MB de 1855 MB" "$REPORTS_DIR"/*-inventory.md'
out="$("$ROOT/scripts/25-review.sh" 2>&1 || true)"
check "25-review recusa sem terminal" '[[ "$out" == *"interativo"* ]]'

echo "== 30-debloat dry-run e proteções"
printf 'com.tcl.tv\ncom.tcl.ttvs\ncom.mediatek.foo\n' > "$WORK/custom.list"
sed -i '1i # risk: safe' "$WORK/custom.list"
out="$("$ROOT/scripts/30-debloat.sh" --dry-run "$WORK/custom.list" 2>&1)"
check "recusa com.tcl.tv (never-disable)" '[[ "$out" == *"Recusados"* && "$out" == *"com.tcl.tv"* ]]'
check "recusa glob com.mediatek.*" '[[ "$out" == *"com.mediatek.foo"* ]]'
check "dry-run não executa pm" '! grep -q "pm disable-user" "$FAKE_ADB_STATE/calls.log"'
out="$("$ROOT/scripts/30-debloat.sh" --dry-run launcher-stock 2>&1 || true)"
check "recusa o perfil launcher-stock" '[[ "$out" == *"50-launcher.sh"* ]]'

echo "== 30-debloat aplica"
"$ROOT/scripts/30-debloat.sh" tcl-telemetry tcl-promo-ads tcl-retail-demo >/dev/null 2>&1
check "desabilita com.tcl.ttvs" 'has disabled com.tcl.ttvs'
check "desabilita com.tcl.waterfall.overseas" 'has disabled com.tcl.waterfall.overseas'
check "desabilita com.tcl.logkit (3ª linha da lista: regressão do stdin engolido pelo adb)" 'has disabled com.tcl.logkit'
check "desabilita com.tcl.usercenter (última linha de outra lista)" 'has disabled com.tcl.usercenter'
check "não toca com.tcl.tv" '! has disabled com.tcl.tv'
check "um pacote por chamada" 'grep -q "shell pm disable-user --user 0 com.tcl.ttvs$" "$FAKE_ADB_STATE/calls.log"'
check "gera log de auditoria" 'grep -q "^disable com.tcl.ttvs" "$REPORTS_DIR"/*-debloat.log'
check "gera arquivo de desfazer executável" 'grep -q "pm enable --user 0 com.tcl.ttvs" "$REPORTS_DIR"/*-undo.sh && [[ -x "$(ls "$REPORTS_DIR"/*-undo.sh | head -n1)" ]]'
out="$("$ROOT/scripts/30-debloat.sh" --dry-run launcher-stock 2>&1 || true)"
check "launcherx recusado mesmo via perfil" '[[ "$out" == *"50-launcher.sh"* ]]'
out="$(ALLOW_STOCK_LAUNCHER_DISABLE=0 "$ROOT/scripts/50-launcher.sh" set-default 2>&1 || true)"
check "50-launcher set-default recusa por regra" '[[ "$out" == *"Regra ativa"* ]]'

echo "== 30-debloat --uninstall app de terceiros"
printf '# risk: optional\norg.example.userapp\n' > "$WORK/apps.list"
out="$("$ROOT/scripts/30-debloat.sh" --uninstall "$WORK/apps.list" 2>&1)"
check "app de terceiros desinstalado conta como aplicado" '[[ "$out" == *"Aplicados: 1"* ]]'

echo "== 35-verify"
out="$("$ROOT/scripts/35-verify.sh" 2>&1)"
check "tudo persistiu" '[[ "$out" == *"Voltaram: 0"* ]]'
printf 'com.tcl.exhibit\n' > "$FAKE_ADB_STATE/disabled"    # simula firmware reabilitando tudo no boot
out="$("$ROOT/scripts/35-verify.sh" 2>&1 || true)"
check "detecta pacotes que voltaram" '[[ "$out" == *"com.tcl.ttvs VOLTOU"* ]]'
"$ROOT/scripts/30-debloat.sh" tcl-telemetry tcl-promo-ads tcl-retail-demo >/dev/null 2>&1   # reaplica para o resto dos testes

echo "== 40-tune"
"$ROOT/scripts/40-tune.sh" --animations 0.5 --screensaver basic --private-dns dns.adguard.com --sleep-after 5 >/dev/null 2>&1
check "animações 0.5" 'grep -q "^global:animator_duration_scale=0.5" "$FAKE_ADB_STATE/settings"'
check "suspender após 5 min" 'grep -q "^system:screen_off_timeout=300000" "$FAKE_ADB_STATE/settings"'
check "screensaver básico" 'grep -q "^secure:screensaver_components=com.android.dreams.basic/.Colors" "$FAKE_ADB_STATE/settings"'
check "private dns" 'grep -q "^global:private_dns_specifier=dns.adguard.com" "$FAKE_ADB_STATE/settings"'

echo "== 50-launcher guardas (com ALLOW_STOCK_LAUNCHER_DISABLE=1, só para testar as guardas)"
export ALLOW_STOCK_LAUNCHER_DISABLE=1
echo com.google.android.tungsten.setupwraith > "$FAKE_ADB_STATE/wifi-owner"
out="$("$ROOT/scripts/50-launcher.sh" set-default 2>&1 || true)"
check "bloqueia quando o Wi-Fi pertence ao setupwraith" '[[ "$out" == *"pertence ao com.google.android.tungsten.setupwraith"* ]]'
check "não desabilitou o launcher" '! has disabled com.google.android.apps.tv.launcherx'
echo com.android.tv.settings > "$FAKE_ADB_STATE/wifi-owner"
printf 'com.google.android.apps.tv.launcherx/.Home\ncom.google.android.tungsten.setupwraith/.RecoveryActivity\n' > "$FAKE_ADB_STATE/home"
out="$("$ROOT/scripts/50-launcher.sh" set-default 2>&1 || true)"
check "bloqueia se o novo launcher não é candidato a HOME" '[[ "$out" == *"não aparece como candidato"* ]]'

echo "== 50-launcher set-default"
printf 'com.google.android.apps.tv.launcherx/.Home\ncom.google.android.tungsten.setupwraith/.RecoveryActivity\ncom.spocky.projengmenu/.Main\n' > "$FAKE_ADB_STATE/home"
out="$("$ROOT/scripts/50-launcher.sh" set-default 2>&1)"
check "HOME passa ao Projectivy" '[[ "$out" == *"HOME agora abre com.spocky.projengmenu/.Main"* ]]'
check "launcherx desabilitado" 'has disabled com.google.android.apps.tv.launcherx'
check "setupwraith desabilitado" 'has disabled com.google.android.tungsten.setupwraith'

echo "== 50-launcher reversão automática"
reset_state2() { del_disabled() { grep -vx "$1" "$FAKE_ADB_STATE/disabled" > "$FAKE_ADB_STATE/d.tmp" || true; mv "$FAKE_ADB_STATE/d.tmp" "$FAKE_ADB_STATE/disabled"; }; del_disabled com.google.android.apps.tv.launcherx; del_disabled com.google.android.tungsten.setupwraith; }
reset_state2
# Simula um launcher que é candidato mas perde para um terceiro inesperado
printf 'com.google.android.apps.tv.launcherx/.Home\ncom.google.android.tungsten.setupwraith/.RecoveryActivity\ncom.tcl.tv/.FallbackHome\ncom.spocky.projengmenu/.Main\n' > "$FAKE_ADB_STATE/home"
out="$("$ROOT/scripts/50-launcher.sh" set-default 2>&1 || true)"
check "detecta HOME errado e reverte" '[[ "$out" == *"Revertendo"* ]] && ! has disabled com.google.android.apps.tv.launcherx'

echo "== 90-rollback"
printf 'com.google.android.apps.tv.launcherx/.Home\ncom.google.android.tungsten.setupwraith/.RecoveryActivity\ncom.spocky.projengmenu/.Main\n' > "$FAKE_ADB_STATE/home"
"$ROOT/scripts/50-launcher.sh" set-default >/dev/null 2>&1
"$ROOT/scripts/30-debloat.sh" --uninstall tcl-extras >/dev/null 2>&1   # smartalexa vira "uninstalled"
check "smartalexa desinstalado (user 0)" '! has installed com.tcl.smartalexa && has known com.tcl.smartalexa'
# Alguém reabilitou um pacote que era desabilitado de fábrica
: > "$FAKE_ADB_STATE/disabled.tmp"; grep -vx com.tcl.exhibit "$FAKE_ADB_STATE/disabled" > "$FAKE_ADB_STATE/disabled.tmp" || true; mv "$FAKE_ADB_STATE/disabled.tmp" "$FAKE_ADB_STATE/disabled"
out="$("$ROOT/scripts/90-rollback.sh" --dry-run 2>&1)"
check "dry-run lista reinstalação" '[[ "$out" == *"Reinstalar (install-existing): 1"* ]]'
check "dry-run não altera" 'has disabled com.tcl.ttvs'
"$ROOT/scripts/90-rollback.sh" >/dev/null 2>&1
check "reabilita com.tcl.ttvs" '! has disabled com.tcl.ttvs'
check "reabilita launcherx" '! has disabled com.google.android.apps.tv.launcherx'
check "reabilita setupwraith" '! has disabled com.google.android.tungsten.setupwraith'
check "reinstala smartalexa" 'has installed com.tcl.smartalexa'
check "re-desabilita exhibit (estado de fábrica)" 'has disabled com.tcl.exhibit'
check "restaura animações" 'grep -q "^global:window_animation_scale=1.0" "$FAKE_ADB_STATE/settings"'
check "restaura screensaver" 'grep -q "^secure:screensaver_components=com.google.android.apps.tv.dreamx/.Dream" "$FAKE_ADB_STATE/settings"'
check "remove private dns (chave era null)" '! grep -q "private_dns_specifier" "$FAKE_ADB_STATE/settings"'
out="$("$ROOT/scripts/90-rollback.sh" --dry-run 2>&1)"
check "segundo rollback: nada a fazer" '[[ "$out" == *"já está no estado do backup"* ]]'

echo "== 95-finish --no-reboot"
out="$("$ROOT/scripts/95-finish.sh" --no-reboot 2>&1 || true)"
check "mostra memória antes/depois e HOME" '[[ "$out" == *"Memória livre:"* && "$out" == *"HOME abre:"* ]]'

echo "== 70-live-tv"
out="$("$ROOT/scripts/70-live-tv.sh" channels 2>&1)"
check "lista canais do sintonizador" '[[ "$out" == *"4.1      Globo SP"*"id=12"* ]]'
out="$("$ROOT/scripts/70-live-tv.sh" now 4 2>&1)"
check "resolve canal 4 → 4.1 e mostra o programa de agora" '[[ "$out" == *"Jornal Nacional"* ]]'
out="$("$ROOT/scripts/70-live-tv.sh" guide Globo 2>&1)"
check "resolve por nome e marca o programa atual" '[[ "$out" == *"▶"*"Jornal Nacional"* && "$out" == *"Novela das Nove"* ]]'
"$ROOT/scripts/70-live-tv.sh" tune 4 >/dev/null 2>&1
check "sintoniza via VIEW content://.../channel/12" 'grep -q "am start -a android.intent.action.VIEW -d content://android.media.tv/channel/12" "$FAKE_ADB_STATE/calls.log"'

out="$("$ROOT/scripts/70-live-tv.sh" doctor 2>&1)"
check "doctor mostra inputs, apps, intents e provedores" '[[ "$out" == *"Há canais visíveis"* && "$out" == *"type=TUNER"* && "$out" == *"Scheme: \"tcltv\""* && "$out" == *"ChannelProvider"* ]]'
echo com.google.android.apps.tv.launcherx > "$FAKE_ADB_STATE/top"
TV_OPEN_WAIT=0 "$ROOT/scripts/70-live-tv.sh" keys 4 >/dev/null 2>&1
check "keys 4 abre a TV (KEYCODE_TV) quando ela não está na tela, depois dígito e OK" 'grep -q "input keyevent KEYCODE_TV$" "$FAKE_ADB_STATE/calls.log" && grep -q "input keyevent KEYCODE_4" "$FAKE_ADB_STATE/calls.log" && grep -q "input keyevent KEYCODE_DPAD_CENTER" "$FAKE_ADB_STATE/calls.log"'
: > "$FAKE_ADB_STATE/calls.log"; echo com.tcl.tv > "$FAKE_ADB_STATE/top"
TV_OPEN_WAIT=0 "$ROOT/scripts/70-live-tv.sh" keys 4 >/dev/null 2>&1
check "keys 4 não reabre a TV se ela já está na tela" '! grep -q "input keyevent KEYCODE_TV$" "$FAKE_ADB_STATE/calls.log"'

echo "== 60-benchmark"
"$ROOT/scripts/60-benchmark.sh" >/dev/null 2>&1
check "salva relatório" 'ls "$REPORTS_DIR"/*-benchmark.txt >/dev/null'
out="$("$ROOT/scripts/60-benchmark.sh" --launch com.tcl.tv 2>&1)"
check "mede cold start" '[[ "$out" == *"TotalTime"* ]]'

echo
echo "Passaram: $pass   Falharam: $fail"
[[ "$fail" -eq 0 ]]
