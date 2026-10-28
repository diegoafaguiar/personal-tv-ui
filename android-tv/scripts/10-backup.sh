#!/usr/bin/env bash
# 10-backup.sh — fotografa o estado da TV ANTES de qualquer mudança.
#
# Uso:
#   android-tv/scripts/10-backup.sh [--label texto]
#
# Gera backups/<data>_<modelo>[_label]/ com:
#   packages-*.txt      listas de pacotes (instalados, habilitados, desabilitados,
#                       conhecidos-inclusive-desinstalados, de terceiros, caminhos de APK)
#   settings-*.txt      dump completo de settings global/secure/system
#   settings.env        só as chaves que os scripts alteram (o rollback usa este)
#   home-owner.txt      quem responde ao botão HOME
#   home-candidates.txt todos os candidatos a HOME, em ordem
#   wifi-owner.txt      pacote dono da rede Wi-Fi salva
#   props.txt           getprop completo (modelo, firmware, chipset)
#   meminfo.txt         baseline de memória para comparar depois
#   README.md           o que é cada arquivo
# e aponta backups/LATEST para ele.
#
# Por que isso é suficiente para voltar ao original: nada que os scripts fazem
# apaga dados. "pm disable-user" e "pm uninstall -k --user 0" mantêm o APK na
# partição /system; o rollback só reverte estados e chaves de settings.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

main() {
  local label=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --label) label="_$(printf '%s' "$2" | tr -c 'A-Za-z0-9_.-' '_')"; shift 2 ;;
      -h|--help) sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
      *) die "Argumento desconhecido: $1" ;;
    esac
  done

  load_config
  ensure_connected

  local dir
  dir="$BACKUPS_DIR/$(timestamp)_$(device_slug)$label"
  mkdir -p "$dir"
  log_step "Salvando backup em $dir"

  log_info "Propriedades do aparelho"
  tv_shell getprop | tr -d '\r' > "$dir/props.txt"
  {
    echo "brand=$(tv_prop ro.product.brand)"
    echo "model=$(tv_prop ro.product.model)"
    echo "device=$(tv_prop ro.product.device)"
    echo "android=$(tv_prop ro.build.version.release)"
    echo "sdk=$(tv_prop ro.build.version.sdk)"
    echo "build=$(tv_prop ro.build.display.id)"
    echo "fingerprint=$(tv_prop ro.build.fingerprint)"
    echo "platform=$(tv_prop ro.board.platform)"
    echo "abi=$(tv_prop ro.product.cpu.abi)"
    echo "stock_launcher=$(stock_launcher_pkg)"
    echo "backup_taken_at=$(date -Iseconds)"
  } > "$dir/device.txt"

  log_info "Listas de pacotes"
  pm_list        > "$dir/packages-installed.txt"
  pm_list -e     > "$dir/packages-enabled.txt"
  pm_list -d     > "$dir/packages-disabled.txt"
  pm_list -u     > "$dir/packages-known.txt"
  pm_list -3     > "$dir/packages-third-party.txt"
  tv_shell pm list packages -f -u | tr -d '\r' | sed 's/^package://' | sort > "$dir/packages-paths.txt"

  log_info "Settings (dump completo + chaves rastreadas)"
  local ns
  for ns in global secure system; do
    tv_shell settings list "$ns" | tr -d '\r' | sort > "$dir/settings-$ns.txt"
  done
  : > "$dir/settings.env"
  local entry key value
  for entry in "${TRACKED_SETTINGS[@]}"; do
    ns="${entry%%:*}"; key="${entry#*:}"
    value="$(settings_get "$ns" "$key")"
    printf '%s:%s=%s\n' "$ns" "$key" "$value" >> "$dir/settings.env"
  done

  log_info "Launcher e Wi-Fi"
  home_owner       > "$dir/home-owner.txt"
  home_candidates  > "$dir/home-candidates.txt"
  wifi_owner       > "$dir/wifi-owner.txt"

  log_info "Baseline de memória"
  tv_shell dumpsys meminfo 2>/dev/null | tr -d '\r' | head -n 60 > "$dir/meminfo.txt" || true
  tv_shell cat /proc/meminfo | tr -d '\r' > "$dir/proc-meminfo.txt"
  log_info "Memória livre (MemAvailable): $(awk '/MemAvailable/ {printf "%d", $2/1024}' "$dir/proc-meminfo.txt") MB"

  cat > "$dir/README.md" <<'MD'
# Backup da TV

Gerado por `android-tv/scripts/10-backup.sh`. Para voltar a este estado:

```bash
android-tv/scripts/90-rollback.sh "<esta pasta>" --dry-run   # veja o plano
android-tv/scripts/90-rollback.sh "<esta pasta>" --reboot    # aplique
```

| Arquivo | Conteúdo | Usado pelo rollback? |
|---|---|---|
| `packages-installed.txt` | pacotes instalados para o usuário 0 | sim (reinstala os que sumiram) |
| `packages-enabled.txt` | pacotes habilitados | sim (reabilita) |
| `packages-disabled.txt` | pacotes desabilitados (inclusive de fábrica) | sim (re-desabilita) |
| `packages-known.txt` | tudo que já esteve instalado (`pm list packages -u`) | referência |
| `packages-third-party.txt` | apps que você instalou | referência |
| `packages-paths.txt` | caminho do APK de cada pacote | referência |
| `settings.env` | chaves de settings que os scripts tocam | sim |
| `settings-*.txt` | dump completo de settings | referência / auditoria |
| `home-owner.txt` | quem respondia ao HOME | verificação |
| `home-candidates.txt` | candidatos a HOME em ordem | referência |
| `wifi-owner.txt` | dono da rede Wi-Fi salva | verificação |
| `props.txt` / `device.txt` | modelo, firmware, chipset | referência |
| `meminfo.txt` / `proc-meminfo.txt` | memória antes das mudanças | comparação (95-finish.sh) |

Atenção: `settings-*.txt` e `props.txt` podem conter SSID, contas e seriais.
Por isso `backups/` está no `.gitignore`.
MD

  printf '%s\n' "$(basename "$dir")" > "$BACKUPS_DIR/LATEST"
  log_ok "Backup concluído: $(wc -l < "$dir/packages-installed.txt") pacotes instalados, $(wc -l < "$dir/packages-disabled.txt") já desabilitados de fábrica."
  log_info "HOME hoje: $(cat "$dir/home-owner.txt")"
  log_info "Próximo passo: android-tv/scripts/20-inventory.sh"
}

main "$@"
