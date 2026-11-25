package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;

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
    private static final java.util.Map<MinecraftServer, ResidentPurchaseJournal> PURCHASE_JOURNALS = new java.util.WeakHashMap<>();

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
        development.removeExpiredGoals(worker.entityId, day);
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

    /** The appropriate tool is available in either the active slot or the carried work view. */
    public static String missingTool(KingdomNpcData.Worker worker) {
        if (worker == null) return null;
        String job = worker.job == null ? "" : worker.job;
        String wanted = switch (job) {
            case "miner" -> "minecraft:iron_pickaxe";
            case "lumberjack" -> "minecraft:iron_axe";
            case "farmer" -> "minecraft:iron_hoe";
            default -> null;
        };
        if (wanted == null || appropriateTool(worker.fieldTool, job)) return null;
        for (var stack : worker.fieldInventory)
            if (appropriateTool(stack, job)) return null;
        return wanted;
    }

    private static boolean appropriateTool(net.minecraft.world.item.ItemStack stack, String job) {
        if (stack == null || stack.isEmpty()) return false;
        return switch (job) {
            case "miner" -> KingdomMineTools.matches(stack, KingdomMineTools.Tool.PICKAXE);
            case "lumberjack" -> stack.getItem() instanceof net.minecraft.world.item.AxeItem
                    || stack.is(net.minecraft.tags.ItemTags.AXES);
            case "farmer" -> stack.getItem() instanceof net.minecraft.world.item.HoeItem
                    || stack.is(net.minecraft.tags.ItemTags.HOES);
            default -> false;
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
                return recordRequest(development, worker.entityId, decision);
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
                return recordRequest(development, worker.entityId, decision);
            }
            default -> {
                return false;
            }
        }
    }

    static boolean recordRequest(KingdomResidentDevelopment development, UUID resident, Decision decision) {
        String kind = switch (decision.action()) {
            case APPLY_FOR_VACANCY -> "vacancy";
            case APPLY_FOR_PROFESSION -> "profession";
            case PROPOSE_ENTERPRISE -> "enterprise";
            default -> null;
        };
        if (kind == null || resident == null) return false;
        String target = decision.action() == Action.PROPOSE_ENTERPRISE ? "proposal"
                : decision.enterprise() + ":" + decision.job();
        int before = development.goalsOf(resident).size();
        development.addGoal(resident, kind, target, 0);
        // A request is never an employment contract or a completed career step.
        return development.goalsOf(resident).size() > before;
    }

    static boolean knownPersonalBag(Container bag) {
        if (bag == null) return false;
        if (bag.getClass() == net.minecraft.world.SimpleContainer.class) return true;
        // Inspected MCA 7.6.23: this class adds only update(Entity); all slot access
        // and storage are inherited directly from SimpleContainer.
        return (bag.getClass().getName().equals("forge.net.mca.entity.UpdatableInventory")
                || bag.getClass().getName().equals("forge.net.conczin.mca.entity.UpdatableInventory"))
                && bag.getClass().getSuperclass() == net.minecraft.world.SimpleContainer.class;
    }

    /**
     * Buys an item at a stall of the market with the own money of the resident.
     *
     * <p>The coins are the real coins of the wage purse and the goods are the
     * real goods of the chest of the stall. Planning checks exact coins and room
     * on both sides. Durable evidence quarantines purchases after restart; it
     * does not make the separate Minecraft inventory saves atomic.
     */
    public static boolean buy(MinecraftServer server, ServerLevel level, Villager resident,
                              KingdomNpcData.Worker worker, String itemId, int amount,
                              long priceCopper) {
        if (server == null || level == null || resident == null || worker == null
                || !resident.isAlive() || !worker.physicalBag || !resident.getUUID().equals(worker.entityId)
                || !worker.dimension.equals(level.dimension().location().toString())
                || itemId == null || amount <= 0 || priceCopper <= 0) return false;
        var journal = PURCHASE_JOURNALS.computeIfAbsent(server, current -> new ResidentPurchaseJournal(
                current.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("data/kingdom-resident-purchases")));
        if (journal.blocked()) return false;
        var quotes = KingdomWarehouseMarket.get(server).search(level, itemId, amount);
        if (quotes.isEmpty()) return false;
        var quote = quotes.get(0);
        if (quote.stall() == null || quote.available() < amount
                || quote.totalCopper() != priceCopper || priceCopper > 1_000_000) return false;
        var stall = quote.stall();
        BlockPos pos = stall.pos();
        if (!level.hasChunkAt(pos) || stall.owner().equals(worker.entityId)
                || !KingdomTerritories.get(server).canUse(level, pos, stall.owner())
                || resident.distanceToSqr(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) > 36) return false;
        Container seller = KingdomContainerAccess.resolve(level, pos);
        Container bag = KingdomResidentInventory.personal(resident);
        // Only known independent backing inventories are currently supported. In particular,
        // unknown capability wrappers and joined chests need the physical-slot proof from #96.
        var blockEntity = level.getBlockEntity(pos);
        if (seller == null || seller != blockEntity || bag == null
                || !knownPersonalBag(bag)
                || !(seller.getClass() == net.minecraft.world.level.block.entity.ChestBlockEntity.class
                    || seller.getClass() == net.minecraft.world.level.block.entity.BarrelBlockEntity.class)) return false;
        var plan = ResidentPhysicalPurchase.prepare(worker.wagePurse, seller, bag,
                stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                        .toString().equals(itemId), amount, priceCopper, KingdomCurrency::value);
        if (plan == null) return false;
        var evidence = plan.evidence();
        evidence.putUUID("Operation", UUID.nameUUIDFromBytes((worker.entityId + ":"
                + Math.floorDiv(level.getDayTime(), 24000) + ":" + itemId + ":" + amount)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        evidence.putUUID("Resident", worker.entityId); evidence.putUUID("Seller", stall.owner());
        evidence.putString("Dimension", stall.dimension()); evidence.putLong("SellerPos", pos.asLong());
        evidence.putString("Item", itemId); evidence.putInt("Amount", amount); evidence.putLong("Price", priceCopper);
        if (!journal.prepare(evidence)) return false;
        try {
            boolean committed = plan.commit();
            journal.finish(committed);
            if (committed) {
                KingdomResidentInventory.syncToWorker(worker, bag);
                KingdomResidentDevelopment.get(server).recordPurchase(worker.entityId, itemId, amount, priceCopper);
                KingdomNpcData.get(server).setDirty();
            }
            return committed;
        } catch (RuntimeException uncertain) {
            journal.quarantine();
            return false;
        }
    }
}
