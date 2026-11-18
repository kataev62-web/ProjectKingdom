#!/usr/bin/env bash
# Backup of the Project Kingdom dedicated server.
#
# The world and the settlement journal must be copied together: the journal is
# the decision record of the deliveries, the world is the physical state. A
# backup without the journal cannot be recovered automatically.
#
# When the server is running, the world is flushed over RCON first. Saving is
# switched off for the copy and switched on again in every case, including a
# failure of the flush or of the archive: a frozen world would be worse than a
# failed backup.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
INSTANCE="${KINGDOM_SERVER_DIR:-$HERE/run}"
RCON="${KINGDOM_RCON_BIN:-$HERE/rcon.py}"
RCON_PORT="${KINGDOM_RCON_PORT:-25575}"
KEEP_DAYS="${KINGDOM_BACKUP_KEEP_DAYS:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

log() { printf '[kingdom] %s\n' "$*"; }
# Errors go to stderr: the answers of the server are redirected, the reason must not be.
err() { printf '[kingdom] ERROR: %s\n' "$*" >&2; }
die() { err "$*"; exit 1; }

[ -d "$INSTANCE" ] || die "no instance directory: $INSTANCE"
WORLD="$INSTANCE/world"
[ -d "$WORLD" ] || die "no world directory: $WORLD"
BACKUPS="$INSTANCE/backups"
mkdir -p "$BACKUPS"

# Runs one RCON command and checks the answer of the server.
rcon_run() {
  local command="$1" output
  if ! output="$(python3 "$RCON" --port "$RCON_PORT" --password "${KINGDOM_RCON_PASSWORD:-}" "$command" 2>&1)"; then
    err "RCON '$command' failed: $output"
    return 1
  fi
  case "$output" in
    *"Unknown command"*|*"rcon error"*|*"Permission denied"*)
      err "RCON '$command' was refused: $output"
      return 1
      ;;
  esac
  printf '%s' "$output"
}

saving_disabled=0
restore_saving() {
  if [ "$saving_disabled" = "1" ]; then
    if rcon_run "save-on" >/dev/null 2>&1; then
      log "saving is enabled again"
    else
      err "saving could not be enabled again: the world stays frozen until it is enabled manually"
    fi
  fi
}
# The exit status of the script must survive the trap.
trap 'status=$?; restore_saving; exit $status' EXIT

server_running() {
  # A server started by systemd has no pid file: the operator says so.
  if [ "${KINGDOM_FORCE_LIVE:-0}" = "1" ]; then return 0; fi
  if [ -f "$INSTANCE/server.pid" ] && kill -0 "$(cat "$INSTANCE/server.pid")" 2>/dev/null; then
    return 0
  fi
  pgrep -f "forge.*47.3.0|net.minecraftforge" >/dev/null 2>&1
}

if server_running; then
  [ -n "${KINGDOM_RCON_PASSWORD:-}" ] \
    || die "the server is running: set KINGDOM_RCON_PASSWORD and enable RCON in server.properties to back up a live world"
  log "switching the saving off and flushing the running world"
  rcon_run "save-off" >/dev/null
  saving_disabled=1
  flush="$(rcon_run "save-all flush")"
  case "$flush" in
    *"Saved the game"*|*"Saving is already turned off"*|*"Automatic saving is already disabled"*)
      log "the world is flushed"
      ;;
    "")
      log "the world is flushed (the server answered nothing)"
      ;;
    *)
      log "the server answered: $flush"
      ;;
  esac
fi

archive="$BACKUPS/kingdom-${STAMP}.tar.gz"
log "writing $archive"
tar -czf "$archive" -C "$INSTANCE" world

# The journal has to be inside the world directory: it is copied together.
# The path is the same as the one the mod uses: world/data/kingdom_settlement.
journal="$WORLD/data/kingdom_settlement"
if [ -d "$journal" ]; then
  records="$(ls -1 "$journal" | wc -l)"
  log "settlement journal included: ${records} file(s)"
  if [ "$records" = "0" ]; then
    log "the journal has no records yet: no delivery was decided on this world"
  fi
else
  log "WARNING: no settlement journal in $journal yet"
fi

find "$BACKUPS" -name 'kingdom-*.tar.gz' -type f -mtime "+${KEEP_DAYS}" -print -delete \
  || log "WARNING: old backups could not be removed"
log "done"
