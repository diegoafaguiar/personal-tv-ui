# 2. Limpeza e privacidade pelo controle

A Roku não deixa desativar nada do sistema nem mudar ajustes pela rede. O que se ganha vem
de remover canais sem uso e desligar coleta de dados e anúncios. Os caminhos abaixo são os
do Roku OS em português; a ordem dos itens pode variar um pouco entre versões.

Antes de começar, rode `roku/scripts/20-inventory.sh`: ele registra o "antes" em `reports/`.

## 1. Privacidade e anúncios

| Onde | O que fazer | Por quê |
|---|---|---|
| Configurações › Privacidade › Experiência da Smart TV | desligar **Usar informações das entradas da TV** | é o reconhecimento automático de conteúdo (ACR): a TV identifica o que passa na antena e no HDMI e usa para anúncios |
| Configurações › Privacidade › Publicidade | ligar **Limitar rastreamento de anúncios** | os anúncios deixam de ser personalizados pelo seu uso |
| Configurações › Privacidade › Microfone | revisar a permissão de cada canal | só a busca por voz precisa |

Nenhum desses ajustes tira função da TV.

## 2. Canais sem uso

Na tela inicial, selecione o canal, aperte **✱** no controle › **Remover canal**. Canais
removidos voltam pela Loja de canais quando quiser, sem custo (assinaturas ficam na sua conta
do serviço, não no canal).

Remover canais libera espaço e evita atualizações em segundo plano. Comece pelos marcados no
inventário como "confira se alguém da casa usa" e pelos que só redirecionam para outro app.

Para reorganizar: **✱ › Mover canal**. Coloque no topo os que a casa mais usa.

## 3. Tela inicial

**Configurações › Tela inicial**: esconda as seções que ninguém usa (por exemplo, a de
filmes e séries gratuitos, se não interessa). As entradas (TV ao vivo, HDMI) podem ser
renomeadas e escondidas em **Configurações › Entradas de TV**.

## 4. Desempenho

- **Configurações › Sistema › Energia › Início rápido da TV**: ligado, a TV liga mais rápido
  (gasta um pouco mais de energia em standby).
- **Configurações › Sistema › Reinício do sistema**: reinicie depois da limpeza; ajuda
  quando a interface fica lenta após semanas ligada.
- As atualizações do Roku OS são automáticas e não podem ser bloqueadas; mantêm a TV segura.

## Conferir

Rode de novo `roku/scripts/20-inventory.sh` e compare com o relatório anterior em `reports/`.
