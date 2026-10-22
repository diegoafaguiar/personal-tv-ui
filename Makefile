# Atalhos para o fluxo. Cada alvo chama um script em scripts/; leia-os.
# Uso: make connect | backup | inventory | debloat | tune | launcher-status | rollback | test | lint
SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

SCRIPTS := $(wildcard scripts/*.sh) lib/common.sh tests/run.sh tests/fake-adb/adb
PROFILES ?= tcl-telemetry tcl-promo-ads tcl-retail-demo

help: ## Lista os alvos
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

connect: ## Conecta o adb à TV (TV_HOST em config/tv.env)
	@scripts/00-connect.sh

backup: ## Fotografa o estado atual em backups/
	@scripts/10-backup.sh

inventory: ## Classifica todos os pacotes em reports/
	@scripts/20-inventory.sh

debloat-plan: ## Mostra o plano (dry-run) para PROFILES
	@scripts/30-debloat.sh --dry-run $(PROFILES)

debloat: ## Aplica PROFILES (padrão: telemetria, anúncios, modo loja)
	@scripts/30-debloat.sh $(PROFILES)

tune: ## Animações 0.5x + limpar caches
	@scripts/40-tune.sh --recommended

launcher-status: ## Quem responde ao HOME
	@scripts/50-launcher.sh status

benchmark: ## Mede memória e processos
	@scripts/60-benchmark.sh

rollback-plan: ## Mostra o que o rollback faria (dry-run)
	@scripts/90-rollback.sh --dry-run

rollback: ## Volta ao último backup
	@scripts/90-rollback.sh

test: ## Roda os testes com o adb falso
	@tests/run.sh

lint: ## shellcheck + bash -n
	@for f in $(SCRIPTS); do bash -n "$$f" || exit 1; done
	@shellcheck -x $(SCRIPTS)

.PHONY: help connect backup inventory debloat-plan debloat tune launcher-status benchmark rollback-plan rollback test lint
