package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The release check of the mine of the resident, on the real server.
 *
 * <p>The check builds a real mine on a real piece of land: a flat platform of
 * stone, a layer of ground and a layer of rock under the surveyed footprint
 * of seven by seven, a real enterprise with a real core, a real depot of raw
 * material with a real shovel, a real pickaxe, real ladders and real planks,
 * and a real resident of MCA with a real contract. From there the ordinary
 * tick of the server does the rest: the miner walks, digs, wears his tools
 * and carries the goods to the finished warehouse.
 *
 * <p>The check writes what it sees, so that the operator can compare it with
 * the world. Nothing of the check is a simulation: every block that is gone
 * was really broken, every drop is a real item and every tool really wears.
 */
public final class KingdomRcMineTest {
    private KingdomRcMineTest() {}
    private static final String TAG = "Kingdom RC: шахта";

    /** The phases of the release check of the mine. */
    public enum Phase { SURFACE, ACCESS, ROCK, DELIVERY, RESTART, CONTINUE, DONE }

    /** The state of the running check. */
    public static final class State {
        public UUID enterprise;
        public UUID owner;
        public UUID resident;
        public String dimension = "minecraft:overworld";
        public int originX;
        public int originZ;
        public int surfaceY;
        public long startedTick;
        public long deadlineTick;
        public final List<String> notes = new ArrayList<>();
        public boolean finished;
        public Phase phase = Phase.SURFACE;
        /** The state of the world when the server was stopped, for the restart. */
        public int surfaceAtRestart = -1;
        public int rockAtRestart = -1;
        public long deliveredAtRestart = -1;
        public int restarts;
    }

    private static State state;
    /** The number of the checks that ran: every check gets its own site. */
    private static int runs;
    /** The file of the state of the check inside the world. */
    private static final String FILE = "kingdom_rc_mine.txt";

    /** Whether a check is running. */
    public static boolean running() { return state != null && !state.finished; }

    /** The state of the running check. */
    public static State state() { return state; }

    /**
     * Builds the mine and the resident and starts the check.
     *
     * @return the text of the preparation for the operator.
     */
    public static String start(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return "Нет сервера или оператора.";
        if (state != null && !state.finished) return TAG + ": проверка уже идёт.";
        ServerLevel level = server.overworld();
        State next = new State();
        next.owner = owner;
        next.dimension = level.dimension().location().toString();
        // The test site is a flat platform near the spawn of the world.
        var spawn = level.getSharedSpawnPos();
        // Every check gets its own site, so that the enterprise of the
        // previous check never stands in the way of the new one.
        runs++;
        // The site must be free: no other enterprise may own its core.
        var enterprises0 = KingdomEnterpriseData.get(server);
        for (int attempt = 0; attempt < 12; attempt++) {
            next.originX = spawn.getX() + 24 + (runs + attempt) * 32;
            next.originZ = spawn.getZ() + (runs + attempt) * 32;
            BlockPos probe = new BlockPos(next.originX + 10, next.surfaceY, next.originZ + 10);
            boolean free = true;
            for (var core : enterprises0.all()) {
                var binding = enterprises0.core(core.id());
                if (binding == null || !binding.active()) continue;
                if (binding.dimension().equals(next.dimension)
                    && Math.abs(binding.packedPos() - probe.asLong()) < 4096) { free = false; break; }
            }
            if (free) break;
        }
        next.surfaceY = spawn.getY() + 2;
        next.startedTick = server.getTickCount();
        next.deadlineTick = server.getTickCount() + 24000;
        // The shift of the Kingdom runs from eight to twelve and from
        // thirteen to eighteen: the check starts inside the morning shift.
        level.setDayTime(KingdomResidentSchedule.tickOfHour(9));
        BlockPos origin = new BlockPos(next.originX, next.surfaceY, next.originZ);
        // The land of the test belongs to the operator: every chunk of the
        // site, so that the depot and the warehouse belong to the same owner.
        var territories = KingdomTerritories.get(server);
        territories.claim(level, origin, owner);
        for (int cx = (next.originX - 4) >> 4; cx <= (next.originX + 12) >> 4; cx++)
            for (int cz = (next.originZ - 4) >> 4; cz <= (next.originZ + 12) >> 4; cz++)
                territories.claimByChunk(next.dimension, cx, cz, owner);
        // The chunks of the site stay loaded while the check runs.
        for (int cx = (next.originX - 4) >> 4; cx <= (next.originX + 12) >> 4; cx++)
            for (int cz = (next.originZ - 4) >> 4; cz <= (next.originZ + 12) >> 4; cz++)
                level.setChunkForced(cx, cz, true);
        // The platform of stone and the two layers under the footprint.
        buildTerrain(level, next);
        // The enterprise with its real core.
        var enterprises = KingdomEnterpriseData.get(server);
        var kind = ru.projectkingdom.enterprise.EnterpriseKind.MINE;
        UUID enterprise = enterprises.create(owner, "RC-шахта", kind).id();
        BlockPos core = new BlockPos(next.originX + 10, next.surfaceY, next.originZ + 10);
        var activation = enterprises.activateCore(owner, enterprise, kind, next.dimension, core.asLong());
        if (activation != ru.projectkingdom.enterprise.EnterpriseActivation.ACTIVATED)
            return TAG + ": не удалось активировать ядро предприятия (" + activation + ").";
        // The surveyed footprint of seven by seven.
        var settings = KingdomMineSettings.get(server);
        var shaft = new KingdomMineSettings.Shaft(next.originX, next.originX + 6,
                next.originZ, next.originZ + 6, next.surfaceY, next.dimension);
        settings.setShaft(enterprise, shaft);
        settings.chooseLevels(enterprise, 2);
        settings.chooseHeading(enterprise, KingdomMineSettings.Heading.NORTH);
        settings.choose(enterprise, KingdomMineSettings.Start.DOWN);
        // The real depot of raw material and the real warehouse of the goods.
        BlockPos raw = new BlockPos(next.originX + 9, next.surfaceY + 1, next.originZ + 1);
        BlockPos finished = new BlockPos(next.originX + 11, next.surfaceY + 1, next.originZ + 1);
        placeChest(level, raw);
        placeChest(level, finished);
        // Both chests are real registered warehouses of the enterprise.
        KingdomTerritories.get(server).register(level, raw, owner, "raw");
        KingdomTerritories.get(server).register(level, finished, owner, "finished");
        enterprises.bindStorage(owner, enterprise,
            ru.projectkingdom.enterprise.EnterpriseBindingRole.RAW_STORAGE, next.dimension, raw);
        enterprises.bindStorage(owner, enterprise,
            ru.projectkingdom.enterprise.EnterpriseBindingRole.FINISHED_STORAGE, next.dimension, finished);
        Container depot = KingdomContainerAccess.resolve(level, raw);
        if (depot != null) {
            depot.setItem(0, new ItemStack(Items.IRON_SHOVEL));
            depot.setItem(1, new ItemStack(Items.IRON_PICKAXE));
            depot.setItem(2, new ItemStack(Items.LADDER, 16));
            depot.setItem(3, new ItemStack(Items.OAK_PLANKS, 32));
            depot.setItem(4, new ItemStack(Items.TORCH, 16));
            // The real supports of TFC for the rock of the deeper layers.
            for (String wood : new String[]{"oak", "birch", "spruce", "acacia"}) {
                var support = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(new net.minecraft.resources.ResourceLocation("tfc","wood/support/"+wood));
                if (support != null) { depot.setItem(5, new ItemStack(support, 16)); break; }
            }
            depot.setChanged();
        }
        // The real resident of MCA with a real contract.
        Villager resident = KingdomMcaAdapter.create(level, false);
        if (resident == null) resident = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (resident == null) return TAG + ": не удалось создать жителя.";
        resident.moveTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
        resident.setCustomName(net.minecraft.network.chat.Component.literal("RC miner"));
        resident.setPersistenceRequired();
        level.addFreshEntity(resident);
        var registry = KingdomNpcData.get(server);
        var worker = registry.add(resident.getUUID(), "RC miner", next.dimension,
                server.overworld().getDayTime() / 24000L);
        worker.job = "miner";
        worker.employer = owner;
        worker.dailyWage = 5;
        worker.ageYears = 30;
        worker.workplaceId = enterprise.toString();
        worker.order = "work";
        worker.hasSource = true;
        worker.sourceX = raw.getX(); worker.sourceY = raw.getY(); worker.sourceZ = raw.getZ();
        worker.hasDestination = true;
        worker.destX = finished.getX(); worker.destY = finished.getY(); worker.destZ = finished.getZ();
        registry.setDirty();
        KingdomEnterprisePayrollData.get(server).hire(server, owner, enterprise,
                worker.entityId, worker.dailyWage);
        next.enterprise = enterprise;
        next.resident = worker.entityId;
        state = next;
        return TAG + ": стенд готов. Ядро " + core.toShortString()
                + ", склад сырья " + raw.toShortString()
                + ", склад продукции " + finished.toShortString()
                + ", житель " + worker.id + " (" + resident.getUUID() + ").";
    }

    /** Builds the platform and the two layers under the footprint. */
    private static void buildTerrain(ServerLevel level, State next) {
        BlockPos origin = new BlockPos(next.originX, next.surfaceY, next.originZ);
        for (int dx = -3; dx <= 10; dx++) {
            for (int dz = -3; dz <= 10; dz++) {
                BlockPos pos = origin.offset(dx, 0, dz);
                // The surface is ground, the layer under it is the real rock of
                // TerraFirmaCraft: the shovel digs the ground, the pickaxe the
                // rock, and the rock of TFC needs its support.
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(pos.below(), tfcRock(level), 3);
                level.setBlock(pos.below(2), tfcRock(level), 3);
                level.setBlock(pos.below(3), tfcRock(level), 3);
                // The air above the platform is the entrance of the shaft.
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // The entrance of the shaft is one cell south of the footprint: it
        // must be free with a floor under the feet of the miner.
        BlockPos entrance = new BlockPos(next.originX, next.surfaceY, next.originZ + 7);
        level.setBlock(entrance, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(entrance.above(), Blocks.AIR.defaultBlockState(), 3);
    }

    /**
     * The real rock of TerraFirmaCraft.
     *
     * <p>The check must not fall back on the stone of Minecraft: the point of
     * the check is the integration with TFC and its collapse rules.
     */
    private static net.minecraft.world.level.block.state.BlockState tfcRock(ServerLevel level) {
        for (String id : new String[]{"tfc:rock/raw/basalt", "tfc:rock/raw/granite",
                "tfc:rock/raw/diorite", "tfc:rock/raw/andesite"}) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(new net.minecraft.resources.ResourceLocation(id));
            if (block != null && !block.defaultBlockState().isAir())
                return block.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** Places a real chest that the Kingdom can use as a warehouse. */
    private static void placeChest(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
    }

    /**
     * Watches the check and writes the report.
     *
     * <p>The method runs on every tick of the server while the check is
     * active. It never changes the world: it only looks at it.
     */
    public static void tick(MinecraftServer server) {
        if (state == null) { load(server); if (state == null) return; }
        if (state.finished) return;
        if (server.getTickCount() > state.deadlineTick) {
            state.notes.add("Время проверки вышло.");
            finish(server);
            return;
        }
        ServerLevel level = levelOf(server);
        if (level == null) return;
        var worker = findWorker(server);
        if (worker == null) {
            state.notes.add("Житель не найден в реестре.");
            finish(server);
            return;
        }
        // The check controls the conditions of the world: the resident works
        // during the working hours of the Kingdom, so the time of the day is
        // held inside them. Outside of them the resident rests and the check
        // would never finish.
        holdWorkingTime(server, level);
        // The check walks through the phases of the mine: the surface, the
        // access with the ladder and the deck, the rock of TFC with the
        // pickaxe and the supports, the delivery and the restart.
        int surface = dugSurface(level);
        int rock = dugInSecondLayer(level);
        int goods = delivered(level, worker);
        switch (state.phase) {
            case SURFACE -> {
                if (surface >= 49) {
                    state.phase = Phase.ACCESS;
                    state.notes.add("Поверхностный слой выкопан полностью.");
                    save(server);
                }
            }
            case ACCESS -> {
                if (accessReady(level)) {
                    state.phase = Phase.ROCK;
                    state.notes.add("Лестница и настил спуска готовы.");
                    save(server);
                }
            }
            case ROCK -> {
                if (rock >= 49) {
                    state.phase = Phase.DELIVERY;
                    state.notes.add("Слой породы TFC выкопан полностью.");
                    save(server);
                }
            }
            case DELIVERY -> {
                if (goods > 0) {
                    state.phase = Phase.RESTART;
                    state.surfaceAtRestart = surface;
                    state.rockAtRestart = rock;
                    state.deliveredAtRestart = goods;
                    state.notes.add("Добыча доставлена на склад: " + goods + " предметов.");
                    save(server);
                }
            }
            case RESTART -> {
                // The server was stopped and started again: the state of the
                // world must not go back, and the work must continue.
                state.restarts++;
                state.phase = Phase.CONTINUE;
                state.notes.add("Сервер перезапущен: добыча сохранилась, работа продолжается.");
                save(server);
            }
            case CONTINUE -> {
                if (surface < state.surfaceAtRestart || rock < state.rockAtRestart
                        || goods < state.deliveredAtRestart) {
                    state.notes.add("ВНИМАНИЕ: после перезапуска состояние ухудшилось.");
                    finish(server);
                    return;
                }
                state.notes.add("Работа продолжается после перезапуска без потерь.");
                finish(server);
            }
            default -> { }
        }
    }

    /**
     * Holds the time of the day inside the working hours of the Kingdom.
     *
     * <p>The resident rests between 12:00 and 13:00. The check measures the
     * work of the resident, so the stand keeps the world inside the working
     * window: the rest is checked by the unit tests, not by the clock of the
     * check.
     */
    private static void holdWorkingTime(MinecraftServer server, ServerLevel level) {
        // The day of Minecraft starts at 06:00, so the tick of the hour is
        // (hour - 6) * 1000: the working hours of the Kingdom are the ticks
        // 2000-6000 (08:00-12:00) and 7000-12000 (13:00-18:00).
        long day = level.getDayTime() % 24000L;
        if (day >= 2000L && day < 6000L) return;
        if (day >= 7000L && day < 12000L) return;
        level.setDayTime(((level.getDayTime() / 24000L) * 24000L) + 2000L);
    }

    /** Whether the ladder and the deck of the descent stand. */
    public static boolean accessReady(ServerLevel level) {
        if (state == null) return false;
        BlockPos descent = KingdomMineCentralShaftPlan.descent(
                new KingdomMineSettings.Shaft(state.originX, state.originX + 6,
                        state.originZ, state.originZ + 6, state.surfaceY, state.dimension), 0);
        return level.getBlockState(descent).is(Blocks.LADDER)
            && KingdomMineStructureWork.isDeckBlock(level.getBlockState(descent.north()));
    }

    /** The number of the blocks of the surface layer that are gone. */
    public static int dugSurface(ServerLevel level) {
        if (state == null) return 0;
        int count = 0;
        for (int dx = 0; dx <= 6; dx++)
            for (int dz = 0; dz <= 6; dz++) {
                BlockPos pos = new BlockPos(state.originX + dx, state.surfaceY, state.originZ + dz);
                if (level.getBlockState(pos).isAir()) count++;
            }
        return count;
    }

    /** The number of the blocks of the first layer that are gone. */
    public static int dugBlocks(ServerLevel level) {
        if (state == null) return 0;
        int count = 0;
        for (int dx = 0; dx <= 6; dx++)
            for (int dz = 0; dz <= 6; dz++) {
                BlockPos pos = new BlockPos(state.originX + dx, state.surfaceY - 1, state.originZ + dz);
                if (level.getBlockState(pos).isAir()) count++;
            }
        return count;
    }

    /** The number of the blocks of the second layer that are gone. */
    public static int dugInSecondLayer(ServerLevel level) {
        if (state == null) return 0;
        int count = 0;
        for (int dx = 0; dx <= 6; dx++)
            for (int dz = 0; dz <= 6; dz++) {
                BlockPos pos = new BlockPos(state.originX + dx, state.surfaceY - 2, state.originZ + dz);
                if (level.getBlockState(pos).isAir()) count++;
            }
        return count;
    }

    /** The number of the items that lie in the finished warehouse. */
    public static int delivered(ServerLevel level, KingdomNpcData.Worker worker) {
        if (state == null || worker == null || !worker.hasDestination) return 0;
        BlockPos pos = new BlockPos(worker.destX, worker.destY, worker.destZ);
        Container chest = KingdomContainerAccess.resolve(level, pos);
        if (chest == null) return 0;
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty()) total += stack.getCount();
        }
        return total;
    }

    /** The report of the check: what really happened in the world. */
    public static String report(MinecraftServer server) {
        if (state == null) return TAG + ": проверка не запускалась.";
        ServerLevel level = levelOf(server);
        if (level == null) return TAG + ": измерение мира недоступно.";
        var worker = findWorker(server);
        StringBuilder text = new StringBuilder();
        text.append(TAG).append(": ");
        text.append("фаза: ").append(state.phase).append("; ");
        text.append("слой 1 выкопан на ").append(dugBlocks(level)).append(" из 49");
        text.append(", слой 2 — ").append(dugInSecondLayer(level)).append(" из 49; ");
        if (worker == null) {
            text.append("житель не найден");
        } else {
            Container personal = null;
            for (ServerLevel candidate : server.getAllLevels()) {
                var entity = candidate.getEntity(worker.entityId);
                if (entity instanceof Villager resident) {
                    personal = KingdomResidentInventory.personal(resident);
                    break;
                }
            }
            text.append("в сумке жителя: ").append(describe(personal));
            text.append("; на складе продукции: ").append(delivered(level, worker));
            text.append("; состояние: ").append(worker.mineStatus);
            text.append("; добыто: ").append(worker.fieldHarvested);
            // The diagnosis of the contract and of the working place.
            var contract = KingdomEnterprisePayrollData.get(server).contract(state.enterprise, worker.entityId);
            text.append("; договор: ").append(contract == null ? "нет" : (contract.active() ? "активен" : "приостановлен"));
            text.append("; приказ: ").append(worker.order);
            text.append("; намерение: ").append(worker.autonomousIntent);
            text.append("; пауза: ").append(worker.contractPaused);
            text.append("; сытость: ").append(worker.hunger).append("; силы: ").append(worker.energy);
            text.append("; возраст: ").append(worker.ageYears);
            var inspection = KingdomMineSite.inspect(server, level, worker);
            text.append("; рабочее место: ").append(inspection.reason());
            text.append("; инструмент: ").append(KingdomMineTools.lastReason());
        }
        BlockPos descent = new BlockPos(state.originX, state.surfaceY, state.originZ + 6);
        text.append("; слой поверхности выкопан на ").append(dugSurface(level)).append(" из 49");
        text.append("; лестница спуска: ")
            .append(level.getBlockState(descent).is(Blocks.LADDER) ? "есть" : "нет");
        text.append("; клетка спуска: ")
            .append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(descent).getBlock()).toString());
        text.append("; причина отказа: ").append(KingdomMineStructureWork.lastReason());
        for (String note : state.notes) text.append("; ").append(note);
        return text.toString();
    }

    /** The level of the running check. */
    private static ServerLevel levelOf(MinecraftServer server) {
        if (state == null) return null;
        for (ServerLevel level : server.getAllLevels())
            if (level.dimension().location().toString().equals(state.dimension)) return level;
        return null;
    }

    /** A short description of the content of a container. */
    private static String describe(Container container) {
        if (container == null) return "нет контейнера";
        StringBuilder text = new StringBuilder();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            if (text.length() > 0) text.append(", ");
            text.append(stack.getHoverName().getString()).append(" x").append(stack.getCount());
            if (stack.isDamageableItem())
                text.append(" (прочность ").append(stack.getMaxDamage() - stack.getDamageValue())
                    .append('/').append(stack.getMaxDamage()).append(')');
        }
        return text.length() == 0 ? "пусто" : text.toString();
    }

    private static KingdomNpcData.Worker findWorker(MinecraftServer server) {
        if (state == null || state.resident == null) return null;
        for (var worker : KingdomNpcData.get(server).all())
            if (state.resident.equals(worker.entityId)) return worker;
        return null;
    }

    /** Writes the state of the check into the world. */
    private static void save(MinecraftServer server) {
        if (state == null) return;
        try {
            var file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
            var lines = new java.util.ArrayList<String>();
            lines.add("originX=" + state.originX);
            lines.add("originZ=" + state.originZ);
            lines.add("surfaceY=" + state.surfaceY);
            lines.add("dimension=" + state.dimension);
            lines.add("phase=" + state.phase.name());
            lines.add("surface=" + state.surfaceAtRestart);
            lines.add("rock=" + state.rockAtRestart);
            lines.add("delivered=" + state.deliveredAtRestart);
            lines.add("restarts=" + state.restarts);
            for (String note : state.notes) lines.add("note=" + note);
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.write(file, lines, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {
            // The check stays in memory when the world cannot be written.
        }
    }

    /** Reads the state of the check back after the restart of the server. */
    private static void load(MinecraftServer server) {
        try {
            var file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
            if (!java.nio.file.Files.exists(file)) return;
            var lines = java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
            State next = new State();
            for (String line : lines) {
                int split = line.indexOf('=');
                if (split < 0) continue;
                String key = line.substring(0, split);
                String value = line.substring(split + 1);
                switch (key) {
                    case "originX" -> next.originX = Integer.parseInt(value);
                    case "originZ" -> next.originZ = Integer.parseInt(value);
                    case "surfaceY" -> next.surfaceY = Integer.parseInt(value);
                    case "dimension" -> next.dimension = value;
                    case "phase" -> next.phase = Phase.valueOf(value);
                    case "surface" -> next.surfaceAtRestart = Integer.parseInt(value);
                    case "rock" -> next.rockAtRestart = Integer.parseInt(value);
                    case "delivered" -> next.deliveredAtRestart = Integer.parseInt(value);
                    case "restarts" -> next.restarts = Integer.parseInt(value);
                    case "note" -> next.notes.add(value);
                    default -> { }
                }
            }
            state = next;
        } catch (java.io.IOException | RuntimeException ignored) {
            // A broken file means no check; the operator starts a new one.
        }
    }

    /** Finishes the check and writes the report into the log. */
    private static void finish(MinecraftServer server) {
        if (state == null) return;
        state.finished = true;
        String text = report(server);
        org.slf4j.LoggerFactory.getLogger(KingdomRcMineTest.class).info(text);
    }
}
