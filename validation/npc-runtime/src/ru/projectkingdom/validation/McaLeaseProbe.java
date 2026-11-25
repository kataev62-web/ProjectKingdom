package ru.projectkingdom.validation;

import java.lang.reflect.Method;
import net.minecraftforge.fml.ModList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import ru.projectkingdom.npc.KingdomMcaAdapter;
import ru.projectkingdom.npc.KingdomMcaShiftArbiter;
import ru.projectkingdom.npc.compat.TownsteadNpcCompatibility;

/** Native MCA entity/brain lease checks; no mock brain or vanilla substitute. */
public final class McaLeaseProbe {
    public static void run(ServerLevel level) {
        var resident = KingdomMcaAdapter.create(level, false);
        require(resident != null && KingdomMcaAdapter.isResident(resident), "actual MCA lease fixture");
        require(KingdomMcaAdapter.personal(resident).getContainerSize() == 27,
                "actual MCA personal bag has 27 slots");
        require(KingdomMcaAdapter.family(resident) != null, "actual MCA family reflection resolves");
        Method townsteadTick = null;
        if (ModList.get().isLoaded("townstead")) {
            try {
                ClassLoader loader = resident.getClass().getClassLoader();
                Class<?> mca = Class.forName("forge.net.conczin.mca.entity.VillagerEntityMCA", false, loader);
                townsteadTick = Class.forName("com.aetherianartificer.townstead.tick.VillagerServerTickDispatcher",
                        true, loader).getMethod("tick", mca);
                long before = TownsteadNpcCompatibility.deferredTicks();
                invokeTownstead(townsteadTick, resident);
                require(TownsteadNpcCompatibility.deferredTicks() == before,
                        "Townstead dispatcher remains native before lease");
                require(!TownsteadNpcCompatibility.mustRest(resident), "Townstead lease fixture is rested");
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Townstead dispatcher public API", error);
            }
        }
        var brain = resident.getBrain();
        var schedule = brain.getSchedule();
        var job = GlobalPos.of(level.dimension(), new BlockPos(34, 200, 35));
        var home = GlobalPos.of(level.dimension(), new BlockPos(35, 200, 35));
        var meeting = GlobalPos.of(level.dimension(), new BlockPos(36, 200, 35));
        brain.setMemory(MemoryModuleType.JOB_SITE, job);
        brain.setMemory(MemoryModuleType.HOME, home);
        brain.setMemory(MemoryModuleType.MEETING_POINT, meeting);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPos(37, 200, 35), 1, 1));
        var lease = KingdomMcaShiftArbiter.begin(resident);
        require(lease != null && KingdomMcaShiftArbiter.onShift(resident.getUUID()), "actual MCA lease begins");
        require(brain.getMemory(MemoryModuleType.WALK_TARGET).isEmpty(), "MCA walk target leased");
        if (townsteadTick != null) {
            long before = TownsteadNpcCompatibility.deferredTicks();
            invokeTownstead(townsteadTick, resident);
            require(TownsteadNpcCompatibility.deferredTicks() == before + 1,
                    "actual transformed Townstead dispatcher defers leased resident");
            require(KingdomMcaShiftArbiter.onShift(resident.getUUID()), "rested Townstead lease retained");
        }
        require(brain.getMemory(MemoryModuleType.JOB_SITE).orElseThrow().equals(job)
                        && brain.getMemory(MemoryModuleType.HOME).orElseThrow().equals(home)
                        && brain.getMemory(MemoryModuleType.MEETING_POINT).orElseThrow().equals(meeting),
                "actual MCA job/home/meeting POIs survive begin");
        brain.setMemory(MemoryModuleType.HURT_BY, resident.damageSources().generic());
        brain.setActiveActivityIfPossible(Activity.PANIC);
        require(brain.isActive(Activity.PANIC), "actual MCA brain activates native PANIC");
        KingdomMcaShiftArbiter.hold(resident);
        require(brain.isActive(Activity.PANIC), "actual MCA active PANIC survives hold");
        KingdomMcaShiftArbiter.release(resident);
        require(brain.isActive(Activity.PANIC), "actual MCA active PANIC survives release");
        require(lease.released() && !KingdomMcaShiftArbiter.onShift(resident.getUUID())
                && brain.getSchedule() == schedule, "actual MCA original schedule restored");
        require(brain.getMemory(MemoryModuleType.JOB_SITE).orElseThrow().equals(job)
                        && brain.getMemory(MemoryModuleType.HOME).orElseThrow().equals(home)
                        && brain.getMemory(MemoryModuleType.MEETING_POINT).orElseThrow().equals(meeting),
                "actual MCA job/home/meeting POIs survive release");
        if (townsteadTick != null) {
            long before = TownsteadNpcCompatibility.deferredTicks();
            invokeTownstead(townsteadTick, resident);
            require(TownsteadNpcCompatibility.deferredTicks() == before,
                    "actual Townstead dispatcher resumes after lease release");
            System.out.println("[NPC-AUTONOMOUS] PASS actual Townstead dispatcher defers only during lease; MCA family and 27-slot bag verified");
        }
        System.out.println("[NPC-AUTONOMOUS] PASS actual MCA lease restores schedule, retains POIs and active PANIC");
    }

    private static void invokeTownstead(Method tick, Object resident) {
        try {
            tick.invoke(null, resident);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("actual Townstead dispatcher invocation", error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
