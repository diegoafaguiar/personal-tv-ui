# Atalhos para o fluxo. Cada alvo chama um script em android-tv/scripts/ ou roku/scripts/; leia-os.
# Uso: make connect | backup | inventory | debloat | tune | launcher-status | rollback   (Android TV)
#      make emulator-setup | emulator | dev | dev-logs | dev-push   (launcher local, android-tv/docs/09)
#      make roku-connect | roku-inventory | roku-apps                (Roku TV)
#      make test | lint
# Escolha a TV com TV=<nome> (ficha em devices/<nome>.env): make inventory TV=tcl-g13
SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help
export TV

A := android-tv
R := roku
SCRIPTS := $(wildcard $(A)/scripts/*.sh) $(A)/lib/common.sh $(A)/tests/run.sh $(A)/tests/fake-adb/adb \
           $(wildcard $(R)/scripts/*.sh) $(R)/lib/roku.sh $(R)/tests/run.sh $(R)/tests/fake-ecp/curl
PROFILES ?= tcl-telemetry tcl-promo-ads tcl-retail-demo

help: ## Lista os alvos
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

connect: ## Conecta o adb à TV (TV= ou android-tv/config/tv.env)
	@$(A)/scripts/00-connect.sh

backup: ## Fotografa o estado atual em backups/
	@$(A)/scripts/10-backup.sh

inventory: ## Lista em português com RAM/espaço por app em reports/
	@$(A)/scripts/20-inventory.sh

review: ## Revisão interativa: marcar um a um o que desligar
	@$(A)/scripts/25-review.sh

finish: ## Espera 30 s, reinicia, confirma boot, memória antes/depois
	@$(A)/scripts/95-finish.sh

debloat-plan: ## Mostra o plano (dry-run) para PROFILES
	@$(A)/scripts/30-debloat.sh --dry-run $(PROFILES)

debloat: ## Aplica PROFILES (padrão: telemetria, anúncios, modo loja)
	@$(A)/scripts/30-debloat.sh $(PROFILES)

verify: ## Após reboot: confere se o debloat persistiu
	@$(A)/scripts/35-verify.sh

tune: ## Animações 0.5x + limpar caches
	@$(A)/scripts/40-tune.sh --recommended

launcher-status: ## Quem responde ao HOME
	@$(A)/scripts/50-launcher.sh status

benchmark: ## Mede memória e processos
	@$(A)/scripts/60-benchmark.sh

rollback-plan: ## Mostra o que o rollback faria (dry-run)
	@$(A)/scripts/90-rollback.sh --dry-run

rollback: ## Volta ao último backup
	@$(A)/scripts/90-rollback.sh

emulator-setup: ## Uma vez: JDK 17 + SDK + imagem Android TV + AVD "tvhome"
	@$(A)/scripts/80-emulator.sh setup

emulator: ## Abre o emulador Android TV e espera o boot
	@$(A)/scripts/80-emulator.sh start

emulator-stop: ## Desliga o emulador
	@$(A)/scripts/80-emulator.sh stop

dev: ## Compila o launcher e roda no emulador
	@$(A)/scripts/85-launcher-dev.sh run

dev-build: ## Só compila (android-tv/launcher/app/build/outputs/apk/debug)
	@$(A)/scripts/85-launcher-dev.sh build

dev-logs: ## logcat do launcher no emulador
	@$(A)/scripts/85-launcher-dev.sh logs

dev-push: ## Compila e instala o APK local na TV (TV= ou android-tv/config/tv.env)
	@$(A)/scripts/85-launcher-dev.sh push

roku-connect: ## Roku: confere a conexão (ECP, porta 8060)
	@$(R)/scripts/00-connect.sh

roku-inventory: ## Roku: aparelho e canais em reports/
	@$(R)/scripts/20-inventory.sh

roku-apps: ## Roku: lista os canais (ID e nome)
	@$(R)/scripts/40-remote.sh apps

test: ## Roda os testes (adb falso e ECP falso)
	@$(A)/tests/run.sh
	@$(R)/tests/run.sh

lint: ## shellcheck + bash -n
	@for f in $(SCRIPTS); do bash -n "$$f" || exit 1; done
	@shellcheck -x $(SCRIPTS)

.PHONY: roku-connect roku-inventory roku-apps emulator-setup emulator emulator-stop dev dev-build dev-logs dev-push help connect backup inventory review finish debloat-plan debloat verify tune launcher-status benchmark rollback-plan rollback test lint
