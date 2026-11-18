package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Server-side authority for physical settlements of goods and money.
 *
 * <p>One durable record is the single decision point for both the goods and the
 * money of a delivery. The decision is written before any container is touched:
 *
 * <ol>
 *   <li>{@code PREPARED}  -  the plan and the expected count of every touched
 *       (container, item) pair;</li>
 *   <li>{@code COMMITTED}  -  the decision itself: the delivery must be visible in
 *       the world;</li>
 *   <li>{@code RECEIPT}  -  the delivery is projected into the containers and
 *       verified.</li>
 * </ol>
 *
 * <p>Projection is idempotent. Every touched pair carries its expected count, so
 * a restart can tell "not applied yet" from "already applied" and finish exactly
 * the missing step instead of repeating a finished one. An unexpected count is
 * never guessed: the delivery is quarantined for an operator.
 *
 * <p>A delivery that was prepared but not committed is never applied
 * automatically  -  the decision was not durable, so the world may be in any
 * state and only an operator may resolve it. An unresolved delivery also blocks
 * other deliveries, because their containers may already have been touched.
 *
 * <p>The authority owns no items and no money: it moves deltas between the
 * containers it is given. It is not an atomic commit for Minecraft chunk or
 * SavedData writes, and the expected-count check cannot detect a change that
 * happens to leave the same count in a container.
 */
public final class SettlementAuthority {
    /** A physical container of the world: a chest, a cashbox, a wallet. */
    public interface Container {
        /** Stable identifier used by the durable records. */
        String id();
        /** Current amount of the item in the container. */
        long count(String item);
        /** Maximum amount of the item the container can hold. */
        long capacity(String item);
        /**
         * Applies a delta. A negative delta must not take the amount below zero.
         * A failed write is an error, not a partial success.
         */
        void apply(String item, long delta) throws IOException;

        /**
         * The variants of the item in this container.
         *
         * <p>A variant is the fingerprint of the exact state of an item: the
         * tags of the stack, for example the freshness of a perishable item of
         * TerraFirmaCraft. The empty string is the item without any tag.
         *
         * <p>A container that cannot tell the variants apart returns only the
         * empty variant: the delivery is then limited to items without a state.
         */
        default java.util.List<String> variants(String item) { return java.util.List.of(""); }

        /**
         * The amount of the item in the exact variant.
         *
         * <p>A container that cannot separate the variants must refuse instead
         * of counting every variant together: the delivery would move the
         * wrong goods.
         */
        default long count(String item, String variant) {
            if (variant == null || variant.isEmpty()) return count(item);
            throw new UnsupportedOperationException("the container cannot separate the variants of " + item);
        }

        /** The free space of the container for the exact variant. */
        default long capacity(String item, String variant) {
            if (variant == null || variant.isEmpty()) return capacity(item);
            throw new UnsupportedOperationException("the container cannot separate the variants of " + item);
        }

        /**
         * Moves the amount of the exact variant.
         *
         * <p>A container that cannot separate the variants must refuse: moving
         * the amount of another variant would destroy goods that were never
         * offered.
         */
        default void apply(String item, String variant, long delta) throws IOException {
            if (variant == null || variant.isEmpty()) { apply(item, delta); return; }
            throw new IOException("the container cannot move the variants of " + item);
        }
    }

    public enum Outcome { COMMITTED, DUPLICATE, REJECTED, QUARANTINED, INCONSISTENT }
    public enum State { PREPARED, COMMITTED, RECEIPTED, REJECTED, QUARANTINED }
    /** Operator decision for a quarantined delivery. */
    public enum Resolution { CONFIRMED_APPLIED, CONFIRMED_NOT_APPLIED }

    /** One delivery request. The id is the idempotency key of the delivery. */
    public record Request(UUID id, String item, String goodsVariant, int quantity, long unitCopper,
                           Container supplier, Container warehouse, Container cashbox, Container wallet) {
        /**
         * A request without a variant: only items without a state are delivered.
         * Kept for the callers of the older journal format.
         */
        public Request(UUID id, String item, int quantity, long unitCopper,
                       Container supplier, Container warehouse, Container cashbox, Container wallet) {
            this(id, item, "", quantity, unitCopper, supplier, warehouse, cashbox, wallet);
        }
    }

    /** Read-only view of a delivery known to the authority. */
    public record TxView(UUID id, State state, String item, int quantity, long totalCopper, String problem) {}

    /** Result of a recovery pass over the durable log. */
    public record RecoveryReport(List<TxView> transactions, List<UUID> quarantined, boolean journalDamaged) {}

    private static final String GOLD = DemandPhysicalSettlementEngine.GOLD;
    private static final String SILVER = DemandPhysicalSettlementEngine.SILVER;
    private static final String COPPER = DemandPhysicalSettlementEngine.COPPER;
    private static final long[] COIN_VALUES = { 10000L, 100L, 1L };
    private static final String SAFE_ID = "[A-Za-z0-9_.:-]{1,96}";

    /** A touched (container, item) pair with the counts before and after the delivery. */
    private record Check(String container, String item, String variant, long before, long after) {}

    /** The durable plan of one delivery. */
    private record Plan(UUID id, String item, int quantity, long unitCopper, long totalCopper,
                        List<Check> goods, List<Check> coins) {
        String payload() {
            StringBuilder out = new StringBuilder();
            out.append("tx=").append(id).append(";item=").append(item)
               .append(";qty=").append(quantity).append(";unit=").append(unitCopper)
               .append(";total=").append(totalCopper);
            appendChecks(out, "goods", goods);
            appendChecks(out, "coins", coins);
            // The variants live in their own section: a log of the older format
            // has none, and a reader of the older format ignores this section.
            // The counts stay in the older four field form for the same reason.
            StringBuilder variants = new StringBuilder();
            for (List<Check> checks : List.of(goods, coins)) {
                for (Check check : checks) {
                    if (check.variant().isEmpty()) continue;
                    if (variants.length() > 0) variants.append(',');
                    variants.append(check.container()).append('~').append(check.item())
                            .append('~').append(check.variant());
                }
            }
            if (variants.length() > 0) out.append(";variants=").append(variants);
            return out.toString();
        }
        private static void appendChecks(StringBuilder out, String name, List<Check> checks) {
            out.append(';').append(name).append('=');
            for (int index = 0; index < checks.size(); index++) {
                if (index > 0) out.append(',');
                Check check = checks.get(index);
                out.append(check.container()).append('~').append(check.item())
                   .append('~').append(check.before()).append('~').append(check.after());
            }
        }
    }

    /** Internal state of a delivery known from the durable log. */
    private static final class TxState {
        final UUID id;
        State state = State.PREPARED;
        Plan plan;
        String item = "";
        int quantity;
        long totalCopper;
        String problem = "";
        TxState(UUID id) { this.id = id; }
    }

    private static final class Rejection extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String reason;
        Rejection(String reason) { super(reason); this.reason = reason; }
    }

    private final SettlementJournal journal;
    private final BiConsumer<String, Long> hook;
    private final Map<UUID, TxState> states = new LinkedHashMap<>();
    private final Set<UUID> quarantined = new LinkedHashSet<>();
    private boolean loaded;
    private boolean journalDamaged;

    public SettlementAuthority(Path directory) throws IOException { this(directory, null); }

    /**
     * @param directory directory of the durable log.
     * @param hook optional crash-injection callback: it receives the boundary name
     *             and the durable length of the log at that moment.
     */
    public SettlementAuthority(Path directory, BiConsumer<String, Long> hook) throws IOException {
        this.hook = hook;
        Path folder = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        Files.createDirectories(folder);
        this.journal = new SettlementJournal(folder.resolve("settlement-authority.log"));
    }

    public Path journalPath() { return journal.file(); }

    /**
     * Settles one delivery: prepare, commit, project, receipt.
     *
     * <p>The decision is durable before the first container is touched, so a
     * crash can never lose a decided delivery and can never apply an undecided
     * one.
     */
    public synchronized Outcome settle(Request request) throws IOException {
        validate(request);
        ensureLoaded();
        TxState known = states.get(request.id());
        if (known != null) {
            switch (known.state) {
                case RECEIPTED:
                case COMMITTED:
                    return Outcome.DUPLICATE;
                case REJECTED:
                    return Outcome.REJECTED;
                case QUARANTINED:
                case PREPARED:
                default:
                    return Outcome.QUARANTINED;
            }
        }
        // Any delivery that is not finished means the world may be in an unknown
        // state. New deliveries wait for the recovery pass or the operator.
        for (TxState state : states.values()) {
            if (state.state != State.RECEIPTED && state.state != State.REJECTED) return Outcome.QUARANTINED;
        }

        Plan plan;
        try {
            plan = plan(request);
        } catch (Rejection rejection) {
            journal.append(SettlementJournal.Kind.REJECTED,
                    "tx=" + request.id() + ";reason=" + rejection.reason, hook);
            TxState state = new TxState(request.id());
            state.state = State.REJECTED;
            state.problem = rejection.reason;
            states.put(request.id(), state);
            return Outcome.REJECTED;
        }

        TxState state = new TxState(request.id());
        state.plan = plan;
        state.item = plan.item();
        state.quantity = plan.quantity();
        state.totalCopper = plan.totalCopper();
        states.put(request.id(), state);

        hit("prepare.before");
        journal.append(SettlementJournal.Kind.PREPARED, plan.payload(), hook);
        hit("commit.before");
        journal.append(SettlementJournal.Kind.COMMITTED, "tx=" + request.id(), hook);
        state.state = State.COMMITTED;

        Outcome projected = project(plan, registry(request));
        if (projected != Outcome.COMMITTED) {
            String reason = projected == Outcome.QUARANTINED ? "container_unknown" : "inconsistent_state";
            journal.append(SettlementJournal.Kind.QUARANTINED, "tx=" + request.id() + ";reason=" + reason, hook);
            state.state = State.QUARANTINED;
            state.problem = reason;
            quarantined.add(request.id());
            return projected == Outcome.QUARANTINED ? Outcome.QUARANTINED : Outcome.INCONSISTENT;
        }

        hit("receipt.before");
        journal.append(SettlementJournal.Kind.RECEIPT, "tx=" + request.id(), hook);
        state.state = State.RECEIPTED;
        return Outcome.COMMITTED;
    }

    /**
     * Rebuilds the state from the durable log and finishes what was decided.
     *
     * <p>A committed delivery is projected again, but idempotently: only the
     * missing steps are applied. A delivery that was prepared but never
     * committed is quarantined, and so is every delivery behind it.
     *
     * @param registry the containers of the world, by identifier.
     */
    public synchronized RecoveryReport recover(Map<String, Container> registry) throws IOException {
        loadStates();
        boolean damaged = journalDamaged;

        List<UUID> blocked = new ArrayList<>();
        for (TxState state : new ArrayList<>(states.values())) {
            if (!blocked.isEmpty()) {
                quarantine(state, "blocked_by_earlier_delivery");
                blocked.add(state.id);
                continue;
            }
            switch (state.state) {
                case PREPARED:
                    quarantine(state, "decision_not_durable");
                    blocked.add(state.id);
                    break;
                case COMMITTED:
                    if (state.plan == null) {
                        quarantine(state, "plan_unknown");
                        blocked.add(state.id);
                        break;
                    }
                    Outcome outcome = project(state.plan, registry);
                    if (outcome != Outcome.COMMITTED) {
                        quarantine(state, outcome == Outcome.QUARANTINED ? "container_unknown" : "inconsistent_state");
                        blocked.add(state.id);
                        break;
                    }
                    journal.append(SettlementJournal.Kind.RECEIPT, "tx=" + state.id + ";recovered=true", hook);
                    state.state = State.RECEIPTED;
                    break;
                case RECEIPTED:
                case REJECTED:
                    break;
                case QUARANTINED:
                default:
                    blocked.add(state.id);
                    break;
            }
        }
        return new RecoveryReport(views(), List.copyOf(blocked), damaged);
    }

    /** Operator decision for a quarantined delivery. */
    public synchronized Outcome resolve(UUID id, Resolution resolution) throws IOException {
        ensureLoaded();
        TxState state = states.get(id);
        if (state == null) throw new IllegalArgumentException("unknown delivery");
        if (state.state != State.QUARANTINED) throw new IllegalStateException("delivery is not quarantined");
        if (resolution == Resolution.CONFIRMED_APPLIED) {
            journal.append(SettlementJournal.Kind.RECEIPT, "tx=" + id + ";operator=confirmed_applied", hook);
            state.state = State.RECEIPTED;
        } else {
            journal.append(SettlementJournal.Kind.REJECTED, "tx=" + id + ";operator=confirmed_not_applied", hook);
            state.state = State.REJECTED;
        }
        quarantined.remove(id);
        return Outcome.COMMITTED;
    }

    /** Read-only view of every delivery known to the authority. */
    public synchronized List<TxView> transactions() { return views(); }

    /**
     * The state of one delivery, null when the authority does not know it.
     *
     * <p>A caller uses it to find out whether a delivery was already
     * finished: a delivery with a durable receipt must never be moved again.
     */
    public synchronized State stateOf(UUID id) throws IOException {
        ensureLoaded();
        TxState state = states.get(id);
        return state == null ? null : state.state;
    }

    /** Bytes of the durable log. */
    public synchronized long journalSize() throws IOException { return journal.durableLength(); }

    /**
     * Whether the durable log was damaged when it was read.
     *
     * <p>A damaged log means that records are missing. The delivery must not
     * continue on such a log: the state of the world is unknown.
     */
    public synchronized boolean journalDamaged() throws IOException {
        ensureLoaded();
        return journalDamaged;
    }

    /** Compacts the durable log so that it stops growing without end. */
    public synchronized int compact() throws IOException { return journal.compact(); }

    /**
     * Identifiers of the containers of the deliveries that are not finished.
     *
     * <p>The recovery needs them to rebuild the containers of a server that
     * was restarted: the containers are not part of the in-memory state.
     */
    public synchronized List<String> pendingContainers() throws IOException {
        ensureLoaded();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (TxState state : states.values()) {
            if (state.state == State.RECEIPTED || state.state == State.REJECTED) continue;
            if (state.plan == null) continue;
            for (Check check : state.plan.goods()) ids.add(check.container());
            for (Check check : state.plan.coins()) ids.add(check.container());
        }
        return List.copyOf(ids);
    }

    // --- internals ------------------------------------------------------------

    /**
     * Reads the durable log once. A settlement must never rely on an in-memory
     * state alone: a restarted authority that skipped the recovery pass would
     * not know about an unresolved delivery and could apply it.
     */
    private void ensureLoaded() throws IOException {
        if (loaded) return;
        loadStates();
    }

    private void loadStates() throws IOException {
        SettlementJournal.LoadResult load = journal.load();
        states.clear();
        quarantined.clear();
        journalDamaged = load.damaged();
        LinkedHashMap<UUID, TxState> rebuilt = new LinkedHashMap<>();
        for (SettlementJournal.Record record : load.records()) {
            TxState state = rebuild(rebuilt, record);
            if (state == null) {
                journalDamaged = true;
                continue;
            }
            switch (record.kind()) {
                case PREPARED: state.state = State.PREPARED; break;
                case COMMITTED: state.state = State.COMMITTED; break;
                case RECEIPT: state.state = State.RECEIPTED; break;
                case REJECTED: state.state = State.REJECTED; break;
                case QUARANTINED: state.state = State.QUARANTINED; break;
                default: break;
            }
        }
        states.putAll(rebuilt);
        for (TxState state : rebuilt.values())
            if (state.state == State.QUARANTINED) quarantined.add(state.id);
        loaded = true;
    }

    private List<TxView> views() {
        List<TxView> out = new ArrayList<>();
        for (TxState state : states.values())
            out.add(new TxView(state.id, state.state, state.item, state.quantity, state.totalCopper, state.problem));
        return List.copyOf(out);
    }

    private void quarantine(TxState state, String reason) throws IOException {
        journal.append(SettlementJournal.Kind.QUARANTINED, "tx=" + state.id + ";reason=" + reason, hook);
        state.state = State.QUARANTINED;
        state.problem = reason;
        quarantined.add(state.id);
    }

    private TxState rebuild(Map<UUID, TxState> map, SettlementJournal.Record record) {
        Map<String, String> fields;
        try {
            fields = fields(record.payload());
        } catch (RuntimeException ex) {
            return null;
        }
        String id = fields.get("tx");
        if (id == null) return null;
        UUID parsed;
        try {
            parsed = UUID.fromString(id);
        } catch (RuntimeException ex) {
            return null;
        }
        TxState state = map.computeIfAbsent(parsed, TxState::new);
        if (record.kind() == SettlementJournal.Kind.PREPARED) {
            try {
                Plan plan = parsePlan(record.payload());
                state.plan = plan;
                state.item = plan.item();
                state.quantity = plan.quantity();
                state.totalCopper = plan.totalCopper();
            } catch (RuntimeException ex) {
                state.plan = null;
                state.problem = "plan_unreadable";
            }
        }
        return state;
    }

    private Outcome project(Plan plan, Map<String, Container> registry) {
        Outcome goods = applyChecks(plan.goods(), registry);
        if (goods != Outcome.COMMITTED) return goods;
        hit("project.after_goods");
        Outcome coins = applyChecks(plan.coins(), registry);
        if (coins != Outcome.COMMITTED) return coins;
        hit("project.after_coins");
        return Outcome.COMMITTED;
    }

    private Outcome applyChecks(List<Check> checks, Map<String, Container> registry) {
        for (Check check : checks) {
            Container container = registry.get(check.container());
            if (container == null) return Outcome.QUARANTINED;
            long actual;
            try {
                actual = container.count(check.item(), check.variant());
            } catch (RuntimeException ex) {
                // An unreadable container is an inconsistent state, never a guess.
                return Outcome.INCONSISTENT;
            }
            if (actual == check.after()) continue;
            if (actual != check.before()) return Outcome.INCONSISTENT;
            try {
                container.apply(check.item(), check.variant(), check.after() - check.before());
            } catch (IOException | RuntimeException ex) {
                return Outcome.INCONSISTENT;
            }
            long applied;
            try {
                applied = container.count(check.item(), check.variant());
            } catch (RuntimeException ex) {
                return Outcome.INCONSISTENT;
            }
            if (applied != check.after()) return Outcome.INCONSISTENT;
        }
        return Outcome.COMMITTED;
    }

    private Plan plan(Request request) {
        String variant = request.goodsVariant();
        long supplierHave;
        long warehouseHave;
        try {
            supplierHave = request.supplier().count(request.item(), variant);
            warehouseHave = request.warehouse().count(request.item(), variant);
        } catch (UnsupportedOperationException ex) {
            // The container cannot tell the variants apart: the delivery of a
            // stateful item would move the wrong goods.
            throw new Rejection("VARIANT_UNSUPPORTED");
        } catch (RuntimeException ex) {
            // The container cannot be read: the amount of the goods is unknown.
            throw new Rejection("CONTAINER_UNREADABLE");
        }
        if (supplierHave < request.quantity()) throw new Rejection("INSUFFICIENT_GOODS");
        long space;
        try {
            space = request.warehouse().capacity(request.item(), variant);
        } catch (UnsupportedOperationException ex) {
            throw new Rejection("VARIANT_UNSUPPORTED");
        } catch (RuntimeException ex) {
            throw new Rejection("CONTAINER_UNREADABLE");
        }
        if (space - warehouseHave < request.quantity()) throw new Rejection("NO_GOODS_SPACE");

        long price;
        try {
            price = Math.multiplyExact((long) request.quantity(), request.unitCopper());
        } catch (ArithmeticException ex) {
            throw new Rejection("INVALID");
        }
        long[] have = { request.cashbox().count(GOLD), request.cashbox().count(SILVER), request.cashbox().count(COPPER) };
        if (totalValue(have) < price) throw new Rejection("INSUFFICIENT_COINS");
        long[] selected = exactSelection(have, COIN_VALUES, price);
        if (selected == null) throw new Rejection("INSUFFICIENT_COINS");

        String[] coins = { GOLD, SILVER, COPPER };
        for (int index = 0; index < coins.length; index++) {
            if (selected[index] <= 0) continue;
            long walletHave = request.wallet().count(coins[index]);
            if (request.wallet().capacity(coins[index]) - walletHave < selected[index])
                throw new Rejection("NO_COIN_SPACE");
        }

        List<Check> goods = new ArrayList<>();
        goods.add(new Check(request.supplier().id(), request.item(), variant, supplierHave, supplierHave - request.quantity()));
        goods.add(new Check(request.warehouse().id(), request.item(), variant, warehouseHave, warehouseHave + request.quantity()));
        List<Check> money = new ArrayList<>();
        for (int index = 0; index < coins.length; index++) {
            if (selected[index] <= 0) continue;
            long cashHave = request.cashbox().count(coins[index]);
            long walletHave = request.wallet().count(coins[index]);
            money.add(new Check(request.cashbox().id(), coins[index], "", cashHave, cashHave - selected[index]));
            money.add(new Check(request.wallet().id(), coins[index], "", walletHave, walletHave + selected[index]));
        }
        return new Plan(request.id(), request.item(), request.quantity(), request.unitCopper(), price,
                List.copyOf(goods), List.copyOf(money));
    }

    private static Map<String, Container> registry(Request request) {
        Map<String, Container> map = new LinkedHashMap<>();
        map.put(request.supplier().id(), request.supplier());
        map.put(request.warehouse().id(), request.warehouse());
        map.put(request.cashbox().id(), request.cashbox());
        map.put(request.wallet().id(), request.wallet());
        return map;
    }

    private static void validate(Request request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.id(), "id");
        requireSafe("item", request.item());
        String variant = request.goodsVariant();
        if (variant == null) throw new IllegalArgumentException("variant");
        if (!variant.isEmpty()) requireSafe("variant", variant);
        if (request.quantity() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (request.unitCopper() <= 0) throw new IllegalArgumentException("unit price must be positive");
        Container[] containers = { request.supplier(), request.warehouse(), request.cashbox(), request.wallet() };
        for (Container container : containers) {
            Objects.requireNonNull(container, "container");
            requireSafe("container", container.id());
        }
        for (int first = 0; first < containers.length; first++)
            for (int second = first + 1; second < containers.length; second++)
                if (containers[first].id().equals(containers[second].id()))
                    throw new IllegalArgumentException("containers must be distinct");
    }

    private static void requireSafe(String kind, String value) {
        if (value == null || !value.matches(SAFE_ID))
            throw new IllegalArgumentException("unsafe " + kind + " id");
    }

    private static Map<String, String> fields(String payload) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : payload.split(";")) {
            if (part.isEmpty()) continue;
            int equals = part.indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException("bad field");
            out.put(part.substring(0, equals), part.substring(equals + 1));
        }
        return out;
    }

    private static Plan parsePlan(String payload) {
        Map<String, String> fields = fields(payload);
        Map<String, String> variants = parseVariants(fields.get("variants"));
        Plan plan = new Plan(UUID.fromString(fields.get("tx")), fields.get("item"),
                Integer.parseInt(fields.get("qty")), Long.parseLong(fields.get("unit")),
                Long.parseLong(fields.get("total")),
                parseChecks(fields.get("goods"), variants), parseChecks(fields.get("coins"), variants));
        requireSafe("item", plan.item());
        for (Check check : plan.goods()) requireSafe("container", check.container());
        for (Check check : plan.coins()) requireSafe("container", check.container());
        return plan;
    }

    /**
     * The variants of a plan, by container and item.
     *
     * <p>A log of the older format has no variants: the delivery of such a
     * record keeps the older rule, the amount of the item is checked as a
     * whole. A record of the new format always names its variant.
     */
    private static Map<String, String> parseVariants(String payload) {
        Map<String, String> out = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) return out;
        for (String part : payload.split(",")) {
            if (part.isEmpty()) continue;
            String[] bits = part.split("~", -1);
            if (bits.length != 3) throw new IllegalArgumentException("bad variant");
            requireSafe("variant", bits[2]);
            out.put(bits[0] + "~" + bits[1], bits[2]);
        }
        return out;
    }

    private static List<Check> parseChecks(String payload, Map<String, String> variants) {
        List<Check> checks = new ArrayList<>();
        if (payload == null || payload.isEmpty()) return List.of();
        for (String part : payload.split(",")) {
            if (part.isEmpty()) continue;
            String[] bits = part.split("~", -1);
            if (bits.length != 4) throw new IllegalArgumentException("bad check");
            String variant = variants.getOrDefault(bits[0] + "~" + bits[1], "");
            checks.add(new Check(bits[0], bits[1], variant,
                    Long.parseLong(bits[2]), Long.parseLong(bits[3])));
        }
        return List.copyOf(checks);
    }

    /** Coins are divisible 100:1. Prefer large coins, but never greedy-fail. */
    private static long[] exactSelection(long[] have, long[] values, long target) {
        long maxGold = Math.min(have[0], target / values[0]);
        for (long gold = maxGold; gold >= 0; gold--) {
            long remainder = target - gold * values[0];
            long silver = Math.min(have[1], remainder / values[1]);
            long copper = remainder - silver * values[1];
            if (copper <= have[2]) return new long[] { gold, silver, copper };
            if (gold == 0) break;
        }
        return null;
    }

    private static long totalValue(long[] counts) {
        long total = 0;
        for (int index = 0; index < counts.length; index++)
            total = Math.addExact(total, Math.multiplyExact(counts[index], COIN_VALUES[index]));
        return total;
    }

    private void hit(String point) {
        if (hook != null) hook.accept(point, journal.durableLength());
    }
}
