package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import ru.projectkingdom.npc.SettlementAuthority.Container;
import ru.projectkingdom.npc.SettlementAuthority.Outcome;
import ru.projectkingdom.npc.SettlementAuthority.RecoveryReport;
import ru.projectkingdom.npc.SettlementAuthority.Request;
import ru.projectkingdom.npc.SettlementAuthority.Resolution;
import ru.projectkingdom.npc.SettlementAuthority.State;
import ru.projectkingdom.npc.SettlementAuthority.TxView;

/**
 * Crash-injection harness for the settlement authority.
 *
 * <p>Every scenario kills the authority at a durable-write boundary, truncates
 * the log to the last durable record (power loss) and replays the recovery. The
 * invariants under test: no item and no coin is ever created, lost or applied
 * twice, and a delivery that was not decided is never applied.
 */
public final class SettlementAuthorityHarness {
    static final String ORE = "tfc:ore";
    static final String GOLD = DemandPhysicalSettlementEngine.GOLD;
    static final String SILVER = DemandPhysicalSettlementEngine.SILVER;
    static final String COPPER = DemandPhysicalSettlementEngine.COPPER;

    static void yes(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    /** A chest of the world. */
    static final class Chest implements Container {
        private final String id;
        private final Map<String, Long> items = new LinkedHashMap<>();
        private final Map<String, Long> limits = new HashMap<>();

        Chest(String id) { this.id = id; }
        Chest limit(String item, long capacity) { limits.put(item, capacity); return this; }
        void set(String item, long amount) { items.put(item, amount); }

        public String id() { return id; }
        public long count(String item) { return items.getOrDefault(item, 0L); }
        public long capacity(String item) { return limits.getOrDefault(item, Long.MAX_VALUE); }
        public void apply(String item, long delta) {
            long next = count(item) + delta;
            if (next < 0) throw new IllegalStateException("negative amount in " + id);
            if (next > capacity(item)) throw new IllegalStateException("over capacity in " + id);
            items.put(item, next);
        }
    }

    /** The four containers of one delivery. */
    static final class World {
        final Chest supplier = new Chest("supplier");
        final Chest warehouse = new Chest("warehouse");
        final Chest cashbox = new Chest("cashbox");
        final Chest wallet = new Chest("wallet");

        long goods() { return supplier.count(ORE) + warehouse.count(ORE); }
        long money() {
            return value(cashbox) + value(wallet);
        }
        private static long value(Chest chest) {
            return chest.count(COPPER) + chest.count(SILVER) * 100L + chest.count(GOLD) * 10000L;
        }
        Map<String, Container> registry() {
            Map<String, Container> map = new LinkedHashMap<>();
            map.put(supplier.id(), supplier);
            map.put(warehouse.id(), warehouse);
            map.put(cashbox.id(), cashbox);
            map.put(wallet.id(), wallet);
            return map;
        }
    }

    /** Simulated process death. */
    static final class Crash extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Crash(String point) { super(point); }
    }

    /** Arms one durable-write boundary of the authority. */
    static final class CrashPlan implements BiConsumer<String, Long> {
        private final String point;
        private final int occurrence;
        private int seen;
        private long durableAtCrash = -1;

        CrashPlan(String point, int occurrence) {
            this.point = point;
            this.occurrence = occurrence;
        }
        public void accept(String name, Long durableLength) {
            if (!name.equals(point)) return;
            seen++;
            if (seen != occurrence) return;
            durableAtCrash = durableLength;
            throw new Crash(name);
        }
        /** Power loss: only the durable prefix of the log survives. */
        void simulatePowerLoss(Path journal) {
            if (durableAtCrash < 0) return;
            try {
                if (Files.exists(journal)) {
                    try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.WRITE)) {
                        channel.truncate(durableAtCrash);
                        channel.force(true);
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    static Path freshDirectory(String prefix) throws IOException {
        Path directory = Files.createTempDirectory(prefix);
        directory.toFile().deleteOnExit();
        return directory;
    }

    static Request request(UUID id, World world) {
        return new Request(id, ORE, 20, 5L, world.supplier, world.warehouse, world.cashbox, world.wallet);
    }

    static World world() {
        World world = new World();
        world.supplier.set(ORE, 20);
        world.cashbox.set(SILVER, 1);
        return world;
    }

    static void assertConserved(World world, String where) {
        yes(world.goods() == 20, where + ": goods must stay 20, was " + world.goods());
        yes(world.money() == 100, where + ": money must stay 100, was " + world.money());
    }

    static void assertFullyApplied(World world, String where) {
        assertConserved(world, where);
        yes(world.supplier.count(ORE) == 0, where + ": supplier must be empty");
        yes(world.warehouse.count(ORE) == 20, where + ": warehouse must hold the goods");
        yes(world.cashbox.count(SILVER) == 0, where + ": cashbox must be empty");
        yes(world.wallet.count(SILVER) == 1, where + ": wallet must hold the payment");
    }

    static void assertUntouched(World world, String where) {
        assertConserved(world, where);
        yes(world.supplier.count(ORE) == 20, where + ": supplier must keep the goods");
        yes(world.warehouse.count(ORE) == 0, where + ": warehouse must stay empty");
        yes(world.cashbox.count(SILVER) == 1, where + ": cashbox must keep the money");
        yes(world.wallet.count(SILVER) == 0, where + ": wallet must stay empty");
    }

    static TxView view(RecoveryReport report, UUID id) {
        for (TxView view : report.transactions()) if (view.id().equals(id)) return view;
        return null;
    }

    // --- scenarios ------------------------------------------------------------

    static void happyPath() throws Exception {
        Path directory = freshDirectory("kingdom-authority-happy-");
        World world = world();
        UUID id = UUID.randomUUID();
        SettlementAuthority authority = new SettlementAuthority(directory);

        yes(authority.settle(request(id, world)) == Outcome.COMMITTED, "delivery must commit");
        assertFullyApplied(world, "committed delivery");
        yes(authority.settle(request(id, world)) == Outcome.DUPLICATE, "the same id must not settle twice");
        assertFullyApplied(world, "duplicate delivery");

        SettlementAuthority restarted = new SettlementAuthority(directory);
        RecoveryReport report = restarted.recover(world.registry());
        yes(!report.journalDamaged(), "a clean log must not be damaged");
        yes(report.quarantined().isEmpty(), "a committed delivery must not be quarantined");
        yes(view(report, id) != null && view(report, id).state() == State.RECEIPTED, "delivery must be receipted");
        assertFullyApplied(world, "restart");
        System.out.println("  ok: durable decision, idempotent delivery, clean restart");
    }

    static void crashMatrix() throws Exception {
        List<String[]> points = new ArrayList<>();
        points.add(new String[] { "prepare.before", "1" });
        points.add(new String[] { "journal.before_write", "1" });
        points.add(new String[] { "journal.after_write", "1" });
        points.add(new String[] { "journal.after_force", "1" });
        points.add(new String[] { "commit.before", "1" });
        points.add(new String[] { "journal.before_write", "2" });
        points.add(new String[] { "journal.after_write", "2" });
        points.add(new String[] { "journal.after_force", "2" });
        points.add(new String[] { "project.after_goods", "1" });
        points.add(new String[] { "project.after_coins", "1" });
        points.add(new String[] { "receipt.before", "1" });

        for (String[] entry : points) {
            String point = entry[0];
            int occurrence = Integer.parseInt(entry[1]);
            String where = point + "#" + occurrence;
            Path directory = freshDirectory("kingdom-authority-crash-");
            World world = world();
            UUID id = UUID.randomUUID();
            CrashPlan plan = new CrashPlan(point, occurrence);
            SettlementAuthority authority = new SettlementAuthority(directory, plan);
            boolean crashed = false;
            try {
                authority.settle(request(id, world));
            } catch (Crash expected) {
                crashed = true;
            }
            yes(crashed, where + ": the crash must happen");
            plan.simulatePowerLoss(authority.journalPath());

            SettlementAuthority restarted = new SettlementAuthority(directory);
            RecoveryReport report = restarted.recover(world.registry());
            TxView tx = view(report, id);
            if (tx == null) {
                assertUntouched(world, where + " (nothing decided)");
            } else if (tx.state() == State.RECEIPTED) {
                assertFullyApplied(world, where + " (decided and finished)");
            } else if (tx.state() == State.QUARANTINED) {
                assertUntouched(world, where + " (decided later, quarantined)");
            } else {
                throw new AssertionError(where + ": unexpected state " + tx.state());
            }
            assertConserved(world, where + " after recovery");

            // A second recovery must not change anything.
            RecoveryReport again = new SettlementAuthority(directory).recover(world.registry());
            yes(again.quarantined().size() == report.quarantined().size(), where + ": recovery must be idempotent");
            assertConserved(world, where + " after the second recovery");

            // The delivery id must never be applied twice.
            Outcome outcome = new SettlementAuthority(directory).settle(request(id, world));
            if (tx == null) {
                // Nothing was decided: the delivery may be settled again, exactly once.
                yes(outcome == Outcome.COMMITTED, where + ": an undecided delivery may be settled again");
                assertFullyApplied(world, where + " after the repeated request");
            } else {
                yes(outcome == Outcome.DUPLICATE || outcome == Outcome.QUARANTINED,
                        where + ": a known delivery must not settle again, was " + outcome);
                assertConserved(world, where + " after the repeated request");
            }
        }
        System.out.println("  ok: " + points.size() + " crash points, no creation, no loss, no double apply");
    }

    static void inconsistentState() throws Exception {
        Path directory = freshDirectory("kingdom-authority-inconsistent-");
        World world = world();
        UUID id = UUID.randomUUID();
        CrashPlan plan = new CrashPlan("project.after_goods", 1);
        SettlementAuthority authority = new SettlementAuthority(directory, plan);
        try {
            authority.settle(request(id, world));
        } catch (Crash expected) {
            // the goods moved, the payment did not
        }
        plan.simulatePowerLoss(authority.journalPath());
        // Someone else changes the warehouse behind the back of the authority.
        world.warehouse.set(ORE, 25);

        RecoveryReport report = new SettlementAuthority(directory).recover(world.registry());
        TxView tx = view(report, id);
        yes(tx != null && tx.state() == State.QUARANTINED, "an unexpected count must be quarantined");
        yes(tx.problem().equals("inconsistent_state"), "the reason must be reported, was " + tx.problem());
        yes(world.cashbox.count(SILVER) == 1, "a quarantined delivery must not move the money");
        yes(world.wallet.count(SILVER) == 0, "a quarantined delivery must not pay the wallet");
        yes(report.quarantined().contains(id), "the delivery must be listed as quarantined");
        System.out.println("  ok: an unexpected container state is quarantined, not guessed");
    }

    static void rejection() throws Exception {
        Path directory = freshDirectory("kingdom-authority-rejected-");
        World world = world();
        world.cashbox.set(SILVER, 0);
        UUID id = UUID.randomUUID();
        SettlementAuthority authority = new SettlementAuthority(directory);
        Request request = new Request(id, ORE, 20, 5L, world.supplier, world.warehouse, world.cashbox, world.wallet);
        yes(authority.settle(request) == Outcome.REJECTED, "a delivery without money must be rejected");
        yes(world.supplier.count(ORE) == 20 && world.warehouse.count(ORE) == 0,
                "a rejected delivery must not move the goods");
        yes(world.wallet.count(SILVER) == 0, "a rejected delivery must not pay the wallet");
        yes(authority.settle(request) == Outcome.REJECTED, "a rejected delivery stays rejected");
        yes(world.supplier.count(ORE) == 20 && world.warehouse.count(ORE) == 0,
                "a repeated rejected delivery must not move the goods");

        World poor = world();
        poor.supplier.set(ORE, 5);
        yes(new SettlementAuthority(freshDirectory("kingdom-authority-poor-"))
                .settle(request(UUID.randomUUID(), poor)) == Outcome.REJECTED,
                "a delivery without goods must be rejected");
        yes(poor.supplier.count(ORE) == 5 && poor.warehouse.count(ORE) == 0,
                "a rejected delivery must not move the goods");

        World full = world();
        full.warehouse.limit(ORE, 10);
        yes(new SettlementAuthority(freshDirectory("kingdom-authority-full-"))
                .settle(request(UUID.randomUUID(), full)) == Outcome.REJECTED,
                "a delivery without space must be rejected");
        yes(full.supplier.count(ORE) == 20 && full.warehouse.count(ORE) == 0,
                "a rejected delivery must not move the goods");
        System.out.println("  ok: a rejected delivery changes nothing and stays rejected");
    }

    static void parallelBuyers() throws Exception {
        Path directory = freshDirectory("kingdom-authority-parallel-");
        World world = world();
        world.supplier.set(ORE, 30);
        world.cashbox.set(SILVER, 0);
        world.cashbox.set(COPPER, 100);
        SettlementAuthority authority = new SettlementAuthority(directory);

        List<Outcome> outcomes = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            UUID id = UUID.randomUUID();
            Request request = new Request(id, ORE, 10, 1L,
                    world.supplier, world.warehouse, world.cashbox, world.wallet);
            Thread thread = new Thread(() -> {
                try {
                    Outcome outcome = authority.settle(request);
                    synchronized (outcomes) { outcomes.add(outcome); }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            try { thread.join(); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
        }
        yes(outcomes.size() == 2, "both buyers must be answered");
        for (Outcome outcome : outcomes) yes(outcome == Outcome.COMMITTED, "both buyers must be served");
        yes(world.goods() == 30, "goods must be conserved, was " + world.goods());
        yes(world.money() == 100, "money must be conserved, was " + world.money());
        yes(world.supplier.count(ORE) == 10, "both deliveries must be applied once");
        yes(world.warehouse.count(ORE) == 20, "both deliveries must be applied once");
        yes(world.wallet.count(COPPER) == 20, "both payments must be applied once");
        System.out.println("  ok: two parallel buyers, every delivery applied exactly once");
    }

    static void operatorResolution() throws Exception {
        Path directory = freshDirectory("kingdom-authority-resolve-");
        World world = world();
        UUID id = UUID.randomUUID();
        CrashPlan plan = new CrashPlan("commit.before", 1);
        SettlementAuthority authority = new SettlementAuthority(directory, plan);
        try {
            authority.settle(request(id, world));
        } catch (Crash expected) {
            // the decision was not made
        }
        plan.simulatePowerLoss(authority.journalPath());

        SettlementAuthority restarted = new SettlementAuthority(directory);
        RecoveryReport report = restarted.recover(world.registry());
        yes(report.quarantined().contains(id), "an undecided delivery must be quarantined");
        yes(restarted.settle(request(UUID.randomUUID(), world)) == Outcome.QUARANTINED,
                "a quarantined delivery must block other deliveries");
        yes(restarted.resolve(id, Resolution.CONFIRMED_NOT_APPLIED) == Outcome.COMMITTED, "the operator must resolve");
        yes(restarted.settle(request(UUID.randomUUID(), world)) == Outcome.COMMITTED,
                "a resolved delivery must unblock the others");
        assertConserved(world, "operator resolution");
        yes(world.warehouse.count(ORE) == 20, "the confirmed delivery must be applied once");
        System.out.println("  ok: an operator resolves a quarantined delivery and the queue continues");
    }

    static void damagedJournal() throws Exception {
        Path directory = freshDirectory("kingdom-authority-damaged-");
        World world = world();
        UUID id = UUID.randomUUID();
        CrashPlan plan = new CrashPlan("commit.before", 1);
        SettlementAuthority authority = new SettlementAuthority(directory, plan);
        try {
            authority.settle(request(id, world));
        } catch (Crash expected) {
            // the decision was not made
        }
        plan.simulatePowerLoss(authority.journalPath());
        Files.writeString(authority.journalPath(), "garbage\n", java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);

        RecoveryReport report = new SettlementAuthority(directory).recover(world.registry());
        yes(report.journalDamaged(), "a damaged log must be reported");
        yes(report.quarantined().contains(id), "a damaged log must not be trusted");
        assertUntouched(world, "damaged log");
        System.out.println("  ok: a damaged log is reported and quarantined, never applied blindly");
    }

    public static void main(String[] args) throws Exception {
        happyPath();
        crashMatrix();
        inconsistentState();
        rejection();
        parallelBuyers();
        operatorResolution();
        damagedJournal();
        System.out.println("PASS settlement authority: durable decision, idempotent projection, crash recovery, conservation");
    }
}
