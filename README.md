# personal-tv-ui

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
- TV com **Opções do desenvolvedor** e **Depuração por rede/USB** ligadas. Veja [docs/02-conexao-adb.md](docs/02-conexao-adb.md).
- Opcional para `make lint`: `shellcheck`.

## Trilha (siga na ordem; cada passo ensina o próximo)

```bash
cp config/tv.env.example config/tv.env     # 0. coloque o IP da TV
scripts/00-connect.sh                      # 1. conecta e identifica modelo/launcher
scripts/10-backup.sh                       # 2. fotografa o estado original  ← obrigatório
scripts/20-inventory.sh                    # 3. classifica cada pacote em reports/
scripts/30-debloat.sh --dry-run tcl-telemetry tcl-promo-ads tcl-retail-demo   # 4. veja o plano
scripts/30-debloat.sh tcl-telemetry tcl-promo-ads tcl-retail-demo --reboot    #    aplique
scripts/35-verify.sh                       #    ~2 min após o boot: confere se tudo continua desabilitado
scripts/40-tune.sh --recommended           # 5. animações 0.5x + caches
scripts/50-launcher.sh install             # 6. Projectivy pela Play Store; abra-o na TV, conclua o onboarding
scripts/50-launcher.sh grant
scripts/50-launcher.sh set-default         #    com verificação de HOME e de Wi-Fi
scripts/60-benchmark.sh                    # 7. compare com backups/<...>/meminfo.txt
scripts/90-rollback.sh --dry-run           # a qualquer momento: veja o que o rollback faria
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

## O que pode ser desabilitado sem risco (resumo)

Lista completa e justificativas em [docs/03-pacotes-e-riscos.md](docs/03-pacotes-e-riscos.md).
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
config/profiles/*.list   listas de pacotes por categoria, com nível de risco e comentários
config/tv.env            IP da sua TV (ignorado pelo git)
lib/common.sh            funções compartilhadas: adb, logging, dry-run, proteções
scripts/NN-*.sh          um script por etapa, numerados na ordem da trilha
backups/                 gerados por 10-backup.sh (ignorados pelo git: contêm dados pessoais)
reports/                 inventários, logs de debloat e benchmarks
tests/                   adb falso + 46 asserções que cobrem o fluxo inteiro e as proteções
docs/                    guias em português
```

## Segurança e princípios

- **Backup primeiro.** `30-debloat.sh`, `40-tune.sh` e `50-launcher.sh` se recusam a rodar sem um backup.
- **Desabilitar, não desinstalar.** `pm disable-user --user 0` mantém o APK; `pm enable` reverte. (`--uninstall` existe, também reversível com `install-existing`.)
- **Lotes com verificação.** Após cada lote o script confere que a TV responde e que o dono do HOME não mudou.
- **Never-disable é lei.** Globs em `never-disable.list` são recusados mesmo que outro perfil peça.
- **Launcher com guarda.** Só troca o HOME se o novo launcher já é candidato e o Wi-Fi não pertence ao setup wizard; reverte sozinho se o HOME cair em outro lugar.
- **ADB por rede é aberto.** Qualquer dispositivo na LAN pode usar a porta 5555. Desligue a depuração ao terminar.

## Desenvolvimento

```bash
make test   # tests/run.sh: roda os scripts contra tests/fake-adb/adb
make lint   # shellcheck -x + bash -n
```

A CI (`.github/workflows/ci.yml`) roda os dois a cada push.

## Licença

MIT.
