#!/usr/bin/env bash
# roku/tests/run.sh — testa os scripts da Roku contra um ECP falso (tests/fake-ecp/curl).
# shellcheck disable=SC2016,SC2034  # as asserções são strings avaliadas por "check" via eval
set -euo pipefail
exec </dev/null
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

export FAKE_ECP_STATE="$WORK/state" REPORTS_DIR="$WORK/reports"
export PATH="$ROOT/tests/fake-ecp:$PATH"
export TV_HOST=10.0.0.60 TV_ENV=/dev/null KEY_DELAY=0
mkdir -p "$FAKE_ECP_STATE" "$REPORTS_DIR"

pass=0; fail=0
ok()   { pass=$((pass+1)); printf '  ✔ %s\n' "$1"; }
bad()  { fail=$((fail+1)); printf '  ✖ %s\n' "$1"; }
check(){ if eval "$2"; then ok "$1"; else bad "$1"; fi; }
posts(){ cat "$FAKE_ECP_STATE/posts" 2>/dev/null || true; }

echo "00-connect"
out="$("$ROOT/scripts/00-connect.sh" 2>&1)"
check "mostra o modelo" '[[ "$out" == *"Philco•Roku TV (PTV58G7UR2CSBL4K)"* ]]'
check "mostra o modo do ECP" '[[ "$out" == *"Controle pela rede: permissive"* ]]'

echo "20-inventory"
"$ROOT/scripts/20-inventory.sh" >/dev/null 2>&1
md="$(ls "$REPORTS_DIR"/*-inventory.md)"
check "nome do relatório tem marca e modelo" '[[ "$md" == *_Philco_PTV58G7UR2CSBL4K-inventory.md ]]'
check "conta canais e entradas" 'grep -q "## Canais instalados (4) e entradas (1)" "$md"'
check "nota do Star+" 'grep -q "| Star Plus | 624140 | 1.0.2 | o Star+ foi incorporado" "$md"'
check "entrada marcada" 'grep -q "| TV ao vivo | tvinput.dtv | 1.0.0 | entrada da TV |" "$md"'
check "desfaz &amp; no nome" 'grep -q "| Arroz & Feijão |" "$md"'
check "guarda o XML cru" '[[ -f "${md%-inventory.md}-apps.xml" && -f "${md%-inventory.md}-device-info.xml" ]]'

echo "40-remote"
"$ROOT/scripts/40-remote.sh" key Home Down Select >/dev/null 2>&1
check "teclas em ordem" '[[ "$(posts)" == $'"'"'/keypress/Home\n/keypress/Down\n/keypress/Select'"'"' ]]'
: > "$FAKE_ECP_STATE/posts"
"$ROOT/scripts/40-remote.sh" launch netflix >/dev/null 2>&1
check "abre canal pelo nome" '[[ "$(posts)" == "/launch/12" ]]'
: > "$FAKE_ECP_STATE/posts"
"$ROOT/scripts/40-remote.sh" type "é a" >/dev/null 2>&1
check "digita com acento em UTF-8" '[[ "$(posts)" == $'"'"'/keypress/Lit_%C3%A9\n/keypress/Lit_%20\n/keypress/Lit_a'"'"' ]]'
: > "$FAKE_ECP_STATE/posts"
DRY_RUN=1 "$ROOT/scripts/40-remote.sh" home >/dev/null 2>&1
check "DRY_RUN não manda tecla" '[[ -z "$(posts)" ]]'
out="$("$ROOT/scripts/40-remote.sh" key "Home;reboot" 2>&1 || true)"
check "recusa tecla inválida" '[[ "$out" == *"Tecla inválida"* ]]'
out="$("$ROOT/scripts/40-remote.sh" launch inexistente 2>&1 || true)"
check "canal inexistente explica" '[[ "$out" == *"Canal não encontrado"* ]]'

echo "modo Limitado"
out="$(FAKE_ECP_LIMITED=1 "$ROOT/scripts/20-inventory.sh" 2>&1 || true)"
check "explica como liberar" '[[ "$out" == *"Acesso à rede › Permissivo"* ]]'

echo
echo "Passaram: $pass   Falharam: $fail"
[[ "$fail" == 0 ]]
