#!/usr/bin/env bash
# 00-connect.sh — conecta o adb à TV e diagnostica problemas de conexão.
#
# Uso:
#   scripts/00-connect.sh                  # usa TV_HOST de config/tv.env
#   scripts/00-connect.sh 192.168.0.50     # ip explícito (e salva em config/tv.env se não existir)
#   scripts/00-connect.sh --scan [subrede] # procura TVs com a porta 5555 aberta (ex.: 192.168.0)
#   scripts/00-connect.sh --usb-to-wifi    # TV só abre a porta 5555 depois de "adb tcpip" via USB
#   scripts/00-connect.sh --disconnect     # encerra a sessão (boa prática ao terminar)
#
# O que você aprende aqui: o adb via rede não tem autenticação além do prompt
# RSA na TV. Qualquer um na sua LAN pode instalar apps enquanto a porta 5555
# estiver aberta. Desligue a depuração por rede quando terminar.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

usage() { sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

scan_subnet() {
  local subnet="${1:-${TV_SUBNET:-}}"
  if [[ -z "$subnet" ]]; then
    # Tenta deduzir da rota padrão (Linux/macOS)
    local ip
    ip="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -n1 || true)"
    [[ -z "$ip" ]] && ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
    [[ -n "$ip" ]] && subnet="${ip%.*}"
  fi
  [[ -n "$subnet" ]] || die "Não consegui deduzir a subrede. Passe explicitamente: --scan 192.168.0"
  log_step "Procurando porta 5555 aberta em $subnet.0/24 (≈5 s)"
  # Linux: /dev/tcp + timeout. macOS não tem "timeout" por padrão; usa nc (BSD).
  if command -v timeout >/dev/null 2>&1; then
    probe() { timeout 1 bash -c "exec 3<>/dev/tcp/$1/5555" 2>/dev/null; }
  elif command -v nc >/dev/null 2>&1; then
    probe() { nc -z -w 1 "$1" 5555 >/dev/null 2>&1; }
  else
    die "Preciso de 'timeout' (coreutils) ou 'nc' para o scan."
  fi
  local i
  for i in $(seq 1 254); do
    ( probe "$subnet.$i" && echo "$subnet.$i  ← porta 5555 aberta" ) &
  done
  wait
  log_info "Se apareceu um IP acima, rode: scripts/00-connect.sh <ip>"
  return 0
}

main() {
  local explicit_host=""
  case "${1:-}" in
    -h|--help) usage; exit 0 ;;
    --scan) shift; scan_subnet "${1:-}"; exit 0 ;;
    --usb-to-wifi)
      require_cmd adb "Instale o Android platform-tools."
      log_step "Alternando a TV para adb via TCP (precisa de cabo USB conectado agora)"
      adb usb >/dev/null 2>&1 || true
      adb tcpip 5555
      log_ok "Agora desconecte o USB e rode: scripts/00-connect.sh <ip-da-tv>"
      exit 0 ;;
    --disconnect)
      require_cmd adb "Instale o Android platform-tools."
      adb disconnect >/dev/null 2>&1 || true
      log_ok "Sessões adb encerradas. Lembre de desligar 'Depuração por rede' na TV se não for usar mais."
      exit 0 ;;
    "") ;;
    *) explicit_host="$1" ;;
  esac

  if [[ -n "$explicit_host" ]]; then
    export TV_HOST="$explicit_host"
    if [[ ! -f "$REPO_ROOT/config/tv.env" ]]; then
      printf 'TV_HOST=%s\n' "$explicit_host" > "$REPO_ROOT/config/tv.env"
      log_ok "Salvei TV_HOST=$explicit_host em config/tv.env"
    fi
  fi

  load_config
  log_step "Conectando em $TV_SERIAL"
  ensure_connected
  log_ok "Conectado."
  log_info "Marca/modelo : $(tv_prop ro.product.brand) $(tv_prop ro.product.model) ($(tv_prop ro.product.device))"
  log_info "Android      : $(tv_prop ro.build.version.release) (SDK $(tv_prop ro.build.version.sdk)) build $(tv_prop ro.build.display.id)"
  log_info "Chipset      : $(tv_prop ro.board.platform) / ABI $(tv_prop ro.product.cpu.abi)"
  local stock
  stock="$(stock_launcher_pkg)"
  log_info "Launcher     : ${stock:-desconhecido} (HOME hoje → $(home_owner))"
  log_info "Próximo passo: scripts/10-backup.sh"
}

main "$@"
