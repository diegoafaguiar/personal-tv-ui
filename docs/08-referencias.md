# 8. Referências

Projetos e textos usados para montar as listas, as guardas e as recomendações.
Todos foram lidos em outubro de 2026; nomes de pacote e comportamentos podem
mudar com firmware.

## Debloat e listas de pacotes

- **Clean Your TV** (Gathin23/cleanyourtv): perfis por modelo, inclusive
  `tcl-65c7k-google-tv` (Brasil, Android 14), `tcl-beyondtv2-latin-america`
  (Android TV 9), `tcl-c755-google-tv`, `tcl-g10-google-tv`, `tcl-q7c-google-tv`.
  Base principal das listas deste repositório, das notas de "mantenha" e das
  pegadinhas de Wi-Fi/`setupwraith`/`TclAppBoot`. Também traz o launcher
  nativo estilo Apple TV. <https://github.com/Gathin23/cleanyourtv>
- **android-tv-tune** (ohernandezdev): listas por categoria (`vendor-tcl`,
  `google-tv`, `telemetry`, `never-disable`), docs de launcher e
  troubleshooting com os modos de falha "que ninguém documenta".
  <https://github.com/ohernandezdev/android-tv-tune>
- **debloat-tlc-android-tv** (chrissy-dev): tabela de 82 pacotes TCL com
  classificação safe/caution/critical. <https://github.com/chrissy-dev/debloat-tlc-android-tv>
- Gist **TCL debloater** (llaumgui, TCL 65C7K): lista curta com `disable-user`
  e `install-existing`. <https://gist.github.com/llaumgui/e39fdbafc920edacf42fdff6d8ab3a0a>
- Gist **TCL TV debloater** (0x4f53, Android TV 7): histórico.
  <https://gist.github.com/0x4f53/27bee9a0069f7bd6a2aa9eb60bb10d1e>
- **tv-debloat** (AryamanSi17) e **androidtv-debloat-script** (tutyamxx):
  abordagens alternativas. <https://github.com/AryamanSi17/tv-debloat>,
  <https://github.com/tutyamxx/androidtv-debloat-script>
- **How I Solve: TCL TV Optimization Guide** <https://howisolve.com/tcl-tv-optimization/>
- **bytesprint: Debloat Android TV** (por que `disable-user`, crash de Settings
  com `tvrecommendations`) <https://bytesprint.io/blog/debloat-android-tv>
- XDA: *[Ask] remove TCL TV bloatware* (pacotes que se reinstalam; não remover
  `com.tcl.tv`, `com.tcl.tvinput`) <https://xdaforums.com/t/ask-remove-tcl-tv-bloatware.4193499/>

## Launchers e design

- Projectivy Launcher na Play Store (`com.spocky.projengmenu`).
- FLauncher: <https://github.com/efesser/flauncher>
- Digitec: *A TV home screen without ads: Projectivy Launcher*
  <https://www.digitec.ch/en/page/a-tv-home-screen-without-ads-projectivy-launcher-spices-up-android-and-google-tv-38294>
- BGR: *3 of the best smart TV launchers* <https://www.bgr.com/2140758/best-smart-tv-launchers-you-can-try/>
- Diretrizes de design do Android TV (foco, zona segura, D-pad):
  <https://developer.android.com/design/ui/tv>
- Compose for TV: <https://developer.android.com/training/tv/playback/compose>

## Performance e settings

- TechPP: *8 Google TV tweaks to boost speed* <https://techpp.com/2025/05/05/google-tv-tweaks-tips/>
- TroyPoint: *Change animation scale on Google TV* <https://troypoint.com/firestick-googletv-animation-scale/>
- Android Authority: *Google TV hidden tricks* <https://www.androidauthority.com/best-google-tv-tips-tricks-2026-3670992/>
- Documentação do `adb`: <https://developer.android.com/tools/adb>
