#!/usr/bin/env bash
# Start the Project Kingdom dedicated server.
#
# The runtime data (world, libraries, logs, backups) lives in the instance
# directory, never in the repository: KINGDOM_SERVER_DIR, by default server/run.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
INSTANCE="${KINGDOM_SERVER_DIR:-$HERE/run}"
FORGE_VERSION="1.20.1-47.3.0"
INSTALLER="forge-${FORGE_VERSION}-installer.jar"
MIN_JAVA_MAJOR=17
MIN_HEAP="${KINGDOM_MIN_HEAP:-2G}"
MAX_HEAP="${KINGDOM_MAX_HEAP:-4G}"

log() { printf '[kingdom] %s\n' "$*"; }
die() { printf '[kingdom] ERROR: %s\n' "$*" >&2; exit 1; }

command -v java >/dev/null 2>&1 || die "java not found: install JDK ${MIN_JAVA_MAJOR} or newer"
java_major="$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/')"
case "$java_major" in
  ''|*[!0-9]*) die "cannot read the java version" ;;
esac
[ "$java_major" -ge "$MIN_JAVA_MAJOR" ] || die "java ${java_major} is too old, ${MIN_JAVA_MAJOR}+ required"

grep -q '^eula=true' "$HERE/eula.txt" \
  || die "eula.txt still says eula=false: accept the Minecraft EULA first"

mod_jar="$(ls -1t "$HERE/../dist"/*.jar "$HERE/../build/libs"/*.jar 2>/dev/null | head -n1 || true)"
[ -n "$mod_jar" ] || die "no mod jar in dist/ or build/libs: build the mod first (01_build_linux.sh)"

mkdir -p "$INSTANCE/mods" "$INSTANCE/backups"
[ -f "$INSTANCE/server.properties" ] || cp "$HERE/server.properties" "$INSTANCE/server.properties"
[ -f "$INSTANCE/eula.txt" ] || cp "$HERE/eula.txt" "$INSTANCE/eula.txt"
if grep -q '^eula=false' "$INSTANCE/eula.txt"; then
  die "$INSTANCE/eula.txt still says eula=false: accept the Minecraft EULA first"
fi

if [ ! -f "$INSTANCE/run.sh" ]; then
  [ -f "$HERE/$INSTALLER" ] \
    || die "missing $INSTALLER: download the Forge ${FORGE_VERSION} installer and put it next to this script"
  log "installing the Forge server into $INSTANCE (once)"
  (cd "$INSTANCE" && java -jar "$HERE/$INSTALLER" --installServer)
fi

# The client and the server must run the same mod build.
cp -f "$mod_jar" "$INSTANCE/mods/"
log "mod: $(basename "$mod_jar")"

printf '%s\n' "-Xms${MIN_HEAP}" "-Xmx${MAX_HEAP}" \
  > "$INSTANCE/user_jvm_args.txt"
printf '%s\n' "-XX:+UseG1GC" "-XX:MaxGCPauseMillis=200" "-XX:+UnlockExperimentalVMOptions" \
  "-XX:+DisableExplicitGC" "-XX:+AlwaysPreTouch" \
  "-XX:G1NewSizePercent=30" "-XX:G1MaxNewSizePercent=40" "-XX:G1HeapRegionSize=16M" \
  "-XX:InitiatingHeapOccupancyPercent=15" "-XX:G1MixedGCLiveThresholdPercent=90" \
  "-XX:G1MixedGCCountTarget=8" "-XX:G1HeapWastePercent=5" \
  "-XX:+UseStringDeduplication" \
  >> "$INSTANCE/user_jvm_args.txt"

log "instance: $INSTANCE"
log "heap: ${MIN_HEAP} - ${MAX_HEAP}"
cd "$INSTANCE"
exec ./run.sh nogui
