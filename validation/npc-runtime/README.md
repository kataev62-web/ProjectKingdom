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
planks and native TFC beams. The scenario holds the test world's clock at 09:00
to provide one continuous work shift; normal schedule/contract gates remain
active. After setup it only observes, without calling work/navigation/delivery
executors or pinning/teleporting the miner. It requires all 49 first-layer cells
clear before any second-layer removal, at least 16 second-layer cells removed,
more than eight blocks of actual locomotion, physical supply consumption and at
least 64 native TFC soil items delivered to the finished chest. Success writes
`npc-runtime-world/npc-autonomous-PASS`. This soil scenario does not assert
autonomous construction in collapse-risk natural rock or completion of all
requested underground horizons.

Both scenarios also exercise a real MCA brain lease: original schedule restored,
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
