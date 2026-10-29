#!/bin/bash
# rodar-mac.command — dois cliques no Finder para ver o launcher "Sintonia" no Mac (emulador).
# Se o macOS disser que não pode abrir: botão direito › Abrir (só na primeira vez).
# Faz o mesmo que "make up" no Terminal. Nada é instalado nas TVs.
cd "$(dirname "$0")" || exit 1

fim() { echo; read -r -p "Pressione Enter para fechar esta janela." _ ; exit "${1:-0}"; }

# Homebrew: instala as ferramentas sem sudo (bash 5, JDK 17, Android SDK)
for b in /opt/homebrew/bin/brew /usr/local/bin/brew; do
  [[ -x "$b" ]] && eval "$("$b" shellenv)" && break
done
if ! command -v brew >/dev/null 2>&1; then
  echo "Falta o Homebrew. Instale com o comando de https://brew.sh e abra este arquivo de novo."
  fim 1
fi
# Os scripts precisam do bash 4+ (o do macOS é o 3.2)
if ! bash -c '(( BASH_VERSINFO[0] >= 4 ))' 2>/dev/null; then
  echo "Instalando o bash atual pelo Homebrew..."
  brew install bash || fim 1
  hash -r
fi

bash android-tv/scripts/81-up.sh
fim $?
