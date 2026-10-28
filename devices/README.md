# devices/: uma ficha por TV

Cada TV da casa tem um arquivo `devices/<nome>.env` com o IP e o sistema. Os scripts
leem a ficha quando você passa `TV=<nome>`:

```bash
TV=tcl-g13 android-tv/scripts/20-inventory.sh
TV=philco-roku-58 roku/scripts/20-inventory.sh
make inventory TV=tcl-g09-sala
make roku-inventory TV=philco-roku-58
```

Sem `TV=`, valem os arquivos antigos: `android-tv/config/tv.env` e `roku/config/roku.env`.
`TV_HOST=ip` na linha de comando continua valendo mais que tudo.

As fichas `*.env` não vão para o git (são da sua rede). Copie `exemplo.env.example`:

```bash
cp devices/exemplo.env.example devices/sala.env
```

Se o roteador trocar o IP de uma TV, basta editar a ficha dela. Para não acontecer,
reserve um IP fixo para cada TV no roteador (reserva por DHCP, pelo endereço MAC).

| Campo | Para quê |
|---|---|
| `TV_HOST` | IP da TV (Android: porta 5555 do adb; Roku: o ECP usa a 8060) |
| `TV_OS` | `android-tv` ou `roku`: qual pasta de scripts usar |
| `TV_MODEL` | só para você saber qual TV é |
