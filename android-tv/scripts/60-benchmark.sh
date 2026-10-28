#!/usr/bin/env bash
# 60-benchmark.sh — mede memória, processos e tempo de abertura de apps.
#
# Uso:
#   android-tv/scripts/60-benchmark.sh                  # salva reports/<data>_<modelo>-benchmark.txt
#   android-tv/scripts/60-benchmark.sh --launch com.google.android.youtube.tv   # mede cold start
#   android-tv/scripts/60-benchmark.sh --compare reports/arquivo-anterior.txt
#
# O que você aprende aqui: "RAM livre" engana no Android, porque o kernel usa
# a memória liberada como cache. Compare "Used PSS" (deve cair) e
# "Cached kernel" (deve subir), e veja quais processos saíram do topo.
# Meça sempre DEPOIS de reiniciar: pacotes desabilitados seguem em memória
# até o próximo boot.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

snapshot() {
  echo "# $(date -Iseconds) $(device_slug) uptime=$(tv_shell cat /proc/uptime | tr -d '\r' | cut -d' ' -f1)s"
  echo
  echo "## Memória (dumpsys meminfo)"
  local mem
  mem="$(tv_shell dumpsys meminfo 2>/dev/null | tr -d '\r')"
  if [[ -z "$mem" ]]; then
    echo "(dumpsys meminfo não respondeu: a TV provavelmente ainda está inicializando; repita em 1-2 min)"
  fi
  grep -E 'Total (RAM|PSS)|Free RAM|Used RAM|Lost RAM|Cached kernel|Used PSS' <<<"$mem" || true
  echo
  echo "## Top 15 processos por PSS"
  sed -n '/Total PSS by process/,/Total PSS by OOM/p' <<<"$mem" | grep -E '^[[:space:]]+[0-9,]+K:' | head -n 15 || true
  echo
  echo "## Contadores"
  echo "processos            : $(tv_shell ps -A 2>/dev/null | tr -d '\r' | wc -l | tr -d ' ')"
  echo "pacotes instalados   : $(pm_list | wc -l | tr -d ' ')"
  echo "pacotes desabilitados: $(pm_list -d | wc -l | tr -d ' ')"
  echo "loadavg             : $(tv_shell cat /proc/loadavg | tr -d '\r')"
}

launch_time() {
  local pkg="$1" comp
  comp="$(tv_shell cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER "$pkg" 2>/dev/null | tr -d '\r' | tail -n1 || true)"
  [[ "$comp" == */* ]] || die "Não achei a atividade LEANBACK_LAUNCHER de $pkg."
  log_step "Cold start de $comp"
  tv_run am force-stop "$pkg"
  sleep 1
  tv_shell am start -W -n "$comp" | tr -d '\r' | grep -E 'TotalTime|WaitTime' || true
}

main() {
  local launch="" compare=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --launch) launch="$2"; shift 2 ;;
      --compare) compare="$2"; shift 2 ;;
      -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
      *) die "Argumento desconhecido: $1" ;;
    esac
  done
  load_config
  ensure_connected
  if [[ -n "$launch" ]]; then launch_time "$launch"; exit 0; fi

  mkdir -p "$REPORTS_DIR"
  local out
  out="$REPORTS_DIR/$(timestamp)_$(device_slug)-benchmark.txt"
  log_step "Coletando métricas"
  snapshot | tee "$out" >&2
  log_ok "Salvo em $out"
  if [[ -n "$compare" ]]; then
    log_step "Diferença em relação a $compare"
    diff -u "$compare" "$out" || true
  fi
}

main "$@"
