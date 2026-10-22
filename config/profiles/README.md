# Perfis de pacotes

Um arquivo `.list` por categoria. Formato:

```
# risk: safe | optional | launcher | keep
pacote.exemplo          # comentário livre (vai para o inventário)
# linha comentada = ignorada
```

| Arquivo | risk | Aplicado por |
|---|---|---|
| `never-disable.list` | keep | ninguém; `30-debloat.sh` recusa o que bater com os globs |
| `tcl-telemetry.list` | safe | `30-debloat.sh tcl-telemetry` |
| `tcl-promo-ads.list` | safe | `30-debloat.sh tcl-promo-ads` |
| `tcl-retail-demo.list` | safe | `30-debloat.sh tcl-retail-demo` |
| `tcl-extras.list` | optional | edite antes; comente o que usa |
| `google-extras.list` | optional | edite antes; comente o que usa |
| `streaming-optional.list` | optional | apague o que assina |
| `sideload-tools.list` | optional | |
| `launcher-stock.list` | launcher | só `50-launcher.sh set-default` |
| `tcl-smart-tv-pro-br.list` | optional | perfil da unidade de referência (Amlogic, Brasil) |
| `tcl-smart-tv-pro-br-apps.list` | optional | apps de terceiros dela; `30-debloat.sh --uninstall` |

Para a sua TV, crie um perfil próprio a partir do inventário
(`reports/*-inventory.md`), por exemplo `config/profiles/minha-tv.list`, e
aplique com `scripts/30-debloat.sh minha-tv`. Justificativas de cada pacote em
`docs/03-pacotes-e-riscos.md`.
