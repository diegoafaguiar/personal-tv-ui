# 7. Troubleshooting

Modos de falha reais, coletados dos projetos que fizeram isso em TCL.

| Sintoma | Causa provável | Saída |
|---|---|---|
| `192.168.0.50:5555 unauthorized` | o prompt RSA está esperando na tela da TV | aceite e marque "sempre permitir". Sem prompt: desligue/ligue a Depuração por rede e `adb disconnect; adb connect IP` |
| `connect` falha / timeout | depuração por rede desligou após reboot; DHCP mudou o IP; a TV só abre a 5555 após sessão USB; TV em Ethernet (Google TV não liga depuração por rede fora do Wi-Fi) | religue; `scripts/00-connect.sh --scan 192.168.0`; `--usb-to-wifi`; pareie em Wi-Fi e `adb tcpip 5555` |
| `offline` | sessão antiga pendurada | `adb kill-server; adb connect IP` |
| **Tela preta, controle não faz nada** | launcher desabilitado sem substituto, ou `setupwraith` assumiu o HOME | `scripts/50-launcher.sh restore` (= `pm enable launcherx` + `pm enable setupwraith` + HOME) |
| Wi-Fi sumiu depois do `set-default` | o `setupwraith` era dono da rede salva | `scripts/50-launcher.sh restore`, esqueça e reconecte a rede em Configurações, repita o `set-default` |
| Controle remoto parou | pacote de input desabilitado | `scripts/90-rollback.sh --reboot`; reaplique em lotes menores mantendo `*.remote.service`, `*.globalkeyoverlay`, `*.autopair`, `*.inputmethod.*` |
| Botão do mic não faz nada | `com.google.android.tv.remote.service` ou `katniss` desabilitado | reabilite o `remote.service` (é KEEP); `katniss` é opcional, sem ele o mic abre a busca da Play Store |
| Botões YouTube/Netflix do controle não abrem nada | eram tratados pelo `launcherx` | remapeie no Projectivy (Controle remoto › Botões) ou `scripts/50-launcher.sh restore` |
| Tile "TV" sumiu da home do Google TV | `com.tcl.partnercustomizer` desabilitado | normal; TV continua em Apps e no Inputs. Reabilite se incomodar |
| Screensaver não liga / erro | `dreamx` desabilitado com `screensaver_components` apontando para ele | `scripts/40-tune.sh --screensaver basic` |
| "Success" mas nada mudou | alguns `pm`/`cmd package` respondem Success sem agir (`set-home-activity` é o caso famoso) | nunca confie na mensagem; os scripts conferem `pkg_state` depois |
| Desabilitou 24, só 1 mudou | zsh não faz word-split em variáveis sem aspas; `pm` ignora argumentos extras em silêncio | use os scripts (bash, um pacote por chamada) |
| RAM igual depois do debloat | não reiniciou; ou está olhando "Free RAM" | `adb reboot`; compare `Used PSS` e `Cached kernel` em `60-benchmark.sh` |
| `com.tcl.esticker` voltou sozinho | firmware reabilita (visto em BeyondTV2) | ignore; inofensivo |
| Vários pacotes voltaram após o reboot (`35-verify.sh` acusa) | firmware restaura estados no boot | para esses, use `30-debloat.sh --uninstall <perfil>`: a "desinstalação para o usuário 0" é outro mecanismo e costuma persistir. Confira de novo com `35-verify.sh` após outro reboot |
| O debloat só pegou o primeiro pacote de cada lista | versão antiga dos scripts: `adb shell` consumia o stdin do loop | `git pull`; corrigido com `</dev/null` em todo adb (lib/common.sh) |
| `com.tcl.tv` loga `tias_sdk getService failure` | `com.tcl.interactive` desabilitado | cosmético; para por si; CPU igual |
| Configurações trava (Android TV 9 antigo) | `com.google.android.tvrecommendations` desabilitado | `pm enable --user 0 com.google.android.tvrecommendations` |
| App sideloaded fecha ao abrir | ABI errada | `adb shell getprop ro.product.cpu.abi` e `adb logcat -d -b crash \| tail -30`; baixe o APK da ABI certa |
| Serviço do launcher novo morre a cada boot (G10) | `TclAppBoot` bloqueia auto-start de apps sem `APP_AUTO_START` | `adb shell appops set <pkg> APP_AUTO_START allow && adb shell appops write-settings` (sem o `write-settings`, um reboot perde a mudança) |
| Quer desfazer tudo | | `scripts/90-rollback.sh --reboot`. Sem ADB: factory reset nas Configurações |

## Diagnóstico rápido

```bash
scripts/50-launcher.sh status                       # dono do HOME, candidatos, dono do Wi-Fi
adb shell pm list packages -d | wc -l               # quantos estão desabilitados
adb shell dumpsys package <pkg> | sed -n '/runtime permissions:/,/^$/p'   # permissões faltando
adb logcat -d -b crash | tail -30                   # último crash
adb logcat -v time | grep 'TIDD: keyCode='          # descobrir keycodes do controle (aperte os botões)
```
