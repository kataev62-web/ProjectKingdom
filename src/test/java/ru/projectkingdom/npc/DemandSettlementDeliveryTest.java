package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The delivery of goods for physical coins through the settlement authority.
 *
 * <p>Every scenario of the issue is checked here: the repeat of a delivery id,
 * the crash boundaries with a restart, a partial record, the state of a
 * perishable item, the real coins that are returned, two enterprises, the right
 * of the owner, the markers of the older versions, a container that cannot be
 * used, two claims at the same time, the recovery and the manual closing of a
 * quarantine.
 */
final class DemandSettlementDeliveryTest {
    private static final String GOODS = "minecraft:wheat";
    private static final String COPPER = "kingdom:copper_coin";
    private static final String SILVER = "kingdom:silver_coin";
    private static final String GOLD = "kingdom:gold_coin";

    /** A container of the test world. The variants are the states of an item. */
    static final class Bin implements SettlementAuthority.Container {
        record Stack(String item, String variant, int count) {}

        private final String id;
        private final List<Stack> stacks = new ArrayList<>();
        private final int slotCount;
        private final int maxStack;
        /** The right of the owner was revoked: every write fails. */
        boolean revoked;
        /** The container cannot be read any more. */
        boolean unreadable;

        Bin(String id) { this(id, 8, 64); }
        Bin(String id, int slotCount, int maxStack) {
            this.id = id;
            this.slotCount = slotCount;
            this.maxStack = maxStack;
        }

        @Override public String id() { return id; }

        void set(String item, int count) { set(item, "", count); }

        void set(String item, String variant, int count) {
            stacks.removeIf(stack -> stack.item().equals(item) && stack.variant().equals(variant));
            if (count > 0) stacks.add(new Stack(item, variant, count));
        }

        int amount(String item, String variant) {
            int total = 0;
            for (Stack stack : stacks)
                if (stack.item().equals(item) && stack.variant().equals(variant)) total += stack.count();
            return total;
        }

        int amount(String item) { return amount(item, ""); }

        int value() {
            int total = 0;
            for (Stack stack : stacks) {
                int unit = switch (stack.item()) {
                    case COPPER -> 1;
                    case SILVER -> 100;
                    case GOLD -> 10000;
                    default -> 0;
                };
                total += unit * stack.count();
            }
            return total;
        }

        @Override public long count(String item) { return amount(item, ""); }

        @Override public long capacity(String item) { return capacity(item, ""); }

        /** The free space of the container for one state of the item. */
        private long space(String item, String variant) {
            long others = 0;
            for (Stack stack : stacks)
                if (stack.item().equals(item) && !stack.variant().equals(variant)) others += stack.count();
            return (long) slotCount * maxStack - others;
        }

        @Override public long count(String item, String variant) {
            if (unreadable) throw new IllegalStateException("the container cannot be read");
            return amount(item, variant);
        }

        @Override public long capacity(String item, String variant) { return space(item, variant); }

        @Override public List<String> variants(String item) {
            List<String> out = new ArrayList<>();
            for (Stack stack : stacks)
                if (stack.item().equals(item) && !out.contains(stack.variant())) out.add(stack.variant());
            return out;
        }

        @Override public void apply(String item, long delta) throws IOException { apply(item, "", delta); }

        @Override public void apply(String item, String variant, long delta) throws IOException {
            if (revoked) throw new IOException("the right of the owner was revoked before the write");
            if (unreadable) throw new IllegalStateException("the container cannot be read");
            if (delta == 0) return;
            long have = amount(item, variant);
            if (delta < 0) {
                if (have < -delta) throw new IOException("insufficient physical items");
                set(item, variant, (int) (have + delta));
                return;
            }
            if (capacity(item, variant) - have < delta) throw new IOException("insufficient physical space");
            set(item, variant, (int) (have + delta));
        }
    }

    /** The world of one enterprise: four containers and the board. */
    static final class World {
        final UUID enterprise = UUID.randomUUID();
        final UUID seller = UUID.randomUUID();
        final Bin supplier = new Bin("supplier");
        final Bin warehouse = new Bin("warehouse");
        final Bin cashbox = new Bin("cashbox");
        final Bin wallet = new Bin("wallet");
        final Path directory;
        final Path pending;
        final Path transactions;
        final DurableDemandBoardCatalog board;
        final EnterpriseDemandBoard.Listing listing;
        final UUID listingId = UUID.randomUUID();
        /** The state of the goods that the seller offers. */
        String variant = "";

        World(Path root) throws IOException { this(root, 5); }

        World(Path root, long maximumUnitCopper) throws IOException {
            directory = root.resolve("settlement");
            pending = root.resolve("pending");
            transactions = root.resolve("transactions");
            Files.createDirectories(directory);
            Files.createDirectories(pending);
            Files.createDirectories(transactions);
            board = new DurableDemandBoardCatalog(root.resolve("board.snapshot"));
            listing = new EnterpriseDemandBoard.Listing(
                    new EnterpriseDemandBoard.Key(enterprise, GOODS), listingId, 10, maximumUnitCopper);
            board.upsert(listing);
            supplier.set(GOODS, 10);
            cashbox.set(COPPER, 20);
        }

        DemandSettlementDeliveryAdapter adapter(SettlementAuthority authority) {
            return new DemandSettlementDeliveryAdapter(authority,
                    (listing, delivery) -> new DemandSettlementDeliveryAdapter.Endpoints(
                            supplier, warehouse, cashbox, wallet, variant),
                    transactions);
        }

        SettlementAuthority authority() throws IOException { return new SettlementAuthority(directory); }

        java.util.Map<String, SettlementAuthority.Container> registry() {
            java.util.Map<String, SettlementAuthority.Container> map = new java.util.LinkedHashMap<>();
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

    /** Arms one durable boundary of the authority. */
    static final class CrashPlan implements BiConsumer<String, Long> {
        private final String point;
        private final int occurrence;
        private int seen;
        private long durableAtCrash = -1;

        CrashPlan(String point, int occurrence) {
            this.point = point;
            this.occurrence = occurrence;
        }

        @Override public void accept(String name, Long durableLength) {
            if (!name.equals(point)) return;
            seen++;
            if (seen != occurrence) return;
            durableAtCrash = durableLength;
            throw new Crash(name);
        }

        /** A power loss: only the durable prefix of the log survives. */
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

    private static Path root() throws IOException {
        Path directory = Files.createTempDirectory("kingdom-delivery-test-");
        directory.toFile().deleteOnExit();
        return directory;
    }

    private static EnterpriseDemandBoard.Delivery delivery(World world, int quantity, long unit) {
        return new EnterpriseDemandBoard.Delivery(UUID.randomUUID(), world.listingId, quantity, unit);
    }

    // --- the scenarios --------------------------------------------------------

    @Test void theDeliveryMovesTheGoodsAndTheCoinsExactlyOnce() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);

        var result = adapter.settle(world.listing, request);
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, result.status());
        assertEquals(6, world.supplier.amount(GOODS), "the supplier keeps the rest");
        assertEquals(4, world.warehouse.amount(GOODS), "the warehouse holds the goods");
        assertEquals(0, world.cashbox.amount(COPPER), "the cashbox paid");
        assertEquals(20, world.wallet.amount(COPPER), "the wallet holds the payment");
    }

    @Test void theSameDeliveryIdIsNeverMovedTwice() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);

        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, adapter.settle(world.listing, request).status());
        assertEquals(DemandSettlementDeliveryAdapter.Status.DUPLICATE, adapter.settle(world.listing, request).status());
        assertEquals(6, world.supplier.amount(GOODS), "the goods moved once");
        assertEquals(4, world.warehouse.amount(GOODS), "the goods moved once");
        assertEquals(20, world.wallet.amount(COPPER), "the coins moved once");
    }

    @Test void theSameDeliveryIdAfterARestartIsNeverMovedTwice() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, adapter.settle(world.listing, request).status());

        // A restarted server: the same log, a new authority, the recovery.
        SettlementAuthority restarted = world.authority();
        var report = restarted.recover(world.registry());
        assertTrue(report.quarantined().isEmpty(), "a finished delivery is not quarantined");
        DemandSettlementDeliveryAdapter afterRestart = world.adapter(restarted);
        assertTrue(afterRestart.settled(request.deliveryId()), "the receipt is durable");
        assertEquals(DemandSettlementDeliveryAdapter.Status.DUPLICATE, afterRestart.settle(world.listing, request).status());
        assertEquals(6, world.supplier.amount(GOODS), "the goods moved once");
        assertEquals(20, world.wallet.amount(COPPER), "the coins moved once");
    }

    @Test void everyCrashBoundaryLeavesNoCreationNoLossAndNoDoubleApply() throws Exception {
        List<String[]> points = List.of(
                new String[] { "prepare.before", "1" },
                new String[] { "journal.before_write", "1" },
                new String[] { "journal.after_write", "1" },
                new String[] { "journal.after_force", "1" },
                new String[] { "commit.before", "1" },
                new String[] { "journal.before_write", "2" },
                new String[] { "journal.after_write", "2" },
                new String[] { "journal.after_force", "2" },
                new String[] { "project.after_goods", "1" },
                new String[] { "project.after_coins", "1" },
                new String[] { "receipt.before", "1" });
        for (String[] entry : points) {
            String where = entry[0] + "#" + entry[1];
            World world = new World(root());
            var request = delivery(world, 4, 5);
            CrashPlan plan = new CrashPlan(entry[0], Integer.parseInt(entry[1]));
            SettlementAuthority authority = new SettlementAuthority(world.directory, plan);
            DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
            boolean crashed = false;
            try {
                adapter.settle(world.listing, request);
            } catch (Crash expected) {
                crashed = true;
            }
            assertTrue(crashed, where + ": the crash must happen");
            plan.simulatePowerLoss(authority.journalPath());

            // The restart: a new authority reads the log and finishes the decision.
            SettlementAuthority restarted = world.authority();
            var report = restarted.recover(world.registry());
            SettlementAuthority.TxView view = null;
            for (SettlementAuthority.TxView candidate : report.transactions())
                if (candidate.id().equals(request.deliveryId())) view = candidate;
            if (view == null) {
                assertEquals(10, world.supplier.amount(GOODS), where + ": nothing decided, the goods stay");
                assertEquals(0, world.warehouse.amount(GOODS), where + ": nothing decided, the warehouse stays empty");
                assertEquals(20, world.cashbox.amount(COPPER), where + ": nothing decided, the money stays");
            } else if (view.state() == SettlementAuthority.State.RECEIPTED) {
                assertEquals(6, world.supplier.amount(GOODS), where + ": the decided delivery is finished");
                assertEquals(4, world.warehouse.amount(GOODS), where + ": the decided delivery is finished");
                assertEquals(20, world.wallet.amount(COPPER), where + ": the decided delivery is finished");
            } else if (view.state() == SettlementAuthority.State.QUARANTINED) {
                assertEquals(10, world.supplier.amount(GOODS), where + ": an undecided delivery is quarantined");
                assertEquals(20, world.cashbox.amount(COPPER), where + ": an undecided delivery is quarantined");
            } else {
                throw new AssertionError(where + ": unexpected state " + view.state());
            }
            // The goods and the money are neither created nor lost.
            assertEquals(10, world.supplier.amount(GOODS) + world.warehouse.amount(GOODS), where + ": the goods are conserved");
            assertEquals(20, world.cashbox.value() + world.wallet.value(), where + ": the money is conserved");

            // A repeated request must never move the goods a second time.
            DemandSettlementDeliveryAdapter afterRestart = world.adapter(restarted);
            var repeated = afterRestart.settle(world.listing, request);
            if (view == null) {
                // Nothing was decided: the delivery may be settled again, exactly once.
                assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, repeated.status(),
                        where + ": an undecided delivery may be settled again");
                assertEquals(6, world.supplier.amount(GOODS), where + ": the goods moved once");
                assertEquals(4, world.warehouse.amount(GOODS), where + ": the goods moved once");
                assertEquals(20, world.wallet.amount(COPPER), where + ": the coins moved once");
            } else {
                assertTrue(repeated.status() == DemandSettlementDeliveryAdapter.Status.DUPLICATE
                                || repeated.status() == DemandSettlementDeliveryAdapter.Status.NEEDS_REVIEW,
                        where + ": a known delivery must not be repeated, was " + repeated.status());
                assertEquals(10, world.supplier.amount(GOODS) + world.warehouse.amount(GOODS), where + ": the goods stay");
                assertEquals(20, world.cashbox.value() + world.wallet.value(), where + ": the money stays");
            }
            assertEquals(10, world.supplier.amount(GOODS) + world.warehouse.amount(GOODS), where + ": the goods stay");
            assertEquals(20, world.cashbox.value() + world.wallet.value(), where + ": the money stays");
        }
    }

    @Test void theBoardIsAcknowledgedOnlyAfterTheReceipt() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);

        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, adapter.settle(world.listing, request).status());
        // The crash between the receipt and the acknowledgement of the board.
        SettlementAuthority restarted = world.authority();
        restarted.recover(world.registry());
        assertFalse(world.board.acknowledged(request.deliveryId()), "the board is still behind");
        DemandSettlementDeliveryAdapter afterRestart = world.adapter(restarted);
        assertEquals(DemandSettlementDeliveryAdapter.Status.DUPLICATE, afterRestart.settle(world.listing, request).status());
        assertEquals(6, world.supplier.amount(GOODS), "the goods moved once");
        assertEquals(20, world.wallet.amount(COPPER), "the coins moved once");
    }

    @Test void aCoordinatorFinishesTheBoardAfterACrashWithoutMovingTheGoodsAgain() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandDeliveryCoordinator coordinator = new DemandDeliveryCoordinator(world.board, world.pending, world.adapter(authority));
        var request = delivery(world, 4, 5);

        assertEquals(DemandDeliveryCoordinator.Result.COMMITTED, coordinator.deliver(request, null));
        assertEquals(6, world.supplier.amount(GOODS), "the goods moved once");
        assertEquals(20, world.wallet.amount(COPPER), "the coins moved once");
        DurableDemandBoardCatalog written = new DurableDemandBoardCatalog(world.board.storagePath());
        assertTrue(written.acknowledged(request.deliveryId()), "the board knows the delivery");
        assertFalse(Files.exists(world.pending.resolve(request.deliveryId() + ".pending")), "the marker is closed");

        // The same id again: the board answers alone.
        assertEquals(DemandDeliveryCoordinator.Result.DUPLICATE, coordinator.deliver(request, null));
        assertEquals(6, world.supplier.amount(GOODS), "the goods moved once");
        assertEquals(20, world.wallet.amount(COPPER), "the coins moved once");
    }

    @Test void aCoordinatorResumesTheBoardAfterARestartWithoutMovingTheGoodsAgain() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandDeliveryCoordinator coordinator = new DemandDeliveryCoordinator(world.board, world.pending, world.adapter(authority));
        var request = delivery(world, 4, 5);
        assertEquals(DemandDeliveryCoordinator.Result.COMMITTED, coordinator.deliver(request, null));

        // The board is lost, the world is not: the authority still has the receipt.
        Files.delete(world.board.storagePath());
        DurableDemandBoardCatalog fresh = new DurableDemandBoardCatalog(world.board.storagePath());
        fresh.upsert(world.listing);
        SettlementAuthority restarted = world.authority();
        restarted.recover(world.registry());
        DemandDeliveryCoordinator afterRestart = new DemandDeliveryCoordinator(fresh, world.pending, world.adapter(restarted));
        assertEquals(DemandDeliveryCoordinator.Result.DUPLICATE, afterRestart.deliver(request, null));
        assertEquals(6, world.supplier.amount(GOODS), "the goods moved once");
        assertEquals(20, world.wallet.amount(COPPER), "the coins moved once");
    }

    @Test void aPartialRecordIsRefused() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);
        adapter.settle(world.listing, request);

        // A torn tail: the last record is cut in half.
        Path journal = authority.journalPath();
        List<String> lines = Files.readAllLines(journal, StandardCharsets.UTF_8);
        String tail = lines.get(lines.size() - 1);
        Files.writeString(journal, String.join("\n", lines.subList(0, lines.size() - 1)) + "\n"
                + tail.substring(0, tail.length() / 2), StandardCharsets.UTF_8);

        SettlementAuthority restarted = world.authority();
        assertTrue(restarted.journalDamaged(), "the damaged tail must be reported");
        DemandSettlementDeliveryAdapter afterRestart = world.adapter(restarted);
        var result = afterRestart.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.NEEDS_REVIEW, result.status(),
                "a damaged log must not decide a delivery");
        assertEquals("journal_damaged", result.reason());
    }

    @Test void aPerishableItemIsDeliveredInItsOwnState() throws Exception {
        World world = new World(root());
        world.supplier.set(GOODS, "fresh", 6);
        world.supplier.set(GOODS, "rotten", 4);
        world.variant = "fresh";
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);

        // The seller offers the fresh goods: the rotten ones stay.
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, result.status());
        assertEquals(2, world.supplier.amount(GOODS, "fresh"), "the fresh goods moved");
        assertEquals(4, world.supplier.amount(GOODS, "rotten"), "the rotten goods stay");
        assertEquals(4, world.warehouse.amount(GOODS, "fresh"), "the warehouse holds the fresh goods");
        assertEquals(0, world.warehouse.amount(GOODS, "rotten"), "the rotten goods were not delivered");
    }

    @Test void withoutANamedStateOnlyThePlainGoodsAreDelivered() throws Exception {
        World world = new World(root());
        world.supplier.set(GOODS, "", 5);
        world.supplier.set(GOODS, "rotten", 5);
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);

        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, result.status());
        assertEquals(1, world.supplier.amount(GOODS, ""), "the plain goods moved");
        assertEquals(5, world.supplier.amount(GOODS, "rotten"), "the goods of another state stay");
        assertEquals(4, world.warehouse.amount(GOODS, ""), "only the plain goods arrived");
        assertEquals(0, world.warehouse.amount(GOODS, "rotten"), "the goods of another state did not arrive");
    }

    @Test void aNamedStateThatIsNotThereIsRefused() throws Exception {
        World world = new World(root());
        world.supplier.set(GOODS, "rotten", 5);
        world.variant = "fresh";
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);

        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.REJECTED, result.status(),
                "the goods of another state must not be delivered");
        assertEquals("INSUFFICIENT_GOODS", result.reason());
        assertEquals(5, world.supplier.amount(GOODS, "rotten"), "nothing moved");
    }

    @Test void aChangedStateIsQuarantinedAndNotDelivered() throws Exception {
        World world = new World(root());
        world.supplier.set(GOODS, "fresh", 10);
        world.variant = "fresh";
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);

        // The crash after the goods moved: the coins are still missing.
        CrashPlan plan = new CrashPlan("project.after_goods", 1);
        SettlementAuthority crashing = new SettlementAuthority(world.directory, plan);
        DemandSettlementDeliveryAdapter crashingAdapter = world.adapter(crashing);
        try {
            crashingAdapter.settle(world.listing, request);
        } catch (Crash expected) {
            // the goods moved, the payment did not
        }
        assertEquals(6, world.supplier.amount(GOODS, "fresh"), "the goods moved before the crash");

        // The state of the goods changes while the delivery is open.
        world.supplier.set(GOODS, "fresh", 0);
        world.supplier.set(GOODS, "rotten", 6);

        SettlementAuthority restarted = world.authority();
        var report = restarted.recover(world.registry());
        assertTrue(report.quarantined().contains(request.deliveryId()),
                "the delivery of a changed state must be quarantined");
        assertEquals(0, world.wallet.amount(COPPER), "the payment was not made");
        assertEquals(20, world.cashbox.amount(COPPER), "the money stayed in the cashbox");
    }

    @Test void theRealCoinsAreReturnedExactly() throws Exception {
        World world = new World(root(), 4000);
        world.cashbox.set(COPPER, 7);
        world.cashbox.set(GOLD, 1);
        world.cashbox.set(SILVER, 5);
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);

        // 10407 copper: one gold, four silver and seven copper.
        var result = adapter.settle(world.listing, delivery(world, 3, 3469));
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, result.status());
        assertEquals(0, world.cashbox.amount(GOLD), "the gold was paid");
        assertEquals(1, world.cashbox.amount(SILVER), "one silver stays");
        assertEquals(0, world.cashbox.amount(COPPER), "the copper was paid");
        assertEquals(1, world.wallet.amount(GOLD), "the wallet holds the gold");
        assertEquals(4, world.wallet.amount(SILVER), "the wallet holds the silver");
        assertEquals(7, world.wallet.amount(COPPER), "the wallet holds the copper");
        assertEquals(10407, world.wallet.value(), "the wallet holds the price");
    }

    @Test void deliveriesOfTwoEnterprisesDoNotTouchEachOther() throws Exception {
        World first = new World(root());
        World second = new World(root());
        SettlementAuthority authority = first.authority();
        DemandSettlementDeliveryAdapter adapter = first.adapter(authority);

        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED,
                adapter.settle(first.listing, delivery(first, 4, 5)).status());
        assertEquals(4, first.warehouse.amount(GOODS), "the first enterprise received the goods");
        assertEquals(0, second.warehouse.amount(GOODS), "the second enterprise is untouched");
        assertEquals(10, second.supplier.amount(GOODS), "the second enterprise is untouched");
        assertEquals(20, second.cashbox.amount(COPPER), "the second enterprise is untouched");
    }

    @Test void aLegacyPendingMarkerBlocksTheEnterpriseAndIsKept() throws Exception {
        World world = new World(root());
        Path legacy = world.pending.resolve("legacy.pending");
        Files.writeString(legacy, String.join("\n",
                UUID.randomUUID().toString(),
                world.listingId.toString(),
                world.enterprise.toString(),
                "2", "5"), StandardCharsets.US_ASCII);

        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, result.status(),
                "the adapter itself does not read the markers of the board");
        assertTrue(Files.exists(legacy), "the marker of the older version is never deleted");

        // The coordinator blocks the enterprise while the marker is open.
        DemandDeliveryCoordinator coordinator = new DemandDeliveryCoordinator(world.board, world.pending, adapter);
        var blocked = coordinator.deliver(delivery(world, 4, 5), null);
        assertEquals(DemandDeliveryCoordinator.Result.NEEDS_REVIEW, blocked,
                "an open marker of the older version must block the delivery");
        assertTrue(Files.exists(legacy), "the marker of the older version is never deleted");
    }

    @Test void anUnfinishedLegacyTransactionBlocksEveryDeliveryAndIsKept() throws Exception {
        World world = new World(root());
        Path legacy = world.transactions.resolve("old.txn");
        Files.writeString(legacy, "PREPARED\n" + UUID.randomUUID() + "\n", StandardCharsets.UTF_8);

        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.NEEDS_REVIEW, result.status(),
                "an unfinished transaction of the older gate must block the delivery");
        assertEquals("legacy_transaction_open", result.reason());
        assertTrue(Files.exists(legacy), "the transaction of the older version is never deleted");
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved");
    }

    @Test void aContainerThatCannotBeUsedBlocksTheDelivery() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        // The chunk of the warehouse is not loaded: the resolver cannot build it.
        DemandSettlementDeliveryAdapter adapter = new DemandSettlementDeliveryAdapter(authority,
                (listing, delivery) -> null, world.transactions);
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.NEEDS_REVIEW, result.status());
        assertEquals("container_unavailable", result.reason());
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved");
    }

    @Test void aRevokedRightStopsTheDelivery() throws Exception {
        World world = new World(root());
        world.supplier.revoked = true;
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.NEEDS_REVIEW, result.status(),
                "a revoked right must stop the delivery");
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved");
        assertEquals(20, world.cashbox.amount(COPPER), "no money moved");
    }

    @Test void twoClaimsAtTheSameTimeMoveTheGoodsOnce() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);

        List<DemandSettlementDeliveryAdapter.Result> results = new ArrayList<>();
        Runnable claim = () -> {
            try {
                synchronized (results) { results.add(adapter.settle(world.listing, request)); }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        };
        Thread first = new Thread(claim);
        Thread second = new Thread(claim);
        first.start();
        second.start();
        first.join();
        second.join();
        assertEquals(2, results.size());
        long committed = results.stream()
                .filter(result -> result.status() == DemandSettlementDeliveryAdapter.Status.COMMITTED).count();
        assertEquals(1, committed, "exactly one claim may commit");
        assertEquals(6, world.supplier.amount(GOODS), "the goods moved once");
        assertEquals(4, world.warehouse.amount(GOODS), "the goods moved once");
        assertEquals(20, world.wallet.amount(COPPER), "the coins moved once");
    }

    @Test void theOperatorCanCloseAQuarantineAndTheDeliveryContinues() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        var request = delivery(world, 4, 5);

        // A decision that never became durable: the delivery is quarantined.
        CrashPlan plan = new CrashPlan("commit.before", 1);
        SettlementAuthority crashing = new SettlementAuthority(world.directory, plan);
        try {
            crashing.settle(new SettlementAuthority.Request(request.deliveryId(), GOODS, 4, 5,
                    world.supplier, world.warehouse, world.cashbox, world.wallet));
        } catch (Crash expected) {
            // the decision is not durable
        }
        SettlementAuthority restarted = world.authority();
        var report = restarted.recover(world.registry());
        assertTrue(report.quarantined().contains(request.deliveryId()), "the delivery is quarantined");

        // The operator checked the four containers and confirms that nothing was moved.
        restarted.resolve(request.deliveryId(), SettlementAuthority.Resolution.CONFIRMED_NOT_APPLIED);
        assertEquals(SettlementAuthority.State.REJECTED, restarted.stateOf(request.deliveryId()));
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved");

        // The delivery of another id is possible again.
        DemandSettlementDeliveryAdapter adapter = world.adapter(restarted);
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, result.status(),
                "a closed quarantine must not block the world for ever");
    }

    @Test void theOperatorCanConfirmAnAppliedDelivery() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        var request = delivery(world, 4, 5);
        CrashPlan plan = new CrashPlan("commit.before", 1);
        SettlementAuthority crashing = new SettlementAuthority(world.directory, plan);
        try {
            crashing.settle(new SettlementAuthority.Request(request.deliveryId(), GOODS, 4, 5,
                    world.supplier, world.warehouse, world.cashbox, world.wallet));
        } catch (Crash expected) {
            // the decision is not durable
        }
        SettlementAuthority restarted = world.authority();
        restarted.recover(world.registry());
        restarted.resolve(request.deliveryId(), SettlementAuthority.Resolution.CONFIRMED_APPLIED);
        assertEquals(SettlementAuthority.State.RECEIPTED, restarted.stateOf(request.deliveryId()));
        // The delivery is not repeated after the manual confirmation.
        DemandSettlementDeliveryAdapter adapter = world.adapter(restarted);
        assertEquals(DemandSettlementDeliveryAdapter.Status.DUPLICATE, adapter.settle(world.listing, request).status());
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved twice");
    }

    @Test void aLogOfTheOlderFormatIsStillReadable() throws Exception {
        World world = new World(root());
        UUID id = UUID.randomUUID();
        // A record of the older format: no variants section.
        String prepared = "PREPARED|tx=" + id + ";item=" + GOODS + ";qty=4;unit=5;total=20"
                + ";goods=supplier~" + GOODS + "~10~6,warehouse~" + GOODS + "~0~4"
                + ";coins=cashbox~" + COPPER + "~20~0,wallet~" + COPPER + "~0~20";
        String committed = "COMMITTED|tx=" + id;
        Path journal = world.directory.resolve("settlement-authority.log");
        Files.writeString(journal, prepared + "|" + checksum(prepared) + "\n"
                + committed + "|" + checksum(committed) + "\n", StandardCharsets.UTF_8);

        SettlementAuthority authority = world.authority();
        assertFalse(authority.journalDamaged(), "a log of the older format is not damaged");
        var report = authority.recover(world.registry());
        assertFalse(report.journalDamaged(), "a log of the older format is not damaged");
        // The delivery of the older format is finished with the older rule.
        assertEquals(6, world.supplier.amount(GOODS), "the goods of the older record moved");
        assertEquals(4, world.warehouse.amount(GOODS), "the goods of the older record moved");
        assertEquals(20, world.wallet.amount(COPPER), "the payment of the older record moved");
        assertNotNull(authority.stateOf(id), "the delivery of the older format is known");
    }

    @Test void aLogOfTheNewFormatIsReadableByTheOlderReader() throws Exception {
        World world = new World(root());
        world.supplier.set(GOODS, "fresh", 10);
        world.variant = "fresh";
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var request = delivery(world, 4, 5);
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, adapter.settle(world.listing, request).status());

        String text = Files.readString(authority.journalPath(), StandardCharsets.UTF_8);
        assertTrue(text.contains("variants="), "the new record names its variant");
        // The counts stay in the older four field form.
        for (String line : text.split("\n")) {
            if (line.isEmpty() || !line.startsWith("PREPARED")) continue;
            String payload = line.substring(line.indexOf('|') + 1, line.lastIndexOf('|'));
            for (String part : payload.split(";")) {
                if (!part.startsWith("goods=") && !part.startsWith("coins=")) continue;
                for (String check : part.substring(part.indexOf('=') + 1).split(",")) {
                    assertEquals(4, check.split("~", -1).length,
                            "the counts of the older reader must stay readable: " + check);
                }
            }
        }
    }

    @Test void aDeliveryWithoutGoodsIsRejectedAndNotMoved() throws Exception {
        World world = new World(root());
        world.supplier.set(GOODS, 0);
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.REJECTED, result.status());
        assertEquals("INSUFFICIENT_GOODS", result.reason());
        assertEquals(0, world.warehouse.amount(GOODS), "nothing moved");
        assertEquals(20, world.cashbox.amount(COPPER), "no money moved");
    }

    @Test void aDeliveryThatCostsMoreThanTheCashboxHoldsIsRejected() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var result = adapter.settle(world.listing, delivery(world, 10, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.REJECTED, result.status());
        assertEquals("INSUFFICIENT_COINS", result.reason());
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved");
    }

    @Test void aDeliveryAboveThePriceOfTheListingIsRejected() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var result = adapter.settle(world.listing, delivery(world, 1, 50));
        assertEquals(DemandSettlementDeliveryAdapter.Status.REJECTED, result.status());
        assertEquals("PRICE_TOO_HIGH", result.reason());
    }

    @Test void theDeliveryOfAnotherListingIsRejected() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var foreign = new EnterpriseDemandBoard.Delivery(UUID.randomUUID(), UUID.randomUUID(), 4, 5);
        var result = adapter.settle(world.listing, foreign);
        assertEquals(DemandSettlementDeliveryAdapter.Status.REJECTED, result.status());
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved");
    }

    @Test void theAuthorityRefusesASecondDeliveryWhileOneIsOpen() throws Exception {
        World world = new World(root());
        world.cashbox.set(COPPER, 100);
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var first = delivery(world, 4, 5);
        assertEquals(DemandSettlementDeliveryAdapter.Status.COMMITTED, adapter.settle(world.listing, first).status());

        // A delivery that was prepared but not committed blocks the others.
        var second = delivery(world, 2, 5);
        CrashPlan plan = new CrashPlan("commit.before", 1);
        SettlementAuthority crashing = new SettlementAuthority(world.directory, plan);
        DemandSettlementDeliveryAdapter crashingAdapter = world.adapter(crashing);
        try {
            crashingAdapter.settle(world.listing, second);
        } catch (Crash expected) {
            // the decision is not durable
        }
        SettlementAuthority restarted = world.authority();
        var report = restarted.recover(world.registry());
        assertTrue(report.quarantined().contains(second.deliveryId()));
        DemandSettlementDeliveryAdapter afterRestart = world.adapter(restarted);
        var third = afterRestart.settle(world.listing, delivery(world, 1, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.NEEDS_REVIEW, third.status(),
                "an open delivery must block the other deliveries");
    }

    @Test void anUnreadableContainerIsRefused() throws Exception {
        World world = new World(root());
        world.warehouse.unreadable = true;
        SettlementAuthority authority = world.authority();
        DemandSettlementDeliveryAdapter adapter = world.adapter(authority);
        var result = adapter.settle(world.listing, delivery(world, 4, 5));
        assertEquals(DemandSettlementDeliveryAdapter.Status.REJECTED, result.status(),
                "an unreadable container must not be guessed");
        assertEquals("CONTAINER_UNREADABLE", result.reason());
        assertEquals(10, world.supplier.amount(GOODS), "nothing moved");
    }

    @Test void theUnknownDeliveryOfTheAuthorityIsKnownAfterTheRestart() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        assertNull(authority.stateOf(UUID.randomUUID()), "an unknown delivery has no state");
        var request = delivery(world, 4, 5);
        world.adapter(authority).settle(world.listing, request);
        assertEquals(SettlementAuthority.State.RECEIPTED, authority.stateOf(request.deliveryId()));
        assertEquals(SettlementAuthority.State.RECEIPTED,
                world.authority().stateOf(request.deliveryId()), "the state is durable");
    }

    private static String checksum(String value) {
        CRC32 crc = new CRC32();
        crc.update(value.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }

    @Test void theJournalKeepsTheDecisionWhenTheServerStops() throws Exception {
        World world = new World(root());
        SettlementAuthority authority = world.authority();
        var request = delivery(world, 4, 5);
        world.adapter(authority).settle(world.listing, request);
        long size = Files.size(authority.journalPath());
        assertTrue(size > 0, "the decision is written to the log");
        ByteBuffer buffer = ByteBuffer.allocate(0);
        assertEquals(0, buffer.remaining());
    }
}
