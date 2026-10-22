#!/usr/bin/env bash
# 50-launcher.sh — troca o launcher (home) com verificações para não ficar sem tela.
#
# Uso:
#   scripts/50-launcher.sh status                     # quem responde ao HOME e candidatos
#   scripts/50-launcher.sh install [pacote]           # abre a página na Play Store (padrão: Projectivy)
#   scripts/50-launcher.sh install --apk caminho.apk  # sideload
#   scripts/50-launcher.sh grant [pacote]             # permissões + serviço de acessibilidade (Projectivy)
#   scripts/50-launcher.sh set-default [pacote]       # desabilita o launcher de fábrica com guarda
#   scripts/50-launcher.sh restore                    # volta o launcher de fábrica
#   scripts/50-launcher.sh set-default --dry-run      # --dry-run logo após o subcomando
#
# Launchers conhecidos:
#   com.spocky.projengmenu   Projectivy (padrão; mais configurável, gratuito)
#   me.efesser.flauncher     FLauncher (open source, minimalista)
#   com.wolf.launcher        Wolf Launcher
#
# O que você aprende aqui (docs/04-launcher-e-design.md):
#   1. "cmd package set-home-activity" imprime Success e NÃO funciona no Google TV.
#      O que funciona é desabilitar a concorrência: o launcher de fábrica.
#   2. Com o launcher de fábrica fora, a RecoveryActivity do setupwraith passa a
#      ganhar o HOME (tela preta/recovery). Ele também precisa sair.
#   3. O setupwraith pode ser o "dono" da rede Wi-Fi salva. Se for, desabilitá-lo
#      derruba o Wi-Fi. Solução: esquecer e reconectar a rede em Configurações
#      antes (passa a pertencer a com.android.tv.settings).
#   4. Ordem: instalar → abrir e concluir onboarding → testar com o controle →
#      só então set-default. Manter o adb conectado até confirmar.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

DEFAULT_LAUNCHER="com.spocky.projengmenu"
SETUPWRAITH="com.google.android.tungsten.setupwraith"

usage() { sed -n '2,27p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

cmd_status() {
  log_step "Estado do HOME"
  log_info "Launcher de fábrica : $(stock_launcher_pkg) ($(pkg_state "$(stock_launcher_pkg)"))"
  log_info "Setup wizard        : $SETUPWRAITH ($(pkg_state "$SETUPWRAITH"))"
  log_info "Dono do Wi-Fi salvo : $(wifi_owner)"
  log_info "HOME abre           : $(home_owner)"
  log_info "Candidatos, em ordem:"
  home_candidates | sed 's/^/      /' >&2
}

cmd_install() {
  local pkg="$DEFAULT_LAUNCHER" apk=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --apk) apk="$2"; shift 2 ;;
      *) pkg="$1"; shift ;;
    esac
  done
  if [[ -n "$apk" ]]; then
    log_step "Instalando $apk"
    log_info "ABI da TV: $(tv_prop ro.product.cpu.abi) (o APK precisa ser compatível)"
    [[ "$DRY_RUN" == "1" ]] || tv_adb install -r "$apk"
  else
    log_step "Abrindo $pkg na Play Store da TV"
    # -p com.android.vending evita que outra loja (Aptoide) sequestre o market://
    tv_run am start -a android.intent.action.VIEW -d "market://details?id=$pkg" -p com.android.vending
    log_info "Instale pela tela da TV. Depois: abra o launcher, conclua o onboarding, e rode 'grant' e 'set-default'."
  fi
}

cmd_grant() {
  local pkg="${1:-$DEFAULT_LAUNCHER}"
  pkg_is_installed "$pkg" || die "$pkg não está instalado."
  log_step "Concedendo permissões a $pkg"
  local perm
  for perm in android.permission.READ_TV_LISTINGS android.permission.READ_EXTERNAL_STORAGE; do
    tv_run pm grant "$pkg" "$perm" || log_dim "($perm não aplicável, ignorado)"
  done
  if [[ "$pkg" == "com.spocky.projengmenu" ]]; then
    log_step "Ativando serviço de acessibilidade do Projectivy (atalhos do controle)"
    settings_put secure enabled_accessibility_services "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService"
    settings_put secure accessibility_enabled 1
  fi
  tv_run am force-stop "$pkg" || true
  log_ok "Feito. Abra o launcher de novo na TV."
}

cmd_set_default() {
  local pkg="${1:-$DEFAULT_LAUNCHER}" force_wifi="${FORCE_SETUPWRAITH:-0}"
  require_backup >/dev/null
  local stock; stock="$(stock_launcher_pkg)"
  [[ -n "$stock" ]] || die "Não identifiquei o launcher de fábrica (launcherx/tvlauncher)."

  log_step "Pré-verificações"
  [[ "$(pkg_state "$pkg")" == "enabled" ]] || die "$pkg não está instalado e habilitado."
  if ! home_candidates | grep -q "^$pkg/"; then
    die "$pkg não aparece como candidato a HOME. Abra-o na TV e conclua o onboarding primeiro."
  fi
  log_ok "$pkg é candidato a HOME."

  local owner; owner="$(wifi_owner)"
  if [[ "$owner" == "$SETUPWRAITH" && "$force_wifi" != "1" ]]; then
    die "A rede Wi-Fi salva pertence ao $SETUPWRAITH. Esqueça e reconecte a rede em Configurações antes, ou (só em Ethernet) FORCE_SETUPWRAITH=1."
  fi
  log_ok "Wi-Fi não pertence ao setup wizard (dono: ${owner:-nenhum})."

  log_info "Plano: desabilitar $stock e $SETUPWRAITH; verificar HOME → $pkg; reverter automaticamente se falhar."
  [[ "$DRY_RUN" == "1" ]] && { log_ok "Dry-run concluído."; return 0; }
  confirm "Aplicar?" || die "Cancelado."

  tv_run pm disable-user --user 0 "$stock"
  tv_run pm disable-user --user 0 "$SETUPWRAITH"
  tv_run input keyevent KEYCODE_HOME
  sleep 2
  local now; now="$(home_owner)"
  if [[ "$now" == "$pkg/"* ]]; then
    log_ok "HOME agora abre $now"
    log_info "Teste o controle (Home, Voltar, Input). Se algo estiver errado: scripts/50-launcher.sh restore"
    log_info "Dica: botões YouTube/Netflix do controle podem parar de abrir apps; o Projectivy remapeia em Configurações > Controle remoto."
  else
    log_err "HOME foi para '$now', não para $pkg. Revertendo."
    cmd_restore
    die "Reversão aplicada. Veja docs/07-troubleshooting.md."
  fi
}

cmd_restore() {
  local stock; stock="$(stock_launcher_pkg)"
  log_step "Restaurando launcher de fábrica"
  [[ -n "$stock" ]] && tv_run pm enable --user 0 "$stock"
  tv_run pm enable --user 0 "$SETUPWRAITH"
  tv_run input keyevent KEYCODE_HOME
  sleep 1
  log_ok "HOME abre: $(home_owner)"
}

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in
    -h|--help|"") usage; exit 0 ;;
  esac
  if [[ "${1:-}" == "--dry-run" ]]; then export DRY_RUN=1; shift; fi
  load_config
  ensure_connected
  case "$cmd" in
    status) cmd_status ;;
    install) cmd_install "$@" ;;
    grant) cmd_grant "$@" ;;
    set-default) cmd_set_default "$@" ;;
    restore) cmd_restore ;;
    *) die "Subcomando desconhecido: $cmd" ;;
  esac
}

main "$@"
