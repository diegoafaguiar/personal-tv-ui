# personal-tv-ui

Kit para deixar as TVs da casa mais rápidas, limpas e úteis, sem root e com tudo
reversível. Os scripts rodam no **seu computador**, na mesma rede das TVs.

O repositório é organizado **por sistema operacional**, porque é o sistema que define o que
dá para fazer em cada TV. Dentro de cada sistema, o que é específico de uma marca fica
em pastas por marca (ex.: `android-tv/config/profiles/tcl/`).

| Sistema | Marcas / modelos | Acesso | O que dá para fazer | Comece por |
|---|---|---|---|---|
| **Android TV / Google TV** | TCL G09 (sala), TCL G13 | adb pela rede (porta 5555) | backup, debloat, ajustes, launcher próprio "Minha TV", rollback | [android-tv/README.md](android-tv/README.md) |
| **Roku OS** | Philco Roku TV 58" | ECP pela rede (porta 8060) | inventário, controle remoto pelo terminal, roteiro de limpeza e privacidade | [roku/README.md](roku/README.md) |

## Estrutura

```
personal-tv-ui/
├── devices/          uma ficha por TV (IP, sistema, modelo); escolha com TV=<nome>
├── android-tv/       tudo que fala adb: scripts, perfis por marca, launcher, testes, docs
├── roku/             tudo que fala ECP: scripts, notas de canais, testes, docs
├── backups/          backups das TVs Android (ignorados pelo git: têm dados pessoais)
├── reports/          inventários, benchmarks, logs (ignorados pelo git)
└── Makefile          atalhos para os dois sistemas
```

## Várias TVs

Cada TV tem uma ficha `devices/<nome>.env` (ignorada pelo git), e todos os scripts aceitam
`TV=<nome>`:

```bash
TV=tcl-g13 android-tv/scripts/20-inventory.sh
TV=philco-roku-58 roku/scripts/20-inventory.sh
make inventory TV=tcl-g09-sala
```

Como criar as fichas: [devices/README.md](devices/README.md).

## Desenvolvimento

```bash
make test   # android-tv/tests (adb falso) e roku/tests (ECP falso)
make lint   # shellcheck -x + bash -n em todos os scripts
```

A CI (`.github/workflows/ci.yml`) roda os dois a cada push; `launcher.yml` compila o APK do
launcher quando `android-tv/launcher/` muda.

## Licença

MIT.
