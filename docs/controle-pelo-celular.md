# Controlar as TVs pelo celular

Os apps oficiais já transformam o celular num controle completo, dentro de qualquer app da TV
(Netflix, YouTube, ajustes), com teclado do celular para digitar buscas e senhas. Não precisa
instalar nada na TV nem deixar o computador ligado.

| TV | App no celular (Android e iPhone) | O que faz |
|---|---|---|
| TCL G09 (Google TV) | **Google TV** | setas, OK, voltar, home, volume, teclado, voz |
| TCL G13 (Android TV) | **Google TV** | o mesmo |
| Philco Roku TV | **Roku** (oficial) | setas, OK, teclado, voz e **escuta privada** (som da TV no fone do celular) |

Requisito comum: **celular e TV na mesma rede Wi-Fi**. Rede de convidados costuma isolar os
aparelhos e impede o controle.

## TCL (Google TV e Android TV): app Google TV

1. Instale o app **Google TV** (Play Store ou App Store) e entre com sua conta Google.
2. Com a TV ligada, toque em **Controle remoto** (canto inferior direito do app).
3. Toque em **Procurar dispositivos** e escolha a TV ("Sala de TV" é a G09).
4. A TV mostra um **código**; digite-o no celular. O pareamento fica salvo.

No Android há um atalho: nas **Configurações rápidas** (puxar a barra de cima), adicione o bloco
**Controle remoto do Google TV**.

Para digitar (busca, senha do Wi-Fi), toque no ícone de **teclado** no app com um campo de
texto aberto na TV.

O serviço que atende o app na TV (`com.google.android.tv.remote.service`) está na lista que a
limpeza nunca desliga (`android-tv/config/profiles/never-disable.list`); o espelhamento
(Chromecast, T-Cast) também continua ligado nas duas TCL.

**Não aparece a TV?** Confira: mesma rede; TV ligada (não em standby profundo); reinicie o
Wi-Fi do celular. O app procura a TV na rede, então uma troca de IP não atrapalha.

> A TCL tem também o app **TCL Home**, com controle para parte dos modelos. Nas suas TCL, o
> app Google TV é o caminho oficial do Google TV e Android TV e o mais completo.

## Roku: app Roku

1. Instale o app **Roku** (oficial) e abra a aba **Controle remoto**.
2. Escolha **58" Philco Roku TV** na lista de dispositivos.
3. Toque no ícone de **fone** para a escuta privada, ou no **teclado** para digitar.

Na TV, **Configurações › Sistema › Configurações avançadas do sistema › Controle por apps
móveis › Acesso à rede** precisa estar em **Limitado** ou **Permissivo** (não Desativado).
Para os scripts deste projeto, use Permissivo (ver [roku/docs/01-primeiros-passos.md](../roku/docs/01-primeiros-passos.md)).

## Pelo computador

Para testes e automações, os scripts também controlam as TVs pela rede:

```bash
TV=philco-roku-58 roku/scripts/40-remote.sh key Home Down Select
DEV_SERIAL=192.168.100.34:5555 android-tv/scripts/85-launcher-dev.sh remote   # setas do teclado do Mac
```

## Controle "Sintonia" por QR code (TCL com o launcher)

Uma página para o celular servida pelo próprio launcher, sem instalar app nem deixar o
computador ligado.

1. Na TV: **Ajustes › Sistema › Controle pelo celular**.
2. Com o celular no mesmo Wi-Fi, aponte a câmera para o QR. Abre a página do controle.
3. Salve nos favoritos do celular: o código fica guardado nele.

| Aba | O que faz |
|---|---|
| Controle | setas, OK, Voltar, Início, volume, **buscar digitando no celular**, TV ao vivo |
| Apps | abrir qualquer app com um toque; **Organizar favoritos** (★ escolhe, ▲▼ ordena) |
| Visual | estilos prontos, papel de parede, cor de destaque, fonte, **foto do celular como fundo** e backup dos ajustes |

**Copiar o visual de uma TV para outra:** na aba Visual da TV de origem, **Baixar ajustes
desta TV**; depois abra a página da outra TV (pelo QR dela) e use **Restaurar ajustes de um
arquivo**. Senhas, perfis, recentes e o modo leve continuam os de cada TV.

**Limite do Android:** setas, OK e Voltar comandam a tela inicial. Dentro de outros apps
(Netflix, YouTube) o celular faz Início, volume e abre apps; para navegar dentro deles, use o
controle da TV ou o app Google TV. A página avisa quando a TV está em outro app.

**Segurança:** só aparelhos da rede local conseguem falar com a TV (porta 8787), e toda ação
exige o código que vai no QR. **Novo código** invalida os celulares que já leram o QR antigo;
**Desligar** fecha o servidor. Para Início e abrir apps com outro app na frente, a TV precisa
da permissão de sobreposição, concedida por `android-tv/scripts/50-launcher.sh grant`.
