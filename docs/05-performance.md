# 5. Performance: o que ganha de verdade

## Expectativas honestas

| Mudança | Efeito medido pela comunidade |
|---|---|
| Desabilitar `com.tcl.waterfall.overseas`, `ttvs`, `tclhome_passive`, `gtvpai` | menos processos no boot; em TCL G10 (Android 12) foi o maior ganho de RAM isolado |
| Debloat completo num TCL Android 12 de 1,8 GB | 54 de 153 pacotes desabilitados; `Used PSS` caiu, `Cached kernel` subiu |
| Debloat num BeyondTV2 (Android 9, 2 GB) | **zero** ganho mensurável de velocidade (CPU ocioso e tempo de abrir apps dentro do ruído). Vale pela privacidade e pelo silêncio, não pela velocidade |
| Trocar o launcher stock (~156 MB) por Projectivy (~metade) | é o maior processo de uma TV de pouca RAM; a home responde mais rápido |
| Animações 0.5x | não acelera nada; a UI **parece** mais responsiva ao controle |
| Private DNS com bloqueio de anúncios | menos requisições dentro dos apps; alguns apps carregam mais rápido |

Uma TV com chipset de 2019 continua com chipset de 2019. O que o kit entrega é
**boot mais limpo, home instantânea e menos ruído**, não milagre.

## O que `40-tune.sh` faz e por quê

```bash
scripts/40-tune.sh --recommended     # = --animations 0.5 --trim-caches
```

| Flag | Comando na TV | Reversão |
|---|---|---|
| `--animations 0.5` | `settings put global {window,transition,animator_duration}_scale 0.5` | rollback ou `… 1` |
| `--screensaver basic` | `settings put secure screensaver_components com.android.dreams.basic/.Colors` | rollback |
| `--screensaver off` | `settings put secure screensaver_enabled 0` | rollback |
| `--private-dns dns.adguard.com` | `settings put global private_dns_mode hostname` + `private_dns_specifier` | `--private-dns off` ou rollback |
| `--trim-caches` | `pm trim-caches 999G` (limpa cache de todos os apps) | n/a |
| `--dexopt` | `cmd package bg-dexopt-job` (recompila apps em background) | n/a |

Não use animações em **0**: alguns apps dependem de callbacks de animação e
travam transições. 0.5 é o consenso.

## Ordem que mais rende

1. `30-debloat.sh tcl-telemetry tcl-promo-ads tcl-retail-demo --reboot`
2. `40-tune.sh --recommended`
3. `50-launcher.sh` (Projectivy) e depois, opcionalmente, desabilitar `dreamx`
   e `katniss` em `google-extras` se não usar Assistente.
4. Reboot e `60-benchmark.sh`.

## Como medir

```bash
scripts/60-benchmark.sh                                  # salva reports/<data>-benchmark.txt
scripts/60-benchmark.sh --compare reports/<anterior>.txt # diff
scripts/60-benchmark.sh --launch com.google.android.youtube.tv   # cold start em ms
```

O baseline do dia zero está em `backups/<pasta>/meminfo.txt`.

Leia:

- **Used PSS** ↓ e **Cached kernel** ↑ = memória de verdade liberada.
- **Top 15 por PSS**: `waterfall`, `ttvs`, `launcherx` devem sumir do topo.
- **processos** ↓ após o reboot.
- `TotalTime` do `am start -W`: compare o mesmo app, a frio, antes/depois.

## O que não fazer

- `settings put global` em chaves de `activity_manager_constants` para limitar
  processos em background: imprevisível em Android TV e revertido por OTA.
- Apps "limpadores" (inclusive `com.tcl.guard`): matam processos que o
  Android reabre, gastando mais.
- Desabilitar `com.google.android.gms` para "economizar": quebra login de
  todos os apps.
