# Android TV / Google TV (TCL)

> Parte do [personal-tv-ui](../README.md). Os comandos abaixo rodam **da raiz do repositório**.
> Com mais de uma TV, escolha qual com `TV=<nome>` (fichas em [`devices/`](../devices/README.md)).

Kit para deixar uma **TV TCL com Android TV / Google TV** mais rápida, limpa e útil,
sem root, com tudo reversível. Scripts Bash sobre ADB, listas de pacotes
pesquisadas e testadas pela comunidade em modelos TCL (inclusive brasileiros),
backup antes de qualquer mudança e rollback de um comando.

> **Importante:** os scripts rodam no **seu computador**, na mesma rede Wi-Fi da
> TV, usando `adb` via rede. Nada é instalado na TV para o debloat funcionar.

## O que você vai conseguir

| Objetivo | Como | Script |
|---|---|---|
| Menos processos no boot, menos RAM usada | desabilitar telemetria, anúncios e modo loja da TCL | `30-debloat.sh` |
| Home limpa, sem linhas patrocinadas | trocar o launcher (Projectivy) com guarda contra tela preta | `50-launcher.sh` |
| UI mais responsiva ao controle | animações a 0.5x, screensaver sem promoções, caches limpos | `40-tune.sh` |
| Menos anúncios e rastreio dentro dos apps | Private DNS (AdGuard) em uma chave de settings | `40-tune.sh` |
| Voltar ao estado original a qualquer momento | backup de pacotes + settings + HOME; rollback idempotente | `10-backup.sh` / `90-rollback.sh` |
| Saber o que cada pacote faz antes de mexer | inventário classificado (KEEP / SAFE / OPTIONAL / UNKNOWN) | `20-inventory.sh` |
| Provar que melhorou | memória, processos e cold start antes/depois | `60-benchmark.sh` |

## Pré-requisitos

- **adb** (Android platform-tools): <https://developer.android.com/tools/releases/platform-tools>
- **bash 4+**. Linux já tem. No macOS, `brew install bash` (o bash do sistema é 3.2).
- TV com **Opções do desenvolvedor** e **Depuração por rede/USB** ligadas. Veja [android-tv/docs/02-conexao-adb.md](docs/02-conexao-adb.md).
- Opcional para `make lint`: `shellcheck`.

## Modo guiado (pergunta antes de cada mudança)

Fluxo que segue estas regras: só desligar (nunca apagar), perguntar antes de
cada mudança, manter a tela inicial do Google TV, lista de desfazer, reiniciar
uma vez no fim e mostrar a memória antes e depois.

```bash
android-tv/scripts/00-connect.sh                         # 1. conecta
android-tv/scripts/10-backup.sh                          # 2. estado original + memória livre
android-tv/scripts/20-inventory.sh                       # 3. lista em português: pode desligar / a TV precisa / apps que você usa
                                                         #    com RAM em uso e espaço de cada app (abra reports/*-inventory.md)
android-tv/scripts/25-review.sh                          # 4. você marca um a um: [d]esligar [m]anter [p]ular → gera um plano
android-tv/scripts/30-debloat.sh --ask-each plano-…      # 5. aplica o plano perguntando antes de cada pacote; grava reports/*-undo.sh
android-tv/scripts/55-tv-detox.sh install "com.tcl.tv,com.google.android.youtube.tv,com.netflix.ninja,…" # 6. tela inicial TV Detox
android-tv/scripts/70-live-tv.sh now 4                   # TV aberta: o que está passando no canal 4 (e "tune 4" para sintonizar)
android-tv/scripts/40-tune.sh --guided                   # 7. animações off, suspender em 5 min, pergunta se é OLED (screensaver)
android-tv/scripts/95-finish.sh                          # 8. espera 30 s, reinicia, confirma o boot, memória antes/depois
```

Desfazer: `bash reports/<data>-undo.sh` (só o que o passo 5 fez) ou `android-tv/scripts/90-rollback.sh` (tudo, até o backup).

## Trilha completa (siga na ordem; cada passo ensina o próximo)

```bash
cp android-tv/config/tv.env.example android-tv/config/tv.env # 0. coloque o IP da TV
android-tv/scripts/00-connect.sh                         # 1. conecta e identifica modelo/launcher
android-tv/scripts/10-backup.sh                          # 2. fotografa o estado original  ← obrigatório
android-tv/scripts/20-inventory.sh                       # 3. classifica cada pacote em reports/
android-tv/scripts/30-debloat.sh --dry-run tcl-telemetry tcl-promo-ads tcl-retail-demo # 4. veja o plano
android-tv/scripts/30-debloat.sh tcl-telemetry tcl-promo-ads tcl-retail-demo --reboot # aplique
android-tv/scripts/35-verify.sh                          # ~2 min após o boot: confere se tudo continua desabilitado
android-tv/scripts/40-tune.sh --recommended              # 5. animações 0.5x + caches
android-tv/scripts/50-launcher.sh install                # 6. Projectivy pela Play Store; abra-o na TV, conclua o onboarding
android-tv/scripts/50-launcher.sh grant
android-tv/scripts/50-launcher.sh set-default            # com verificação de HOME e de Wi-Fi
android-tv/scripts/60-benchmark.sh                       # 7. compare com backups/<...>/meminfo.txt
android-tv/scripts/90-rollback.sh --dry-run              # a qualquer momento: veja o que o rollback faria
```

Ou com `make`: `make connect backup inventory debloat-plan debloat tune benchmark`.
Todo script aceita `--dry-run` e `-h`.

Leitura sugerida enquanto executa:

1. [Conceitos: Android TV vs Google TV, ADB, disable vs uninstall, como o HOME é resolvido](docs/01-conceitos.md)
2. [Conectar o ADB à TV (e as pegadinhas da TCL)](docs/02-conexao-adb.md)
3. [Todos os pacotes e o risco de cada um](docs/03-pacotes-e-riscos.md)
4. [Launcher e design de UI para TV (10-foot UI), com exemplos clean da comunidade](docs/04-launcher-e-design.md)
5. [Performance: o que realmente ganha e como medir](docs/05-performance.md)
6. [Rollback: como funciona e o que não cobre](docs/06-rollback.md)
7. [Troubleshooting: tela preta, controle mudo, "Success" que não fez nada](docs/07-troubleshooting.md)
8. [Referências](docs/08-referencias.md)
9. [Launcher próprio "Sintonia" (fase 2): seções unificadas de todos os streamings, estilo vidro (v0.7)](docs/09-launcher-proprio.md)

## Launcher "Sintonia": rodar no Mac, testar no emulador, publicar na TV

O launcher próprio (`android-tv/launcher/`, Kotlin + Compose for TV) tem um ciclo local
completo: compila no Mac, roda num emulador Android TV, você navega com o
teclado ou o mouse, lê o logcat e só então manda para a TV. Detalhes em
[android-tv/docs/09-launcher-proprio.md](docs/09-launcher-proprio.md).

> Atalho: `make up` (ou dois cliques em `rodar-mac.command`) faz os passos 1 e 2 de uma vez.
> Guia curto: [../docs/rodar-localmente.md](../docs/rodar-localmente.md).

### 1. Preparar (uma vez)

```bash
make emulator-setup                                      # JDK 17 + Android SDK + imagem Android TV + AVD "tvhome" (1080p, 2 GB RAM)
```

Tudo via Homebrew, sem sudo, em `~/Library/Android/sdk`. Pode repetir: é idempotente.

### 2. Rodar

```bash
make emulator                                            # abre a janela do emulador e espera o boot (~40 s)
make dev                                                 # compila (assembleDebug), instala, concede READ_TV_LISTINGS e abre o launcher
make dev-logs                                            # logcat só do processo do launcher (Ctrl+C sai)
```

Editou código? `make dev` de novo (build incremental, ~20 s). `make emulator-stop` desliga.

### 3. Interagir

Na **janela do emulador** (clique nela primeiro para ela receber o teclado):

| Tecla do Mac | Na TV |
|---|---|
| ← ↑ → ↓ | D-pad |
| Enter | OK |
| Esc | Voltar |
| Home (fn+←) | botão Home |
| 0-9 | dígitos (senha dos perfis, número do canal) |
| **clique do mouse** | foca e aciona o item, como o OK (toque habilitado no AVD; o app trata toque em `Modifier.tapToClick`) |

Pelo **terminal**, sem tocar na janela:

```bash
android-tv/scripts/85-launcher-dev.sh remote             # controle remoto: setas, Enter, Backspace/Esc, h, m, 0-9, s (screenshot), q
android-tv/scripts/85-launcher-dev.sh key down 3         # uma tecla, n vezes: up|down|left|right|ok|back|home|menu|0-9
android-tv/scripts/85-launcher-dev.sh tap 960 570        # clique numa coordenada (tela 1920x1080)
android-tv/scripts/85-launcher-dev.sh screenshot         # PNG em reports/
android-tv/scripts/85-launcher-dev.sh home               # botão Home do emulador passa a abrir o launcher (role HOME)
android-tv/scripts/80-emulator.sh wipe                   # volta o AVD ao estado de fábrica (TV recém-ligada)
```

O que dá para validar no emulador: layout, foco, hero (relógio, clima, dólar/euro),
perfis e senha, menu, busca, ajustes, Flamengo, crashes. A imagem traz o YouTube,
que publica cards em "Para você". O que **não** tem: sintonizador (TV aberta),
HDMI e os apps de streaming brasileiros, então Continuar assistindo / Filmes /
Séries ficam vazios; isso só se vê na TV.

### 4. Publicar na TV (instalar, atualizar, desinstalar: android-tv/docs/09)

```bash
make dev-push                                            # compila o debug e instala na TV de android-tv/config/tv.env
android-tv/scripts/85-launcher-dev.sh push --release     # ou o release (R8, menor)
android-tv/scripts/50-launcher.sh grant io.github.diegoafaguiar.sintonia
android-tv/scripts/50-launcher.sh status
ALLOW_STOCK_LAUNCHER_DISABLE=1 android-tv/scripts/50-launcher.sh set-default io.github.diegoafaguiar.sintonia # botão Home abre o launcher
```

Build local e CI assinam com a mesma chave (`android-tv/launcher/keystore/`), então o APK do
Mac atualiza por cima do da CI sem perder ajustes. Alternativa sem compilar: faça
push do código, espere a Action `Launcher APK` e rode `android-tv/scripts/57-launcher-apk.sh`
(baixa o artefato e instala). Deu tela preta? `android-tv/scripts/59-launcher-rescue.sh`;
voltar ao launcher de fábrica: `android-tv/scripts/50-launcher.sh restore`.

## O que pode ser desabilitado sem risco (resumo)

Lista completa e justificativas em [android-tv/docs/03-pacotes-e-riscos.md](docs/03-pacotes-e-riscos.md).
Os perfis abaixo são aplicados pelo `30-debloat.sh`; o `20-inventory.sh` diz quais existem na sua TV.

| Perfil | Pacotes | Risco |
|---|---|---|
| `tcl-telemetry` | `com.tcl.ttvs` (Samba TV ACR + analytics), `com.tcl.logkit`, `com.tcl.bi`, `com.google.android.feedback`, `com.android.adservices.api`… | **nenhum** |
| `tcl-promo-ads` | `com.tcl.waterfall.overseas` (motor de anúncios, maior ganho), `com.tcl.tv.tclhome_passive`, `com.tcl.messagebox`, `com.tcl.channelplus`, `com.tcl.partnercustomizer`, `android.autoinstalls.config.google.gtvpai` (instala apps sozinho)… | **nenhum** |
| `tcl-retail-demo` | `com.tcl.exhibit`, `com.tcl.esticker`, `com.tcl.hotelmenu`, `com.tcl.repairguide`, `com.tcl.ocean.instructions` | **nenhum** |
| `tcl-extras` | Alexa, T-Cast, Miracast, AirPlay, game bar, player USB, galeria, soundbar updater… | depende do seu uso |
| `google-extras` | Assistente (`katniss`), screensaver Ambiente (`dreamx`), Chromecast (`mediashell`), Play Games, YouTube Music… | depende do seu uso |
| `streaming-optional` | Netflix, Prime, Disney+, Claro TV+, Mercado Play… | apague o que assina |
| `tcl-smart-tv-pro-br` / `-apps` | perfil da unidade de referência (Amlogic, Brasil): escolhas opcionais e apps de terceiros a desinstalar | depende do seu uso |
| `never-disable` | `com.tcl.tv`, `com.tcl.tvinput`, `com.tcl.suspension`, `*.autopair`, `*.remote.service`, `com.mediatek.*`, `com.droidlogic*`, `tv.mopa.ginga` / `com.mirakulo.*` (TV aberta), Play Services… | **o script recusa** |

## Estrutura

```
android-tv/
├── config/profiles/   listas de pacotes por marca (google/, tcl/, geral/), com risco e comentários
├── config/tv.env      IP padrão da TV, quando não se usa TV=<nome> (ignorado pelo git)
├── lib/common.sh      funções compartilhadas: adb, logging, dry-run, proteções
├── scripts/NN-*.sh    um script por etapa, numerados na ordem da trilha
├── tests/             adb falso + asserções que cobrem o fluxo inteiro e as proteções
├── docs/              guias em português
└── launcher/          app Android (Kotlin + Compose for TV): a home "Sintonia"; APK vem da CI
                       ou do Mac: make emulator-setup && make dev (emulador), make dev-push (TV)
```

Backups e relatórios ficam na raiz (`backups/`, `reports/`), compartilhados com a Roku.

## Segurança e princípios

- **Backup primeiro.** `30-debloat.sh`, `40-tune.sh` e `50-launcher.sh` se recusam a rodar sem um backup.
- **Desabilitar, não desinstalar.** `pm disable-user --user 0` mantém o APK; `pm enable` reverte. (`--uninstall` existe, também reversível com `install-existing`.)
- **Lotes com verificação.** Após cada lote o script confere que a TV responde e que o dono do HOME não mudou.
- **Never-disable é lei.** Globs em `never-disable.list` são recusados mesmo que outro perfil peça.
- **Tela inicial de fábrica fica.** `launcherx`, `tvlauncher` e o assistente de configuração estão em `never-disable`. Launchers alternativos (TV Detox, Projectivy) convivem com ela; `50-launcher.sh set-default` só age com `ALLOW_STOCK_LAUNCHER_DISABLE=1`, e mesmo assim com guarda e reversão automática.
- **ADB por rede é aberto.** Qualquer dispositivo na LAN pode usar a porta 5555. Desligue a depuração ao terminar.

## Desenvolvimento

```bash
make test                                                # android-tv/tests/run.sh: roda os scripts contra android-tv/tests/fake-adb/adb
make lint                                                # shellcheck -x + bash -n
```

A CI (`.github/workflows/ci.yml`) roda os dois a cada push.

## Licença

MIT (veja [LICENSE](../LICENSE)).
