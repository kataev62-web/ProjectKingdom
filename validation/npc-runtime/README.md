# Explicit dedicated server validation

Build with `gradle assembleNpcRuntimeValidation build` after applying
`gradle/npc-runtime-validation.gradle`. The validation source sets and agent
are separate artifacts and are never bundled in the gameplay JAR.

With JDK 17 active, run:

```sh
bash validation/npc-runtime/run.sh BASELINE_SERVER NEW_SERVER_DIRECTORY build/libs/kingdomnpc-VERSION.jar build/libs
```

The runner refuses an existing destination, copies the baseline dependency mods
and configuration, uses a fresh local-only flat world, and launches the production
Forge 47.4.10 server twice. The baseline must already contain the pinned MCA
7.7.1-beta.5, Townstead 0.8.0, TFC 3.2.26, Create 0.5.1.j and Patchouli dependencies, the
installed Forge libraries, and an accepted EULA. The Java agent bootstraps the
reobfuscated helper through Forge's class loader. Every assertion runs on the
server thread from real Forge tick callbacks. A world path plus a disposable
world token are required before any probe modifies a world.

Assertions cover the production 40-tick resident dispatcher, actual MCA personal
inventory, once-only legacy migration, repeated bind/flush, tool NBT and cargo
serialization, a native Forge cancellation event, successful native block loot
and exactly-once durability, shared fake player restoration, actual TFC food and
calendar APIs, and the same physical inventory after a complete JVM restart.
Only the second process can write `npc-runtime-world/npc-runtime-PASS`.
After proving the normal production dispatcher, the runner temporarily removes
the fixture's worker record from scheduling and holds the actual MCA entity
stationary while calling the production action engine from server ticks. This
isolates native block/drop/event semantics from navigation and off-shift dig
resets. It restores the record before the restart persistence probe. The TFC
calendar assertion compares the adapter to the actual native API; zero is valid
when TFC pauses time on an empty dedicated server.
This is targeted integration validation; it does not claim autonomous 7x7 mining,
all profession recipes, full settlement economics, or client/UI coverage.

An independent autonomous scenario is available with
`NPC_RUNTIME_PROBE=autonomous bash validation/npc-runtime/run.sh ...` using the
same four arguments and a different new destination. It launches one JVM with a
900-second process limit and 12,000-tick assertion limit. It creates an actual
MCA miner, active physical mine core, enterprise, payroll contract, surveyed 7x7
shaft and real raw/finished chests. The raw chest supplies actual tools, ladders,
planks and native TFC beams. The scenario reads the actual resident's native schedule and holds the test
clock inside that individual's WORK period; normal schedule/contract gates remain
active. After setup it only observes, without calling work/navigation/delivery
executors or pinning/teleporting the miner. It requires all 49 first-layer cells
clear before any second-layer removal, at least 16 second-layer cells removed,
more than eight blocks of actual locomotion, physical supply consumption and at
least 64 native TFC soil items delivered to the finished chest. Success writes
`npc-runtime-world/npc-autonomous-PASS`. This soil scenario does not assert
autonomous construction in collapse-risk natural rock or completion of all
requested underground horizons.

Both scenarios also exercise a real MCA brain lease: individual schedule kept live,
job/home/meeting POIs preserved and native active PANIC retained by hold/release.


The optional `NPC_RUNTIME_PROBE=autonomous NPC_RUNTIME_ROCK=true` mode keeps the
first layer as TFC loam at y=199 and uses raw TFC granite at y=198. It requires
49 soil cells before any granite cut, at least 16 granite cells, actual pickaxe
wear, borrowed native TFC supports, live horizontal/vertical support blocks,
native granite loot and at least 64 physical items in the finished warehouse.
It stops after the first granite cut and launches a second JVM on the same
world. The checkpoint records resident UUID, worker/job assignment, counts,
position, tool/status and support count; phase 2 loads the existing fixtures and
requires further cuts and delivery before PASS. This adds no calls to production
mining/navigation/delivery methods. The original soil mode still runs one JVM.


`NPC_RUNTIME_PROBE=individual bash validation/npc-runtime/run.sh ...` launches a
separate bounded native MCA lifecycle scenario. It reads actual day/night schedules,
uses the real MCA STAY/FOLLOW/chore APIs, registers a physical bed using the production
Kingdom HOME bridge and native MCA Residency/POI transaction, then observes actual
sleep, energy recovery and automatic return to work. It never calls startSleeping,
stopSleeping, or grants recovery energy. Success writes `npc-individual-life-PASS`.
This is a lifecycle fixture with an independent crop-worker lease, not a completed
miner excavation/warehouse scenario. TFC calendar control is confined to the fixture.


After the autonomous soil scenario has stopped and saved, run:

```sh
bash validation/npc-runtime/miner-rest-resume.sh COMPLETED_SOIL_SERVER NEW_SERVER_DIRECTORY GAMEPLAY_JAR BUILD_LIBS
```

This copies the completed disposable world and loads its original miner, contract,
physical inventory and warehouses. It waits for another actual second-layer cut,
registers a real reachable bed outside the shaft, triggers energy25, and observes
stopped production, evacuation, surface arrival, native sleep, recovery to40 and
another cut after waking. It never teleports the miner, calls a production mining
executor directly, forces sleep, or grants recovery. It is bounded to5000 ticks;
PASS is `npc-miner-rest-resume-PASS`. A soil run does not prove deeper TFC support
construction or all underground escape routes.
