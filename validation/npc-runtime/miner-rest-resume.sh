#!/usr/bin/env bash
# Copy an already stopped, completed soil world; continue it with a new gameplay JAR.
set -euo pipefail
if [[ $# != 4 ]]; then
  echo "usage: $0 COMPLETED_SOIL_SERVER NEW_SERVER_DIRECTORY GAMEPLAY_JAR BUILD_LIBS" >&2
  exit 2
fi
completed=$(realpath "$1")
target=$(realpath -m "$2")
gameplay=$(realpath "$3")
libs=$(realpath "$4")
[[ ! -e "$target" ]]
[[ -f "$completed/npc-runtime-world/npc-autonomous-PASS" ]]
agent=("$libs"/*-runtime-validation-agent.jar)
helper=("$libs"/*-runtime-validation.jar)
[[ ${#agent[@]} == 1 && -f ${agent[0]} && ${#helper[@]} == 1 && -f ${helper[0]} ]]
mkdir -p "$target/mods"
ln -s "$(realpath "$completed/libraries")" "$target/libraries"
cp "$completed/eula.txt" "$completed/server.properties" "$target/"
cp -a "$completed/npc-runtime-world" "$target/npc-runtime-world"
for dependency in "$completed"/mods/*.jar; do
  case "$(basename "$dependency")" in kingdomnpc-*.jar) continue ;; esac
  cp "$dependency" "$target/mods/"
done
cp "$gameplay" "$target/mods/"
[[ ! -d "$completed/config" ]] || cp -R "$completed/config" "$target/config"
[[ ! -d "$completed/defaultconfigs" ]] || cp -R "$completed/defaultconfigs" "$target/defaultconfigs"
cd "$target"
timeout --signal=TERM --kill-after=20s 900s java -XX:ActiveProcessorCount=2 -Xms512m -Xmx2G \
  -Dnpc.runtime.enabled=true \
  -Dnpc.runtime.probeClass=ru.projectkingdom.validation.MinerRestResumeProbe \
  "-Dnpc.runtime.allowedWorld=$target/npc-runtime-world" \
  "-javaagent:${agent[0]}=${helper[0]}" \
  @libraries/net/minecraftforge/forge/${NPC_RUNTIME_FORGE:-1.20.1-47.4.10}/unix_args.txt nogui \
  > npc-miner-rest-resume.log 2>&1
if rg -q '\[NPC-RUNTIME\] FAIL' npc-miner-rest-resume.log; then
  cat npc-miner-rest-resume.log >&2
  exit 1
fi
[[ -f npc-runtime-world/npc-miner-rest-resume-PASS ]]
rg '\[NPC-RUNTIME\]' npc-miner-rest-resume.log
