package ru.projectkingdom.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.npc.*;

/** Optional independent scenario. No calls to mining, movement or delivery executors. */
public final class AutonomousMineProbe {
    private static final UUID OWNER = UUID.fromString("137855f8-c822-45b1-80aa-00ca9783dc02");
    private static final BlockPos CORE = new BlockPos(44, 200, 45);
    private static final BlockPos RAW = new BlockPos(37, 200, 45);
    private static final BlockPos FINISHED = new BlockPos(41, 200, 45);
    private static final KingdomMineSettings.Shaft SHAFT =
            new KingdomMineSettings.Shaft(36, 42, 36, 42, 200, "minecraft:overworld");
    private static MinecraftServer server;
    private static ServerLevel level;
    private static Path root;
    private static Villager resident;
    private static KingdomNpcData.Worker worker;
    private static Container raw, finished;
    private static final boolean ROCK = Boolean.getBoolean("npc.runtime.rock");
    private static BlockState loam, granite;
    private static Properties checkpoint;
    private static boolean resumed;
    private static int resumeSecond, resumeDelivered;
    private static int ticks, firstLayer, secondLayer;
    private static double distance;
    private static net.minecraft.world.phys.Vec3 previous;
    private static boolean done;

    public static void boot(Object value) {
        server = (MinecraftServer) value;
        server.execute(() -> {
            try {
                root = server.getWorldPath(LevelResource.ROOT).toRealPath();
                require(root.equals(Path.of(System.getProperty("npc.runtime.allowedWorld")).toRealPath()), "isolated path");
                require(Files.readString(root.resolve(".npc-runtime-validation-world")).trim()
                        .equals("disposable-npc-runtime-validation-v1"), "disposable world token");
                Path saved = root.resolve("npc-autonomous-checkpoint.properties");
                if (ROCK && Files.exists(saved)) {
                    checkpoint = new Properties();
                    try (var input = Files.newInputStream(saved)) { checkpoint.load(input); }
                } else {
                    require(!Files.exists(root.resolve("npc-autonomous-started")), "fresh autonomous world required");
                    Files.writeString(root.resolve("npc-autonomous-started"), "disposable scenario\n");
                }
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                        TickEvent.ServerTickEvent.class, AutonomousMineProbe::beforeTick);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                        TickEvent.ServerTickEvent.class, AutonomousMineProbe::observe);
            } catch (Throwable failure) { fail(failure); }
        });
    }

    private static void beforeTick(TickEvent.ServerTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.START && server.overworld() != null) {
            // Test clock control keeps one work shift available. Production
            // contract/schedule gates still execute normally on every tick.
            // Restore the saved working view before allowing another productive tick.
            TfcClock.set(server, checkpoint != null && !resumed ? 6000 : 3000);
        }
    }

    private static void observe(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            ++ticks;
            if (resident == null) {
                if (checkpoint == null) createFixture(); else resumeFixture();
                return;
            }
            require(ticks <= 12000, "autonomous timeout; " + worker.mineStatus);
            require(resident.isAlive(), "actual MCA miner survives");
            distance += resident.position().distanceTo(previous);
            previous = resident.position();
            int removedFirst = removed(1), removedSecond = removed(2);
            require(removedSecond == 0 || removedFirst == 49,
                    "second layer remains intact until all 49 first-layer cells are clear");
            if (removedFirst == 49 && firstLayer != 49)
                pass("all 49 first-layer cells cleared by production miner before second layer");
            firstLayer = removedFirst;
            secondLayer = removedSecond;
            if (firstLayer == 0 && ticks % 20 == 0) telemetry();
            if (ticks % 400 == 0) System.out.println("[NPC-AUTONOMOUS] PROGRESS tick=" + ticks
                    + " first=" + firstLayer + " second=" + secondLayer
                    + " position=" + resident.blockPosition() + " status=" + worker.mineStatus
                    + " schedule=" + KingdomResidentSchedule.modeOf(server, resident.getUUID(), worker));
            int delivered = delivered();
            if (ROCK && !resumed && firstLayer == 49 && secondLayer >= 1) {
                checkpoint(delivered);
                return;
            }
            if (firstLayer == 49 && secondLayer >= 16 && delivered >= 64) {
                if (ROCK) {
                    require(resumed && secondLayer > resumeSecond && delivered > resumeDelivered,
                            "production cuts and physical delivery continue after full JVM restart");
                    require(wornPick(), "native TFC rock mined with a physically worn diamond pickaxe");
                    require(KingdomResidentInventory.count(raw, item("tfc:wood/support/oak", 1).getItem()) < 32,
                            "native TFC support items borrowed from raw warehouse");
                    require(nativeSupports(true) > 0 && nativeSupports(false) > 0,
                            "native TFC horizontal beams and vertical posts exist in the live shaft");
                    require(nativeRockLoot() > 0, "actual TFC granite loot delivered to finished warehouse");
                    pass("soil49 then native TFC granite>=16, actual pick wear, native support borrowing/frames and delivery continue after full JVM restart");
                }
                require(distance > 8, "actual locomotion traveled more than eight blocks");
                require(KingdomResidentInventory.count(raw, Items.DIAMOND_SHOVEL) == 0,
                        "actual shovel was borrowed from raw warehouse");
                require(KingdomResidentInventory.count(raw, Items.LADDER) < 32,
                        "actual ladders consumed from raw warehouse");
                require(KingdomNpcWorkday.employed(server, worker), "real enterprise payroll contract retained");
                require(worker.physicalBag && KingdomMcaAdapter.hasMcaInventory(resident), "real MCA physical bag retained");
                pass("production schedule, live MCA locomotion, 7x7 layer order, native "
                        + (ROCK ? "soil/granite" : "soil")
                        + " mining, supply borrowing, ladders and >=64 physical warehouse delivery");
                Files.writeString(root.resolve("npc-autonomous-PASS"), "PASS first=" + firstLayer
                        + " second=" + secondLayer + " delivered=" + delivered + " distance=" + distance + "\n");
                done = true;
                server.halt(false);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void createFixture() {
        level = server.overworld();
        McaLeaseProbe.run(level);
        for (int cx = 1; cx <= 3; cx++) for (int cz = 1; cz <= 3; cz++) {
            level.setChunkForced(cx, cz, true);
            BlockPos position = new BlockPos(cx * 16, 200, cz * 16);
            level.getChunkAt(position);
            require(KingdomTerritories.get(server).claim(level, position, OWNER), "fixture owned territory");
        }
        loam = block("tfc:dirt/loam");
        granite = ROCK ? block("tfc:rock/raw/granite") : loam;
        // Two real TFC layers above bedrock: soil then optional raw granite.
        // The untouched south wall accepts native ladders.
        for (int x = 32; x <= 48; x++) for (int z = 32; z <= 48; z++)
            for (int y = 197; y <= 204; y++) level.setBlock(new BlockPos(x, y, z),
                    y == 197 ? Blocks.BEDROCK.defaultBlockState()
                            : y == 198 ? granite : y == 199 ? loam : Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(CORE, block("kingdomnpc:mine_core"), 3);
        level.setBlock(RAW, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(FINISHED, Blocks.CHEST.defaultBlockState(), 3);
        raw = (Container) level.getBlockEntity(RAW);
        finished = (Container) level.getBlockEntity(FINISHED);
        raw.setItem(0, new ItemStack(Items.DIAMOND_SHOVEL));
        raw.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
        raw.setItem(2, new ItemStack(Items.LADDER, 32));
        raw.setItem(3, new ItemStack(Items.OAK_PLANKS, 32));
        raw.setItem(4, item("tfc:wood/support/oak", 32));
        raw.setItem(5, new ItemStack(Items.TORCH, 32));
        raw.setChanged();
        var land = KingdomTerritories.get(server);
        require(land.register(level, RAW, OWNER, "raw"), "raw storage authorized");
        require(land.register(level, FINISHED, OWNER, "finished"), "finished storage authorized");
        var enterprises = KingdomEnterpriseData.get(server);
        var mine = enterprises.create(OWNER, "Autonomous runtime mine", EnterpriseKind.MINE);
        require(mine != null, "real mine enterprise created");
        ((EnterpriseCoreBlockEntity) level.getBlockEntity(CORE)).bind(mine.id(), OWNER, EnterpriseKind.MINE);
        enterprises.activateCore(OWNER, mine.id(), EnterpriseKind.MINE, "minecraft:overworld", CORE.asLong());
        require(enterprises.core(mine.id()).active(), "actual enterprise core active");
        require(enterprises.bindStorage(OWNER, mine.id(), EnterpriseBindingRole.RAW_STORAGE, "minecraft:overworld", RAW), "enterprise raw storage");
        require(enterprises.bindStorage(OWNER, mine.id(), EnterpriseBindingRole.FINISHED_STORAGE, "minecraft:overworld", FINISHED), "enterprise finished storage");
        var settings = KingdomMineSettings.get(server);
        require(settings.choose(mine.id(), KingdomMineSettings.Start.DOWN), "central shaft selected");
        require(settings.setShaft(mine.id(), SHAFT), "real 7x7 footprint surveyed");
        require(settings.chooseLevels(mine.id(), 1), "one requested horizon");
        resident = KingdomMcaAdapter.create(level, false);
        require(resident != null, "actual registered MCA miner created");
        resident.setPersistenceRequired();
        resident.setPos(39.5, 200, 44.5);
        require(level.addFreshEntity(resident), "actual MCA miner added");
        worker = KingdomNpcData.get(server).add(resident.getUUID(), "Autonomous miner", "minecraft:overworld", 0);
        worker.employer = OWNER;
        worker.job = "miner";
        worker.order = "work";
        worker.workplaceId = mine.id().toString();
        worker.hasSource = worker.hasDestination = true;
        worker.sourceX = RAW.getX(); worker.sourceY = RAW.getY(); worker.sourceZ = RAW.getZ();
        worker.destX = FINISHED.getX(); worker.destY = FINISHED.getY(); worker.destZ = FINISHED.getZ();
        worker.hunger = worker.energy = worker.safety = 100;
        worker.dietGrain = worker.dietFruit = worker.dietVegetables = worker.dietProtein = worker.dietDairy = 100;
        require(KingdomEnterprisePayrollData.get(server).hire(server, OWNER, mine.id(), resident.getUUID(), 1), "real payroll employment contract");
        require(KingdomMineSite.inspect(server, level, worker).ready(), "real mine assignment ready");
        require(KingdomResidentSchedule.mayProduce(server, worker), "production work shift active");
        previous = resident.position();
        pass("real MCA miner, mine enterprise/core, payroll contract, 7x7 footprint and physical supply depots created");
    }

    private static void checkpoint(int delivered) throws Exception {
        Properties saved = new Properties();
        saved.setProperty("resident", resident.getUUID().toString());
        saved.setProperty("worker", Integer.toString(worker.id));
        saved.setProperty("workplace", worker.workplaceId);
        saved.setProperty("first", Integer.toString(firstLayer));
        saved.setProperty("second", Integer.toString(secondLayer));
        saved.setProperty("delivered", Integer.toString(delivered));
        saved.setProperty("distance", Double.toString(distance));
        saved.setProperty("x", Double.toString(resident.getX()));
        saved.setProperty("y", Double.toString(resident.getY()));
        saved.setProperty("z", Double.toString(resident.getZ()));
        saved.setProperty("tool", worker.fieldTool.save(new net.minecraft.nbt.CompoundTag()).toString());
        saved.setProperty("rawInventory", inventorySnapshot(raw));
        saved.setProperty("finishedInventory", inventorySnapshot(finished));
        saved.setProperty("personalInventory", inventorySnapshot(KingdomMcaAdapter.personal(resident)));
        saved.setProperty("cells", cellSnapshot());
        saved.setProperty("status", worker.mineStatus);
        saved.setProperty("horizontalSupports", Integer.toString(nativeSupports(true)));
        ((net.minecraft.world.level.saveddata.SavedData) KingdomNpcData.get(server)).setDirty();
        server.saveAllChunks(false, true, true);
        try (var output = Files.newOutputStream(root.resolve("npc-autonomous-checkpoint.properties"))) {
            saved.store(output, "Observe only: resume this same world after graceful full JVM shutdown");
        }
        pass("checkpoint first=" + firstLayer + " second=" + secondLayer + " delivered=" + delivered
                + " resident=" + resident.getUUID() + "; stop and resume same world");
        done = true;
        server.halt(false);
    }

    private static void resumeFixture() {
        level = server.overworld();
        loam = block("tfc:dirt/loam");
        granite = block("tfc:rock/raw/granite");
        for (int cx = 1; cx <= 3; cx++) for (int cz = 1; cz <= 3; cz++) {
            level.setChunkForced(cx, cz, true);
            level.getChunkAt(new BlockPos(cx * 16, 200, cz * 16));
        }
        level.getChunkAt(BlockPos.containing(Double.parseDouble(checkpoint.getProperty("x")),
                Double.parseDouble(checkpoint.getProperty("y")), Double.parseDouble(checkpoint.getProperty("z"))));
        var loaded = level.getEntity(UUID.fromString(checkpoint.getProperty("resident")));
        if (loaded == null && ticks < 150) return;
        require(loaded instanceof Villager, "same persisted MCA entity UUID loaded after full JVM restart");
        resident = (Villager) loaded;
        worker = KingdomNpcData.get(server).workers.get(Integer.parseInt(checkpoint.getProperty("worker")));
        require(worker != null && worker.entityId.equals(resident.getUUID())
                && worker.workplaceId.equals(checkpoint.getProperty("workplace")) && "miner".equals(worker.job),
                "same persisted miner job and enterprise assignment");
        raw = (Container) level.getBlockEntity(RAW);
        finished = (Container) level.getBlockEntity(FINISHED);
        require(raw != null && finished != null, "same physical warehouses loaded");
        resumeSecond = Integer.parseInt(checkpoint.getProperty("second"));
        resumeDelivered = Integer.parseInt(checkpoint.getProperty("delivered"));
        if (Boolean.getBoolean("npc.runtime.continueVerifiedWorld")) {
            // Explicit continuation of the same disposable world after a
            // previously verified checkpoint/restart and observed extra cuts.
            // Never recreate a fixture, reset inventory or claim the old
            // snapshot still describes subsequently completed real work.
            require(removed(1)==49 && removed(2)>=resumeSecond && delivered()>=resumeDelivered,
                    "saved continuation cannot lose previously observed excavation/delivery");
            require(KingdomResidentInventory.bind(worker,resident), "bind persisted continuation inventory");
            pass("continue same verified disposable world from saved rock cells="+removed(2)+"; no fixture reset");
        } else {
        require(removed(1) == 49 && removed(2) == resumeSecond && delivered() == resumeDelivered
                && cellSnapshot().equals(checkpoint.getProperty("cells")),
                "exact excavation cell positions/states and physical delivery persisted across restart");
        // The physical bag persists; fieldTool is deliberately a transient
        // working view and is reconstructed by the production bind operation.
        require(KingdomResidentInventory.bind(worker, resident), "restore physical working view after restart");
        reportDifference("raw", checkpoint.getProperty("rawInventory"), inventorySnapshot(raw));
        reportDifference("finished", checkpoint.getProperty("finishedInventory"), inventorySnapshot(finished));
        reportDifference("personal", checkpoint.getProperty("personalInventory"), inventorySnapshot(KingdomMcaAdapter.personal(resident)));
        reportDifference("tool", checkpoint.getProperty("tool"), worker.fieldTool.save(new net.minecraft.nbt.CompoundTag()).toString());
        require(normalizedSnapshot(inventorySnapshot(raw)).equals(normalizedSnapshot(checkpoint.getProperty("rawInventory")))
                && normalizedSnapshot(inventorySnapshot(finished)).equals(normalizedSnapshot(checkpoint.getProperty("finishedInventory")))
                && normalizedSnapshot(inventorySnapshot(KingdomMcaAdapter.personal(resident))).equals(normalizedSnapshot(checkpoint.getProperty("personalInventory")))
                && worker.fieldTool.save(new net.minecraft.nbt.CompoundTag()).toString().equals(checkpoint.getProperty("tool")),
                "exact physical inventories and field-tool durability/NBT survived restart");
        }
        distance = Double.parseDouble(checkpoint.getProperty("distance"));
        previous = resident.position();
        resumed = true;
        pass("full JVM restart restored same resident, miner job, cell states and physical warehouses; observing continuation");
    }

    private static void reportDifference(String name, String expected, String actual) {
        if (expected.equals(actual)) return;
        String[] a=expected.split(";"), b=actual.split(";");
        for(int i=0;i<Math.max(a.length,b.length);i++) {
            String before=i<a.length?a[i]:"<missing>", after=i<b.length?b[i]:"<missing>";
            if(!before.equals(after)) System.out.println("[NPC-AUTONOMOUS] SNAPSHOT " + name + " before=" + before + " after=" + after);
        }
    }

    private static String normalizedSnapshot(String snapshot) {
        StringBuilder result=new StringBuilder();
        for (String row:snapshot.split(";")) {
            if (row.isEmpty()) continue;
            int separator=row.indexOf(':');
            try {
                var tag=net.minecraft.nbt.TagParser.parseTag(row.substring(separator+1));
                // Vanilla intentionally does not save empty slots. A consumed
                // tool's zero-count shell may still carry Damage:0 in memory;
                // that shell is not an item and must not cause a false loss.
                if (tag.getByte("Count")<=0 || tag.getString("id").equals("minecraft:air"))
                    tag=ItemStack.EMPTY.save(new net.minecraft.nbt.CompoundTag());
                result.append(row,0,separator+1).append(tag).append(';');
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
                throw new IllegalArgumentException("Invalid saved inventory snapshot",invalid);
            }
        }
        return result.toString();
    }

    private static String inventorySnapshot(Container container) {
        StringBuilder result = new StringBuilder();
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            result.append(slot).append(':').append(container.getItem(slot)
                    .save(new net.minecraft.nbt.CompoundTag())).append(';');
        return result.toString();
    }

    private static String cellSnapshot() {
        StringBuilder result = new StringBuilder();
        for (int depth = 1; depth <= 2; depth++)
            for (var cell : KingdomMineCentralShaftPlan.layer(SHAFT, depth))
                result.append(cell.target().toShortString()).append('=')
                        .append(level.getBlockState(cell.target())).append(';');
        return result.toString();
    }

    private static int delivered() {
        if (!ROCK) return KingdomResidentInventory.count(finished, loam.getBlock().asItem());
        int count = 0;
        for (int slot = 0; slot < finished.getContainerSize(); slot++) count += finished.getItem(slot).getCount();
        return count;
    }

    private static int nativeRockLoot() {
        int count = 0;
        for (int slot = 0; slot < finished.getContainerSize(); slot++) {
            ItemStack stack = finished.getItem(slot);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.startsWith("tfc:rock/") && id.endsWith("/granite")) count += stack.getCount();
        }
        return count;
    }

    private static boolean wornPick() {
        if (worker.fieldTool.is(Items.DIAMOND_PICKAXE) && worker.fieldTool.getDamageValue() > 0) return true;
        for (Container container : new Container[] {raw, KingdomMcaAdapter.personal(resident)})
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(Items.DIAMOND_PICKAXE) && stack.getDamageValue() > 0) return true;
            }
        return false;
    }

    private static int nativeSupports(boolean horizontal) {
        int count = 0;
        String prefix = horizontal ? "tfc:wood/horizontal_support/" : "tfc:wood/vertical_support/";
        for (int x = 32; x <= 48; x++) for (int z = 32; z <= 48; z++) for (int y = 197; y <= 204; y++)
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock())
                    .toString().startsWith(prefix)) count++;
        return count;
    }

    private static int removed(int depth) {
        int removed = 0;
        for (var cell : KingdomMineCentralShaftPlan.layer(SHAFT, depth))
            if (!level.getBlockState(cell.target()).is((depth == 2 ? granite : loam).getBlock())) removed++;
        return removed;
    }
    private static void telemetry() throws Exception {
        var navigation = resident.getNavigation();
        var path = navigation.getPath();
        String pathInfo = path == null ? "null" : "done=" + path.isDone()
                + ",canReach=" + path.canReach() + ",next=" + path.getNextNodeIndex()
                + ",count=" + path.getNodeCount() + ",end=" + path.getEndNode()
                + ",nodes=" + java.util.stream.IntStream.range(0, path.getNodeCount())
                    .mapToObj(index -> path.getNodePos(index).toShortString()).toList();
        var navigationField = KingdomActionEngine.class.getDeclaredField("NAVIGATIONS");
        navigationField.setAccessible(true);
        var progress = ((java.util.Map<?, ?>) navigationField.get(null)).get(resident.getUUID());
        String progressInfo = "none";
        if (progress != null) {
            var values = new java.util.ArrayList<String>();
            for (var field : progress.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                values.add(field.getName() + "=" + field.get(progress));
            }
            progressInfo = values.toString();
        }
        System.out.println("[NPC-AUTONOMOUS] TELEMETRY tick=" + ticks
                + " xyz=" + resident.position() + " delta=" + resident.getDeltaMovement()
                + " noAi=" + resident.isNoAi() + " onGround=" + resident.onGround()
                + " speed=" + resident.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                + " navigationDone=" + navigation.isDone() + " path=" + pathInfo
                + " walkTarget=" + resident.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                + " navProgress=" + progressInfo + " tool=" + worker.fieldTool
                + " status=" + worker.mineStatus);
    }
    private static BlockState block(String id) {
        ResourceLocation key = new ResourceLocation(id);
        require(BuiltInRegistries.BLOCK.containsKey(key), "registered fixture block " + id);
        return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
    }
    private static ItemStack item(String id, int count) {
        ResourceLocation key = new ResourceLocation(id);
        require(BuiltInRegistries.ITEM.containsKey(key), "registered fixture item " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key), count);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void pass(String message) { System.out.println("[NPC-AUTONOMOUS] PASS " + message); }
    private static void fail(Throwable failure) {
        done = true;
        System.err.println("[NPC-AUTONOMOUS] FAIL tick=" + ticks);
        failure.printStackTrace();
        Runtime.getRuntime().halt(33);
    }
}
