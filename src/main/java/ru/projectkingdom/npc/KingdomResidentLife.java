package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The life of the resident on its own.
 *
 * <p>The development of a resident is not a record that nobody acts on. Every
 * decision of this service uses the mechanics of the Kingdom: the contracts of
 * the enterprises, the real coins of the wage purse, the real chests of the
 * market and the real plots of the territory.
 *
 * <p>Nothing is created: a resident without money does not buy, a resident
 * without a contract does not work, a resident without a registered plot does
 * not build. When something is missing, the action is postponed and the
 * reason is written down for the operator.
 */
public final class KingdomResidentLife {
    private KingdomResidentLife() {}

    /** What the resident does on its own. */
    public enum Action {
        NONE,
        /** The resident asks the owner of an enterprise for a contract. */
        APPLY_FOR_VACANCY,
        /** The resident asks for another profession. */
        APPLY_FOR_PROFESSION,
        /** The resident buys something with its own money. */
        BUY,
        /** The resident proposes to open an enterprise of its own. */
        PROPOSE_ENTERPRISE
    }

    /** A vacant position of an enterprise. */
    public record Vacancy(UUID enterprise, String name, String job, long wage) {}

    /** The decision of the resident with the reason for the operator. */
    public record Decision(Action action, UUID enterprise, String job, String itemId,
                           int amount, long priceCopper, String reason) {
        public static Decision none(String reason) {
            return new Decision(Action.NONE, null, "", "", 0, 0, reason);
        }
    }

    /**
     * The vacant positions that the resident may take.
     *
     * <p>A position is vacant when the enterprise exists, its core is placed
     * and the resident has no contract with it yet.
     */
    public static List<Vacancy> vacancies(MinecraftServer server, KingdomNpcData.Worker worker) {
        List<Vacancy> found = new ArrayList<>();
        if (server == null || worker == null || worker.entityId == null) return List.of();
        var enterprises = KingdomEnterpriseData.get(server);
        var payroll = KingdomEnterprisePayrollData.get(server);
        for (var firm : enterprises.all()) {
            var kind = enterprises.kind(firm.id());
            if (kind == null) continue;
            var core = enterprises.core(firm.id());
            if (core == null || !core.active()) continue;
            if (payroll.contract(firm.id(), worker.entityId) != null) continue;
            String job = jobOf(kind.id());
            if (job == null) continue;
            found.add(new Vacancy(firm.id(), firm.name(), job, worker.dailyWage));
        }
        return List.copyOf(found);
    }

    /** The profession of the kind of an enterprise. */
    public static String jobOf(String enterpriseKind) {
        if (enterpriseKind == null) return null;
        return switch (enterpriseKind) {
            case "mine" -> "miner";
            case "farm" -> "farmer";
            case "forestry" -> "lumberjack";
            case "smith" -> "smith";
            case "construction" -> "builder";
            case "courier" -> "courier";
            default -> null;
        };
    }

    /**
     * The decision of the resident for this day.
     *
     * <p>The order is stable: a resident without work looks for a contract, a
     * resident with work and with a better skill asks for another profession,
     * a resident with money and with a missing tool buys it and a resident
     * with enough savings proposes an enterprise of its own.
     */
    public static Decision decide(MinecraftServer server, KingdomNpcData.Worker worker, long day) {
        if (server == null || worker == null) return Decision.none("нет жителя");
        var development = KingdomResidentDevelopment.get(server);
        var career = KingdomNpcCareerPolicy.inspect(worker,
                KingdomEnterpriseData.get(server));
        boolean employed = KingdomNpcWorkday.employed(server, worker);
        long savings = KingdomNpcCareerPolicy.physicalSavings(worker);
        // 1. Work: the contract comes first.
        if (!employed) {
            var vacancies = vacancies(server, worker);
            if (vacancies.isEmpty())
                return Decision.none("нет вакансий с зарегистрированным ядром");
            var best = vacancies.get(0);
            return new Decision(Action.APPLY_FOR_VACANCY, best.enterprise(), best.job(),
                    "", 0, best.wage(), "ищет вакансию " + best.job());
        }
        // 2. The profession: a resident with a skill looks for a better place.
        String currentJob = worker.job == null ? "" : worker.job;
        for (var vacancy : vacancies(server, worker)) {
            if (vacancy.job().equals(currentJob)) continue;
            if (!development.skillsOf(worker.entityId).containsKey(vacancy.job())) continue;
            return new Decision(Action.APPLY_FOR_PROFESSION, vacancy.enterprise(), vacancy.job(),
                    "", 0, vacancy.wage(), "хочет сменить профессию на " + vacancy.job());
        }
        // 3. The tool: a resident without a working tool buys one.
        var missing = missingTool(worker);
        if (missing != null) {
            long price = marketPrice(server, missing);
            if (price > 0 && savings >= price)
                return new Decision(Action.BUY, null, currentJob, missing, 1, price,
                        "покупает " + missing + " на свои деньги");
            return Decision.none("не хватает денег на " + missing);
        }
        // 4. The enterprise: the savings of a long working life become a plan.
        if (career != null && career.stage() == KingdomNpcCareerPolicy.Stage.READY_FOR_OFFERS
                && savings >= career.initialGoal())
            return new Decision(Action.PROPOSE_ENTERPRISE, null, currentJob, "", 0, 0,
                    "предлагает открыть своё предприятие");
        // 5. The goals of the resident are kept alive.
        development.removeExpiredGoals(worker.entityId, day);
        return Decision.none("работает и копит");
    }

    /** The tool that the resident misses for its profession. */
    public static String missingTool(KingdomNpcData.Worker worker) {
        if (worker == null) return null;
        String job = worker.job == null ? "" : worker.job;
        return switch (job) {
            case "miner" -> worker.fieldTool.isEmpty() ? "minecraft:iron_pickaxe" : null;
            case "lumberjack" -> worker.fieldTool.isEmpty() ? "minecraft:iron_axe" : null;
            case "farmer" -> worker.fieldTool.isEmpty() ? "minecraft:iron_hoe" : null;
            default -> null;
        };
    }

    /** The price of an item on the market, zero when it is not sold. */
    public static long marketPrice(MinecraftServer server, String itemId) {
        if (server == null || itemId == null) return 0;
        for (var level : server.getAllLevels()) {
            var market = KingdomWarehouseMarket.get(server);
            var quotes = market.search(level, itemId, 1);
            for (var quote : quotes)
                if (quote.stall() != null) return quote.totalCopper();
        }
        return 0;
    }

    /**
     * Performs the decision of the resident.
     *
     * <p>The actions that need the consent of a player are only requests: the
     * owner of the enterprise sees them and hires the resident. The purchase
     * is a real exchange of real items.
     *
     * @return true when something really happened.
     */
    public static boolean apply(MinecraftServer server, Villager npc,
                                KingdomNpcData.Worker worker, Decision decision) {
        if (server == null || worker == null || decision == null) return false;
        var development = KingdomResidentDevelopment.get(server);
        switch (decision.action()) {
            case APPLY_FOR_VACANCY, APPLY_FOR_PROFESSION -> {
                // The request is a real record that the owner of the
                // enterprise sees. No contract is made without the owner.
                development.addGoal(worker.entityId,
                    decision.action() == Action.APPLY_FOR_VACANCY ? "vacancy" : "profession",
                    decision.job(), 0);
                development.recordCareer(worker.entityId, 0, decision.job(), decision.enterprise());
                return true;
            }
            case BUY -> {
                for (var level : server.getAllLevels()) {
                    if (!worker.dimension.equals(level.dimension().location().toString())) continue;
                    var entity = level.getEntity(worker.entityId);
                    if (entity instanceof Villager resident && resident.isAlive())
                        return buy(server, level, resident, worker, decision.itemId(),
                            decision.amount(), decision.priceCopper());
                }
                return false;
            }
            case PROPOSE_ENTERPRISE -> {
                development.addGoal(worker.entityId, "enterprise", "proposal", 0);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Buys an item at a stall of the market with the own money of the resident.
     *
     * <p>The coins are the real coins of the wage purse and the goods are the
     * real goods of the chest of the stall. When one of the two sides fails,
     * the other one is not taken: nothing is lost and nothing is created.
     */
    public static boolean buy(MinecraftServer server, ServerLevel level, Villager resident,
                              KingdomNpcData.Worker worker, String itemId, int amount,
                              long priceCopper) {
        if (level == null || resident == null || worker == null || itemId == null || amount <= 0)
            return false;
        var market = KingdomWarehouseMarket.get(server);
        var quotes = market.search(level, itemId, amount);
        if (quotes.isEmpty()) return false;
        var quote = quotes.get(0);
        if (quote.stall() == null || quote.available() < amount) return false;
        long price = quote.totalCopper();
        if (price <= 0) return false;
        // The money must really be there.
        long savings = KingdomNpcCareerPolicy.physicalSavings(worker);
        if (savings < price) return false;
        BlockPos pos = quote.stall().pos();
        if (!level.hasChunkAt(pos)) return false;
        if (resident.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 36.0)
            return false;
        Container chest = KingdomContainerAccess.resolve(level, pos);
        if (chest == null) return false;
        // The goods are taken first and only then the money is paid.
        var goods = KingdomResidentInventory.extract(chest,
                stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString().equals(itemId), amount);
        int taken = goods.stream().mapToInt(ItemStack::getCount).sum();
        if (taken < amount) {
            for (var stack : goods) KingdomResidentInventory.insertAndRemainder(chest, stack);
            return false;
        }
        if (!pay(worker, price)) {
            // The money was not there: the goods go back into the chest.
            for (var stack : goods) KingdomResidentInventory.insertAndRemainder(chest, stack);
            return false;
        }
        // The goods go into the real bag of the resident; the rest stays in
        // the world as a real item.
        for (var stack : goods) {
            ItemStack rest = stack.copy();
            Container personal = KingdomResidentInventory.personal(resident);
            if (personal == null) {
                Block.popResource(level, resident.blockPosition(), rest);
                continue;
            }
            KingdomResidentInventory.insertCount(personal, rest);
            if (!rest.isEmpty()) Block.popResource(level, resident.blockPosition(), rest);
        }
        KingdomNpcData.get(server).setDirty();
        return true;
    }

    /** Pays the price with the real coins of the wage purse. */
    private static boolean pay(KingdomNpcData.Worker worker, long price) {
        long left = price;
        for (var stack : worker.wagePurse) {
            if (stack.isEmpty() || left <= 0) continue;
            long value = KingdomCurrency.value(stack);
            if (value <= 0) continue;
            int count = stack.getCount();
            int needed = (int) Math.min(count, Math.max(1, (left + value - 1) / value));
            stack.shrink(needed);
            left -= needed * value;
            if (stack.isEmpty()) stack.setCount(0);
        }
        return left <= 0;
    }
}
