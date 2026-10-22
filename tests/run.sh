#!/usr/bin/env bash
# tests/run.sh — testa os scripts contra um adb falso (tests/fake-adb/adb).
# Cobre o fluxo inteiro: backup → inventário → debloat → tune → launcher → rollback,
# e as proteções (never-disable, HOME guard, Wi-Fi do setup wizard).
# shellcheck disable=SC2016,SC2034  # as asserções são strings avaliadas por "check" via eval
set -euo pipefail
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
check "com.tcl.tv é KEEP" 'grep -q "^com.tcl.tv,enabled,KEEP" "$csv"'
check "com.tcl.ttvs é SAFE" 'grep -q "^com.tcl.ttvs,enabled,SAFE,tcl-telemetry" "$csv"'
check "com.tcl.smartalexa é OPTIONAL" 'grep -q "^com.tcl.smartalexa,enabled,OPTIONAL" "$csv"'
check "launcherx é LAUNCHER" 'grep -q "^com.google.android.apps.tv.launcherx,enabled,LAUNCHER" "$csv"'
check "app do usuário é USER" 'grep -q "^org.example.userapp,enabled,USER" "$csv"'
check "esticker aparece como uninstalled" 'grep -q "^com.tcl.esticker,uninstalled" "$csv"'

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
check "não toca com.tcl.tv" '! has disabled com.tcl.tv'
check "um pacote por chamada" 'grep -q "shell pm disable-user --user 0 com.tcl.ttvs$" "$FAKE_ADB_STATE/calls.log"'
check "gera log de auditoria" 'grep -q "^disable com.tcl.ttvs" "$REPORTS_DIR"/*-debloat.log'

echo "== 30-debloat --uninstall app de terceiros"
printf '# risk: optional\norg.example.userapp\n' > "$WORK/apps.list"
out="$("$ROOT/scripts/30-debloat.sh" --uninstall "$WORK/apps.list" 2>&1)"
check "app de terceiros desinstalado conta como aplicado" '[[ "$out" == *"Aplicados: 1"* ]]'

echo "== 40-tune"
"$ROOT/scripts/40-tune.sh" --animations 0.5 --screensaver basic --private-dns dns.adguard.com >/dev/null 2>&1
check "animações 0.5" 'grep -q "^global:animator_duration_scale=0.5" "$FAKE_ADB_STATE/settings"'
check "screensaver básico" 'grep -q "^secure:screensaver_components=com.android.dreams.basic/.Colors" "$FAKE_ADB_STATE/settings"'
check "private dns" 'grep -q "^global:private_dns_specifier=dns.adguard.com" "$FAKE_ADB_STATE/settings"'

echo "== 50-launcher guardas"
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

echo "== 60-benchmark"
"$ROOT/scripts/60-benchmark.sh" >/dev/null 2>&1
check "salva relatório" 'ls "$REPORTS_DIR"/*-benchmark.txt >/dev/null'
out="$("$ROOT/scripts/60-benchmark.sh" --launch com.tcl.tv 2>&1)"
check "mede cold start" '[[ "$out" == *"TotalTime"* ]]'

echo
echo "Passaram: $pass   Falharam: $fail"
[[ "$fail" -eq 0 ]]
