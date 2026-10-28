# 3. Pacotes e riscos

Esta é a lista consolidada a partir de perfis testados pela comunidade em TVs
TCL, com peso maior para dois cenários muito próximos do seu:

- **TCL 65C7K, Brasil** (Google TV, Android 14, placa G08/MediaTek mt5896, pt-BR):
  lista aplicada e verificada em 21/10/2026, com HDMI, TV aberta (Ginga) e
  launcher substituto funcionando. Mesma placa e firmware do C755 e Q7C.
- **TCL BeyondTV2, América Latina** (Android TV 9, Realtek RTD2851, 2 GB):
  cada pacote desabilitado isoladamente, reboot e 24 verificações.
- **TCL G10** (Google TV, Android 12, Realtek): plataforma dos modelos 2023.

Nomes de pacote mudam entre modelos e anos. **O inventário da sua TV é a fonte
da verdade**: `android-tv/scripts/20-inventory.sh` cruza o que existe nela com estas listas.

## Legenda

| Classe | Significado |
|---|---|
| **KEEP** | nunca mexa; `30-debloat.sh` recusa |
| **SAFE** | nenhuma função perdida; ganha RAM, privacidade e silêncio |
| **OPTIONAL** | só se você não usa o recurso |
| **LAUNCHER** | apenas por `50-launcher.sh set-default` |
| **UNKNOWN** | não está em lista nenhuma; pesquise antes (seção no fim) |

## KEEP: o que segura a TV em pé

| Pacote | Por quê |
|---|---|
| `com.tcl.tv` | app de TV ao vivo **e** player de HDMI/antena |
| `com.tcl.tvinput`, `*.tvinput` | TV Input Framework: tuner e HDMI 1–3 |
| `com.tcl.suspension` | o painel de fontes que o botão **Input** abre |
| `com.tcl.sourcemananger` (sic) | gerenciador de fontes nos Android TV 9 |
| `com.tcl.dashboard` | painel de ajustes rápidos (engrenagem do controle) |
| `com.tcl.autopair`, `com.tcl.tcl_bt_rcu_service`, `com.tcl.keycustomfunctionservice`, `*.globalkeyoverlay` | pareamento e mapeamento do controle Bluetooth |
| `com.google.android.tv.remote.service` | apesar do nome ("remoto do celular"), carrega o serviço do **botão do microfone** do controle BT; sem ele o mic morre |
| `com.tcl.inputmethod.international`, `*.inputmethod.*` | teclado |
| `com.tcl.systemserver`, `com.tcl.system.server`, `com.tcl.providers.config`, `com.tcl.systemui.plugin`, `com.tcl.framework.custom`, `com.tcl.initsetup`, `com.tcl.settings`, `com.tcl.eva`, `com.tvos` | serviços de sistema da TCL; papel nem sempre documentado, perda confirmada em alguns |
| `com.tcl.factory.view` | nunca abra; mas desabilitar quebra menus em firmwares antigos |
| `com.tcl.versionUpdateApp`, `com.tcl.rc.ota` | atualização de firmware (Android TV 9; no Google TV vem pelo Play Services) |
| `tv.mopa.ginga` | middleware **Ginga** da TV aberta brasileira (interatividade DTV) |
| `com.mediatek.*`, `com.realtek.*`, `com.amlogic.*`, `com.dolby.*`, `com.tpa.kb` | drivers de vídeo/áudio do chipset |
| `com.google.android.gms`, `com.android.vending`, `com.google.android.webview`, `com.google.android.permissioncontroller`… | Play Services, loja, WebView, permissões |
| `android`, `com.android.systemui`, `com.android.tv.settings*`, `com.android.providers.*`, `com.android.networkstack*`… | núcleo do Android |
| `com.google.android.tungsten.setupwraith` | assistente de configuração; **candidato a HOME** e possível dono do Wi-Fi. Só via `50-launcher.sh` |
| `com.spocky.projengmenu`, `me.efesser.flauncher`, `com.wolf.launcher`, `com.gathin.tvhome` | seu launcher substituto; desabilitar sem outro Home = tela preta |

## SAFE: telemetria (`tcl-telemetry`)

| Pacote | O que é |
|---|---|
| `com.tcl.ttvs` | "TTVSCore" (~43 MB residente): Samba TV ACR, analytics "BI" da TCL, núcleo da Alexa e do T-Cast. Observa `BOOT_COMPLETED`, `PACKAGE_ADDED/REMOVED`. Em alguns firmwares G10 já vem desabilitado de fábrica |
| `com.tcl.bi` | analytics (Android TV 9) |
| `com.tcl.logkit` | captura e upload de logs |
| `tv.samba.ssm.ui`, `com.samba.tv`, `com.inscape.tv`, `com.alphonso.service` | ACR: amostram o que está na tela (inclusive HDMI) para anunciantes |
| `com.google.android.feedback`, `com.android.tv.feedbackconsent` | feedback ao Google |
| `com.android.adservices.api`, `com.android.ondevicepersonalization.services`, `com.android.federatedcompute.services` | Privacy Sandbox / anúncios (Android 13+) |

## SAFE: promoções e anúncios (`tcl-promo-ads`)

| Pacote | O que é |
|---|---|
| `com.tcl.waterfall.overseas` | motor de anúncios / "TCL Channel" / páginas de filmes. **Maior ganho isolado** |
| `com.tcl.tv.tclhome_passive` | empurra conteúdo TCL para a home a cada boot |
| `com.tcl.partnercustomizer` | altera as linhas da home do Google TV. Efeito colateral: o tile vermelho "TV" some da linha principal (continua em Apps e atrás de Inputs) |
| `com.tcl.messagebox` | push de mensagens e promoções |
| `com.tcl.channelplus` | canais FAST da TCL (também some do Live TV) |
| `com.tcl.tv.plus` | app TCL TV+ |
| `com.tcl.interactive` | serviço TIAS. Com ele desligado o `com.tcl.tv` loga "tias_sdk getService failure" por alguns segundos ao iniciar; CPU medida igual |
| `com.tcl.usercenter` | conta TCL e avisos para logar |
| `com.tcl.smartlink.core` | traz um instalador silencioso (`CustomInstallService`) |
| `android.autoinstalls.config.google.gtvpai` | instala apps "parceiros" sem perguntar |
| `com.google.android.partnersetup` | atribuição de parceiros |

## SAFE: modo loja (`tcl-retail-demo`)

`com.tcl.exhibit`, `com.tcl.esticker`, `com.tcl.hotelmenu`, `com.tcl.repairguide`,
`com.tcl.ocean.instructions` (manual). Observação: no BeyondTV2 o `esticker`
voltou a ficar habilitado sozinho na mesma noite. Inofensivo; não insista.

## OPTIONAL: extras TCL (`tcl-extras`)

| Pacote | Mantenha se… |
|---|---|
| `com.tcl.smartalexa` | usa Alexa |
| `com.tcl.guard` | gosta do "limpador" da TCL (dispensável; é o que gera avisos de "memória") |
| `com.tcl.magiconnectfree`, `com.tcl.miracast` | espelha o celular (T-Cast / Miracast) |
| `com.tcl.airplay2` + `com.mediatek.AirplayAPK` + `com.mediatek.airplaydaemon` | usa AirPlay (os três juntos) |
| `com.tcl.gamebar` | joga console e quer o overlay de modo jogo / VRR |
| `com.tcl.ui_mediaCenter`, `com.tcl.gallery`, `com.tcl.audioplayer` | toca mídia de pendrive (a galeria também traz screensavers TCL) |
| `com.tcl.hearaid`, `com.tcl.t_solo` | caixas TCL sem fio / "ouvir sozinho" por BT |
| `com.tcl.UpdatePeripheral` | tem soundbar TCL |
| `com.tcl.useragreement`, `com.tcl.copydatatotv`, `com.tcl.notereminder`, `com.tcl.MultiScreenInteraction_TV` | raramente |

## OPTIONAL: extras Google (`google-extras`)

| Pacote | Mantenha se… |
|---|---|
| `com.google.android.katniss` (+ `.tv`) | usa o Assistente / busca por voz (~90 MB). Sem ele o botão do mic abre a busca da Play Store |
| `com.google.android.apps.tv.dreamx` | gosta do screensaver "Ambiente" (~40 MB, com promoções). **Troque o screensaver antes** (`40-tune.sh --screensaver basic`), senão o Android fica apontando para um componente inexistente |
| `com.google.android.apps.mediashell` | faz **Chromecast** |
| `com.google.android.videos`, `com.google.android.play.games`, `com.google.android.youtube.tvmusic`, `com.google.android.youtube.tvunplugged` | usa loja de filmes, Play Games, YT Music, YouTube TV (EUA) |
| `com.google.android.tvrecommendations` | Android TV 9: linha de recomendações. Em alguns firmwares TCL antigos desabilitar faz Configurações travar; mantenha em dúvida |
| `com.google.android.tts`, `com.google.android.marvin.talkback` | acessibilidade |

## OPTIONAL: streaming e sideload

`streaming-optional.list` (Netflix, Prime, Disney+, Claro TV+, Mercado Play,
Speedtest…) e `sideload-tools.list` (Aptoide, Downloader…). Edite antes de aplicar.

## Unidade de referência deste repositório: TCL "Smart TV Pro" G09 (Amlogic, Brasil)

Inventário de 22/10/2026, device G09, Google TV / Android 14, 152 pacotes, 1911 MB de RAM (728 MB livres de fábrica). Diferenças em
relação ao 65C7K da literatura:

- **Chipset Amlogic**, não MediaTek: `com.droidlogic`, `com.droidlogic.overlay`,
  `com.droidlogic.imageplayer`, `com.droidlogic.wifitetheringoverlay`. Todos KEEP
  (`com.droidlogic*` em `never-disable.list`).
- **TV aberta via AstroTV** (`com.mirakulo.astrotv.service`), middleware Ginga da
  Mirakulo, no lugar do `tv.mopa.ginga`. KEEP.
- `com.tcl.android.webview`: WebView próprio da TCL. KEEP.
- Sem AirPlay da MediaTek: só `com.tcl.airplay2`.
- 12 apps de terceiros, a maioria instalada pelo `gtvpai` (Red Ball 4, Pluto TV,
  CBS, Twitch, Record Play). Esses não ficam em /system: o certo é
  `30-debloat.sh --uninstall tcl-smart-tv-pro-br-apps`, e o rollback não os
  cobre (voltam pela Play Store).
- 30 pacotes AOSP que o inventário marcava UNKNOWN (`com.android.cts.*`,
  `providers.calendar/contacts`, `frameworkpackagestubs`, overlays…) agora
  são KEEP: não rodam nada relevante em background e desabilitar só traz
  risco de crash ("No Activity found") ou de OTA.

Perfis desta unidade: `tcl-smart-tv-pro-br.list` (escolhas OPTIONAL) e
`tcl-smart-tv-pro-br-apps.list` (terceiros a desinstalar).

## UNKNOWN: como investigar um pacote

```bash
# o que ele declara: permissões, receivers de boot, serviços
adb shell dumpsys package com.tcl.xyz | less

# receptores de boot = inicia sozinho
adb shell dumpsys package com.tcl.xyz | grep -A3 BOOT_COMPLETED

# quanto pesa agora
adb shell dumpsys meminfo com.tcl.xyz | head -20

# pede acesso ao hardware de TV? então é KEEP
adb shell dumpsys package com.tcl.xyz | grep TV_INPUT_HARDWARE
```

Regra prática: receivers de `BOOT_COMPLETED` + permissões de rede + nome com
"bi", "log", "stat", "ad", "promo" = telemetria. Qualquer coisa com "input",
"key", "rcu", "source", "tvinput", "hdmi" = KEEP. Desabilite um por vez,
reinicie, teste HDMI / Live TV / controle / mic / Bluetooth.
