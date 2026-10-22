#!/usr/bin/env bash
# 57-launcher-apk.sh — baixa o APK mais recente do launcher (artefato da CI) e instala.
#
# Uso:
#   scripts/57-launcher-apk.sh                # baixa o último build da branch atual e instala o release
#   scripts/57-launcher-apk.sh --debug        # instala o debug em vez do release
#   scripts/57-launcher-apk.sh --file caminho/app-release.apk   # pula o download
#
# Precisa do GitHub CLI autenticado (brew install gh; gh auth login) para o
# download. Sem ele, baixe o artefato "tvhome-apk" na aba Actions e passe --file.
# O zip do artefato preserva pastas: o APK fica em release/app-release.apk.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

DIST="$REPO_ROOT/launcher/dist"
PKG="tech.futureme.tvhome"

main() {
  local variant=release file=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --debug) variant=debug; shift ;;
      --file) file="$2"; shift 2 ;;
      -h|--help) sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
      *) die "Argumento desconhecido: $1" ;;
    esac
  done

  if [[ -z "$file" ]]; then
    require_cmd gh "Instale o GitHub CLI (brew install gh) e rode gh auth login, ou use --file."
    local branch; branch="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
    log_step "Procurando o último build verde de launcher.yml na branch $branch"
    local run_id
    run_id="$(gh run list -R diegoafaguiar/personal-tv-ui --workflow launcher.yml --branch "$branch" --status success --limit 1 --json databaseId --jq '.[0].databaseId')"
    [[ -n "$run_id" && "$run_id" != "null" ]] || die "Nenhum build verde encontrado para $branch. Veja a aba Actions."
    rm -rf "$DIST"; mkdir -p "$DIST"
    gh run download "$run_id" -R diegoafaguiar/personal-tv-ui -n tvhome-apk -D "$DIST"
    file="$(find "$DIST" -name "app-$variant.apk" | head -n1)"
    [[ -f "$file" ]] || die "APK não encontrado no artefato (procurei app-$variant.apk em $DIST)."
    log_ok "Baixado: $file (run $run_id)"
  fi
  [[ -f "$file" ]] || die "Arquivo não existe: $file"

  load_config
  ensure_connected
  log_step "Instalando $(basename "$file") (ABI da TV: $(tv_prop ro.product.cpu.abi))"
  if [[ "$DRY_RUN" != "1" ]]; then
    local out
    out="$(tv_adb install -r "$file" 2>&1 || true)"
    echo "$out" >&2
    if [[ "$out" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
      log_warn "A versão instalada foi assinada com outra chave (builds antigos da CI usavam uma chave nova a cada execução)."
      log_warn "Para atualizar é preciso desinstalar uma vez. Você perde os ajustes da home (dock, fundo) e o Home volta ao TV Detox até o próximo set-default."
      confirm "Desinstalar $PKG e instalar de novo?" || die "Cancelado."
      tv_adb uninstall "$PKG" || true
      tv_adb install "$file"
      log_info "Agora: scripts/50-launcher.sh grant $PKG && ALLOW_STOCK_LAUNCHER_DISABLE=1 scripts/50-launcher.sh set-default $PKG"
    elif [[ "$out" != *Success* ]]; then
      die "Instalação falhou."
    fi
  fi
  log_ok "$PKG: $(pkg_state "$PKG")"
  log_info "Próximos: scripts/50-launcher.sh grant $PKG && scripts/50-launcher.sh status"
  log_info "Depois:   ALLOW_STOCK_LAUNCHER_DISABLE=1 scripts/50-launcher.sh set-default $PKG"
}

main "$@"
