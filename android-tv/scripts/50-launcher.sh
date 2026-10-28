#!/usr/bin/env bash
# 50-launcher.sh — troca o launcher (home) com verificações para não ficar sem tela.
#
# Uso:
#   android-tv/scripts/50-launcher.sh status                     # quem responde ao HOME e candidatos
#   android-tv/scripts/50-launcher.sh install [pacote]           # abre a página na Play Store (padrão: Projectivy)
#   android-tv/scripts/50-launcher.sh install --apk caminho.apk  # sideload
#   android-tv/scripts/50-launcher.sh grant [pacote]             # permissões + serviço de acessibilidade (Projectivy)
#   android-tv/scripts/50-launcher.sh set-default [pacote]       # desabilita o launcher de fábrica com guarda
#   android-tv/scripts/50-launcher.sh set-default [pacote] --disable-others   # também desliga outros launchers de terceiros que disputem o HOME
#                                                     #   exige ALLOW_STOCK_LAUNCHER_DISABLE=1 (decisão explícita)
#                                                     #   ex.: ALLOW_STOCK_LAUNCHER_DISABLE=1 android-tv/scripts/50-launcher.sh set-default com.ansub.tvhome
#   android-tv/scripts/50-launcher.sh restore                    # volta o launcher de fábrica
#   android-tv/scripts/50-launcher.sh set-default --dry-run      # --dry-run logo após o subcomando
#
# Launchers conhecidos:
#   tech.futureme.tvhome     Minha TV (android-tv/launcher/ deste repositório; APK vem da CI)
#   com.ansub.tvhome         TV Detox (instalado por 55-tv-detox.sh)
#   com.spocky.projengmenu   Projectivy (padrão; mais configurável, gratuito)
#   me.efesser.flauncher     FLauncher (open source, minimalista)
#   com.wolf.launcher        Wolf Launcher
#
# O que você aprende aqui (android-tv/docs/04-launcher-e-design.md):
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
  if [[ "$pkg" == "tech.futureme.tvhome" ]]; then
    # Acesso a "uso de apps": ordena a linha "Em alta" pelos apps mais usados
    tv_run appops set "$pkg" GET_USAGE_STATS allow || log_dim "(uso de apps não concedido; Em alta usa a ordem dos Ajustes)"
    # Controle pelo celular: Início e abrir apps com outro app na frente
    tv_run appops set "$pkg" SYSTEM_ALERT_WINDOW allow || log_dim "(sobreposição não concedida; o celular só comanda com a home na frente)"
    log_info "A permissão READ_TV_LISTINGS é o que libera 'Continuar assistindo' e as seções."
  fi
  if [[ "$pkg" == "com.spocky.projengmenu" ]]; then
    log_step "Ativando serviço de acessibilidade do Projectivy (atalhos do controle)"
    settings_put secure enabled_accessibility_services "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService"
    settings_put secure accessibility_enabled 1
  fi
  tv_run am force-stop "$pkg" || true
  log_ok "Feito. Abra o launcher de novo na TV."
}

cmd_set_default() {
  local pkg="${1:-$DEFAULT_LAUNCHER}" force_wifi="${FORCE_SETUPWRAITH:-0}" disable_others=0
  [[ "${2:-}" == "--disable-others" ]] && disable_others=1
  if [[ "${ALLOW_STOCK_LAUNCHER_DISABLE:-0}" != "1" ]]; then
    die "Regra ativa: a tela inicial de fábrica não é desligada. Use o 'Override current launcher' do Projectivy, ou exporte ALLOW_STOCK_LAUNCHER_DISABLE=1 se mudar de ideia."
  fi
  require_backup >/dev/null
  local stock; stock="$(stock_launcher_pkg)"
  [[ -n "$stock" ]] || die "Não identifiquei o launcher de fábrica (launcherx/tvlauncher)."

  log_step "Pré-verificações"
  [[ "$(pkg_state "$pkg")" == "enabled" ]] || die "$pkg não está instalado e habilitado."
  if ! home_candidates | grep -q "^$pkg/"; then
    log_info "Candidatos a HOME hoje:"; home_candidates | sed 's/^/      /' >&2
    die "$pkg não aparece como candidato a HOME. Abra-o na TV e conclua o onboarding primeiro."
  fi
  log_ok "$pkg é candidato a HOME."

  local owner; owner="$(wifi_owner)"
  if [[ "$owner" == "$SETUPWRAITH" && "$force_wifi" != "1" ]]; then
    die "A rede Wi-Fi salva pertence ao $SETUPWRAITH. Esqueça e reconecte a rede em Configurações antes, ou (só em Ethernet) FORCE_SETUPWRAITH=1."
  fi
  log_ok "Wi-Fi não pertence ao setup wizard (dono: ${owner:-nenhum})."

  # Estado anterior, para a reversão devolver só o que este passo mudou.
  local stock_was setup_was home_was
  stock_was="$(pkg_state "$stock")"; setup_was="$(pkg_state "$SETUPWRAITH")"; home_was="$(home_owner)"
  local -a others=()
  local c
  while IFS= read -r c; do
    case "$c" in
      "$pkg/"*|"$stock/"*|"$SETUPWRAITH/"*|com.android.tv.settings/*|com.android.settings/*|com.android.systemui/*) ;;
      *) others+=("${c%%/*}") ;;
    esac
  done < <(home_candidates)
  mapfile -t others < <(printf '%s\n' "${others[@]+"${others[@]}"}" | awk 'NF && !seen[$0]++')

  log_info "Plano: desabilitar $stock e $SETUPWRAITH (se ligados); set-home-activity → $pkg; verificar; reverter só o que mudou se falhar."
  if [[ ${#others[@]} -gt 0 ]]; then
    if [[ "$disable_others" == "1" ]]; then log_info "Também desligar outros launchers de terceiros: ${others[*]}"
    else log_info "Outros launchers de terceiros presentes (${others[*]}); se o HOME ficar com eles, repita com --disable-others."; fi
  fi
  [[ "$DRY_RUN" == "1" ]] && { log_ok "Dry-run concluído."; return 0; }
  confirm "Aplicar?" || die "Cancelado."

  [[ "$stock_was" == "enabled" ]] && tv_run pm disable-user --user 0 "$stock"
  [[ "$setup_was" == "enabled" ]] && tv_run pm disable-user --user 0 "$SETUPWRAITH"
  if [[ "$disable_others" == "1" ]]; then
    for c in "${others[@]}"; do tv_run pm disable-user --user 0 "$c"; done
  fi
  # Entre launchers de terceiros de mesma prioridade, isto escolhe o preferido.
  # (Não funciona contra o launcherx habilitado; por isso ele é desligado antes.)
  local comp; comp="$(home_candidates | grep "^$pkg/" | head -n1)"
  [[ -n "$comp" ]] && { tv_run cmd package set-home-activity "$comp" || true; }
  tv_run input keyevent KEYCODE_HOME
  sleep 2
  local now; now="$(home_owner)"
  if [[ "$now" == "$pkg/"* ]]; then
    log_ok "HOME agora abre $now"
    log_step "Aguardando ${PM_WRITE_WAIT:-15} s para o Android gravar o estado (não reinicie antes)"
    sleep "${PM_WRITE_WAIT:-15}"
    log_info "Teste o controle (Home, Voltar, Input, mic). Se algo estiver errado: android-tv/scripts/50-launcher.sh restore"
    log_info "Botões YouTube/Netflix do controle podem parar de abrir apps (eram tratados pelo launcherx)."
    log_info "Depois: android-tv/scripts/95-finish.sh para reiniciar com verificação."
  else
    log_err "HOME foi para '$now', não para $pkg. Revertendo só o que este passo mudou."
    [[ "$stock_was" == "enabled" ]] && tv_run pm enable --user 0 "$stock"
    [[ "$setup_was" == "enabled" ]] && tv_run pm enable --user 0 "$SETUPWRAITH"
    if [[ "$disable_others" == "1" ]]; then for c in "${others[@]}"; do tv_run pm enable --user 0 "$c"; done; fi
    [[ "$home_was" == */* ]] && { tv_run cmd package set-home-activity "$home_was" || true; }
    tv_run input keyevent KEYCODE_HOME
    sleep 1
    log_info "HOME abre: $(home_owner) (antes era $home_was)"
    if [[ ${#others[@]} -gt 0 && "$disable_others" != "1" ]]; then
      die "Outro launcher de terceiros ficou com o HOME. Repita com: ALLOW_STOCK_LAUNCHER_DISABLE=1 android-tv/scripts/50-launcher.sh set-default $pkg --disable-others"
    fi
    die "Veja android-tv/docs/07-troubleshooting.md."
  fi
}

cmd_restore() {
  local stock; stock="$(stock_launcher_pkg)"
  log_step "Restaurando launcher de fábrica"
  [[ -n "$stock" ]] && tv_run pm enable --user 0 "$stock"
  tv_run pm enable --user 0 "$SETUPWRAITH"
  # O launcherx usa um listener de notificações; o undo do TV Detox também reautoriza.
  if [[ "$stock" == "com.google.android.apps.tv.launcherx" ]]; then
    tv_run cmd notification allow_listener "$stock/.coreservices.notificationlistener.TvNotificationListenerService" || true
  fi
  tv_run input keyevent KEYCODE_HOME
  sleep 1
  log_ok "HOME abre: $(home_owner)"
  [[ "$DRY_RUN" == "1" ]] || sleep "${PM_WRITE_WAIT:-15}"
}

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in
    -h|--help|"") usage; exit 0 ;;
  esac
  # --dry-run em qualquer posição
  local -a rest=()
  local a; for a in "$@"; do [[ "$a" == "--dry-run" ]] && export DRY_RUN=1 || rest+=("$a"); done
  set -- ${rest[@]+"${rest[@]}"}
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
