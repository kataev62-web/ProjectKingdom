package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * Mixed NPC economy. Personal career ambition is NOT a land title, wage
 * contract, rental agreement or money balance.
 *
 * All money used by this policy already exists in a registered NPC's physical
 * wage purse. A future lease adapter must settle the actual payment and grant
 * temporary permissions before declaring an NPC a tenant.
 */
public final class KingdomNpcCareerPolicy {
    private KingdomNpcCareerPolicy() {}

    /** Starter balance target, not a rent price or an entitlement to any property. */
    public static final long START_CAPITAL_GOAL_COPPER = 1_000L;
    /** Deterministic initial preference. No dependence on iteration order or game ticks. */
    public static final int ENTREPRENEUR_PERCENT = 40;

    public enum Path {
        EMPLOYEE("Предпочитает работу по найму"),
        ENTREPRENEUR("Стремится открыть собственное дело");

        private final String title;
        Path(String title) { this.title = title; }
        public String title() { return title; }
    }

    public enum Stage {
        TOO_YOUNG("Пока учится и взрослеет"),
        EMPLOYED("Предпочитает стабильную работу"),
        SEEKING_WORK("Ищет оплачиваемую работу"),
        SAVING("Работает и копит на своё дело"),
        READY_FOR_OFFERS("Накопил начальный капитал; ждёт предложения аренды"),
        OPERATING("Управляет действующим предприятием"),
        UNPAID("Не работает: задолженность по зарплате");

        private final String title;
        Stage(String title) { this.title = title; }
        public String title() { return title; }
    }

    public record Career(Path path, Stage stage, long physicalSavings, long initialGoal) {}

    /** Create a stable initial trait for existing and newly registered villagers. */
    public static Path initial(UUID entityId) {
        if (entityId == null) return Path.EMPLOYEE;
        return Math.floorMod(entityId.hashCode(), 100) < ENTREPRENEUR_PERCENT
                ? Path.ENTREPRENEUR : Path.EMPLOYEE;
    }

    /** Read old save data safely without changing the character on each reload. */
    public static Path parse(String persisted, UUID entityId) {
        if (persisted != null) {
            try { return Path.valueOf(persisted); }
            catch (IllegalArgumentException ignored) { /* migrate from old worlds */ }
        }
        return initial(entityId);
    }

    /** Real coins only, never earned totals, virtual balances or unpaid wages. */
    public static long physicalSavings(KingdomNpcData.Worker worker) {
        if (worker == null) return 0;
        long sum = 0;
        for (ItemStack stack : worker.wagePurse) {
            if (stack == null || stack.isEmpty()) continue;
            long denomination = KingdomCurrency.value(stack);
            if (denomination <= 0) continue;
            try { sum = Math.addExact(sum, Math.multiplyExact(denomination, stack.getCount())); }
            catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
        }
        return sum;
    }

    public static Stage stage(Path path, int ageYears, boolean contractPaused,
                              boolean employed, boolean operatesBusiness, long physicalSavings) {
        if (ageYears < 18) return Stage.TOO_YOUNG;
        if (operatesBusiness) return Stage.OPERATING;
        if (contractPaused) return Stage.UNPAID;
        if (path == Path.ENTREPRENEUR) {
            return physicalSavings >= START_CAPITAL_GOAL_COPPER
                    ? Stage.READY_FOR_OFFERS : Stage.SAVING;
        }
        return employed ? Stage.EMPLOYED : Stage.SEEKING_WORK;
    }

    public static Career inspect(KingdomNpcData.Worker worker,
                                 KingdomEnterpriseData businesses) {
        if (worker == null) return new Career(Path.EMPLOYEE, Stage.SEEKING_WORK, 0,
                START_CAPITAL_GOAL_COPPER);
        boolean operates = false;
        if (businesses != null) {
            for (var enterprise : businesses.ownedAll(worker.entityId)) {
                var core = businesses.core(enterprise.id());
                if (core != null && core.active()) { operates = true; break; }
            }
        }
        long saved = physicalSavings(worker);
        var state = stage(worker.careerPath,worker.ageYears,worker.contractPaused,
                worker.employer != null,operates,saved);
        return new Career(worker.careerPath,state,saved,START_CAPITAL_GOAL_COPPER);
    }
}
