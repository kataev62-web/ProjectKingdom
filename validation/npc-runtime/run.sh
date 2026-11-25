#!/usr/bin/env bash
# A test-only agent exercises the real reobfuscated JAR; no gameplay commands.
set -euo pipefail
if [[ $# != 4 ]]; then
  echo "usage: $0 BASELINE_SERVER NEW_SERVER_DIRECTORY GAMEPLAY_JAR BUILD_LIBS" >&2
  exit 2
fi
baseline=$(realpath "$1")
target=$(realpath -m "$2")
gameplay=$(realpath "$3")
libs=$(realpath "$4")
if [[ -e "$target" ]]; then
  echo "Refusing existing directory: $target (use a new disposable directory)" >&2
  exit 2
fi
agent=("$libs"/*-runtime-validation-agent.jar)
helper=("$libs"/*-runtime-validation.jar)
[[ ${#agent[@]} == 1 && -f ${agent[0]} && ${#helper[@]} == 1 && -f ${helper[0]} ]]
probe=${NPC_RUNTIME_PROBE:-standard}
case "$probe" in
  standard) probe_class=ru.projectkingdom.validation.RuntimeProbe; phases=2; timeout_seconds=480 ;;
  individual) probe_class=ru.projectkingdom.validation.McaIndividualLifeProbe; phases=1; timeout_seconds=480 ;;
  autonomous) probe_class=ru.projectkingdom.validation.AutonomousMineProbe; phases=1; timeout_seconds=900 ;;
  *) echo "Unknown NPC_RUNTIME_PROBE: $probe" >&2; exit 2 ;;
esac
rock=${NPC_RUNTIME_ROCK:-false}
if [[ "$probe" == autonomous && "$rock" == true ]]; then phases=2; fi
mkdir -p "$target/mods" "$target/npc-runtime-world"
ln -s "$baseline/libraries" "$target/libraries"
cp "$baseline/eula.txt" "$target/eula.txt"
# Copy only the installed dependency jars, replacing every older Kingdom JAR.
for dependency in "$baseline"/mods/*.jar; do
  case "$(basename "$dependency")" in kingdomnpc-*.jar) continue ;; esac
  cp "$dependency" "$target/mods/"
done
cp "$gameplay" "$target/mods/"
[[ ! -d "$baseline/config" ]] || cp -R "$baseline/config" "$target/config"
[[ ! -d "$baseline/defaultconfigs" ]] || cp -R "$baseline/defaultconfigs" "$target/defaultconfigs"
cat > "$target/server.properties" <<'PROPERTIES'
level-name=npc-runtime-world
level-seed=830424
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
online-mode=false
server-ip=127.0.0.1
server-port=0
view-distance=2
simulation-distance=2
max-tick-time=120000
PROPERTIES
echo disposable-npc-runtime-validation-v1 > "$target/npc-runtime-world/.npc-runtime-validation-world"
cd "$target"
for ((phase=1; phase<=phases; phase++)); do
  timeout --signal=TERM --kill-after=20s "${timeout_seconds}s" java -XX:ActiveProcessorCount=2 -Xms512m -Xmx2G \
    -Dnpc.runtime.enabled=true \
    "-Dnpc.runtime.rock=$rock" \
    "-Dnpc.runtime.probeClass=$probe_class" \
    "-Dnpc.runtime.allowedWorld=$target/npc-runtime-world" \
    "-javaagent:${agent[0]}=${helper[0]}" \
    @libraries/net/minecraftforge/forge/${NPC_RUNTIME_FORGE:-1.20.1-47.4.10}/unix_args.txt nogui \
    > "npc-runtime-phase-$phase.log" 2>&1
  if rg -q '\[NPC-(RUNTIME|AUTONOMOUS)\] FAIL' "npc-runtime-phase-$phase.log"; then
    cat "npc-runtime-phase-$phase.log" >&2
    exit 1
  fi
  [[ "$probe" != standard || -f npc-runtime-world/npc-runtime-checkpoint.properties ]]
  if [[ "$probe" == autonomous && "$rock" == true && "$phase" == 1 ]]; then
    [[ -f npc-runtime-world/npc-autonomous-checkpoint.properties ]]
    [[ ! -f npc-runtime-world/npc-autonomous-PASS ]]
  fi
done
if [[ "$probe" == individual ]]; then
  [[ -f npc-runtime-world/npc-individual-life-PASS ]]
elif [[ "$probe" == autonomous ]]; then
  [[ -f npc-runtime-world/npc-autonomous-PASS ]]
else
  [[ -f npc-runtime-world/npc-runtime-PASS ]]
fi
rg '\[NPC-(RUNTIME|AUTONOMOUS)\]' npc-runtime-phase-*.log
