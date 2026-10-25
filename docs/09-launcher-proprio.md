# 9. Launcher próprio: "Minha TV" (`launcher/`)

Fase 2 do projeto: uma home nativa, em Kotlin + Compose for TV, que unifica
o que os streamings publicam e organiza em seções.

## O que mostra

| Linha | De onde vem |
|---|---|
| Dock: TV ao vivo, seus apps, HDMI, "Todos os apps" | `PackageManager` (LEANBACK_LAUNCHER) e `TvInputManager` |
| **Continuar assistindo** | `content://android.media.tv/watch_next_program`, de todos os apps, por último uso |
| **Futebol** | programas de prévia com gênero `SPORTS` ou texto com cara de futebol (`data/Classifier.kt`) |
| **Filmes** | programas de prévia com `TYPE_MOVIE` |
| **Séries** | `TYPE_TV_SERIES`, `TYPE_TV_SEASON`, `TYPE_TV_EPISODE` |
| **Para você** | o resto (clipes, eventos, música) |
| **Sugestões · app** | os canais de prévia de cada app, separadamente |
| Sistema | Configurações da TV, ajustes da home, diagnóstico |

Cada card abre o conteúdo direto no app, pelo `intent_uri` que o próprio app
publicou (é o mesmo deep link que a home do Google usa).

## v0.2: hero, cotações, densidade, fundos e botões rápidos

- **Hero**: saudação pela hora, relógio, data por extenso e, à direita, dólar
  e euro do dia (cotação comercial e variação) pela AwesomeAPI, gratuita e sem
  chave; atualiza a cada 30 min enquanto a home existe. Desligável nos ajustes.
- **Densidade**: "compacta" (padrão, ~6 apps por linha em 1080p) ou "normal".
- **Fundo**: preto, grafite, azul-noite, gradiente ou a sua imagem
  (`scripts/58-launcher-wallpaper.sh foto.jpg`, escurecida 50% para leitura).
- **Linha de atalhos**: HDMI 1/2/3 (nomes vindos da TV) e botões rápidos
  Bluetooth, Rede, Som, Imagem, Configurações; cada um liga/desliga nos ajustes.
- Cards semitransparentes para o fundo aparecer.

## v0.3: a regra do TvProvider, TV aberta, clima, busca, recentes, hero focado

- **"Selection not allowed"**: para apps sem permissão de sistema o TvProvider
  recusa qualquer cláusula `WHERE`; só aceita filtros por parâmetros de URI.
  Tudo agora consulta sem selection e filtra em memória; a grade (EPG) usa
  `buildProgramsUriForChannel(id, início, fim)`, que é parâmetro de URI.
- **TV aberta**: com `READ_TV_LISTINGS` o launcher enxerga os canais do
  sintonizador que o `adb shell` não via. Linha "TV aberta" com número, nome e
  o programa de agora; clicar tenta `VIEW` no canal e, sem quem atenda, abre o
  app de TV ao vivo. O diagnóstico lista canais e grade encontrados.
- **Clima** (Open-Meteo, sem chave) ao lado das cotações; coordenadas do
  Recreio dos Bandeirantes por padrão (`Prefs`).
- **Widget de estado** no hero: rede, Bluetooth, RAM livre, espaço livre.
- **Hero reage ao foco**: título, app, gênero e sinopse do item focado, com a
  arte dele ao fundo, escurecida.
- **Busca unificada** (pílula "Buscar"): títulos e sinopses de todos os apps e
  os próprios apps, sem acentos/maiúsculas.
- **Abertos recentemente**: últimos 12 apps/conteúdos abertos pela home, local.
- **Modo noturno automático**: das 21h às 6h o fundo escurece mais.

## v0.4: calibrado com o diagnóstico real da TV

Diagnóstico de 22/10: 49 itens de Watch Next (Globoplay 14, Apple TV 13,
Disney+ 11, Netflix 4, Prime 4, HBO Max 2), canais de prévia de 14 apps mas
zero programas na listagem geral, nenhum gênero publicado, 25 canais de TV
aberta (Globo 4.1 com grade) e ~200 canais do TCL Channel+ na mesma tabela.

- **Sugestões canal a canal**: `buildPreviewProgramsUriForChannel(id)`; a
  listagem geral de `preview_program` volta vazia para apps comuns.
- **Continuar assistindo**: agrupado por app, apps mais abertos pela home
  primeiro (contagem de "recentes"), até 5 por app.
- **TV aberta vs internet**: pelo tipo do canal (`TYPE_ISDB_T` = aberta);
  os FAST do Channel+ ficam numa linha opcional. Globo vem primeiro.
- **Futebol sem gênero**: canais de esporte ao vivo (CazéTV, GE TV, FIFA+,
  GOAT, Desimpedidos, Nsports, SportyNet…) por nome, mais o que a Globo
  estiver passando de futebol e títulos com palavras do `Classifier`.
- **Filmes e Séries** também a partir do Watch Next (tipo MOVIE / EPISODE).
- **Menu nativo só com ícones** (busca, TV, HDMI 1/2/3, rede, Bluetooth, som,
  imagem, configurações, apps), rótulo só no foco. Hero com mais respiro.
- **Placeholders**: gradiente estável derivado do nome (app, canal) quando
  não há imagem; cards de canal com número grande e "Agora: …".

## v0.5: hero fixo e minimalista, TV aberta dentro da home, Flamengo, ordem de apps

- **Hero fixo** acima da lista: expandido com a lista no topo (hora grande,
  data, clima e cotações à direita, item focado embaixo); ao rolar, colapsa
  para uma linha; ao voltar ao topo, reexpande. "Hero sempre compacto" nos ajustes.
- **Ícones limpos**: sem fundo em repouso; foco vira círculo claro com rótulo.
- **TV aberta dentro do launcher** (`ui/LiveTvScreen.kt`): `TvView.tune(inputId,
  canal)`, o mesmo mecanismo do app de TV, sem depender do app da TCL. Cima/baixo
  trocam de canal, dígitos sintonizam, OK mostra a grade, Voltar sai. O ícone
  "TV aberta" abre o último canal (ou a Globo). Se o sintonizador recusar a
  sessão, cai no app da TCL.
- **Seção Flamengo** (`data/FlamengoRepository.kt`): vídeos do canal oficial no
  YouTube via RSS (sem chave; abre no app do YouTube), próximo jogo, último
  resultado e posição pela API pública da ESPN (time 819, liga `bra.1`), e o que
  os apps publicam com "Flamengo" no título. Canais do YouTube e time em `Prefs`.
- **Logos dos canais**: `TvContract.buildChannelLogoUri(id)`; sem logo, número
  grande sobre a cor do canal.
- **Ordem de apps** em todas as seções (Netflix, Apple TV, YouTube, Globoplay,
  Prime, HBO Max, Disney+, Spotify por padrão), editável nos ajustes com ▲▼.
- Ajustes novos: hero compacto, estado da TV no hero, seção do time, ordem dos apps.

## v0.6: perfis

Seletor "Quem está assistindo?" ao abrir o launcher (processo novo, ou seja,
TV recém-ligada) e pelo ícone de perfil no menu. Definidos em `data/Profile.kt`:

| Perfil | Senha | Apps | Seções próprias |
|---|---|---|---|
| Diego | 1234 | todos | Flamengo |
| Letícia | 1234 | todos | TV Globo (canal com sintonia, agora/depois, grade das próximas 10 h, Globoplay) |
| Arthur e Davi | não | Netflix, Prime Video, Apple TV, HBO Max e TV aberta | nada de Futebol, Flamengo, busca, HDMI, ajustes ou canais da internet; as seções só mostram conteúdo desses apps |

A senha é digitada pelos números do controle ou pelo teclado na tela, e pode
ser trocada em Ajustes › Perfis (fica no DataStore do app). Ao trocar de
perfil pelo menu, Diego e Letícia pedem senha de novo; o infantil não.

## v0.7: estilo vidro ("Liquid Glass")

Identidade própria inspirada no vidro líquido da Apple, sem copiar o tvOS: papel de
parede escuro com manchas de cor (Aurora, Oceano, Ocaso; os antigos continuam, mais
"Minha imagem"), painéis translúcidos com borda iluminada e reflexo no topo, tiles de
app com o banner publicado pelo próprio app (nada de logos proprietários) e
acabamento de vidro por cima, brilho na cor dominante do banner quando focado,
varredura de luz ao ganhar foco e o fundo tingido com a mesma cor. Menu de ícones em
bolhas de vidro; clima e cotações em painéis. Protótipo aprovado em HTML:
<https://claude.ai/artifact/E3HASLEPfNxe8jAsbjKYr6>.

**Como é desenhado (`ui/Glass.kt`)**: o papel de parede é renderizado uma vez num
bitmap de meia resolução (gradientes) ou lido e desfocado uma vez (imagem, por
redução e ampliação bilinear). O fundo desenha esse bitmap; cada painel desenha o
mesmo bitmap recortado na própria forma, deslocado pela posição do painel. O olho vê
o fundo "atravessando" o vidro, mas não há desfoque por quadro: custa o mesmo que um
card opaco. Essa foi a escolha para a TV de referência (G09: Android 12, Amlogic T5M,
2 GB), onde `RenderEffect` de desfoque em 4K derrubaria os 60 fps. Limitação: o que
passa por baixo de um painel além do papel de parede (a arte do item focado, por
exemplo) não aparece dentro dele.

**Ajustes › Vidro**: estilo ligado/desligado (desligado = cards escuros da v0.6),
transparência (10 a 100%), intensidade dos efeitos (0 a 150%), desfoque de "Minha
imagem" (nítido/leve/médio/forte), animações (escala 1,08 e varredura de luz) e
**modo leve** (painéis escuros translúcidos, sem ver-através nem reflexos: o fallback
para GPU fraca). Tudo em `Prefs` (`glass_*`), aplicado na hora.

**Organização da home de vidro** (`ui/GlassHomeScreen.kt`, inspirada em conceitos de
home de TV com barra lateral): relógio e data no canto superior esquerdo; busca, rede
e perfil no direito; **barra lateral** com Início, Apps, Filmes e Séries, TV ao Vivo,
Jogos, Música e Configurações (só ícones; expande com rótulos quando o foco entra
nela; o perfil infantil vê só os quatro primeiros); **hero em carrossel** com o que
os apps publicam (imagem, app, título, "Assistir agora"/"Continuar"; ← → trocam,
troca sozinho a cada 8 s sem foco; OK abre pelo deep link do app); **widgets** de
clima e de entradas (HDMI 1; OK abre a entrada) à direita; **Seus aplicativos** em
ícones quadrados (android:icon do app) com rótulo; e as mesmas linhas de conteúdo da
home clássica (`homeRows`, em `HomeScreen.kt`). "Continuar assistindo" mostra a
**barra de progresso** quando o app publica posição e duração no Watch Next. Jogos e
Música filtram pela categoria que o app declara (`ApplicationInfo.category`), mais
uma lista de players conhecidos. Com "Estilo vidro" desligado, volta a home clássica
da v0.6.

**Papel de parede "Horizonte"**: foto embutida no APK (`res/drawable-nodpi/wallpaper_horizonte.jpg`,
1920 px, ~480 KB), padrão da v0.7; quem estava num fundo liso da v0.6 migra uma vez
(`bg_migrated_v7b`). Fotos (Horizonte e "Minha imagem") geram dois bitmaps: o nítido
levemente escurecido para o fundo e o desfocado para dentro dos painéis. Com um
conteúdo focado, a arte dele cobre o fundo, escurecida; sem foco, volta a foto.

**Botão "TV ao Vivo" e card "TV aberta"**: abrem o app de TV da TCL já na antena, pelo
URI de passthrough da entrada do sintonizador (`content://android.media.tv/passthrough/
com.tcl.tvinput%2F.TunerInputService%2FHW2`, confirmado na TV: sobe `com.tcl.tv/TVActivity`).
Antes caíam no `TvView` do launcher, que a TCL recusa, e o fallback abria o app no último
input usado (HDMI 2). O `TvView` continua nos cards de canal; se falhar, `VIEW` do canal com
pacote e tipo `vnd.android.cursor.item/channel` (o app da TCL declara esse filtro) e, por
último, o passthrough.

**Continuar assistindo**: legenda "App · T4 E7 · 32 min restantes" (temporada/episódio e
posição/duração que o app publica no Watch Next; a Apple TV publica, a Netflix não) e barra
de progresso branca.

**Perfil restaurado**: o sistema mata o launcher quando um app pesado abre (2 GB); ao
voltar, o perfil escolhido é restaurado se a TV não foi religada (`active_profile` +
instante do boot). TV ligada de novo = seletor "Quem está assistindo?".

Também na v0.7: **Voltar na home não sai do launcher** (como HOME, sair fazia o
sistema reabrir e o seletor de perfil voltar) e toque/clique do mouse aciona os cards
(`Modifier.tapToClick`), útil no emulador.

## v0.8: navegação, Flamengo com notícias, Tecnologia, Música

**Navegação**
- Barra lateral em todas as telas (Apps, Filmes e Séries, Jogos, Música, Configurações,
  Busca, leitura de notícia), com a tela atual marcada. Troca de seção sem Voltar.
- Ao abrir uma seção o foco entra no conteúdo; → na lateral sempre leva ao conteúdo
  (`focusProperties { right = … }` + `focusGroup`).
- **Home** com o launcher aberto volta ao Início (qualquer tela, inclusive TV ao vivo e
  senha), via `onNewIntent` com `CATEGORY_HOME`. Não intercepta nada: é o sistema que
  entrega o Home ao launcher porque ele é o HOME.
- **Voltar** com a home rolada sobe ao topo e devolve o foco ao primeiro app; no topo, não faz nada.
- Foco inicial da home: o primeiro app de "Seus aplicativos".

**Flamengo** (perfis com a seção): card de jogo/tabela (ESPN), **notícias** do Google
Notícias (RSS em pt-BR, sem chave; fonte e "há 2 h"), o que os apps publicam com
"Flamengo" e um atalho "Flamengo no YouTube" (busca no app do YouTube). O RSS de vídeos
do YouTube (`/feeds/videos.xml`) responde 404 para qualquer canal desde outubro/2026;
o código continua tentando e a seção funciona sem ele.

**Tecnologia** (adultos): manchetes de "tecnologia" intercaladas com notícias sobre os
apps instalados (os do dock primeiro: Netflix, Spotify, YouTube…), filtradas por termos
de produto (app, recurso, lançamento, atualização, preço). As três buscas rodam em paralelo.

**Notícias, como abrem**: a TV não tem navegador; o `VIEW https` cai num stub do sistema
(`com.android.tv.frameworkpackagestubs`) que não mostra nada. O launcher detecta o stub e
abre a **tela de leitura**: título, fonte, data e resumo, num painel de vidro.

**Música**: tela própria com seus apps de música, "Continuar ouvindo" (o que eles
publicam), abertos recentemente e sugestões das paradas da Deezer (API pública, sem chave):
**Mais tocadas no Brasil, Playlists para você, Álbuns em alta, Artistas do momento**, com
capas. OK numa sugestão abre `spotify:search:<artista título>` no Spotify (testado na TV:
abre e toca), ou a busca no YouTube se não houver Spotify. Limite: as sugestões são as
paradas do Brasil, não o seu histórico; o Spotify não expõe o que você ouve para outros apps.

## v0.8.1: segunda TV, TV aberta, atualização ao ligar, lançamentos

- **Botão "TV ao Vivo"**: abria o Globoplay porque o Globoplay registra um "tuner" próprio
  (`com.globo.globotv/.androidtv.tif.service.LiveTvInputService`) e era o primeiro em ordem
  alfabética. Agora o launcher usa o sintonizador físico da TV (id `…/HWn`), digital antes do
  analógico; entradas de apps ficam por último e saem dos cards de HDMI.
- **Atualizar ao ligar a TV**: ao sair do standby (`ACTION_SCREEN_ON`) o launcher busca de novo
  notícias, lançamentos, paradas musicais, Flamengo, cotações, clima e o Watch Next.
- **Linha "Lançamentos"**: estreias da semana nos streamings instalados (Google Notícias).
- **Home** volta a lista e a linha de apps ao começo e foca o primeiro app.
- **TVs com pouca memória** (menos de 1,5 GB): "modo leve" vira o padrão e a arte do item focado
  não cobre o fundo (duas camadas de tela cheia a menos). Na TCL G13 (mt5867, 1 GB) a mediana
  caiu de 97 ms para 65 ms por quadro; é o limite com foto de fundo nessa GPU.

**Segunda TV (TCL G13, 192.168.100.28)**: backup em `backups/20261025-133922_TCL_Smart_TV`,
perfil `config/profiles/tcl-g13-br.list`, 19 pacotes desabilitados, launcher como Home. Para
falar com ela sem mexer no `config/tv.env` da primeira:

```bash
TV_HOST=192.168.100.28 scripts/35-verify.sh
TV_HOST=192.168.100.28 scripts/85-launcher-dev.sh push --release
TV_HOST=192.168.100.28 scripts/90-rollback.sh --dry-run
TV_HOST=192.168.100.28 scripts/50-launcher.sh restore     # volta a home do Google
```

## Por que isso funciona sem scraping

No Android TV os apps publicam "Watch Next" e "canais de prévia" num banco do
sistema (TvProvider). A home do Google lê esse banco. A permissão para ler o
de todos os apps é `android.permission.READ_TV_LISTINGS`, de nível
"dangerous": um app comum pode pedi-la na tela. O changelog do Projectivy
descreve exatamente isso ("READ_TV_LISTINGS: ability to show channels/watch
next"). Sem a permissão o provedor devolve só as linhas do próprio app, sem
erro; por isso a home mostra um aviso e o diagnóstico mostra o estado.

Ressalva: no Google TV alguns apps publicam só o Watch Next e pararam de
publicar canais de sugestões. A tela **Diagnóstico** mostra o que cada app
publica na sua TV; é com ela que calibramos as seções.

## Build

O APK é compilado pela CI (`.github/workflows/launcher.yml`) a cada push em
`launcher/` e publicado como artefato `tvhome-apk`. Para compilar no Mac:
JDK 17 + Android Studio (ou `sdkmanager "platforms;android-34" "build-tools;34.0.0"`),
depois `cd launcher && ./gradlew assembleDebug`. Ou `make emulator-setup && make dev`
(seção abaixo), que instala tudo via Homebrew e roda num emulador Android TV.

Debug e release são assinados com uma chave **fixa e versionada**
(`launcher/keystore/tvhome-local.jks`, senha `tvhome-local`), para que todo
build, da CI ou do Mac, tenha a mesma assinatura e atualize por cima. É uma
chave de uso pessoal: quem tiver o repositório consegue assinar atualizações
deste pacote. Para publicar, aponte `TVHOME_KEYSTORE`, `TVHOME_KEYSTORE_PASSWORD`,
`TVHOME_KEY_ALIAS` e `TVHOME_KEY_PASSWORD` para uma keystore secreta.
Builds anteriores à v0.3.1 usavam a chave de debug do runner, diferente a cada
execução; o `57-launcher-apk.sh` detecta o conflito e oferece desinstalar.

## Rodar no Mac antes de mandar para a TV (emulador)

Ciclo local: compila, abre num emulador Android TV, navega com o teclado, lê o
logcat; só então `push` para a TV. Tudo em `scripts/80-emulator.sh` (o aparelho)
e `scripts/85-launcher-dev.sh` (o app).

```bash
make emulator-setup   # uma vez: JDK 17 (brew openjdk@17), SDK em ~/Library/Android/sdk,
                      # imagem "android-34;android-tv;arm64-v8a", AVD "tvhome" (1080p, 2 GB RAM)
make emulator         # abre a janela e espera o boot (~40 s na primeira vez)
make dev              # ./gradlew assembleDebug + instala + READ_TV_LISTINGS + abre o launcher
make dev-logs         # logcat só do processo do launcher
make dev-push         # mesmo APK, agora na TV de config/tv.env (via 57-launcher-apk.sh)
```

Na janela do emulador: setas = D-pad, Enter = OK, Esc = Voltar, tecla Home =
botão Home, dígitos = dígitos, **clique do mouse = foco + OK**. O `setup` liga
`hw.keyboard`, `hw.dPad` e `hw.screen=touch` no `config.ini` do AVD (o perfil
`tv_1080p` vem sem teclado e sem toque). O Surface do Compose for TV só reage ao
OK do D-pad, então `ui/Rows.kt` tem `Modifier.tapToClick`, aplicado em
`FocusSurface` e `IconButton`: um toque pede foco e chama o `onClick`. Na TV não
há toque, nada muda.

Pelo terminal: `scripts/85-launcher-dev.sh remote` (controle remoto: setas,
Enter, Backspace/Esc, h = Home, m = Menu, 0-9, s = screenshot, q = sair),
`key down 3`, `key ok`, `tap X Y`, `screenshot` (salva em `reports/`).
`scripts/85-launcher-dev.sh home` faz o botão Home do emulador abrir o launcher
(role HOME), como o `set-default` faz na TV. `scripts/80-emulator.sh wipe`
volta o AVD ao estado de fábrica.

O que o emulador **não** tem: sintonizador (a linha "TV aberta" e a
`LiveTvScreen` ficam vazias ou caem no fallback), entradas HDMI, os apps de
streaming e o que eles publicam no TvProvider (Watch Next e canais de prévia).
Então as seções Continuar assistindo / Filmes / Séries / Sugestões aparecem
vazias (a imagem traz o YouTube, que publica cards em "Para você"); o que dá
para validar: layout, foco, hero, clima e cotações (há rede),
perfis e senha, busca de apps, ajustes, menu, Flamengo (YouTube RSS e ESPN) e
qualquer crash. Para ver as seções com conteúdo, o caminho continua sendo a
TV (`make dev-push`) ou instalar no emulador apps que publiquem Watch Next
(a Play Store da imagem Android TV é de desenvolvedor e não tem os apps de
streaming brasileiros).

Build local e CI assinam com a mesma chave (`launcher/keystore/`), então o
`dev-push` atualiza por cima do que a CI instalou, sem perder ajustes.

## Instalar, atualizar, desinstalar, recuperar

Alvo confirmado: TCL G09 "Smart TV Pro", Google TV, Android 12 (API 31), ABI
armeabi-v7a, interface em 1920x1080 (`wm size` mostra override). O APK (minSdk 28,
targetSdk 34) é assinado com a chave fixa do projeto (`launcher/keystore/`); todo
build, do Mac ou da CI, instala por cima do anterior sem perder ajustes.

**Instalar (primeira vez)**

```bash
cp config/tv.env.example config/tv.env          # IP da TV; Depuração por rede ligada (docs/02)
scripts/00-connect.sh                           # pareia o adb (aceite o prompt na TV)
scripts/10-backup.sh                            # fotografa o estado original (obrigatório)
scripts/85-launcher-dev.sh push --release       # compila no Mac (JDK 17 + SDK: make emulator-setup) e instala
#   ou, sem compilar: scripts/57-launcher-apk.sh      (baixa o artefato da CI e instala)
scripts/50-launcher.sh grant tech.futureme.tvhome    # READ_TV_LISTINGS: libera Watch Next e seções
scripts/50-launcher.sh status                        # confira os candidatos a HOME
```

Nesse ponto o launcher é um app comum: abre pela lista de apps ou por
`adb shell am start -n tech.futureme.tvhome/.MainActivity`. O botão Home continua
com a tela de fábrica. Para o botão Home abrir o launcher (passo com guarda e
reversão automática; num Google TV só funciona desligando a home de fábrica):

```bash
ALLOW_STOCK_LAUNCHER_DISABLE=1 scripts/50-launcher.sh set-default tech.futureme.tvhome
```

**Atualizar** (mesma chave, ajustes preservados, sem precisar refazer o set-default):

```bash
scripts/85-launcher-dev.sh push --release       # ou scripts/57-launcher-apk.sh
```

Se aparecer `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a versão instalada é anterior à
v0.3.1 (chave antiga); o `57-launcher-apk.sh` oferece desinstalar e instalar de novo
(perde os ajustes da home, não os da TV).

**Desinstalar e voltar à tela de fábrica**

```bash
scripts/50-launcher.sh restore                  # reativa launcherx + setupwraith e devolve o HOME
adb -s <ip>:5555 uninstall tech.futureme.tvhome # remove o app (opcional)
scripts/90-rollback.sh --dry-run                # e, se quiser desfazer o debloat também, sem --dry-run
```

**Recuperação** se a TV ligar em tela preta ou sem Home: `scripts/59-launcher-rescue.sh`
(diagnóstico, reabre a home de fábrica ou o TV Detox) e docs/07. O Home nunca é
"interceptado": o launcher só responde ao botão porque o sistema o escolheu como
HOME; `restore` devolve a escolha ao sistema.

**Verificar depois de instalar**

```bash
adb -s <ip>:5555 shell dumpsys package tech.futureme.tvhome | grep -E "versionName|READ_TV_LISTINGS"
adb -s <ip>:5555 logcat -d | grep -E "FATAL EXCEPTION"        # vazio = sem crash
adb -s <ip>:5555 shell dumpsys gfxinfo tech.futureme.tvhome | grep -E "Janky|90th"   # fluidez
```

## Estado da entrega (v0.8, 24/10/2026)

Testado na TV pelo adb (mesmos keycodes do controle) e no emulador Android TV:

| Item | Estado |
|---|---|
| Abrir apps (ícone, hero, seções) | ok na TV: Netflix, Prime, HBO Max, Apple TV, YouTube |
| Setas, OK, Voltar, dígitos | ok na TV e no emulador |
| Home | o sistema abre o launcher porque ele é o HOME (set-default feito em 22/10); não há interceptação |
| Botões Netflix/YouTube/Prime do controle | tratados pelo sistema, fora do launcher; validar com o controle na mão |
| HDMI | card "Entradas" e slide no carrossel abrem `TvContract.buildChannelUriForPassthroughInput`; validar com o controle (perfis adultos) |
| Ajustes do launcher (vidro, papel de parede, dock, ordem, perfis) | ok |
| Encerrar/abrir (force-stop + start) | ok; seletor de perfil volta a cada processo novo (por desenho) |
| Desligar/ligar a TV | não testado por adb (reiniciar a TV exige sua autorização); `95-finish.sh` faz isso com confirmação de boot |
| Fluidez na TV | mediana 32 ms, p90 77 ms navegando por adb em velocidade de máquina; com o controle, mais folga. Animações e modo leve nos ajustes |
| RAM do launcher na TV | ~130 MB PSS com capas carregadas (Coil) |

Limitações: interface em 1080p ampliada pela TV (limite do sistema); o vidro só
atravessa o papel de parede; sugestões de Jogos/Música dependem do que esses apps
publicam no TvProvider (Spotify e a maioria dos jogos não publicam nada).

## Estrutura

```
launcher/app/src/main/java/tech/futureme/tvhome/
  MainActivity.kt          HOME, permissão, abrir itens/apps/entradas, botão Input
  HomeViewModel.kt         carrega tudo em IO, agrupa por seção, dedup
  data/TvContentRepository.kt   Watch Next + canais/programas de prévia
  data/Classifier.kt       regras Futebol/Filmes/Séries (ajuste aqui)
  data/AppsRepository.kt   apps, entradas, intents
  data/Prefs.kt            DataStore: dock, ocultos, app de TV, seções
  ui/HomeScreen.kt         a home clássica + homeRows (linhas compartilhadas)
  ui/GlassHomeScreen.kt    a home de vidro: barra lateral, carrossel, widgets, categorias
  ui/Glass.kt              papel de parede pré-renderizado, Modifier.glass, brilho e varredura de foco
  ui/Rows.kt               cards com foco (escala + borda), ícones de app, notícia, música, linhas
  ui/Screens.kt            Música e leitura de notícia
  data/NewsRepository.kt   Google Notícias (RSS): Flamengo, tecnologia, apps
  data/MusicRepository.kt  paradas da Deezer: músicas, playlists, álbuns, artistas
  ui/SettingsScreen.kt     dock, ordem, ocultar, seções, app de TV ao vivo
  ui/DiagnosticsScreen.kt  o que cada app publica; "Enviar ao logcat"
```

## Próximos passos previstos

- Calibrar `Classifier` com o diagnóstico da sua TV (palavras de futebol,
  gêneros que os apps usam de verdade).
- TV aberta: tile que sintoniza a Globo e mostra a grade (pendente, ver
  docs/04).
- Metadados ricos (sinopse, nota, onde assistir) via TMDB, opcional, com
  chave sua.
