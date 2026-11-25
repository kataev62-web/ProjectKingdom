package ru.projectkingdom.validation;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import ru.projectkingdom.npc.*;

/** Real MCA schedules, public player orders, native bed sleep and observed recovery. */
public final class McaIndividualLifeProbe {
    private static MinecraftServer server;
    private static ServerLevel level;
    private static Path root;
    private static Villager day, night;
    private static KingdomNpcData.Worker worker;
    private static Schedule daySchedule, nightSchedule;
    private static int ticks, phase, phaseTick, sleepingTicks;
    private static boolean finished, recoveredWhileSleeping;
    private static final BlockPos HEAD = new BlockPos(8, 200, 8);

    public static void boot(Object value) {
        server = (MinecraftServer) value;
        server.execute(() -> {
            try {
                root = server.getWorldPath(LevelResource.ROOT).toRealPath();
                require(root.equals(Path.of(System.getProperty("npc.runtime.allowedWorld")).toRealPath()), "isolated exact world");
                require(Files.readString(root.resolve(".npc-runtime-validation-world")).trim()
                        .equals("disposable-npc-runtime-validation-v1"), "disposable world token");
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                        TickEvent.ServerTickEvent.class, McaIndividualLifeProbe::tick);
            } catch (Throwable failure) { fail(failure); }
        });
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END) return;
        try {
            require(++ticks <= 3000, "bounded native rest probe; status=" + (day == null ? "none"
                    : KingdomResidentRestController.status(day.getUUID())) + " energy=" + (worker == null ? -1 : worker.energy));
            if (phase == 0) { initialize(); phase = 1; phaseTick = ticks; return; }
            if (phase == 1) {
                // Let the real world register bed POIs before using MCA's public setHome command.
                if (ticks - phaseTick < 5) return;
                schedulesAndOrders();
                ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(
                        "75dc6b8b-e11f-4129-8dc8-b0323dfbfa17"), "McaLifeFixture"));
                player.setPos(8.5, 200, 10.5);
                var assigned = KingdomResidentHome.assign(player,worker);
                require(assigned.assigned(), "production home assignment: " + assigned.message());
                var home = day.getBrain().getMemory(MemoryModuleType.HOME);
                require(home.isPresent() && home.get().pos().equals(HEAD), "MCA Residency.setHome claimed real bed HEAD");
                require(level.getPoiManager().getType(HEAD).isPresent(), "native bed POI exists");
                require(KingdomResidentRestController.inspectBed(level, day)
                        == KingdomResidentRestController.Status.SEEKING_BED, "registered native bed pair valid");
                day.getBrain().setSchedule(daySchedule);
                day.setNoAi(false);
                day.setNoGravity(false);
                day.setPos(8.5, 200, 10.5);
                worker.energy = KingdomResidentRestController.CRITICAL_ENERGY;
                worker.fatigueRestRequired = false;
                TfcClock.set(server, 3000); // Actual daytime WORK, critical fatigue must override it.
                KingdomNpcMod.arbitrateResidentNavigation(server);
                require(KingdomResidentSchedule.modeOf(server, day.getUUID(), worker)
                        == KingdomResidentSchedule.Mode.KINGDOM_REST, "critical energy selects REST during individual WORK");
                require(!KingdomMcaShiftArbiter.onShift(day.getUUID()), "fatigue REST owns no lease");
                require(KingdomResidentRestController.requiresRestControl(day), "native fatigue REST is held without movement lease");
                if (net.minecraftforge.fml.ModList.get().isLoaded("townstead")) {
                    ClassLoader loader = day.getClass().getClassLoader();
                    Class<?> mca = Class.forName("forge.net.conczin.mca.entity.VillagerEntityMCA",false,loader);
                    var dispatcher = Class.forName("com.aetherianartificer.townstead.tick.VillagerServerTickDispatcher",true,loader)
                        .getMethod("tick",mca);
                    long before = ru.projectkingdom.npc.compat.TownsteadNpcCompatibility.deferredTicks();
                    dispatcher.invoke(null,day);
                    require(ru.projectkingdom.npc.compat.TownsteadNpcCompatibility.deferredTicks() == before + 1,
                        "actual Townstead dispatcher yields native fatigue REST without Kingdom movement lease");
                }
                require(day.getBrain().getSchedule() == daySchedule, "fatigue does not replace schedule");
                pass("real MCA Residency bed registered; critical daytime fatigue releases work lease");
                phase = 2; phaseTick = ticks;
                return;
            }
            if (phase >= 2) TfcClock.set(server, 3000);
            if (phase == 2) {
                require(day.getBrain().getSchedule() == daySchedule, "native schedule reference retained during sleep");
                if (day.isSleeping()) sleepingTicks++;
                if (day.isSleeping() && worker.energy > KingdomResidentRestController.CRITICAL_ENERGY)
                    recoveredWhileSleeping = true;
                if (worker.energy < KingdomResidentRestController.RESUME_ENERGY) {
                    require(!KingdomMcaShiftArbiter.onShift(day.getUUID()), "no lease before fatigue recovery");
                    require(worker.fatigueRestRequired, "fatigue hysteresis remains latched");
                    if (ticks - phaseTick == 600) pass("rest progress status="
                            + KingdomResidentRestController.status(day.getUUID()) + " energy=" + worker.energy
                            + " actualSleepingTicks=" + sleepingTicks);
                    return;
                }
                require(sleepingTicks >= 100 && recoveredWhileSleeping, "observed real sleeping and energy gain");
                require(!worker.fatigueRestRequired, "observed recovery clears fatigue latch");
                pass("native bed sleep recovered energy to " + worker.energy + " over " + sleepingTicks + " actual sleeping ticks");
                phase = 3; phaseTick = ticks;
                return;
            }
            if (phase == 3) {
                if (day.isSleeping()) return; // Native wake task, never probe stopSleeping().
                if (!KingdomMcaShiftArbiter.onShift(day.getUUID())) return;
                require(KingdomResidentSchedule.working(KingdomResidentSchedule.modeOf(server, day.getUUID(), worker)),
                        "native wake resumes actual scheduled WORK");
                require(day.getBrain().getSchedule() == daySchedule, "same individual schedule after recovery");
                pass("native wake resumed Kingdom work lease without replacing native schedule");
                Files.writeString(root.resolve("npc-individual-life-PASS"), "PASS actual MCA individual day/night controls, registered bed, sleep, recovery and work resumption\n");
                finished = true;
                server.halt(false);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void initialize() throws Exception {
        level = server.overworld();
        level.setChunkForced(0, 0, true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        TfcClock.set(server, 3000);
        for (int x = 3; x <= 14; x++) for (int z = 3; z <= 14; z++) for (int y = 199; y <= 204; y++)
            level.setBlock(new BlockPos(x, y, z), y == 199 || y == 204 || x == 3 || x == 14 || z == 3 || z == 14
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(HEAD.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(HEAD, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        day = fixture(8.5, 10.5);
        night = fixture(12.5, 12.5);
        Class<?> schedules = Class.forName("forge.net.conczin.mca.entity.ai.SchedulesMCA", true, day.getClass().getClassLoader());
        daySchedule = (Schedule) schedules.getField("DEFAULT").get(null);
        nightSchedule = (Schedule) schedules.getField("NIGHT_OWL_DEFAULT").get(null);
        day.getBrain().setSchedule(daySchedule);
        night.getBrain().setSchedule(nightSchedule);
        worker = KingdomNpcData.get(server).add(day.getUUID(), "Individual MCA life fixture", level.dimension().location().toString(), 0);
        worker.job = "farmer"; worker.order = "work"; worker.hunger = 100; worker.safety = 100;
    }

    private static Villager fixture(double x, double z) {
        Villager villager = KingdomMcaAdapter.create(level, false);
        require(villager != null, "actual MCA fixture created");
        villager.setAge(0); villager.setNoAi(true); villager.setNoGravity(true);
        villager.setPersistenceRequired(); villager.setPos(x, 200, z);
        require(level.addFreshEntity(villager), "actual MCA fixture added");
        villager.refreshBrain(level);
        return villager;
    }

    private static void schedulesAndOrders() throws Exception {
        for (long time : new long[] { 3000, 16000, 9500, 13000 }) {
            TfcClock.set(server, time);
            require(KingdomMcaSchedule.inspect(day, daySchedule).workAllowed() == (time == 3000), "actual MCA day shift at " + time);
            require(KingdomMcaSchedule.inspect(night, nightSchedule).workAllowed() == (time == 16000), "actual MCA night shift at " + time);
        }
        TfcClock.set(server, 3000);
        KingdomNpcMod.arbitrateResidentNavigation(server);
        require(KingdomMcaShiftArbiter.onShift(day.getUUID()), "production arbitration grants actual day WORK");
        require(day.getBrain().getSchedule() == daySchedule, "lease leaves actual schedule live");
        day.getBrain().updateActivityFromSchedule(13000, level.getGameTime() + 100);
        require(day.getBrain().getSchedule() == daySchedule, "transformed native Brain preserves leased schedule");
        require(day.getBrain().isActive(Activity.CORE) && !day.getBrain().isActive(Activity.REST),
                "transformed Brain schedule update cannot reactivate REST during movement lease");
        Object helper = day.getClass().getMethod("getVillagerBrain").invoke(day);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(
                "75dc6b8b-e11f-4129-8dc8-b0323dfbfa17"), "McaLifeFixture"));
        Class<?> moveClass = Class.forName("forge.net.conczin.mca.entity.ai.MoveState", true, day.getClass().getClassLoader());
        for (String order : new String[] { "STAY", "FOLLOW", "MOVE" }) {
            Object move = moveClass.getField(order).get(null);
            helper.getClass().getMethod("setMoveState", moveClass, Player.class).invoke(helper, move, player);
            day.getBrain().setSchedule(daySchedule);
            KingdomNpcMod.arbitrateResidentNavigation(server);
            require(KingdomMcaSchedule.inspect(day, daySchedule).manualOverride() == !order.equals("MOVE"), "real MCA player " + order);
            if (!order.equals("MOVE")) require(!KingdomMcaShiftArbiter.onShift(day.getUUID()), "real player order releases lease");
        }
        Class<?> choreClass = Class.forName("forge.net.conczin.mca.entity.ai.Chore", true, day.getClass().getClassLoader());
        Object chore = java.util.Arrays.stream(choreClass.getEnumConstants()).filter(v -> !((Enum<?>) v).name().equals("NONE")).findFirst().orElseThrow();
        helper.getClass().getMethod("assignJob", choreClass, Player.class).invoke(helper, chore, player);
        KingdomNpcMod.arbitrateResidentNavigation(server);
        require(KingdomMcaSchedule.inspect(day, day.getBrain().getSchedule()).manualOverride(), "real MCA assigned chore read");
        require(!KingdomMcaShiftArbiter.onShift(day.getUUID()), "real MCA assigned chore keeps exclusive ownership");
        helper.getClass().getMethod("abandonJob").invoke(helper);
        day.getBrain().setSchedule(daySchedule);
        night.discard();
        pass("actual MCA day/night WORK gates, live schedule lease, STAY/FOLLOW and assigned chore overrides");
    }

    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void pass(String message) { System.out.println("[NPC-RUNTIME] PASS " + message); }
    private static void fail(Throwable error) {
        finished = true;
        System.err.println("[NPC-RUNTIME] FAIL individual-life phase=" + phase + " ticks=" + ticks);
        error.printStackTrace();
        Runtime.getRuntime().halt(32);
    }
}
