#!/usr/bin/env bash
# 20-inventory.sh — lista todos os pacotes da TV e classifica cada um.
#
# Uso:
#   scripts/20-inventory.sh [--details]
#
# Gera reports/<data>_<modelo>-inventory.md (tabela legível) e .csv.
# Classificação:
#   KEEP      bate com config/profiles/never-disable.list (nunca mexa)
#   SAFE      está num perfil "risk: safe" (telemetria, anúncios, modo loja)
#   OPTIONAL  está num perfil "risk: optional" (depende do seu uso)
#   LAUNCHER  launcher de fábrica / assistente (só via 50-launcher.sh)
#   USER      app de terceiros que você instalou (pm list packages -3)
#   UNKNOWN   não está em nenhuma lista: pesquise antes de mexer
#
# --details acrescenta versionName (lento: um dumpsys por pacote).
#
# O que você aprende aqui: a diferença entre "instalado", "desabilitado" e
# "desinstalado para o usuário 0" (o APK continua em /system e volta com
# pm install-existing).

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

# Monta um índice "pacote<TAB>perfil<TAB>risco<TAB>comentário" com todas as listas
# (menos never-disable, que usa globs e é tratada à parte).
build_index() {
  local file name risk
  for file in "$PROFILES_DIR"/*.list; do
    name="$(basename "$file" .list)"
    [[ "$name" == "never-disable" ]] && continue
    risk="$(list_risk "$file")"
    # Linhas "pkg   # comentário" → campos separados por TAB
    sed -nE 's/^[[:space:]]*([A-Za-z0-9_.]+)[[:space:]]*(#[[:space:]]*(.*))?$/\1\t'"$name"'\t'"$risk"'\t\3/p' "$file"
  done
}

main() {
  local details=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --details) details=1; shift ;;
      -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
      *) die "Argumento desconhecido: $1" ;;
    esac
  done

  load_config
  ensure_connected

  local slug ts out_md out_csv
  slug="$(device_slug)"; ts="$(timestamp)"
  mkdir -p "$REPORTS_DIR"
  out_md="$REPORTS_DIR/${ts}_${slug}-inventory.md"
  out_csv="$REPORTS_DIR/${ts}_${slug}-inventory.csv"

  log_step "Coletando listas da TV"
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
  pm_list -u  > "$tmp/known"
  pm_list     > "$tmp/installed"
  pm_list -d  > "$tmp/disabled"
  pm_list -3  > "$tmp/third"
  build_index > "$tmp/index"

  log_step "Classificando $(wc -l < "$tmp/known") pacotes"
  printf 'package,state,class,profile,note\n' > "$out_csv"

  local pkg state class profile note hit version
  while IFS= read -r pkg; do
    if grep -qx "$pkg" "$tmp/installed"; then
      if grep -qx "$pkg" "$tmp/disabled"; then state=disabled; else state=enabled; fi
    else
      state=uninstalled
    fi
    profile=""; note=""
    if is_protected "$pkg"; then
      class=KEEP; profile=never-disable
    elif grep -qx "$pkg" "$tmp/third"; then
      class=USER
    else
      hit="$(awk -F'\t' -v p="$pkg" '$1==p {print; exit}' "$tmp/index")"
      if [[ -n "$hit" ]]; then
        profile="$(cut -f2 <<<"$hit")"
        note="$(cut -f4 <<<"$hit")"
        case "$(cut -f3 <<<"$hit")" in
          safe) class=SAFE ;;
          optional) class=OPTIONAL ;;
          launcher) class=LAUNCHER ;;
          *) class=UNKNOWN ;;
        esac
      else
        class=UNKNOWN
      fi
    fi
    if [[ "$details" == "1" && "$state" != "uninstalled" ]]; then
      version="$(tv_shell dumpsys package "$pkg" 2>/dev/null | tr -d '\r' | sed -nE 's/^\s*versionName=(.*)$/\1/p' | head -n1 || true)"
      note="${note:+$note }${version:+(v$version)}"
    fi
    printf '%s,%s,%s,%s,"%s"\n' "$pkg" "$state" "$class" "$profile" "${note//\"/\'}" >> "$out_csv"
  done < "$tmp/known"

  # ---- Markdown ----
  {
    echo "# Inventário de pacotes — $slug"
    echo
    echo "Gerado em $(date -Iseconds) por \`scripts/20-inventory.sh\`."
    echo
    echo "| Classe | Qtde | Significado |"
    echo "|---|---:|---|"
    for class in SAFE OPTIONAL LAUNCHER USER KEEP UNKNOWN; do
      local n; n="$(awk -F, -v c="$class" 'NR>1 && $3==c' "$out_csv" | wc -l)"
      case "$class" in
        SAFE) note="Pode desabilitar sem perder função (telemetria, anúncios, modo loja)" ;;
        OPTIONAL) note="Depende do seu uso; leia a nota de cada um" ;;
        LAUNCHER) note="Launcher de fábrica; só via scripts/50-launcher.sh" ;;
        USER) note="Instalado por você; desinstale pela TV se quiser" ;;
        KEEP) note="Essencial; nunca mexa" ;;
        UNKNOWN) note="Fora das listas; pesquise antes (docs/03-pacotes-e-riscos.md)" ;;
      esac
      echo "| $class | $n | $note |"
    done
    echo
    for class in SAFE OPTIONAL LAUNCHER USER UNKNOWN KEEP; do
      echo "## $class"
      echo
      echo "| Pacote | Estado | Perfil | Nota |"
      echo "|---|---|---|---|"
      awk -F, -v c="$class" 'NR>1 && $3==c {
        note=$5; gsub(/^"|"$/, "", note);
        printf "| `%s` | %s | %s | %s |\n", $1, $2, $4, note
      }' "$out_csv"
      echo
    done
  } > "$out_md"

  log_ok "Relatório: $out_md"
  log_ok "CSV      : $out_csv"
  log_info "Resumo:"
  awk -F, 'NR>1 {c[$3]++} END {for (k in c) printf "      %-9s %d\n", k, c[k]}' "$out_csv" >&2
  log_info "Próximo passo: leia o relatório, ajuste config/profiles/*.list e rode scripts/30-debloat.sh --dry-run"
}

main "$@"
