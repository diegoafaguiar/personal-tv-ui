# 1. Primeiros passos com a Roku TV

## Como o computador fala com a Roku

As TVs Android deste projeto são controladas pelo **adb**, que dá acesso ao sistema inteiro.
A Roku não tem adb. O que ela oferece é o **ECP (External Control Protocol)**: um pequeno
servidor HTTP na porta **8060** da TV, o mesmo que o app "Roku" do celular usa como controle.

Pelo ECP dá para:

- **ler**: modelo, versão do Roku OS, canais instalados, o app aberto agora (`GET /query/...`);
- **controlar**: apertar teclas do controle, abrir um canal, digitar (`POST /keypress/...`, `/launch/...`).

Não dá para instalar, remover ou desativar nada, nem mudar Configurações diretamente. Qualquer
mudança assim é feita pelo controle, ou mandando as mesmas teclas pelo `40-remote.sh`.

## Liberar o ECP na TV

**Configurações › Sistema › Configurações avançadas do sistema › Controle por apps móveis ›
Acesso à rede**

| Opção | O que acontece |
|---|---|
| Desativado | nada funciona pela rede, nem o app do celular |
| Limitado (padrão nas versões recentes) | o app do celular funciona, mas a TV recusa a lista de canais e o inventário: `ECP command not allowed in Limited mode` |
| **Permissivo** | tudo funciona; é o que estes scripts precisam |

Em "Permissivo", qualquer aparelho da sua rede pode controlar a TV, como já acontece com o adb
nas TVs Android. Para fechar, volte a "Limitado".

## Achar a TV na rede

```bash
roku/scripts/00-connect.sh --scan         # procura Roku em toda a subrede (porta 8060)
roku/scripts/00-connect.sh 192.168.100.50 # confere e salva o IP
```

O IP também aparece na TV em **Configurações › Rede › Sobre**. Reserve um IP fixo para a TV
no roteador; senão, ele muda e os scripts não a acham (é só editar a ficha em `devices/`).

## Modo de desenvolvedor (opcional, só para um canal próprio)

Necessário apenas se, no futuro, quisermos instalar um canal feito por nós (por exemplo, uma
versão "Minha TV" para a Roku). Para inventário e controle **não precisa**.

Com o controle, na tela inicial: **Home 3×, Cima 2×, Direita, Esquerda, Direita, Esquerda,
Direita**. A TV mostra o IP, pede para aceitar o contrato e definir uma senha, e reinicia.
Depois, o instalador de canais fica em `http://<ip-da-tv>` no navegador. Para desligar, repita
a sequência e escolha desativar.

Esse canal próprio seria um app aberto **a partir da tela inicial da Roku**, nunca a tela
inicial em si: a Roku não permite trocá-la.
