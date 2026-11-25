package ru.projectkingdom.npc.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.fml.ModList;
import ru.projectkingdom.npc.KingdomMcaAdapter;

/** Exact optional emergency-rest boundary for Townstead 0.8.0 / MCA 7.7.1. */
public final class TownsteadNpcCompatibility {
    private TownsteadNpcCompatibility() {}

    private static final LongAdder DEFERRED_TICKS = new LongAdder();

    /** In-memory integration diagnostic; no resident data or world state is persisted. */
    public static void recordDeferral() { DEFERRED_TICKS.increment(); }
    public static long deferredTicks() { return DEFERRED_TICKS.sum(); }

    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private record Access(Method collapse, Method state, Method needs, Method rest) {}
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> entityClass) {
            try {
                ClassLoader loader = entityClass.getClassLoader();
                Class<?> mca = Class.forName("forge.net.conczin.mca.entity.VillagerEntityMCA", false, loader);
                Class<?> collapse = Class.forName("com.aetherianartificer.townstead.performance.CollapsePlayback", false, loader);
                Class<?> villagers = Class.forName("com.aetherianartificer.townstead.villager.TownsteadVillagers", false, loader);
                Method state = villagers.getMethod("get", mca);
                Method needs = state.getReturnType().getMethod("needs");
                return new Access(collapse.getMethod("active", mca), state, needs,
                        needs.getReturnType().getMethod("restOverrideActive"));
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
                return new Access(null, null, null, null);
            }
        }
    };

    /** Unknown installed Townstead APIs prevent Kingdom from taking a work lease. */
    public static boolean mustRest(Villager resident) {
        if (resident == null || !KingdomMcaAdapter.isResident(resident)
                || !ModList.get().isLoaded("townstead")) return false;
        Access access = ACCESS.get(resident.getClass());
        if (access.collapse() == null) return true;
        try {
            if (Boolean.TRUE.equals(access.collapse().invoke(null, resident))) return true;
            Object state = access.state().invoke(null, resident);
            Object needs = access.needs().invoke(state);
            return Boolean.TRUE.equals(access.rest().invoke(needs));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return true;
        }
    }

    private static void warn(Throwable error) {
        if (WARNED.compareAndSet(false, true))
            LogUtils.getLogger().warn("Townstead emergency-rest API unavailable; Kingdom work leases are disabled", error);
    }
}
