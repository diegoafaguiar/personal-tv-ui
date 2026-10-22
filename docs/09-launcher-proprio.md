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
depois `cd launcher && ./gradlew assembleDebug`.

Debug e release são assinados com uma chave **fixa e versionada**
(`launcher/keystore/tvhome-local.jks`, senha `tvhome-local`), para que todo
build, da CI ou do Mac, tenha a mesma assinatura e atualize por cima. É uma
chave de uso pessoal: quem tiver o repositório consegue assinar atualizações
deste pacote. Para publicar, aponte `TVHOME_KEYSTORE`, `TVHOME_KEYSTORE_PASSWORD`,
`TVHOME_KEY_ALIAS` e `TVHOME_KEY_PASSWORD` para uma keystore secreta.
Builds anteriores à v0.3.1 usavam a chave de debug do runner, diferente a cada
execução; o `57-launcher-apk.sh` detecta o conflito e oferece desinstalar.

## Instalar e tornar Home

```bash
scripts/57-launcher-apk.sh                               # baixa o último artefato (gh CLI) e instala
# ou, baixando à mão na aba Actions (o zip tem pastas: release/app-release.apk):
scripts/50-launcher.sh install --apk ~/Downloads/tvhome-apk/release/app-release.apk
scripts/50-launcher.sh grant tech.futureme.tvhome        # READ_TV_LISTINGS por adb (ou aceite o pedido na tela)
scripts/50-launcher.sh status                            # tech.futureme.tvhome deve estar entre os candidatos
ALLOW_STOCK_LAUNCHER_DISABLE=1 scripts/50-launcher.sh set-default tech.futureme.tvhome
```

Com o launcherx já desligado, o `set-default` usa `cmd package
set-home-activity` para escolher entre os launchers de terceiros (TV Detox e
este). Se o firmware ignorar a preferência e o Home ficar com o outro, repita
com `--disable-others`, que desliga os demais launchers de terceiros (o
TV Detox volta com `pm enable --user 0 com.ansub.tvhome`). Voltar para o TV Detox: `... set-default com.ansub.tvhome`. Voltar para
a fábrica: `scripts/50-launcher.sh restore`.

## Estrutura

```
launcher/app/src/main/java/tech/futureme/tvhome/
  MainActivity.kt          HOME, permissão, abrir itens/apps/entradas, botão Input
  HomeViewModel.kt         carrega tudo em IO, agrupa por seção, dedup
  data/TvContentRepository.kt   Watch Next + canais/programas de prévia
  data/Classifier.kt       regras Futebol/Filmes/Séries (ajuste aqui)
  data/AppsRepository.kt   apps, entradas, intents
  data/Prefs.kt            DataStore: dock, ocultos, app de TV, seções
  ui/HomeScreen.kt         a home
  ui/Rows.kt               cards com foco (escala + borda), linhas
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
