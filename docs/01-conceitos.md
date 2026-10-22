# 1. Conceitos

## Android TV, Google TV ou Roku TV?

A TCL vende no Brasil TVs com três sistemas. Este kit serve para os dois primeiros.

| Sistema | Como reconhecer | Launcher de fábrica | Exemplos TCL |
|---|---|---|---|
| **Google TV** (Android 12+) | home com abas "Para você / Apps / Biblioteca", busca com Assistente | `com.google.android.apps.tv.launcherx` | C7K, C6K, P7K, P6K, C755, C655, Q7C (2023+) |
| **Android TV** (Android 9–11) | home com linhas de apps e "canais" | `com.google.android.tvlauncher` | P615, C715, C725, S5400A, "BeyondTV2" (2019–2022) |
| **Roku TV** | menu roxo, sem Play Store | não se aplica | alguns modelos S/RP |

Descubra o seu: `scripts/00-connect.sh` imprime versão do Android e qual launcher existe.
Internamente o Google TV é Android TV com outra casca: os comandos `pm` e `settings`
são iguais; mudam os nomes de alguns pacotes.

## ADB

Android Debug Bridge. Um daemon na TV (`adbd`) aceita comandos de um cliente no
computador. Por USB ou por rede (porta 5555). O que usamos:

```bash
adb connect 192.168.0.50          # abre a sessão
adb -s 192.168.0.50:5555 shell pm list packages
adb -s ... shell settings put global window_animation_scale 0.5
```

`-s` escolhe o dispositivo; `lib/common.sh` sempre passa isso para você não
mandar comando para o aparelho errado.

## Pacote, usuário 0 e os três estados

Todo app é um **pacote** (`com.tcl.ttvs`). Apps de fábrica ficam na partição
`/system`, somente leitura; ninguém apaga de verdade sem root. O que dá para
fazer é mudar o **estado para o usuário 0** (o único usuário de uma TV):

| Comando | Estado | Reverte com | O que acontece |
|---|---|---|---|
| `pm disable-user --user 0 X` | `disabled` | `pm enable --user 0 X` | some do launcher, não inicia no boot, não recebe broadcasts |
| `pm uninstall -k --user 0 X` | `uninstalled` (user 0) | `cmd package install-existing --user 0 X` | mesmo efeito; o APK continua em /system |
| (nada) | `enabled` | | |

**Preferimos `disable-user`.** É o consenso dos projetos pesquisados: mais
previsível em TVs TCL (alguns pacotes "desinstalados" voltam sozinhos, e um
"Success" do `pm` nem sempre significa que agiu). O `30-debloat.sh` confere o
estado real depois de cada comando por isso.

## Por que reiniciar

Um pacote desabilitado continua carregado em memória até o próximo boot. O
ganho de RAM e de processos só aparece após `adb reboot`. Meça depois.

## Como o botão HOME é resolvido

O botão Home dispara um intent `MAIN` com categoria `HOME`. O Android lista os
candidatos em ordem de prioridade e abre o primeiro **habilitado**:

```bash
adb shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME"
```

Três consequências que explicam quase todos os problemas de launcher:

1. `cmd package set-home-activity` imprime "Success" e **não muda nada** no
   Google TV. O único jeito de o seu launcher vencer é desabilitar quem está na
   frente dele.
2. Com o launcher de fábrica desabilitado, quem passa a ganhar é a
   `RecoveryActivity` do assistente de configuração
   (`com.google.android.tungsten.setupwraith`): tela preta ou tela de recovery.
   Ele precisa ser desabilitado junto.
3. O `setupwraith` pode ser o pacote que criou a configuração da rede Wi-Fi
   (quem configurou a TV na primeira vez). Desabilitá-lo nesse caso derruba a
   rede. Solução: esquecer e reconectar o Wi-Fi em Configurações antes; a
   rede passa a pertencer ao `com.android.tv.settings`.

`50-launcher.sh set-default` verifica as três coisas antes de agir e reverte
sozinho se o HOME não cair no launcher novo.

## RAM "livre" engana

O kernel usa memória liberada como cache de arquivos. Depois do debloat,
"Free RAM" pode parecer igual. Olhe `Used PSS` (deve cair) e `Cached kernel`
(deve subir), e quais processos saíram do topo. `60-benchmark.sh` mostra os três.

## Settings

Preferências do sistema em três namespaces: `global`, `secure`, `system`.
Lemos e escrevemos com `settings get|put|delete`. As chaves que os scripts tocam
estão em `TRACKED_SETTINGS` (lib/common.sh) e são salvas no backup, para o
rollback devolver exatamente o valor anterior (inclusive "inexistente").
