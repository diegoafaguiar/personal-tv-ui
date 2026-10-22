#!/usr/bin/env bash
# 70-live-tv.sh — TV aberta pelo adb: canais do sintonizador, programação (EPG)
# e sintonia direta num canal.
#
# Uso:
#   scripts/70-live-tv.sh channels              # lista canais sintonizados (número, nome, id)
#   scripts/70-live-tv.sh now [4]               # o que está passando agora (e a seguir) no canal 4
#   scripts/70-live-tv.sh guide [4] [horas]     # grade do canal 4 pelas próximas N horas (padrão 6)
#   scripts/70-live-tv.sh tune 4                # liga a TV no canal 4
#   scripts/70-live-tv.sh inputs                # entradas (HDMI, tuner) e seus ids
#
# O que você aprende aqui: toda TV Android guarda canais e programação num
# banco padrão, o TvContract (content://android.media.tv/...). O app de TV
# ao vivo da TCL (com.tcl.tv) escreve os canais e a grade que a emissora
# transmite (EPG do ISDB-T brasileiro) nesse banco. Quem tem a permissão
# READ_TV_LISTINGS lê de lá: o adb tem; um launcher próprio pode pedir.
# "tune" dispara um VIEW em content://android.media.tv/channel/<id>, que o
# Android entrega ao app de TV ao vivo já no canal certo.
#
# Se "channels" vier vazio: faça a busca de canais na TV (Configurações › Canais)
# ou abra a TV ao vivo uma vez; o com.tcl.tv só popula o banco depois disso.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

CHANNEL_URI="content://android.media.tv/channel"
PROGRAM_URI="content://android.media.tv/program"

# Saída do "content query" vem como "Row: 0 _id=12, display_number=4.1, ..."
# Convertemos para linhas "chave=valor<TAB>chave=valor".
query() {
  tv_shell content query --uri "$1" ${2:+--projection "$2"} ${3:+--where "\"$3\""} 2>/dev/null \
    | tr -d '\r' | sed -nE 's/^Row: [0-9]+ //p' | sed 's/, \([a-z_]*=\)/\t\1/g'
}
field() { tr '\t' '\n' <<<"$1" | sed -nE "s/^$2=//p" | head -n1; }

list_channels() {
  query "$CHANNEL_URI" "_id:display_number:display_name:type:browsable:input_id" \
    | while IFS= read -r row; do
        printf '%-8s %-28s id=%-5s %s\n' "$(field "$row" display_number)" "$(field "$row" display_name)" "$(field "$row" _id)" "$(field "$row" type | sed 's/TYPE_//')"
      done | sort -V
}

# Resolve "4" para o id do canal: aceita 4, 4.1, 04 ou parte do nome ("Globo").
channel_id() {
  local want="$1" row num name id
  while IFS= read -r row; do
    num="$(field "$row" display_number)"; name="$(field "$row" display_name)"; id="$(field "$row" _id)"
    if [[ "$num" == "$want" || "$num" == "$want.1" || "${num#0}" == "$want" || "${name,,}" == *"${want,,}"* ]]; then
      echo "$id"; return 0
    fi
  done < <(query "$CHANNEL_URI" "_id:display_number:display_name")
  return 1
}

fmt_time() { date -d "@$(( $1 / 1000 ))" +%H:%M 2>/dev/null || date -r "$(( $1 / 1000 ))" +%H:%M; }

programs() {
  local id="$1" from_ms="$2" to_ms="$3"
  query "$PROGRAM_URI" "title:short_description:start_time_utc_millis:end_time_utc_millis:episode_title" \
    "channel_id=$id AND end_time_utc_millis>$from_ms AND start_time_utc_millis<$to_ms" \
    | sort -t$'\t' -k3
}

show_guide() {
  local ch="${1:-4}" hours="${2:-6}" id now_ms to_ms row
  id="$(channel_id "$ch")" || die "Canal '$ch' não encontrado. Veja: scripts/70-live-tv.sh channels"
  now_ms=$(( $(date +%s) * 1000 )); to_ms=$(( now_ms + hours * 3600 * 1000 ))
  log_step "Canal $ch (id $id): próximas ${hours}h"
  local n=0
  while IFS= read -r row; do
    [[ -z "$row" ]] && continue
    n=$((n+1))
    local start end title desc
    start="$(field "$row" start_time_utc_millis)"; end="$(field "$row" end_time_utc_millis)"
    title="$(field "$row" title)"; desc="$(field "$row" short_description)"
    if [[ "$start" -le "$now_ms" && "$end" -gt "$now_ms" ]]; then
      printf '%s▶ %s–%s  %s%s\n' "$C_BOLD" "$(fmt_time "$start")" "$(fmt_time "$end")" "$title" "$C_RESET"
    else
      printf '  %s–%s  %s\n' "$(fmt_time "$start")" "$(fmt_time "$end")" "$title"
    fi
    [[ -n "$desc" && "$desc" != "NULL" ]] && printf '    %s%s%s\n' "$C_DIM" "$desc" "$C_RESET"
  done < <(programs "$id" "$now_ms" "$to_ms")
  [[ "$n" -gt 0 ]] || log_warn "Sem programação no banco para este canal. A grade chega pelo ar: deixe o canal sintonizado alguns minutos e tente de novo."
}

show_now() {
  local ch="${1:-4}" id now_ms
  id="$(channel_id "$ch")" || die "Canal '$ch' não encontrado. Veja: scripts/70-live-tv.sh channels"
  now_ms=$(( $(date +%s) * 1000 ))
  log_step "Agora no canal $ch"
  programs "$id" "$now_ms" "$(( now_ms + 4 * 3600 * 1000 ))" | head -n 2 | while IFS= read -r row; do
    printf '%s–%s  %s\n    %s\n' "$(fmt_time "$(field "$row" start_time_utc_millis)")" "$(fmt_time "$(field "$row" end_time_utc_millis)")" "$(field "$row" title)" "$(field "$row" short_description)"
  done
}

tune() {
  local ch="${1:?informe o canal}" id
  id="$(channel_id "$ch")" || die "Canal '$ch' não encontrado. Veja: scripts/70-live-tv.sh channels"
  log_step "Sintonizando canal $ch (id $id)"
  tv_run am start -a android.intent.action.VIEW -d "$CHANNEL_URI/$id"
}

list_inputs() {
  log_step "Entradas (dumpsys tv_input)"
  tv_shell dumpsys tv_input 2>/dev/null | tr -d '\r' | grep -E 'inputId=|TvInputInfo\{|type=' | sed 's/^ *//' | head -n 40
}

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in
    -h|--help|"") sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
  esac
  load_config
  ensure_connected
  case "$cmd" in
    channels) list_channels ;;
    now) show_now "$@" ;;
    guide) show_guide "$@" ;;
    tune) tune "$@" ;;
    inputs) list_inputs ;;
    *) die "Subcomando desconhecido: $cmd" ;;
  esac
}

main "$@"
