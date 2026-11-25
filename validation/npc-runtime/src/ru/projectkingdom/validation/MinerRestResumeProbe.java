package ru.projectkingdom.validation;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import ru.projectkingdom.npc.*;

/** Observe a saved production miner leave the shaft, sleep, recover and resume cutting. */
public final class MinerRestResumeProbe {
    private static MinecraftServer server;
    private static ServerLevel level;
    private static Path root;
    private static KingdomNpcData.Worker worker;
    private static Villager resident;
    private static Schedule schedule;
    private static final BlockPos RAW = new BlockPos(37, 200, 45);
    private static final BlockPos FINISHED = new BlockPos(41, 200, 45);
    private static final BlockPos HEAD = new BlockPos(34, 200, 49);
    private static Container raw, finished;
    private static Item loam;
    private static int ticks, phase, baselineCuts, fatigueCuts, baselineCargo, sleepingTicks;
    private static long workTick;
    private static boolean done, evacuated, surfaced, gainedWhileSleeping;

    public static void boot(Object value) {
        server = (MinecraftServer) value;
        server.execute(() -> {
            try {
                root = server.getWorldPath(LevelResource.ROOT).toRealPath();
                require(root.equals(Path.of(System.getProperty("npc.runtime.allowedWorld")).toRealPath()), "exact copied disposable world");
                require(Files.readString(root.resolve(".npc-runtime-validation-world")).trim()
                        .equals("disposable-npc-runtime-validation-v1"), "disposable world token");
                require(Files.exists(root.resolve("npc-autonomous-PASS")), "completed autonomous soil acceptance world required");
                level = server.overworld();
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
                    level.setChunkForced(x, z, true);
                    level.getChunkAt(new BlockPos(x * 16, 200, z * 16));
                }
                worker = KingdomNpcData.get(server).all().stream()
                        .filter(w -> "miner".equals(w.job) && "Autonomous miner".equals(w.name)).findFirst().orElseThrow();
                require(KingdomNpcWorkday.employed(server, worker), "saved original miner contract retained");
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                        TickEvent.ServerTickEvent.class, MinerRestResumeProbe::beforeTick);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                        TickEvent.ServerTickEvent.class, MinerRestResumeProbe::observe);
            } catch (Throwable failure) { fail(failure); }
        });
    }

    private static void beforeTick(TickEvent.ServerTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.START && resident != null) {
            try { TfcClock.set(server, workTick); }
            catch (Throwable failure) { fail(failure); }
        }
    }

    private static void observe(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            require(++ticks <= 5000, "5000-tick miner rest bound");
            if (phase == 0) {
                if (!(level.getEntity(worker.entityId) instanceof Villager found)) {
                    require(ticks < 200, "saved original miner entity must load");
                    return;
                }
                resident = found;
                require(KingdomMcaAdapter.isResident(resident) && resident.isAlive(), "original live MCA miner");
                require(!resident.isNoAi(), "original production miner AI remains enabled");
                schedule = resident.getBrain().getSchedule();
                boolean workFound = false;
                for (int time = 0; time < 24000; time += 100)
                    if (schedule.getActivityAt(time) == Activity.WORK) { workTick = time; workFound = true; break; }
                require(workFound, "original individual's native schedule has WORK");
                TfcClock.set(server, workTick);
                raw = (Container) level.getBlockEntity(RAW);
                finished = (Container) level.getBlockEntity(FINISHED);
                require(raw != null && finished != null, "original warehouses loaded");
                loam = BuiltInRegistries.ITEM.get(new ResourceLocation("tfc:dirt/loam"));
                baselineCuts = cuts();
                baselineCargo = cargo();
                require(baselineCuts >= 16 && baselineCuts < 49, "completed soil world has second-layer work remaining");
                buildHouse();
                phase = 1;
                pass("original saved MCA miner contract, warehouses and actual WORK schedule loaded; second cuts=" + baselineCuts);
                return;
            }
            require(resident.isAlive(), "original miner survives");
            require(resident.getBrain().getSchedule() == schedule, "original individual schedule stays live");
            require(cargo() >= baselineCargo, "physical soil cargo conserved across legitimate delivery");
            if (ticks % 200 == 0) System.out.println("[NPC-RUNTIME] PROGRESS miner-rest phase=" + phase
                    + " tick=" + ticks + " pos=" + resident.position() + " energy=" + worker.energy
                    + " cuts=" + cuts() + " rest=" + KingdomResidentRestController.status(resident.getUUID())
                    + " mode=" + KingdomResidentSchedule.modeOf(server, resident.getUUID(), worker)
                    + " mine=" + worker.mineStatus);
            if (phase == 1) {
                if (cuts() <= baselineCuts || resident.getY() >= 199.5) return;
                var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(
                        "7c320771-2355-4c3d-88b7-e006f3500482"), "MinerBedFixture"));
                player.setPos(35.5, 200, 49.5); // Only the fixture player moves; never move the miner.
                var home = KingdomResidentHome.assign(player, worker);
                require(home.assigned(), "production HOME bridge: " + home.message());
                require(worker.hasHome && worker.homeX == HEAD.getX() && worker.homeY == HEAD.getY()
                        && worker.homeZ == HEAD.getZ(), "native bed registered and Kingdom coordinates synchronized");
                fatigueCuts = cuts();
                worker.energy = 25; // Explicit fatigue trigger; all later recovery is observed production behavior.
                worker.fatigueRestRequired = false;
                pass("new production cut at second layer Y198 observed; native bed assigned; fatigue25 triggered at " + resident.position());
                phase = 2;
                return;
            }
            if (phase == 2) {
                var mode = KingdomResidentSchedule.modeOf(server, resident.getUUID(), worker);
                evacuated |= mode == KingdomResidentSchedule.Mode.EVACUATING;
                surfaced |= evacuated && resident.getY() >= 200;
                if (resident.isSleeping()) sleepingTicks++;
                gainedWhileSleeping |= resident.isSleeping() && worker.energy > 25;
                if (worker.energy < KingdomResidentRestController.RESUME_ENERGY) {
                    require(cuts() == fatigueCuts, "production cutting stops throughout fatigue/evacuation/rest");
                    require(!KingdomResidentSchedule.working(mode), "fatigue never selects productive work");
                    if (mode != KingdomResidentSchedule.Mode.EVACUATING)
                        require(!KingdomMcaShiftArbiter.onShift(resident.getUUID()), "native REST owns no navigation lease");
                    return;
                }
                require(evacuated, "production EVACUATING attempted shaft exit");
                require(surfaced, "original miner physically reached surface without teleport");
                require(sleepingTicks >= 100 && gainedWhileSleeping, "actual registered-bed sleep recovered energy");
                require(!worker.fatigueRestRequired, "sleep recovery clears persistent fatigue latch");
                pass("physical shaft exit, native bed sleep and25->40 recovery observed; sleepingTicks=" + sleepingTicks);
                phase = 3;
            }
            if (phase == 3 && cuts() > fatigueCuts) {
                require(!resident.isSleeping() && KingdomResidentSchedule.working(
                        KingdomResidentSchedule.modeOf(server, resident.getUUID(), worker)), "actual scheduled work resumes after native wake");
                require(KingdomMcaShiftArbiter.onShift(resident.getUUID()), "resumed productive work has exclusive lease");
                require(KingdomNpcWorkday.employed(server, worker), "same original payroll contract retained");
                require(KingdomResidentInventory.count(raw, Items.DIAMOND_SHOVEL)
                        + KingdomResidentInventory.count(finished, Items.DIAMOND_SHOVEL)
                        + KingdomResidentInventory.count(KingdomMcaAdapter.personal(resident), Items.DIAMOND_SHOVEL)
                        + (worker.fieldTool.is(Items.DIAMOND_SHOVEL) ? 1 : 0) >= 1, "physical shovel retained with legitimate wear");
                pass("same miner resumed another production Y198 cut; physical cargo monotonic and real tool retained");
                Files.writeString(root.resolve("npc-miner-rest-resume-PASS"), "PASS before=" + baselineCuts
                        + " fatigue=" + fatigueCuts + " after=" + cuts() + " sleepTicks=" + sleepingTicks
                        + " cargoBefore=" + baselineCargo + " cargoAfter=" + cargo() + "\n");
                done = true;
                server.halt(false);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void buildHouse() {
        // Safe housing south of the surveyed7x7, never changing the shaft or its route.
        for (int x = 32; x <= 36; x++) for (int z = 47; z <= 51; z++) for (int y = 199; y <= 203; y++) {
            boolean shell = y == 199 || y == 203 || x == 32 || x == 36 || z == 47 || z == 51;
            boolean doorway = x == 34 && z == 47 && (y == 200 || y == 201);
            level.setBlock(new BlockPos(x, y, z), shell && !doorway ? Blocks.OAK_PLANKS.defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), 3);
        }
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(HEAD.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(HEAD, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
    }

    private static int cuts() {
        int count = 0;
        for (int x = 36; x <= 42; x++) for (int z = 36; z <= 42; z++)
            if (!level.getBlockState(new BlockPos(x, 198, z)).is(BuiltInRegistries.BLOCK.get(
                    new ResourceLocation("tfc:dirt/loam")))) count++;
        return count;
    }
    private static int cargo() {
        return KingdomResidentInventory.count(raw, loam) + KingdomResidentInventory.count(finished, loam)
                + KingdomResidentInventory.count(KingdomMcaAdapter.personal(resident), loam);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void pass(String message) { System.out.println("[NPC-RUNTIME] PASS " + message); }
    private static void fail(Throwable failure) {
        done = true;
        System.err.println("[NPC-RUNTIME] FAIL miner-rest phase=" + phase + " tick=" + ticks
                + " pos=" + (resident == null ? "unloaded" : resident.position()) + " energy="
                + (worker == null ? -1 : worker.energy) + " rest=" + (resident == null ? "none"
                : KingdomResidentRestController.status(resident.getUUID())));
        failure.printStackTrace();
        Runtime.getRuntime().halt(32);
    }
}
