#!/usr/bin/env bash
# 58-launcher-wallpaper.sh — envia uma imagem para ser o fundo da home "Minha TV".
#
# Uso:
#   android-tv/scripts/58-launcher-wallpaper.sh ~/Pictures/fundo.jpg
#   android-tv/scripts/58-launcher-wallpaper.sh --remove
#
# A imagem vai para a pasta privada do app na TV (não precisa de permissão):
#   /sdcard/Android/data/tech.futureme.tvhome/files/wallpaper.jpg
# Depois escolha "Minha imagem" em Ajustes da home › Fundo. Use JPEG 1920x1080
# (ou maior, 16:9); imagens escuras ficam melhores, o launcher já escurece 50%.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

DEST="/sdcard/Android/data/tech.futureme.tvhome/files/wallpaper.jpg"

main() {
  local src="${1:-}"
  [[ -n "$src" ]] || { sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1; }
  load_config
  ensure_connected
  [[ "$(pkg_state tech.futureme.tvhome)" == "enabled" ]] || die "O launcher Minha TV não está instalado."
  if [[ "$src" == "--remove" ]]; then
    tv_run rm -f "$DEST"; log_ok "Imagem removida; o fundo volta ao predefinido."; exit 0
  fi
  [[ -f "$src" ]] || die "Arquivo não existe: $src"
  log_step "Enviando $(basename "$src") ($(du -h "$src" | cut -f1)) para a TV"
  [[ "$DRY_RUN" == "1" ]] || { tv_shell mkdir -p "$(dirname "$DEST")"; tv_adb push "$src" "$DEST"; }
  log_ok "Pronto. Na TV: Ajustes da home › Fundo › Minha imagem. Reabra a home (Home) para atualizar."
}

main "$@"
