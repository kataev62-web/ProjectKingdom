package ru.projectkingdom.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
    private static BlockState loam;
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
                require(!Files.exists(root.resolve("npc-autonomous-started")), "fresh autonomous world required");
                Files.writeString(root.resolve("npc-autonomous-started"), "disposable scenario\n");
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
            server.overworld().setDayTime(3000);
        }
    }

    private static void observe(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            if (++ticks == 1) { createFixture(); return; }
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
            int delivered = KingdomResidentInventory.count(finished, loam.getBlock().asItem());
            if (firstLayer == 49 && secondLayer >= 16 && delivered >= 64) {
                require(distance > 8, "actual locomotion traveled more than eight blocks");
                require(KingdomResidentInventory.count(raw, Items.DIAMOND_SHOVEL) == 0,
                        "actual shovel was borrowed from raw warehouse");
                require(KingdomResidentInventory.count(raw, Items.LADDER) < 32,
                        "actual ladders consumed from raw warehouse");
                require(KingdomNpcWorkday.employed(server, worker), "real enterprise payroll contract retained");
                require(worker.physicalBag && KingdomMcaAdapter.hasMcaInventory(resident), "real MCA physical bag retained");
                pass("production schedule, live MCA locomotion, 7x7 layer order, native soil mining, supply borrowing, ladders and >=64 physical warehouse delivery");
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
        // A closed, fully solid starting ground: two real TFC soil layers above
        // a bedrock floor. The untouched south wall accepts native ladders.
        for (int x = 32; x <= 48; x++) for (int z = 32; z <= 48; z++)
            for (int y = 197; y <= 204; y++) level.setBlock(new BlockPos(x, y, z),
                    y == 197 ? Blocks.BEDROCK.defaultBlockState()
                            : y <= 199 ? loam : Blocks.AIR.defaultBlockState(), 3);
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

    private static int removed(int depth) {
        int removed = 0;
        for (var cell : KingdomMineCentralShaftPlan.layer(SHAFT, depth))
            if (!level.getBlockState(cell.target()).is(loam.getBlock())) removed++;
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
