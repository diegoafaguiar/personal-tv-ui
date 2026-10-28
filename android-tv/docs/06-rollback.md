# 6. Rollback

## Como funciona

`android-tv/scripts/10-backup.sh` grava em `backups/<data>_<modelo>/` o que importa para
voltar atrás: listas de pacotes por estado, as chaves de settings que os scripts
tocam, quem responde ao HOME e quem é dono do Wi-Fi. `backups/LATEST` aponta para
o mais recente.

`android-tv/scripts/90-rollback.sh [pasta]` compara o backup com a TV **agora** e executa só
a diferença, nesta ordem:

1. `cmd package install-existing --user 0` para pacotes que estavam instalados
   e hoje estão "desinstalados para o usuário 0";
2. `pm enable --user 0` para pacotes que estavam habilitados e hoje estão
   desabilitados (inclui o launcher de fábrica e o setup wizard);
3. `pm disable-user --user 0` para pacotes que **já vinham desabilitados de
   fábrica** e alguém habilitou (fidelidade ao original; `--no-redisable` desliga);
4. `settings put|delete` para cada chave rastreada cujo valor difere
   (`null` no backup = a chave não existia = `delete`);
5. aperta HOME e confere se o dono é o mesmo do backup.

É **idempotente**: rodar duas vezes não faz nada na segunda ("A TV já está no
estado do backup"). Sempre veja o plano antes:

```bash
android-tv/scripts/90-rollback.sh --dry-run
android-tv/scripts/90-rollback.sh --reboot
```

## O "ctrl+z" rápido: reports/*-undo.sh

Cada execução do `30-debloat.sh` grava, além do log, um `reports/<data>-undo.sh`
com um comando por pacote (`pm enable` ou `install-existing`) e o nome amigável
no comentário. `bash reports/<data>-undo.sh` desfaz só aquela sessão. O TV
Detox tem o próprio `android-tv/scripts/55-tv-detox.sh undo`.

## O que ele cobre e o que não cobre

| Situação | Cobre? |
|---|---|
| pacotes desabilitados por `30-debloat.sh` | sim |
| pacotes "desinstalados" por `30-debloat.sh --uninstall` | sim (APK continua em /system) |
| launcher trocado por `50-launcher.sh` | sim (reabilita `launcherx` e `setupwraith`) |
| animações, screensaver, Private DNS, acessibilidade do Projectivy | sim |
| o Projectivy continua instalado | sim, e continua instalado (não é de fábrica); desinstale pela TV se quiser |
| um app de terceiros que você desinstalou **pela TV** | **não** (o APK foi embora); reinstale pela Play Store |
| dados internos de apps, logins, Wi-Fi | nunca foram tocados; nada a restaurar |
| configurações que você mudou à mão na TV fora das chaves rastreadas | não (mas `settings-*.txt` no backup mostra o valor antigo para você repor) |

## Vários backups

Faça um backup por marco: `--label antes-debloat`, `--label antes-launcher`.
O rollback aceita a pasta como argumento, então você pode voltar para qualquer
ponto, não só para o original.

## Se o ADB não alcança mais a TV

1. Desligue a TV da tomada por 30 s e ligue (reboot frio). A depuração por rede
   pode ter desligado: religue em Configurações, se conseguir navegar.
2. Tela preta no Home mas Configurações abre pelo botão de engrenagem:
   **Apps › Ver todos › (launcher de fábrica) › Ativar**.
3. Nada responde: **Configurações › Sistema › Redefinir › Restaurar padrão de
   fábrica**. Isso reabilita todos os pacotes de /system. Você perde logins e
   apps instalados; é o último recurso e sempre funciona, porque nada aqui
   apaga /system.

## Versionar backups?

`backups/` está no `.gitignore` porque `settings-*.txt` e `props.txt` contêm
SSID, contas e seriais. Se quiser guardar um backup no repositório, copie só
`packages-*.txt`, `settings.env`, `home-owner.txt` e `device.txt` para uma
pasta versionada (o rollback funciona com esses quatro).
