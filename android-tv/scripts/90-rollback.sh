#!/usr/bin/env bash
# 90-rollback.sh — devolve a TV ao estado de um backup (padrão: o mais recente).
#
# Uso:
#   android-tv/scripts/90-rollback.sh --dry-run                 # mostra o plano, não altera nada
#   android-tv/scripts/90-rollback.sh                           # usa backups/LATEST
#   android-tv/scripts/90-rollback.sh backups/20261022-..._TCL_G08 --reboot
#   android-tv/scripts/90-rollback.sh --packages-only | --settings-only
#   android-tv/scripts/90-rollback.sh --no-redisable            # não volta a desabilitar o que era de fábrica
#
# O que ele faz, nesta ordem:
#   1. Reinstala (pm install-existing) pacotes que estavam instalados e hoje
#      estão "desinstalados para o usuário 0".
#   2. Reabilita (pm enable) pacotes que estavam habilitados e hoje estão
#      desabilitados. Isso inclui o launcher de fábrica e o setup wizard.
#   3. Re-desabilita pacotes que estavam desabilitados de fábrica e hoje estão
#      habilitados (fidelidade ao estado original; desligue com --no-redisable).
#   4. Restaura as chaves de settings rastreadas (animações, screensaver,
#      Private DNS, acessibilidade).
#   5. Aperta HOME e confere se o dono do HOME é o mesmo do backup.
#
# Limites (sinceros): apps de terceiros que você desinstalou de verdade pela
# TV não voltam por aqui (reinstale pela Play Store). Dados internos de apps
# não são tocados por nenhum script, então não há o que restaurar.
# Se o adb não alcançar a TV, o último recurso é "Redefinir para padrão de
# fábrica" nas Configurações: isso reabilita tudo.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

usage() { sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

main() {
  local dir="" do_reboot=0 packages=1 settings=1 redisable=1
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run) export DRY_RUN=1; shift ;;
      --yes|-y) export ASSUME_YES=1; shift ;;
      --reboot) do_reboot=1; shift ;;
      --packages-only) settings=0; shift ;;
      --settings-only) packages=0; shift ;;
      --no-redisable) redisable=0; shift ;;
      -h|--help) usage; exit 0 ;;
      -*) die "Flag desconhecida: $1" ;;
      *) dir="$1"; shift ;;
    esac
  done

  load_config
  ensure_connected
  [[ -n "$dir" ]] || dir="$(latest_backup_dir)"
  [[ -d "$dir" && -f "$dir/packages-enabled.txt" ]] || die "Backup inválido: ${dir:-nenhum}. Rode android-tv/scripts/10-backup.sh ou informe a pasta."
  log_step "Rollback para $dir"
  if [[ -f "$dir/device.txt" ]]; then
    local bk_model; bk_model="$(sed -n 's/^model=//p' "$dir/device.txt")"
    local now_model; now_model="$(tv_prop ro.product.model)"
    [[ "$bk_model" == "$now_model" ]] || log_warn "Backup é do modelo '$bk_model', TV conectada é '$now_model'."
  fi

  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
  pm_list    > "$tmp/installed"
  pm_list -d > "$tmp/disabled"
  pm_list -u > "$tmp/known"

  local -a to_reinstall=() to_enable=() to_disable=() missing=()
  local pkg
  if [[ "$packages" == "1" ]]; then
    while IFS= read -r pkg; do
      [[ -z "$pkg" ]] && continue
      if ! grep -qx "$pkg" "$tmp/installed"; then
        if grep -qx "$pkg" "$tmp/known"; then to_reinstall+=("$pkg"); else missing+=("$pkg"); fi
      fi
    done < "$dir/packages-installed.txt"
    while IFS= read -r pkg; do
      [[ -z "$pkg" ]] && continue
      if grep -qx "$pkg" "$tmp/disabled"; then to_enable+=("$pkg"); fi
      # reinstalados voltam habilitados por padrão; nada a fazer
    done < "$dir/packages-enabled.txt"
    if [[ "$redisable" == "1" && -f "$dir/packages-disabled.txt" ]]; then
      while IFS= read -r pkg; do
        [[ -z "$pkg" ]] && continue
        if grep -qx "$pkg" "$tmp/installed" && ! grep -qx "$pkg" "$tmp/disabled"; then
          if is_protected "$pkg"; then
            log_warn "$pkg estava desabilitado no backup mas é protegido; não vou re-desabilitar."
          else
            to_disable+=("$pkg")
          fi
        fi
      done < "$dir/packages-disabled.txt"
    fi
  fi

  local -a setting_changes=()
  if [[ "$settings" == "1" && -f "$dir/settings.env" ]]; then
    local line ns key want have
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      ns="${line%%:*}"; key="${line#*:}"; key="${key%%=*}"; want="${line#*=}"
      have="$(settings_get "$ns" "$key")"
      [[ "$have" != "$want" ]] && setting_changes+=("$ns:$key=$want (hoje: $have)")
    done < "$dir/settings.env"
  fi

  # ---- Plano ----
  echo >&2
  log_info "Reinstalar (install-existing): ${#to_reinstall[@]}"; for pkg in "${to_reinstall[@]}"; do log_dim "  $pkg"; done
  log_info "Reabilitar (enable): ${#to_enable[@]}";             for pkg in "${to_enable[@]}"; do log_dim "  $pkg"; done
  log_info "Re-desabilitar (estado de fábrica): ${#to_disable[@]}"; for pkg in "${to_disable[@]}"; do log_dim "  $pkg"; done
  log_info "Settings a restaurar: ${#setting_changes[@]}";       for line in "${setting_changes[@]}"; do log_dim "  $line"; done
  if [[ ${#missing[@]} -gt 0 ]]; then
    log_warn "Não dá para restaurar por adb (APK não existe mais na TV; reinstale pela Play Store):"
    for pkg in "${missing[@]}"; do log_dim "  $pkg"; done
  fi
  local total=$(( ${#to_reinstall[@]} + ${#to_enable[@]} + ${#to_disable[@]} + ${#setting_changes[@]} ))
  if [[ "$total" -eq 0 ]]; then log_ok "A TV já está no estado do backup."; exit 0; fi
  echo >&2
  [[ "$DRY_RUN" == "1" ]] && { log_ok "Dry-run concluído. Remova --dry-run para aplicar."; exit 0; }
  confirm "Aplicar rollback ($total ações) na TV ($TV_SERIAL)?" || die "Cancelado."

  # ---- Aplica ----
  for pkg in "${to_reinstall[@]}"; do
    log_step "install-existing $pkg"
    tv_run cmd package install-existing --user 0 "$pkg" || log_warn "falhou: $pkg"
  done
  for pkg in "${to_enable[@]}"; do
    log_step "enable $pkg"
    tv_run pm enable --user 0 "$pkg" || log_warn "falhou: $pkg"
  done
  for pkg in "${to_disable[@]}"; do
    log_step "disable-user $pkg (estado de fábrica)"
    tv_run pm disable-user --user 0 "$pkg" || log_warn "falhou: $pkg"
  done
  if [[ ${#setting_changes[@]} -gt 0 ]]; then
    log_step "Restaurando settings"
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      ns="${line%%:*}"; key="${line#*:}"; key="${key%%=*}"; want="${line#*=}"
      [[ "$(settings_get "$ns" "$key")" == "$want" ]] && continue
      settings_put "$ns" "$key" "$want"
    done < "$dir/settings.env"
  fi

  # ---- Verifica ----
  log_step "Verificando"
  tv_run input keyevent KEYCODE_HOME || true
  sleep 1
  local still_disabled=0
  for pkg in "${to_enable[@]}"; do pkg_is_disabled "$pkg" && { still_disabled=$((still_disabled+1)); log_warn "ainda desabilitado: $pkg"; }; done
  local home_now; home_now="$(home_owner)"
  if [[ -f "$dir/home-owner.txt" && "$home_now" != "$(cat "$dir/home-owner.txt")" ]]; then
    log_warn "HOME abre '$home_now'; no backup era '$(cat "$dir/home-owner.txt")'. Um reboot costuma resolver."
  else
    log_ok "HOME abre $home_now (igual ao backup)."
  fi
  [[ "$still_disabled" -eq 0 ]] && log_ok "Todos os pacotes do backup estão habilitados."
  if [[ "$do_reboot" == "1" ]]; then
    log_step "Reiniciando a TV"
    tv_run reboot || true
  else
    log_info "Recomendado reiniciar: adb -s $TV_SERIAL reboot (ou --reboot)"
  fi
}

main "$@"
