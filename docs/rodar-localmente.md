# Rodar o launcher "Minha TV" no Mac

O launcher roda num **emulador Android TV** no próprio Mac: você vê e navega como se fosse a
TV, sem tocar nas TVs da casa.

## Do jeito mais simples: dois cliques

No Finder, abra a pasta do projeto e dê dois cliques em **`rodar-mac.command`**.

- Na primeira vez o macOS pode dizer que não consegue abrir: **botão direito › Abrir › Abrir**.
- Se faltar o **Homebrew**, a janela avisa; instale pelo comando de <https://brew.sh> e abra de novo.
- A primeira vez demora uns 10 minutos (baixa JDK 17, Android SDK e a imagem Android TV, ~6 GB).
  Das próximas, uns 30 segundos.

No fim, a janela do emulador mostra a home "Minha TV".

## Pelo Terminal: um comando

```bash
make up
```

Faz o mesmo: prepara o que faltar, liga o emulador (se estiver desligado), compila, instala,
abre o launcher e faz o botão Home do emulador abrir o "Minha TV".

## Usando o emulador

| No Mac | Na TV |
|---|---|
| ← ↑ → ↓ | setas do controle |
| Enter | OK |
| Esc | Voltar |
| clique do mouse | foca e abre o item |

| Para | Comando |
|---|---|
| Recompilar depois de editar o código (~20 s) | `make dev` |
| Ver os logs do launcher | `make dev-logs` |
| Controle pelo terminal (setas, OK, screenshot) | `android-tv/scripts/85-launcher-dev.sh remote` |
| Desligar o emulador | `make emulator-stop` |
| Voltar o emulador ao estado de fábrica | `android-tv/scripts/80-emulator.sh wipe` |

O emulador não tem antena, HDMI nem os apps de streaming brasileiros: Continuar assistindo,
TV ao vivo e Entradas só aparecem na TV de verdade.

## Mandar para a TV

Depois de conferir no emulador:

```bash
make dev-push TV=tcl-g13                                   # versão de teste (debug)
TV=tcl-g13 android-tv/scripts/85-launcher-dev.sh push --release   # versão final, menor
```

Detalhes (instalar, atualizar, voltar ao launcher de fábrica): [android-tv/docs/09-launcher-proprio.md](../android-tv/docs/09-launcher-proprio.md).
