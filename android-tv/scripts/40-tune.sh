#!/usr/bin/env bash
# 40-tune.sh — ajustes de performance e limpeza que não removem nada.
#
# Uso:
#   android-tv/scripts/40-tune.sh --recommended            # animações 0.5x + limpar caches
#   android-tv/scripts/40-tune.sh --animations 0.5         # 0 = sem animação; 1 = padrão
#   android-tv/scripts/40-tune.sh --screensaver off|basic|keep
#   android-tv/scripts/40-tune.sh --private-dns dns.adguard.com   # bloqueio de anúncios/telemetria via DNS
#   android-tv/scripts/40-tune.sh --private-dns off
#   android-tv/scripts/40-tune.sh --trim-caches            # equivale a "limpar cache" de todos os apps
#   android-tv/scripts/40-tune.sh --dexopt                 # recompila apps em background (demora; ajuda em TVs antigas)
#   android-tv/scripts/40-tune.sh --sleep-after 5          # suspender após N minutos sem uso
#   android-tv/scripts/40-tune.sh --oled-ask               # mostra o modelo e pergunta se é OLED; se NÃO for, desliga o screensaver
#   android-tv/scripts/40-tune.sh --guided                 # = --animations 0 --sleep-after 5 --oled-ask (modo guiado)
#   android-tv/scripts/40-tune.sh --dry-run ...
#
# Tudo aqui é revertido por android-tv/scripts/90-rollback.sh (as chaves estão em
# TRACKED_SETTINGS no android-tv/lib/common.sh) ou manualmente:
#   adb shell settings put global window_animation_scale 1
#
# O que você aprende aqui:
#   - Animações a 0.5x não deixam a TV mais rápida, deixam a UI mais responsiva
#     ao controle. 0 pode quebrar transições de alguns apps; 0.5 é o consenso.
#   - "Ambient mode" (dreamx) carrega promoções e ~40 MB; se quiser desabilitar
#     o pacote depois, troque o screensaver AQUI primeiro, senão o Android
#     fica apontando para um componente inexistente.
#   - Private DNS é uma forma limpa e reversível de cortar anúncios e
#     telemetria em todos os apps, sem instalar nada na TV.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

usage() { sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

set_animations() {
  local scale="$1"
  log_step "Animações → ${scale}x"
  local key
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    settings_put global "$key" "$scale"
  done
}

set_screensaver() {
  local mode="$1"
  case "$mode" in
    keep) return 0 ;;
    off)
      log_step "Screensaver → desligado"
      settings_put secure screensaver_enabled 0
      ;;
    basic)
      log_step "Screensaver → básico do AOSP (sem promoções)"
      if pkg_is_installed com.android.dreams.basic; then
        settings_put secure screensaver_components "com.android.dreams.basic/.Colors"
        settings_put secure screensaver_enabled 1
      else
        log_warn "com.android.dreams.basic não existe nesta TV; desligando o screensaver em vez disso."
        settings_put secure screensaver_enabled 0
      fi
      ;;
    *) die "--screensaver aceita off|basic|keep" ;;
  esac
}

set_sleep_after() {
  local minutes="$1"
  log_step "Suspender após $minutes min sem uso"
  settings_put system screen_off_timeout "$((minutes * 60 * 1000))"
}

oled_ask() {
  local model; model="$(tv_prop ro.product.model) ($(tv_prop ro.product.device), $(tv_prop ro.product.brand))"
  log_step "Modelo: $model"
  log_info "Em OLED o screensaver protege contra burn-in e deve ficar LIGADO."
  if confirm "Esta TV é OLED?"; then
    log_ok "OLED: screensaver fica ligado."
    [[ "$(settings_get secure screensaver_enabled)" == "1" ]] || settings_put secure screensaver_enabled 1
  else
    log_info "Não é OLED: screensaver desligado (menos CPU e nada de promoções à noite)."
    settings_put secure screensaver_enabled 0
  fi
}

set_private_dns() {
  local host="$1"
  if [[ "$host" == "off" ]]; then
    log_step "Private DNS → desligado"
    settings_put global private_dns_mode off
    settings_put global private_dns_specifier null
  else
    log_step "Private DNS → $host (DNS-over-TLS)"
    settings_put global private_dns_mode hostname
    settings_put global private_dns_specifier "$host"
    log_info "Se algum app reclamar de rede, volte com: android-tv/scripts/40-tune.sh --private-dns off"
  fi
}

main() {
  local anim="" ss="keep" dns="" trim=0 dexopt=0 any=0 sleep_after="" oled=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run) export DRY_RUN=1; shift ;;
      --recommended) anim="0.5"; trim=1; any=1; shift ;;
      --animations) anim="$2"; any=1; shift 2 ;;
      --screensaver) ss="$2"; any=1; shift 2 ;;
      --private-dns) dns="$2"; any=1; shift 2 ;;
      --trim-caches) trim=1; any=1; shift ;;
      --dexopt) dexopt=1; any=1; shift ;;
      --sleep-after) sleep_after="$2"; any=1; shift 2 ;;
      --oled-ask) oled=1; any=1; shift ;;
      --guided) anim="0"; sleep_after="5"; oled=1; any=1; shift ;;
      -h|--help) usage ;;
      *) die "Argumento desconhecido: $1" ;;
    esac
  done
  [[ "$any" == "1" ]] || usage 1

  load_config
  ensure_connected
  require_backup >/dev/null

  [[ -n "$anim" ]] && set_animations "$anim"
  [[ -n "$sleep_after" ]] && set_sleep_after "$sleep_after"
  set_screensaver "$ss"
  [[ "$oled" == "1" ]] && oled_ask
  [[ -n "$dns" ]] && set_private_dns "$dns"
  if [[ "$trim" == "1" ]]; then
    log_step "Limpando caches de todos os apps"
    tv_run pm trim-caches 999G || true
  fi
  if [[ "$dexopt" == "1" ]]; then
    log_step "Disparando dexopt em background (pode levar vários minutos)"
    tv_run cmd package bg-dexopt-job || true
  fi
  log_ok "Pronto. Estado atual:"
  log_info "animações: $(settings_get global window_animation_scale) / $(settings_get global transition_animation_scale) / $(settings_get global animator_duration_scale)"
  log_info "screensaver: enabled=$(settings_get secure screensaver_enabled) components=$(settings_get secure screensaver_components)"
  log_info "private dns: $(settings_get global private_dns_mode) $(settings_get global private_dns_specifier)"
  log_info "suspender após: $(( $(settings_get system screen_off_timeout | sed 's/null/0/') / 60000 )) min"
}

main "$@"
