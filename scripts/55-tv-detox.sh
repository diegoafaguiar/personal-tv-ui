#!/usr/bin/env bash
# 55-tv-detox.sh — instala a tela inicial do projeto TV Detox (Ansub/tv-detox).
#
# Uso:
#   scripts/55-tv-detox.sh install "com.google.android.youtube.tv,com.netflix.ninja,..." ["bluetooth,sound,picture"]
#   scripts/55-tv-detox.sh undo
#   scripts/55-tv-detox.sh status
#   scripts/55-tv-detox.sh open                     # abre a home do TV Detox agora (sem mexer no botão Home)
#
# O que é: uma home de uma tela só (pacote com.ansub.tvhome, Java puro, ~30 MB
# de RAM) com seus apps, entradas HDMI, relógio e ícone de rede. MIT.
#
# Como respeita a regra "nunca desligar a tela inicial do Google TV": o
# install.sh do projeto só desliga o launcherx com GOOGLE_TV=1, que este wrapper
# NÃO define. Num Google TV, portanto, ele instala o app e tenta registrá-lo
# como Home via "cmd role add-role-holder"; se o botão Home continuar abrindo o
# Google TV, abra o TV Detox pela lista de apps ou mapeie um botão do controle.
# Também não passamos YES=1: cada pergunta do install.sh chega até você.
#
# Desfazer: "undo" roda o undo.sh do projeto (reabilita o que ele desligou, se
# algo, e desinstala o com.ansub.tvhome).

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

VENDOR_DIR="${VENDOR_OVERRIDE:-$REPO_ROOT/vendor/tv-detox}"
REPO_URL="https://github.com/Ansub/tv-detox.git"

ensure_repo() {
  require_cmd git "Instale o git."
  if [[ -d "$VENDOR_DIR/.git" ]]; then
    log_info "tv-detox já clonado em $VENDOR_DIR (git -C vendor/tv-detox pull para atualizar)"
  else
    log_step "Clonando $REPO_URL"
    git clone --depth 1 "$REPO_URL" "$VENDOR_DIR"
  fi
  [[ -f "$VENDOR_DIR/install.sh" ]] || die "install.sh não encontrado em $VENDOR_DIR"
  log_info "Leia antes de rodar: $VENDOR_DIR/install.sh (é código de terceiros executado com acesso à sua TV)"
}

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in
    -h|--help|"") sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
  esac
  load_config
  ensure_connected
  require_backup >/dev/null
  ensure_repo
  case "$cmd" in
    status)
      log_step "TV Detox"
      log_info "com.ansub.tvhome: $(pkg_state com.ansub.tvhome)"
      log_info "HOME abre: $(home_owner)"
      log_info "Candidatos a HOME, em ordem:"
      home_candidates | sed 's/^/      /' >&2
      if [[ "$(home_owner)" != com.ansub.tvhome/* ]] && [[ "$(pkg_state com.ansub.tvhome)" == "enabled" ]]; then
        log_warn "Instalado, mas o botão Home abre outro app. Num Google TV isso só muda desligando o launcherx (regra atual: não) ou com um app que intercepte o Home (Projectivy 'Override'). Veja docs/04."
      fi
      ;;
    open)
      [[ "$(pkg_state com.ansub.tvhome)" == "enabled" ]] || die "com.ansub.tvhome não está instalado. Rode: scripts/55-tv-detox.sh install \"apps\""
      log_step "Abrindo o TV Detox"
      tv_run am start -n com.ansub.tvhome/.MainActivity
      ;;
    install)
      local apps="${1:-}" quick="${2:-}"
      [[ -n "$apps" ]] || die "Informe os apps: scripts/55-tv-detox.sh install \"pkg1,pkg2,...\""
      [[ "$DRY_RUN" == "1" ]] && { log_warn "dry-run: rodaria: sh install.sh $TV_SERIAL \"$apps\" ${quick:+\"$quick\"}"; exit 0; }
      log_step "Rodando install.sh do tv-detox (sem GOOGLE_TV=1 e sem YES=1: ele vai perguntar)"
      log_info "Memória livre antes: $(mem_available_mb) MB"
      (cd "$VENDOR_DIR" && env -u GOOGLE_TV -u YES -u YES_SETUP sh install.sh "$TV_SERIAL" "$apps" ${quick:+"$quick"})
      log_ok "HOME abre: $(home_owner)"
      log_info "Memória livre depois: $(mem_available_mb) MB"
      ;;
    undo)
      [[ "$DRY_RUN" == "1" ]] && { log_warn "dry-run: rodaria: sh undo.sh $TV_SERIAL"; exit 0; }
      (cd "$VENDOR_DIR" && sh undo.sh "$TV_SERIAL")
      ;;
    *) die "Subcomando desconhecido: $cmd" ;;
  esac
}

main "$@"
