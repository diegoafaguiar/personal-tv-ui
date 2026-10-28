# Perfis de pacotes (Android TV)

Um arquivo `.list` por categoria, em pastas por marca:

```
profiles/
├── never-disable.list   proteção: vale para qualquer marca
├── google/              Google TV (launcher de fábrica, extras do Google)
├── tcl/                 TCL (telemetria, anúncios, modo loja, perfis dos modelos G09 e G13)
├── geral/               apps de qualquer marca (streaming, ferramentas de sideload)
└── planos/              planos da revisão interativa (25-review.sh), não versionados
```

Os scripts acham o perfil pelo nome, em qualquer pasta: `30-debloat.sh tcl-telemetry`.
Para outra marca, crie a pasta dela (ex.: `philips/`) com os `.list` do modelo.

Formato:

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
| `tcl-g13-br.list` | optional | perfil da TCL G13 (MediaTek, Android 14, Brasil) |

Para a sua TV, crie um perfil próprio a partir do inventário
(`reports/*-inventory.md`), por exemplo `android-tv/config/profiles/minha-tv.list`, e
aplique com `android-tv/scripts/30-debloat.sh minha-tv`. Justificativas de cada pacote em
`android-tv/docs/03-pacotes-e-riscos.md`.
