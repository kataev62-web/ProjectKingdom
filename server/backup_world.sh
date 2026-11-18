#!/usr/bin/env bash
# Backup of the Project Kingdom dedicated server.
#
# The world and the settlement journal must be copied together: the journal is
# the decision record of the deliveries, the world is the physical state. A
# backup without the journal cannot be recovered automatically.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
INSTANCE="${KINGDOM_SERVER_DIR:-$HERE/run}"
KEEP_DAYS="${KINGDOM_BACKUP_KEEP_DAYS:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

log() { printf '[kingdom] %s\n' "$*"; }
die() { printf '[kingdom] ERROR: %s\n' "$*" >&2; exit 1; }

[ -d "$INSTANCE" ] || die "no instance directory: $INSTANCE"
WORLD="$INSTANCE/world"
[ -d "$WORLD" ] || die "no world directory: $WORLD"
BACKUPS="$INSTANCE/backups"
mkdir -p "$BACKUPS"

server_running() {
  if [ -f "$INSTANCE/server.pid" ] && kill -0 "$(cat "$INSTANCE/server.pid")" 2>/dev/null; then
    return 0
  fi
  pgrep -f "forge.*47.3.0|net.minecraftforge" >/dev/null 2>&1
}

flushed=0
if server_running; then
  password="${KINGDOM_RCON_PASSWORD:-}"
  [ -n "$password" ] \
    || die "the server is running: set KINGDOM_RCON_PASSWORD and enable RCON in server.properties to back up a live world"
  log "flushing the running world over RCON"
  python3 "$HERE/rcon.py" --port "${KINGDOM_RCON_PORT:-25575}" --password "$password" "save-off" >/dev/null
  python3 "$HERE/rcon.py" --port "${KINGDOM_RCON_PORT:-25575}" --password "$password" "save-all flush" >/dev/null
  flushed=1
fi

archive="$BACKUPS/kingdom-${STAMP}.tar.gz"
log "writing $archive"
tar -czf "$archive" -C "$INSTANCE" world

if [ "$flushed" -eq 1 ]; then
  python3 "$HERE/rcon.py" --port "${KINGDOM_RCON_PORT:-25575}" --password "${KINGDOM_RCON_PASSWORD:-}" "save-on" >/dev/null
fi

# The journal has to be inside the world directory: it is copied together.
journal="$WORLD/kingdom-settlement"
if [ -d "$journal" ]; then
  log "settlement journal included: $(ls -1 "$journal" | wc -l) file(s)"
else
  log "WARNING: no settlement journal in $journal yet"
fi

find "$BACKUPS" -name 'kingdom-*.tar.gz' -type f -mtime "+${KEEP_DAYS}" -print -delete \
  || log "WARNING: old backups could not be removed"
log "done"
