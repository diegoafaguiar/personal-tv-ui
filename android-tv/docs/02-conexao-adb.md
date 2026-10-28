# 2. Conectar o ADB à TV

## Na TV

1. **Configurações › Sistema › Sobre › Build do Android**: pressione OK 7 vezes
   até "Você agora é um desenvolvedor".
2. **Configurações › Sistema › Opções do desenvolvedor**: ligue
   **Depuração USB** e **Depuração por rede** (nome varia: "Network debugging",
   "Depuração sem fio").
3. Anote o IP em **Configurações › Rede e Internet › (sua rede)**.
4. Recomendado: reserve esse IP no DHCP do roteador. Se o IP mudar, o
   `android-tv/config/tv.env` fica desatualizado e `00-connect.sh --scan` ajuda a reencontrar.

### Pegadinhas conhecidas em TCL

- **Google TV (Android 14, G08):** a "Depuração por rede" só liga quando a TV
  está em **Wi-Fi**. Em Ethernet o botão volta a desligar. Saída: parear uma vez
  em Wi-Fi, rodar `adb tcpip 5555` e a porta responde em qualquer rede até o
  próximo reboot. Em alguns firmwares, ligar a Depuração USB já abre a 5555.
- **Alguns modelos** só abrem a 5555 após uma sessão USB: conecte o cabo, rode
  `android-tv/scripts/00-connect.sh --usb-to-wifi`, desconecte.
- A depuração por rede **se desliga sozinha** em alguns reboots. Se `connect`
  falhar depois de reiniciar, ligue de novo.

## No computador

```bash
# Instale o platform-tools e confirme
adb version

# Configure o IP
cp android-tv/config/tv.env.example android-tv/config/tv.env && $EDITOR android-tv/config/tv.env

# Conecte
android-tv/scripts/00-connect.sh
```

Na primeira conexão a TV mostra **"Permitir depuração USB?"** com a chave RSA do
seu computador. Marque **"Sempre permitir deste computador"** e OK. Enquanto não
aceitar, `adb` responde `unauthorized`.

Saída esperada:

```
==> Conectando em 192.168.0.50:5555
    ✔ Conectado.
    Marca/modelo : TCL Smart TV Pro (G08)
    Android      : 14 (SDK 34) build UTT2.250416.001
    Chipset      : mt5896 / ABI armeabi-v7a
    Launcher     : com.google.android.apps.tv.launcherx (HOME hoje → com.google.android.apps.tv.launcherx/...)
```

## Segurança

A porta 5555 **não tem autenticação além do prompt RSA**, e o prompt só aparece
para chaves novas. Enquanto a depuração por rede estiver ligada, qualquer
dispositivo na sua LAN que já tenha sido aceito (ou que não precise ser, em
firmwares que não pedem) pode instalar apps e mudar configurações.

Ao terminar: `android-tv/scripts/00-connect.sh --disconnect` e **desligue a Depuração por
rede** na TV. Ligue de novo só quando for usar.

## Problemas

Veja [07-troubleshooting.md](07-troubleshooting.md) para `unauthorized`,
`offline`, "não conecta" e IP que mudou.
