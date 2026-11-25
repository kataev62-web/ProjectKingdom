package ru.projectkingdom.validation;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import ru.projectkingdom.npc.*;

/** Run only through the explicit agent in a disposable, token-marked world. */
public final class RuntimeProbe {
    private static final UUID OWNER = UUID.fromString("137855f8-c822-45b1-80aa-00ca9783dc01");
    private static final BlockPos TARGET = new BlockPos(8, 200, 8);
    private static MinecraftServer server;
    private static ServerLevel level;
    private static Path root;
    private static Villager resident;
    private static KingdomNpcData.Worker worker;
    private static int ticks, stage, breakEvents;
    private static boolean cancel = true, finished;
    private static FakePlayer actor;
    private static Properties state;
    private static ItemStack pickaxe;

    public static void boot(Object value) {
        server = (MinecraftServer) value;
        server.execute(() -> {
            try {
                root = server.getWorldPath(LevelResource.ROOT).toRealPath();
                require(root.equals(Path.of(System.getProperty("npc.runtime.allowedWorld")).toRealPath()),
                        "exact isolated world path");
                require(Files.readString(root.resolve(".npc-runtime-validation-world")).trim()
                        .equals("disposable-npc-runtime-validation-v1"), "disposable world token");
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                        TickEvent.ServerTickEvent.class, RuntimeProbe::tick);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                        BlockEvent.BreakEvent.class, RuntimeProbe::onBreak);
            } catch (Throwable failure) { fail(failure); }
        });
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPos().equals(TARGET) && event.getPlayer() == actor) {
            breakEvents++;
            if (cancel) event.setCanceled(true);
        }
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END) return;
        try {
            if (++ticks > 800) throw new AssertionError("Runtime probe timed out at stage " + stage);
            if (stage == 0) { initialize(); return; }
            if (stage == 10) { verifyRestart(); return; }
            if (stage == 20) {
                if (!worker.physicalBag) return;
                require(KingdomResidentInventory.count(KingdomResidentInventory.personal(resident), Items.APPLE) == 3,
                        "production 40-tick dispatcher migrated legacy cargo");
                pass("production KingdomNpcMod 40-tick dispatcher bound actual MCA resident");
                initializeMining();
                return;
            }
            if (stage == 1 || stage == 2 || stage == 3 || stage == 4) {
                // This fixture tests the production action engine, not navigation.
                resident.setNoAi(true);
                resident.setNoGravity(true);
                resident.getNavigation().stop();
                resident.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                resident.setPos(8.5, 200, 10.5);
                int damage = worker.fieldTool.getDamageValue();
                var outcome = KingdomActionEngine.breakBlock(level, resident, worker, TARGET, worker.fieldTool);
                checkActor();
                if (stage == 1 && breakEvents > 0) {
                    require(outcome.status() == KingdomActionEngine.Status.BLOCKED, "Forge cancellation honored");
                    require(level.getBlockState(TARGET).is(Blocks.STONE), "canceled block remains");
                    require(worker.fieldTool.getDamageValue() == damage, "canceled break causes no wear");
                    require(KingdomResidentInventory.count(KingdomResidentInventory.personal(resident), Items.COBBLESTONE) == 7,
                            "canceled break creates no inventory drops");
                    pass("native Forge BreakEvent cancellation and actor restoration");
                    cancel = false;
                    stage = 2;
                } else if (stage == 2 && outcome.done()) {
                    require(breakEvents >= 2, "native destroyBlock dispatched second Forge event");
                    require(level.getBlockState(TARGET).isAir(), "native block destroyed");
                    require(worker.fieldTool.getDamageValue() == 13, "native tool wear exactly once");
                    KingdomResidentInventory.flush(level, resident, worker);
                    require(KingdomResidentInventory.count(KingdomResidentInventory.personal(resident), Items.COBBLESTONE) == 8,
                            "exact native loot collected");
                    pass("native destroyBlock loot, durability, and physical bag flush");
                    ResourceLocation rock = new ResourceLocation("tfc:rock/raw/granite");
                    require(BuiltInRegistries.BLOCK.containsKey(rock), "registered natural TFC rock");
                    level.setBlock(TARGET, BuiltInRegistries.BLOCK.get(rock).defaultBlockState(), 3);
                    stage = 3;
                } else if (stage == 3 && outcome.done()) {
                    require(breakEvents >= 3, "TFC rock native Forge event");
                    require(level.getBlockState(TARGET).isAir(), "native natural TFC rock destroyed");
                    require(worker.fieldTool.getDamageValue() == 14, "TFC rock native durability exactly once");
                    require(worker.fieldInventory.stream().anyMatch(stack -> !stack.isEmpty()
                                    && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("tfc")),
                            "native natural TFC rock loot retained");
                    KingdomResidentInventory.flush(level, resident, worker);
                    pass("natural TFC raw granite: native Forge event, TFC loot, native durability");
                    pickaxe = worker.fieldTool;
                    worker.fieldInventory.add(pickaxe);
                    worker.fieldTool = new ItemStack(Items.DIAMOND_SHOVEL);
                    KingdomResidentInventory.flush(level, resident, worker);
                    ResourceLocation soil = new ResourceLocation("tfc:dirt/loam");
                    require(BuiltInRegistries.BLOCK.containsKey(soil), "registered TFC soil");
                    level.setBlock(TARGET, BuiltInRegistries.BLOCK.get(soil).defaultBlockState(), 3);
                    stage = 4;
                } else if (stage == 4 && outcome.done()) {
                    require(breakEvents >= 4 && level.getBlockState(TARGET).isAir(), "native TFC soil destroy and Forge event");
                    require(worker.fieldTool.getDamageValue() == 1, "native TFC soil shovel wear exactly once");
                    require(worker.fieldInventory.stream().anyMatch(stack -> !stack.isEmpty()
                            && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("tfc:dirt/loam")),
                            "native TFC soil loot retained");
                    worker.fieldInventory.removeIf(stack -> stack.is(Items.DIAMOND_PICKAXE));
                    worker.fieldInventory.add(worker.fieldTool);
                    worker.fieldTool = pickaxe;
                    KingdomResidentInventory.flush(level, resident, worker);
                    pass("TFC loam soil: native Forge event, soil loot, shovel durability");
                    nativeDeckPlacement();
                    nativeEastLadder();
                    persist();
                } else require(!outcome.failed(), "mining progressed: " + outcome);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void initialize() throws Exception {
        require(ModList.get().getModContainerById("forge").orElseThrow().getModInfo().getVersion().toString().equals(System.getProperty("npc.runtime.forge", "47.4.10")), "Forge version");
        for (String id : new String[]{"mca", "tfc", "create", "kingdomnpc"})
            require(ModList.get().isLoaded(id), "loaded production mod " + id);
        level = server.overworld();
        level.setChunkForced(0, 0, true);
        level.getChunkAt(TARGET);
        state = new Properties();
        Path checkpoint = root.resolve("npc-runtime-checkpoint.properties");
        if (Files.exists(checkpoint)) {
            try (var input = Files.newInputStream(checkpoint)) { state.load(input); }
            stage = 10;
            return;
        }
        resident = KingdomMcaAdapter.create(level, false);
        require(resident != null && KingdomMcaAdapter.isResident(resident), "real registered MCA villager");
        resident.setNoAi(true);
        resident.setNoGravity(true);
        resident.setPersistenceRequired();
        resident.setPos(8.5, 200, 10.5);
        require(level.addFreshEntity(resident), "MCA entity added to live server");
        Container bag = KingdomResidentInventory.personal(resident);
        require(bag != null && bag.getContainerSize() >= 27, "MCA physical 27-slot inventory");
        bag.clearContent();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.setDamageValue(12);
        tool.getOrCreateTag().putString("KingdomRuntimeProbe", "native-tool-nbt");
        bag.setItem(0, tool);
        bag.setItem(1, new ItemStack(Items.COBBLESTONE, 7));
        worker = KingdomNpcData.get(server).add(resident.getUUID(), "Runtime validation", "minecraft:overworld", 0);
        worker.employer = OWNER;
        worker.fieldInventory.add(new ItemStack(Items.APPLE, 3)); // legacy migration
        stage = 20; // The production subscriber, not this probe, must bind first.
    }

    private static void initializeMining() throws Exception {
        Container bag = KingdomResidentInventory.personal(resident);
        require(KingdomResidentInventory.bind(worker, resident), "bind real MCA container");
        for (int i = 0; i < 3; i++) {
            require(KingdomResidentInventory.bind(worker, resident), "repeat bind");
            KingdomResidentInventory.flush(level, resident, worker);
        }
        require(KingdomResidentInventory.count(bag, Items.APPLE) == 3, "legacy migration exactly once");
        require(KingdomResidentInventory.count(bag, Items.COBBLESTONE) == 7, "cargo preserved");
        require(worker.fieldTool.getDamageValue() == 12 && "native-tool-nbt".equals(worker.fieldTool.getTag().getString("KingdomRuntimeProbe")), "tool NBT preserved");
        CompoundTag entityNbt = new CompoundTag();
        resident.save(entityNbt);
        Villager restored = KingdomMcaAdapter.create(level, false);
        restored.load(entityNbt);
        require(KingdomResidentInventory.count(KingdomResidentInventory.personal(restored), Items.APPLE) == 3, "MCA entity NBT round trip");
        pass("real MCA bind/flush, once-only legacy migration, tool/cargo NBT round trip");
        nativeTfcFood();
        nativeTfcSupportGeometry();
        McaLeaseProbe.run(level);
        // The real dispatcher was asserted above. Isolate the subsequent native
        // action probes from schedule arbitration (which correctly forgets digs
        // for this free resident). Reinsert before saving/restart verification.
        require(KingdomNpcData.get(server).workers.remove(worker.id) == worker,
                "isolate direct action fixture after production dispatcher proof");
        require(KingdomTerritories.get(server).claim(level, TARGET, OWNER), "isolated mining claim");
        for (int x = 6; x <= 10; x++) for (int y = 199; y <= 202; y++) for (int z = 6; z <= 11; z++)
            level.setBlock(new BlockPos(x, y, z), y == 199 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(TARGET, Blocks.STONE.defaultBlockState(), 3);
        actor = FakePlayerFactory.get(level, new GameProfile(OWNER, "KingdomWorker"));
        actor.getInventory().clearContent();
        actor.getInventory().selected = 4;
        actor.getInventory().setItem(4, new ItemStack(Items.EMERALD, 9));
        actor.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLD_INGOT, 2));
        actor.setPos(20, 205, 20);
        actor.setGameMode(GameType.CREATIVE);
        stage = 1;
    }

    private static void nativeTfcFood() throws Exception {
        ResourceLocation id = new ResourceLocation("tfc:food/red_apple");
        require(BuiltInRegistries.ITEM.containsKey(id), "TFC food registry entry");
        ItemStack food = new ItemStack(BuiltInRegistries.ITEM.get(id));
        Class<?> capability = Class.forName("net.dries007.tfc.common.capabilities.food.FoodCapability", true, level.getClass().getClassLoader());
        Object data = capability.getMethod("get", ItemStack.class).invoke(null, food);
        require(data != null, "native TFC IFood capability");
        long nativeExpiry = ((Number) Class.forName("net.dries007.tfc.common.capabilities.food.IFood", true, capability.getClassLoader())
                .getMethod("getRottenDate").invoke(data)).longValue();
        require(KingdomFoodFreshness.expiry(food) == nativeExpiry && nativeExpiry != Long.MAX_VALUE,
                "production expiry equals native TFC API");
        Class<?> calendars = Class.forName("net.dries007.tfc.util.calendar.Calendars", true, capability.getClassLoader());
        Object nativeCalendar = calendars.getField("SERVER").get(null);
        long nativeTicks = ((Number) Class.forName("net.dries007.tfc.util.calendar.ICalendar", true, capability.getClassLoader())
                .getMethod("getTicks").invoke(nativeCalendar)).longValue();
        require(KingdomFoodFreshness.now() == nativeTicks,
                "production calendar equals native TFC API (zero allowed for paused empty server)");
        pass("production food adapter against real TFC capability/calendar");
    }

    private static void nativeTfcSupportGeometry() throws Exception {
        ResourceLocation id = new ResourceLocation("tfc:wood/horizontal_support/oak");
        require(BuiltInRegistries.BLOCK.containsKey(id), "registered native TFC oak support");
        var beamState = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        Class<?> supportApi = Class.forName("net.dries007.tfc.util.Support", true, level.getClass().getClassLoader());
        Object support = supportApi.getMethod("get", net.minecraft.world.level.block.state.BlockState.class)
                .invoke(null, beamState);
        require(support != null, "actual TFC data-driven support definition");
        require(((Number) supportApi.getMethod("getSupportDown").invoke(support)).intValue() == 2,
                "pinned native TFC support reaches exactly two blocks downward");
        var canSupport = supportApi.getMethod("canSupport", BlockPos.class, BlockPos.class);
        for (int distance : new int[]{-2, -1, 1, 2}) for (boolean xAxis : new boolean[]{true, false}) {
            BlockPos approach = TARGET.above().offset(xAxis ? distance : 0, 0, xAxis ? 0 : distance);
            var frame = KingdomMineSupportPlan.emergency(approach, TARGET);
            require(KingdomMineSupportPlan.valid(frame), "production above-layer emergency frame valid");
            require(Boolean.TRUE.equals(canSupport.invoke(support, frame.crossbeam(), TARGET)),
                    "production corrected crossbeam supports actual target by native TFC API");
            require(Boolean.FALSE.equals(canSupport.invoke(support, approach.above(2), TARGET)),
                    "old three-block-high crossbeam rejected by native TFC API");
        }
        pass("native TFC Support: above-layer emergency beam covers target; old high beam rejected");
    }

    /** One native deck cell regression; this does not assert five-horizon autonomy. */
    private static void nativeDeckPlacement() {
        BlockPos deck = new BlockPos(9, 200, 9);
        require(level.getBlockState(deck).isAir()
                        && level.getBlockState(deck.below()).is(Blocks.BEDROCK),
                "owned deck fixture has air above the existing solid platform");
        require(KingdomResidentInventory.count(KingdomResidentInventory.personal(resident), Items.OAK_PLANKS) == 0,
                "deck fixture starts without plank cargo");
        worker.fieldInventory.add(new ItemStack(Items.OAK_PLANKS, 2));
        KingdomResidentInventory.flush(level, resident, worker);
        require(KingdomResidentInventory.count(KingdomResidentInventory.personal(resident), Items.OAK_PLANKS) == 2,
                "exactly two actual planks stocked in the MCA personal bag");
        var area = new KingdomMineSettings.Shaft(6, 12, 6, 12, 201, "minecraft:overworld");
        require(area.contains(deck), "deck position belongs to the surveyed fixture area");
        var result = KingdomMineStructureWork.plankAt(level, resident, worker, area, deck);
        checkActor();
        require(result == KingdomMineStructureWork.Result.BUILT
                        && level.getBlockState(deck).is(Blocks.OAK_PLANKS),
                "production native deck placement succeeds: " + result + "; "
                        + KingdomMineStructureWork.lastReason());
        KingdomResidentInventory.flush(level, resident, worker);
        require(KingdomResidentInventory.count(KingdomResidentInventory.personal(resident), Items.OAK_PLANKS) == 1,
                "native deck placement consumes exactly one physical plank");
        pass("one owned deck cell: native plank placement, exact material consumption and FakePlayer restoration");
    }

    /** The west wall is external; an EAST facing ladder at this descent is cleared infrastructure. */
    private static void nativeEastLadder() throws Exception {
        BlockPos ladder = new BlockPos(10, 200, 9);
        var area = new KingdomMineSettings.Shaft(10, 16, 3, 9, 201, "minecraft:overworld");
        require(KingdomMineCentralShaftPlan.descent(area, 1).equals(ladder), "corner descent fixture");
        require(level.getBlockState(ladder.west()).is(Blocks.OAK_PLANKS)
                        && level.getBlockState(ladder.south()).isAir(),
                "only west wall supports the native ladder fixture");
        worker.fieldInventory.add(new ItemStack(Items.LADDER, 2));
        KingdomResidentInventory.flush(level, resident, worker);
        var result = KingdomMineStructureWork.ladderAt(level, resident, worker, area, ladder);
        checkActor();
        var placed = level.getBlockState(ladder);
        require(result == KingdomMineStructureWork.Result.BUILT && placed.is(Blocks.LADDER)
                        && placed.getValue(net.minecraft.world.level.block.LadderBlock.FACING)
                            == net.minecraft.core.Direction.EAST,
                "native external west-wall ladder faces EAST");
        var cleared = KingdomMineLayerWork.class.getDeclaredMethod("cleared",
                KingdomMineSettings.Shaft.class, int.class, BlockPos.class,
                net.minecraft.world.level.block.state.BlockState.class);
        cleared.setAccessible(true);
        require(Boolean.TRUE.equals(cleared.invoke(null, area, 1, ladder, placed)),
                "actual layer completion accepts the native EAST descent ladder");
        KingdomResidentInventory.flush(level, resident, worker);
        require(KingdomResidentInventory.count(KingdomResidentInventory.personal(resident), Items.LADDER) == 1,
                "native EAST ladder consumed exactly one physical ladder");
        pass("native EAST descent ladder on external west wall is accepted by actual layer completion");
    }

    private static void checkActor() {
        require(actor.getInventory().selected == 4, "shared actor selected slot restored");
        require(actor.getMainHandItem().is(Items.EMERALD) && actor.getMainHandItem().getCount() == 9, "shared actor hotbar restored");
        require(actor.getOffhandItem().is(Items.GOLD_INGOT) && actor.getOffhandItem().getCount() == 2, "shared actor offhand restored");
        require(actor.getX() == 20 && actor.getY() == 205 && actor.getZ() == 20, "shared actor position restored");
        require(actor.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "shared actor game mode restored");
    }

    private static void persist() throws Exception {
        state.setProperty("entity", resident.getUUID().toString());
        state.setProperty("worker", Integer.toString(worker.id));
        state.setProperty("tfcCargo", Integer.toString(KingdomResidentInventory.count(
                KingdomResidentInventory.personal(resident), stack -> BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).getNamespace().equals("tfc"))));
        KingdomNpcData.get(server).workers.put(worker.id, worker);
        // Explicit vanilla owner lets the separate helper reobfuscate this
        // inherited call without bundling KingdomNpcData into the test JAR.
        ((net.minecraft.world.level.saveddata.SavedData) KingdomNpcData.get(server)).setDirty();
        server.saveAllChunks(false, true, true);
        try (var output = Files.newOutputStream(root.resolve("npc-runtime-checkpoint.properties"))) { state.store(output, "restart probe"); }
        pass("phase 1 saved; full JVM restart required");
        finished = true;
        server.halt(false);
    }

    private static void verifyRestart() throws Exception {
        var entity = level.getEntity(UUID.fromString(state.getProperty("entity")));
        if (entity == null && ticks < 150) return;
        require(entity instanceof Villager && KingdomMcaAdapter.isResident(entity), "MCA entity loaded after full JVM restart");
        resident = (Villager) entity;
        worker = KingdomNpcData.get(server).workers.get(Integer.parseInt(state.getProperty("worker")));
        require(worker != null && worker.physicalBag, "worker physical inventory migration flag persisted");
        require(KingdomResidentInventory.bind(worker, resident), "bind after full restart");
        for (int i = 0; i < 3; i++) {
            KingdomResidentInventory.flush(level, resident, worker);
            KingdomResidentInventory.bind(worker, resident);
        }
        Container bag = KingdomResidentInventory.personal(resident);
        require(KingdomResidentInventory.count(bag, Items.APPLE) == 3, "restart no duplicated migration");
        require(KingdomResidentInventory.count(bag, Items.COBBLESTONE) == 8, "restart exact native loot count");
        require(KingdomResidentInventory.count(bag, Items.DIAMOND_PICKAXE) == 1, "restart exactly one tool");
        require(KingdomResidentInventory.count(bag, Items.DIAMOND_SHOVEL) == 1, "restart exactly one soil shovel");
        require(KingdomResidentInventory.count(bag, stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("tfc"))
                == Integer.parseInt(state.getProperty("tfcCargo")), "restart exact native TFC rock and soil cargo");
        require(worker.fieldTool.getDamageValue() == 14 && "native-tool-nbt".equals(worker.fieldTool.getTag().getString("KingdomRuntimeProbe")), "restart native durability and tool NBT");
        pass("full JVM restart: MCA physical bag, Kingdom SavedData, migration, loot and durability");
        Files.writeString(root.resolve("npc-runtime-PASS"), "PASS full dedicated-server runtime probes\n");
        finished = true;
        server.halt(false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void pass(String message) { System.out.println("[NPC-RUNTIME] PASS " + message); }
    private static void fail(Throwable failure) {
        finished = true;
        System.err.println("[NPC-RUNTIME] FAIL stage=" + stage + " tick=" + ticks);
        failure.printStackTrace();
        Runtime.getRuntime().halt(32);
    }
}
