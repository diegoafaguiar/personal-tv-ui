#!/usr/bin/env bash
# 30-debloat.sh — desabilita pacotes por perfil, de forma reversível e em lotes.
#
# Uso:
#   scripts/30-debloat.sh --dry-run tcl-telemetry tcl-promo-ads tcl-retail-demo
#   scripts/30-debloat.sh tcl-telemetry tcl-promo-ads tcl-retail-demo
#   scripts/30-debloat.sh --yes --batch 5 tcl-extras google-extras
#   scripts/30-debloat.sh --uninstall tcl-promo-ads     # "pm uninstall -k --user 0" em vez de disable
#   scripts/30-debloat.sh --uninstall minha-tv-apps      # apps de terceiros: remove de vez (volta pela Play Store)
#   scripts/30-debloat.sh --reboot ...                  # reinicia ao final (necessário para liberar RAM)
#
# Perfis são arquivos em config/profiles/*.list (nome sem .list) ou caminhos.
#
# Proteções:
#   - exige um backup (scripts/10-backup.sh) salvo neste repositório;
#   - recusa qualquer pacote que bata com never-disable.list;
#   - recusa o perfil launcher-stock (use scripts/50-launcher.sh, que verifica HOME);
#   - aplica em lotes e, entre lotes, confere que a TV responde e que o HOME não mudou;
#   - confere o estado após cada comando: "Success" do pm nem sempre significa que agiu.
#
# Por que "disable-user" e não "uninstall": os dois mantêm o APK em /system.
# disable é revertido com "pm enable"; uninstall --user 0 é revertido com
# "pm install-existing". disable é mais previsível em TVs TCL (alguns
# pacotes se reinstalam sozinhos quando "desinstalados").

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

usage() { sed -n '2,23p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

main() {
  local batch=8 mode=disable do_reboot=0 profiles=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run) export DRY_RUN=1; shift ;;
      --yes|-y) export ASSUME_YES=1; shift ;;
      --batch) batch="$2"; shift 2 ;;
      --uninstall) mode=uninstall; shift ;;
      --reboot) do_reboot=1; shift ;;
      -h|--help) usage ;;
      -*) die "Flag desconhecida: $1" ;;
      *) profiles+=("$1"); shift ;;
    esac
  done
  [[ ${#profiles[@]} -gt 0 ]] || { log_err "Informe ao menos um perfil."; usage 1; }

  load_config
  ensure_connected
  local backup_dir
  backup_dir="$(require_backup)"
  log_info "Backup de referência: $backup_dir"

  # ---- Monta o plano ----
  log_step "Montando plano"
  local file risk pkg state
  local -a todo=() refused=() skipped=()
  declare -A reason=()
  for name in "${profiles[@]}"; do
    file="$(resolve_profile "$name")"
    risk="$(list_risk "$file")"
    if [[ "$risk" == "launcher" ]]; then
      die "O perfil $(basename "$file") só pode ser aplicado por scripts/50-launcher.sh set-default (verifica HOME e Wi-Fi)."
    fi
    if [[ "$risk" == "keep" ]]; then
      die "O perfil $(basename "$file") é a lista de proteção, não um alvo."
    fi
    while IFS= read -r pkg; do
      if is_protected "$pkg"; then
        refused+=("$pkg"); reason[$pkg]="never-disable"; continue
      fi
      state="$(pkg_state "$pkg")"
      case "$state" in
        enabled) todo+=("$pkg") ;;
        disabled) skipped+=("$pkg"); reason[$pkg]="já desabilitado" ;;
        uninstalled) skipped+=("$pkg"); reason[$pkg]="já desinstalado (user 0)" ;;
        absent) skipped+=("$pkg"); reason[$pkg]="não existe nesta TV" ;;
      esac
    done < <(read_list "$file")
  done

  # Dedup mantendo ordem (só se houver algo; printf de array vazio gera uma linha vazia)
  if [[ ${#todo[@]} -gt 0 ]]; then
    mapfile -t todo < <(printf '%s\n' "${todo[@]}" | awk '!seen[$0]++')
  fi

  echo >&2
  if [[ ${#refused[@]} -gt 0 ]]; then
    log_warn "Recusados por never-disable.list:"
    for pkg in "${refused[@]}"; do log_dim "  $pkg"; done
  fi
  if [[ ${#skipped[@]} -gt 0 ]]; then
    log_info "Ignorados:"
    for pkg in "${skipped[@]}"; do log_dim "  $pkg  (${reason[$pkg]})"; done
  fi
  if [[ ${#todo[@]} -eq 0 ]]; then
    log_ok "Nada a fazer."
    exit 0
  fi
  local verb="desabilitados"; [[ "$mode" == "uninstall" ]] && verb="desinstalados"
  log_info "Serão $verb: ${#todo[@]} pacotes, em lotes de $batch:"
  for pkg in "${todo[@]}"; do log_info "  $pkg"; done
  echo >&2

  if [[ "$DRY_RUN" == "1" ]]; then
    log_ok "Dry-run concluído. Remova --dry-run para aplicar."
    exit 0
  fi
  confirm "Aplicar na TV ($TV_SERIAL)?" || die "Cancelado."

  # ---- Aplica em lotes ----
  local home_before applied=() failed=() i=0 n=${#todo[@]}
  home_before="$(home_owner)"
  mkdir -p "$REPORTS_DIR"
  local log_file
  log_file="$REPORTS_DIR/$(timestamp)_$(device_slug)-debloat.log"
  # ">>" e não ">": duas execuções no mesmo segundo compartilham o arquivo
  {
    echo "# $(date -Iseconds) mode=$mode backup=$backup_dir"
    echo "# profiles: ${profiles[*]}"
  } >> "$log_file"

  for pkg in "${todo[@]}"; do
    i=$((i + 1))
    log_step "[$i/$n] $pkg"
    # Um pacote por chamada: "pm" ignora argumentos extras em silêncio.
    if [[ "$mode" == "uninstall" ]]; then
      tv_run pm uninstall -k --user 0 "$pkg" || true
      state="$(pkg_state "$pkg")"
      # Apps de fábrica ficam "uninstalled" (APK segue em /system); apps de terceiros
      # somem de vez ("absent") e só voltam pela Play Store.
      if [[ "$state" == "uninstalled" || "$state" == "absent" ]]; then
        applied+=("$pkg"); echo "uninstall $pkg" >> "$log_file"; log_ok "desinstalado ($state)"
      else
        failed+=("$pkg"); log_warn "estado após o comando: $state (não mudou)"
      fi
    else
      tv_run pm disable-user --user 0 "$pkg" || true
      state="$(pkg_state "$pkg")"
      if [[ "$state" == "disabled" ]]; then
        applied+=("$pkg"); echo "disable $pkg" >> "$log_file"; log_ok "desabilitado"
      else
        failed+=("$pkg"); log_warn "estado após o comando: $state (não mudou)"
      fi
    fi

    if (( i % batch == 0 || i == n )); then
      log_info "Verificando saúde da TV após o lote..."
      [[ "$(tv_shell echo ok | tr -d '\r')" == "ok" ]] || die "A TV parou de responder ao adb. Reinicie-a e rode scripts/90-rollback.sh."
      local home_now; home_now="$(home_owner)"
      if [[ "$home_now" != "$home_before" ]]; then
        log_err "O dono do HOME mudou de '$home_before' para '$home_now'. Interrompendo."
        die "Rode scripts/90-rollback.sh (ou reabilite o último lote) antes de continuar."
      fi
      log_ok "TV responde, HOME inalterado."
    fi
  done

  echo >&2
  log_ok "Aplicados: ${#applied[@]}   Falharam: ${#failed[@]}   Log: $log_file"
  [[ ${#failed[@]} -gt 0 ]] && for pkg in "${failed[@]}"; do log_warn "  $pkg"; done
  log_info "Pacotes desabilitados continuam em memória até reiniciar."
  if [[ "$do_reboot" == "1" ]]; then
    log_step "Reiniciando a TV"
    tv_run reboot || true
  else
    log_info "Reinicie quando puder: adb -s $TV_SERIAL reboot  (ou --reboot)"
  fi
  log_info "Após o boot (espere ~2 min): scripts/35-verify.sh confere se tudo continua desabilitado."
  log_info "Depois, meça: scripts/60-benchmark.sh"
}

main "$@"
