#!/usr/bin/env bash
# 25-review.sh — revisão interativa: para cada app candidato, você decide
# manter, desligar ou pular, vendo nome, o que faz, RAM agora e espaço.
#
# Uso:
#   scripts/25-review.sh                       # usa o inventário mais recente em reports/
#   scripts/25-review.sh reports/<arquivo>-inventory.csv
#   scripts/25-review.sh --group PODE          # só "Pode desligar" (padrão: PODE e USA)
#
# Gera config/profiles/plano-<data>.list com os escolhidos, e o aplica assim:
#   scripts/30-debloat.sh --ask-each plano-<data>
#
# Nada é alterado na TV por este script.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

main() {
  local csv="" groups="PODE USA"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --group) groups="$2"; shift 2 ;;
      -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
      *) csv="$1"; shift ;;
    esac
  done
  [[ -n "$csv" ]] || csv="$(find "$REPORTS_DIR" -name '*-inventory.csv' | sort | tail -n1)"
  [[ -f "$csv" ]] || die "Nenhum inventário em $REPORTS_DIR. Rode scripts/20-inventory.sh."
  [[ -t 0 ]] || die "Este script é interativo; rode num terminal."
  head -n1 "$csv" | grep -q '^package,name,state,group' || die "CSV no formato antigo; rode scripts/20-inventory.sh de novo."

  local plan plan_name
  plan_name="plano-$(timestamp)"
  plan="$PROFILES_DIR/$plan_name.list"
  log_step "Revisão a partir de $(basename "$csv")"
  log_info "Teclas: [d] desligar  [m] manter  [p] pular (decidir depois)  [q] sair e salvar"
  log_info "Apenas apps LIGADOS dos grupos: $groups"
  echo >&2

  {
    echo "# risk: optional"
    echo "# Plano gerado por scripts/25-review.sh em $(date -Iseconds) a partir de $(basename "$csv")."
    echo "# Aplicar: scripts/30-debloat.sh --ask-each $plan_name"
    echo
  } > "$plan"

  local total_ram=0 total_apk=0 n_off=0 n_keep=0 n_skip=0
  local pkg name state group profile ram apk sys note answer g
  # shellcheck disable=SC2034  # "profile" é uma coluna do CSV que não usamos aqui
  while IFS=, read -r pkg name state group profile ram apk sys note; do
    [[ "$state" == "ligado" ]] || continue
    for g in $groups; do [[ "$group" == "$g" ]] && break; done
    [[ "$group" == "$g" ]] || continue
    name="${name//\"/}"; note="${note//\"/}"
    printf '%s%s%s  %s\n' "$C_BOLD" "${name:-$pkg}" "$C_RESET" "$C_DIM$pkg$C_RESET" >&2
    printf '    %s\n' "${note:-(sem descrição; pesquise antes)}" >&2
    printf '    grupo: %s   RAM agora: %s MB   APK: %s MB%s\n' "$group" "$ram" "$apk" "$( [[ "$sys" == "sim" ]] && echo ' (em /system: não libera espaço)' || echo ' (libera espaço se desinstalar)')" >&2
    while true; do
      read -r -p "    [d]esligar / [m]anter / [p]ular / [q]uit: " answer </dev/tty
      case "$answer" in
        d|D) printf '%s   # %s | RAM %s MB | APK %s MB\n' "$pkg" "${name:-?}" "$ram" "$apk" >> "$plan"
             n_off=$((n_off+1)); total_ram=$((total_ram+ram)); [[ "$sys" == "nao" ]] && total_apk=$((total_apk+apk)); break ;;
        m|M) printf '# %s   # MANTER: %s\n' "$pkg" "${name:-?}" >> "$plan"; n_keep=$((n_keep+1)); break ;;
        p|P) n_skip=$((n_skip+1)); break ;;
        q|Q) break 2 ;;
        *) ;;
      esac
    done
    echo >&2
  done < <(tail -n +2 "$csv")

  echo >&2
  log_ok "Plano salvo: $plan"
  log_info "Desligar: $n_off   Manter: $n_keep   Pular: $n_skip"
  log_info "Impacto estimado: ~${total_ram} MB de RAM liberados após reiniciar; ~${total_apk} MB de espaço se os apps fora de /system forem desinstalados."
  log_info "Aplicar (pergunta antes de cada um): scripts/30-debloat.sh --ask-each $plan_name"
}

main "$@"
