#!/usr/bin/env bash
# 20-inventory.sh — lista todos os apps da TV em português, em três grupos, com
# memória em uso e espaço de cada um, e a memória livre da TV.
#
# Uso:
#   android-tv/scripts/20-inventory.sh            # completo (mede RAM e espaço por app; ~2 min)
#   android-tv/scripts/20-inventory.sh --fast     # sem medir RAM/espaço (~10 s)
#
# Gera reports/<data>_<modelo>-inventory.md e .csv.
#
# Grupos:
#   PODE DESLIGAR      telemetria, anúncios, modo loja e extras que você não usa
#   A TV PRECISA       nunca desligar (android-tv/config/profiles/never-disable.list)
#   APPS QUE VOCÊ USA  apps de terceiros (instalados por você ou pré-carregados)
#   NÃO SEI            fora das listas: fica ligado (regra: na dúvida, deixa)
#
# Colunas de impacto:
#   RAM agora  = PSS do processo neste instante (0 se não está rodando)
#   APK        = espaço do pacote. Só apps fora de /system liberam espaço ao
#                desligar; apps de /system continuam ocupando a partição.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

build_index() {
  local file name risk
  while IFS= read -r file; do
    name="$(basename "$file" .list)"
    [[ "$name" == "never-disable" ]] && continue
    risk="$(list_risk "$file")"
    sed -nE 's/^[[:space:]]*([A-Za-z0-9_.]+)[[:space:]]*(#[[:space:]]*(.*))?$/\1\t'"$name"'\t'"$risk"'\t\3/p' "$file"
  done < <(all_profiles)
}

main() {
  local fast=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --fast) fast=1; shift ;;
      -h|--help) sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
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

  log_step "Memória da TV"
  local mem_avail mem_total
  mem_avail="$(mem_available_mb)"; mem_total="$(mem_total_mb)"
  log_info "Livre (MemAvailable): ${mem_avail} MB de ${mem_total} MB"

  log_step "Coletando listas da TV"
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
  pm_list -u  > "$tmp/known"
  pm_list     > "$tmp/installed"
  pm_list -d  > "$tmp/disabled"
  pm_list -3  > "$tmp/third"
  build_index > "$tmp/index"

  local n; n="$(wc -l < "$tmp/known" | tr -d ' ')"
  log_step "Classificando $n pacotes$( [[ "$fast" == "0" ]] && echo ' e medindo RAM/espaço (~2 min)')"
  printf 'package,name,state,group,profile,ram_mb,apk_mb,system,note\n' > "$out_csv"

  local pkg state group profile note hit name ram apk sys i=0
  while IFS= read -r pkg; do
    i=$((i+1)); (( i % 25 == 0 )) && log_dim "  $i/$n"
    if grep -qx "$pkg" "$tmp/installed"; then
      if grep -qx "$pkg" "$tmp/disabled"; then state=desligado; else state=ligado; fi
    else
      state=desinstalado
    fi
    name="$(app_friendly_name "$pkg")"; note="$(app_description "$pkg")"
    profile=""
    if is_protected "$pkg"; then
      group=PRECISA; profile=never-disable
    elif grep -qx "$pkg" "$tmp/third"; then
      group=USA
    else
      hit="$(awk -F'\t' -v p="$pkg" '$1==p {print; exit}' "$tmp/index")"
      if [[ -n "$hit" ]]; then
        profile="$(cut -f2 <<<"$hit")"
        [[ -z "$note" ]] && note="$(cut -f4 <<<"$hit")"
        case "$(cut -f3 <<<"$hit")" in
          safe|optional) group=PODE ;;
          *) group=PRECISA ;;
        esac
      else
        group=NAOSEI
      fi
    fi
    ram=0; apk=0; sys=sim
    if [[ "$fast" == "0" && "$state" != "desinstalado" ]]; then
      ram="$(pkg_pss_mb "$pkg")"; apk="$(pkg_apk_mb "$pkg")"
      pkg_is_system "$pkg" || sys=nao
    fi
    printf '%s,"%s",%s,%s,%s,%s,%s,%s,"%s"\n' "$pkg" "${name//\"/\'}" "$state" "$group" "$profile" "$ram" "$apk" "$sys" "${note//\"/\'}" >> "$out_csv"
  done < "$tmp/known"

  # ---- Markdown ----
  local total_ram_pode
  total_ram_pode="$(awk -F, 'NR>1 && $4=="PODE" && $3=="ligado" {s+=$6} END {print s+0}' "$out_csv")"
  {
    echo "# Apps da TV — $slug"
    echo
    echo "Gerado em $(date -Iseconds) por \`android-tv/scripts/20-inventory.sh\`."
    echo
    echo "**Memória livre agora: ${mem_avail} MB de ${mem_total} MB.** Os apps em \"Pode desligar\" que estão rodando ocupam ${total_ram_pode} MB neste instante."
    echo
    echo "| Grupo | Qtde | O que significa |"
    echo "|---|---:|---|"
    for group in PODE PRECISA USA NAOSEI; do
      n="$(awk -F, -v g="$group" 'NR>1 && $4==g' "$out_csv" | wc -l | tr -d ' ')"
      case "$group" in
        PODE) note="Pode desligar sem perder nada que a TV precisa. Você escolhe um a um em android-tv/scripts/25-review.sh" ;;
        PRECISA) note="A TV precisa. Nunca desligue" ;;
        USA) note="Apps que você usa (ou que vieram pré-instalados). Você decide" ;;
        NAOSEI) note="Fora das listas. Regra: na dúvida, fica ligado" ;;
      esac
      echo "| $group | $n | $note |"
    done
    echo
    for group in PODE USA NAOSEI PRECISA; do
      case "$group" in
        PODE) echo "## Pode desligar" ;;
        USA) echo "## Apps que você usa" ;;
        NAOSEI) echo "## Não sei (fica ligado)" ;;
        PRECISA) echo "## A TV precisa (nunca desligar)" ;;
      esac
      echo
      echo "| App | Pacote | Estado | RAM agora | APK | Em /system? | O que faz |"
      echo "|---|---|---|---:|---:|---|---|"
      awk -F, -v g="$group" 'NR>1 && $4==g {
        name=$2; gsub(/^"|"$/, "", name); if (name=="") name="—";
        note=$9; gsub(/^"|"$/, "", note);
        printf "| %s | `%s` | %s | %s MB | %s MB | %s | %s |\n", name, $1, $3, $6, $7, $8, note
      }' "$out_csv" | sort -t'|' -k5,5nr
      echo
    done
  } > "$out_md"

  log_ok "Relatório: $out_md"
  log_ok "CSV      : $out_csv"
  awk -F, 'NR>1 {c[$4]++} END {for (k in c) printf "      %-8s %d\n", k, c[k]}' "$out_csv" >&2
  log_info "Próximo passo: android-tv/scripts/25-review.sh (escolher um a um o que desligar)"
}

main "$@"
