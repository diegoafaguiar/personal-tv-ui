# Roku TV

> Parte do [personal-tv-ui](../README.md). Os comandos abaixo rodam **da raiz do repositório**.
> Com mais de uma TV, escolha qual com `TV=<nome>` (fichas em [`devices/`](../devices/README.md)).

Scripts para inventariar e controlar uma **Roku TV** (Philco, TCL Roku, AOC Roku…) pela rede,
mais o roteiro de limpeza e privacidade que só dá para fazer pelo controle.

## O que dá e o que não dá na Roku

A Roku não é Android: o sistema (Roku OS) é fechado e não tem adb. O acesso pela rede é o
**ECP** (External Control Protocol), uma API HTTP na porta 8060 da TV.

| Na Android TV (TCL) | Na Roku |
|---|---|
| Backup completo e rollback | Não existe backup; o inventário registra o "antes" |
| Desativar apps de fábrica (debloat) | Impossível; só remover canais, pelo controle |
| Trocar a tela inicial (launcher "Minha TV") | Impossível; a tela inicial da Roku é fixa |
| Ajustes por comando (animações, DNS…) | Só pelo controle (roteiro em [docs/02-limpeza.md](docs/02-limpeza.md)) |
| Controle e leitura pela rede | **Sim**: teclas, abrir canal, digitar, listar canais, ver o app aberto |

## Começar

```bash
roku/scripts/00-connect.sh 192.168.100.50   # confere a TV e salva o IP em roku/config/roku.env
roku/scripts/20-inventory.sh                # aparelho e canais em reports/, com notas por canal
roku/scripts/40-remote.sh apps              # canais com ID
roku/scripts/40-remote.sh launch netflix    # abre um canal pelo nome ou ID
roku/scripts/40-remote.sh key Home Down Select
roku/scripts/40-remote.sh type "flamengo"   # digita numa busca aberta
```

Ou com `make`: `make roku-connect roku-inventory TV=philco-roku-58`.
`DRY_RUN=1` mostra as teclas sem mandá-las à TV.

Antes, na TV: **Configurações › Sistema › Configurações avançadas do sistema › Controle por
apps móveis › Acesso à rede › Permissivo**. Veja [docs/01-primeiros-passos.md](docs/01-primeiros-passos.md).

## Estrutura

```
roku/
├── config/canais.tsv        notas por canal (ID do ECP), usadas no inventário
├── config/roku.env          IP padrão da Roku, quando não se usa TV=<nome> (ignorado pelo git)
├── lib/roku.sh              ECP (ecp_get só lê; ecp_post muda a TV e obedece DRY_RUN), logging
├── scripts/                 00-connect, 20-inventory, 40-remote
├── tests/                   ECP falso (fake-ecp/curl) + asserções
└── docs/                    primeiros passos e roteiro de limpeza
```

## Leitura

1. [Primeiros passos: ECP, o que cada modo permite, modo de desenvolvedor](docs/01-primeiros-passos.md)
2. [Limpeza e privacidade pelo controle](docs/02-limpeza.md)
