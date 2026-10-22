# 4. Launcher e design de UI para TV

## Decisão na unidade de referência (G09): caminho 2

Em 22/10/2026 o dono optou por manter o TV Detox e desligar o `launcherx` com
as guardas do `50-launcher.sh` (`ALLOW_STOCK_LAUNCHER_DISABLE=1`). Motivo: num
Google TV o botão Home só muda de dono assim. Reversão: `50-launcher.sh restore`.

## Regra padrão: a tela inicial de fábrica não é desligada

Por decisão do dono, `com.google.android.apps.tv.launcherx` (e o `setupwraith`)
ficam ligados. O launcher alternativo convive com ela: o botão Home continua
abrindo o Google TV, e o novo launcher é aberto pela lista de apps, por um
botão remapeado, ou pelo "Override current launcher" do Projectivy (que
intercepta o Home sem desligar nada). O TV Detox tenta registrar-se via
`cmd role add-role-holder`, que em alguns Google TV funciona sem desligar o
launcherx; em outros, não. A seção "Setting as default" abaixo fica como
referência e exige `ALLOW_STOCK_LAUNCHER_DISABLE=1`.

## O que a comunidade está fazendo (exemplos clean)

| Abordagem | O que é | Quando escolher |
|---|---|---|
| **Projectivy Launcher** (`com.spocky.projengmenu`) | o launcher de terceiros mais usado em Android/Google TV. Remove anúncios e linhas patrocinadas; categorias, ícones e tamanhos configuráveis; "Override current launcher" nas configurações; remapeia botões via serviço de acessibilidade. Gratuito; wallpapers e ícones custom são premium | **padrão deste kit**: melhor relação esforço/resultado, mantido ativamente |
| **FLauncher** (`me.efesser.flauncher`) | open source (Flutter), minimalista, grade de apps e wallpaper. Relatos: "mais parecido com o que eu quero" e "tosco nas bordas" | você quer o mínimo absoluto e aceita arestas |
| **Wolf Launcher** (`com.wolf.launcher`) | simples, lembra o Android TV clássico | nostalgia do layout em linhas |
| **Clean Your TV launcher** (`com.gathin.tvhome`) | launcher nativo em Java puro (~445 KB, sem permissão de rede, sem serviços em background) com **estilo Apple TV**: fundo preto, dock de 4 favoritos numa faixa arredondada, "shelf" no topo com o nome do app focado e brilho ambiente na cor da capa, grade de apps que sobe no D-pad, destaque de vidro flutuante, botões de Inputs/rede/Settings, relógio em Outfit. Config via `config.xml` e temas via `config.json` | você quer **codar o seu** e tem JDK 17 + Gradle. É o melhor ponto de partida para uma fase 2 deste repositório |
| **TV Detox** (`com.ansub.tvhome`, Ansub/tv-detox) | home de uma tela só, Java puro, ~30 MB de RAM: seus apps, entradas HDMI, relógio, rede, atalhos de configurações. `install.sh <ip> "apps"` instala e registra como Home; num Google TV **não** desliga o launcherx (só com `GOOGLE_TV=1`). Tem `undo.sh`. MIT | **padrão do modo guiado** deste kit (`55-tv-detox.sh`) |
| TV Box Tools | app open source que grava a mesma chave que *Configurações › Apps padrão › Home* por ADB e restaura o stock com um toque | só para quem não quer linha de comando |

Nota de quem testou: em Google TV o Projectivy e o FLauncher precisam de ADB
para serem o Home de verdade (desabilitar `launcherx` + `setupwraith`); só o
"Override" do Projectivy, sem ADB, funciona interceptando o botão.

## TV aberta na home: o que o TV Detox faz e não faz (lido no código, v1.5.2)

- Tiles aceitam **só nomes de pacote** (`appList()` descarta o que não tem
  `getLeanbackLaunchIntentForPackage`/`getLaunchIntentForPackage`). Entradas
  HDMI vêm do `TvInputManager` (apenas `TYPE_HDMI`, até 4) e abrem com
  `ACTION_VIEW` em `TvContract.buildChannelUriForPassthroughInput(id)`.
- **Não** lista o sintonizador (`TYPE_TUNER`), **não** lê `TvContract.Programs`
  (sem `READ_TV_LISTINGS` no manifesto) e não sabe abrir "canal 4".
- O que dá hoje: um tile `com.tcl.tv` (TV ao vivo) que abre o app da TCL no
  último canal sintonizado; a grade fica dentro dele.
- O que exige launcher próprio (fase 2): um tile "TV Globo" que mostra o
  programa de agora e o próximo (lidos de `content://android.media.tv/program`,
  permissão concedida via `adb shell pm grant <pkg> android.permission.READ_TV_LISTINGS`)
  e sintoniza com `ACTION_VIEW content://android.media.tv/channel/<id>`.
  `scripts/70-live-tv.sh` já faz as três coisas pelo adb e serve de protótipo.
  Como o TV Detox é MIT, Java puro, uma Activity e sem Gradle, um fork em
  `launcher/` é o caminho mais curto.

**Estado em 22/10/2026:** pendente. Na unidade de referência, `VIEW channel/<id>`
não tem quem atenda, o shell não enxerga o TvContract e `keys 4` ainda não
sintonizou de forma confiável. A home usa o tile `com.tcl.tv` (abre no último
canal). Retomar com a saída de `70-live-tv.sh doctor` (itens 6 a 8).

## Princípios de 10-foot UI (o que faz uma home de TV parecer "limpa")

Baseado nas diretrizes do Android TV e no que os launchers acima acertam:

1. **Uma decisão por tela.** A home existe para abrir 4 a 8 apps. Tudo que não
   é isso (recomendações, "canais", notícias) compete por atenção a 3 metros.
2. **Foco sempre visível.** Navegação é por D-pad; o item focado precisa de
   escala (~1.1x), borda ou brilho, nunca só mudança de cor. Transições de
   foco curtas (≤150 ms); é por isso que animação 0.5x "parece mais rápida".
3. **Fundo escuro, contraste alto.** Preto/cinza-escuro reduz brilho à noite,
   esconde o overscan e faz os ícones coloridos virarem a hierarquia visual.
4. **Zona segura de 5%.** Nada a menos de 5% das bordas (48 px em 1080p);
   TVs ainda cortam.
5. **Tipografia grande e pouca.** Mínimo 24 sp para texto de leitura, 16 sp
   para legendas. Uma fonte, dois pesos. Relógio e nome do app focado bastam.
6. **Grade por frequência, não por categoria.** Dock com os 4–6 que você abre
   todo dia; o resto numa grade escondida que sobe no "baixo". Categorias
   fixas (Filmes / Jogos / Utilidades) viram ruído rápido.
7. **Nada carrega rede na home.** Capas dinâmicas e "continuar assistindo"
   custam RAM e segundos no boot numa TV de 2 GB. Se quiser, só para 1 app.
8. **Inputs e Settings a um clique.** Os dois atalhos que o launcher stock
   enterra. Pílulas discretas no canto.

## Configuração recomendada do Projectivy

Depois de `scripts/50-launcher.sh install`, na TV:

1. Abra o Projectivy, conclua o onboarding (se ficar preso em permissões,
   rode `scripts/50-launcher.sh grant` e abra de novo).
2. **Configurações › Geral › Override current launcher: ligado** (funciona
   mesmo antes do `set-default`).
3. **Aparência:** wallpaper escuro sólido ou foto escura; tamanho de ícone
   médio; "mostrar nome do app" só no foco; relógio ligado.
4. **Categorias:** apague todas menos "Favoritos"; marque 4–6 favoritos;
   ponha o resto em "Todos os apps" (colapsado).
5. **Recomendações / canais:** desligue tudo (Google TV "Para você" não existe
   mais aqui, mas o Projectivy pode mostrar canais Leanback; desligue).
6. **Controle remoto › Botões:** remapeie YouTube/Netflix/Prime se eles
   pararem de funcionar após o `set-default` (acontece em controles
   BT_RC833 da TCL, que enviam keycodes 4062/4065/4099). Mapeie o botão de
   engrenagem (keycode 83) para Configurações se ele morrer.
7. **Screensaver:** o do Projectivy (relógio) ou `scripts/40-tune.sh --screensaver basic`.

Então:

```bash
scripts/50-launcher.sh status        # confirme que com.spocky.projengmenu/... está entre os candidatos
scripts/50-launcher.sh set-default   # desabilita launcherx + setupwraith, confere HOME, reverte se falhar
```

Teste na TV: Home, Voltar, Input, botão do mic, YouTube. Se algo falhar:
`scripts/50-launcher.sh restore`.

## Quando (e como) construir o próprio launcher

Se a fase 1 (Projectivy) não for "limpa" o bastante, a fase 2 é um launcher
nativo seu. Diretrizes para esse repositório, quando chegar lá:

- Kotlin ou Java puro + Leanback *não* (pesado); use `RecyclerView` com
  `FocusFinder` ou Compose for TV. Sem permissão de rede. Sem serviço em
  background. Mínimo API 28 cobre Android TV 9+.
- Resolve `LEANBACK_LAUNCHER` para listar apps; `PackageManager.getLaunchIntentForPackage`
  como fallback.
- Declare `<category android:name="android.intent.category.HOME"/>` com
  `android:priority` alto e trate `KEYCODE_TV_INPUT`, `KEYCODE_SETTINGS`.
- Instale com `adb install`, verifique com `50-launcher.sh status`, só então
  `set-default`. O script já faz o rollback automático.
- Referência de arquitetura e de visual: o launcher do projeto Clean Your TV.
