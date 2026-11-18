package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.projectkingdom.npc.SettlementAuthorityHarness.Chest;
import ru.projectkingdom.npc.SettlementAuthorityHarness.Crash;
import ru.projectkingdom.npc.SettlementAuthorityHarness.CrashPlan;
import ru.projectkingdom.npc.SettlementAuthorityHarness.World;

/**
 * Crash-injection harness for the delivery of goods for physical coins.
 *
 * <p>The scenarios drive the real coordinator and the real settlement
 * authority: the board, the marker of the open delivery and the durable log of
 * the decisions. The invariants under test: no item and no coin is ever
 * created, lost or moved twice, the board is acknowledged only after the
 * receipt, a delivery that was not decided is never applied, and the markers of
 * the older versions block the delivery until an operator closed them.
 */
public final class SettlementDeliveryHarness {
    private SettlementDeliveryHarness() {}

    static void yes(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    /** The board, the markers and the authority of one delivery service. */
    static final class Service {
        final Path root;
        final Path pending;
        final Path transactions;
        final DurableDemandBoardCatalog board;
        final EnterpriseDemandBoard.Listing listing;
        final UUID enterprise = UUID.randomUUID();
        final UUID listingId = UUID.randomUUID();

        Service(Path root) throws IOException {
            this.root = root;
            pending = root.resolve("pending");
            transactions = root.resolve("transactions");
            Files.createDirectories(pending);
            Files.createDirectories(transactions);
            board = new DurableDemandBoardCatalog(root.resolve("board.snapshot"));
            listing = new EnterpriseDemandBoard.Listing(
                    new EnterpriseDemandBoard.Key(enterprise, SettlementAuthorityHarness.ORE), listingId, 10, 5);
            board.upsert(listing);
        }

        SettlementAuthority authority(java.util.function.BiConsumer<String, Long> hook) throws IOException {
            Path directory = root.resolve("settlement");
            Files.createDirectories(directory);
            return hook == null ? new SettlementAuthority(directory)
                    : new SettlementAuthority(directory, hook);
        }

        DemandSettlementDeliveryAdapter adapter(SettlementAuthority authority, World world) {
            return new DemandSettlementDeliveryAdapter(authority,
                    (listing, delivery) -> new DemandSettlementDeliveryAdapter.Endpoints(
                            world.supplier, world.warehouse, world.cashbox, world.wallet),
                    transactions);
        }
    }

    static Service service(String prefix) throws IOException {
        Path directory = Files.createTempDirectory(prefix);
        directory.toFile().deleteOnExit();
        return new Service(directory);
    }

    static World world() {
        World world = new World();
        world.supplier.set(SettlementAuthorityHarness.ORE, 20);
        world.cashbox.set(SettlementAuthorityHarness.COPPER, 20);
        return world;
    }

    static EnterpriseDemandBoard.Delivery delivery(Service service, UUID id) {
        return new EnterpriseDemandBoard.Delivery(id, service.listingId, 4, 5);
    }

    static void deliveryThroughTheCoordinator() throws Exception {
        Service service = service("kingdom-delivery-happy-");
        World world = world();
        SettlementAuthority authority = service.authority(null);
        DemandDeliveryCoordinator coordinator = new DemandDeliveryCoordinator(
                service.board, service.pending, service.adapter(authority, world));
        UUID id = UUID.randomUUID();
        var request = delivery(service, id);

        yes(coordinator.deliver(request, null) == DemandDeliveryCoordinator.Result.COMMITTED,
                "the delivery must commit");
        yes(world.supplier.count(SettlementAuthorityHarness.ORE) == 16, "the supplier keeps the rest");
        yes(world.warehouse.count(SettlementAuthorityHarness.ORE) == 4, "the warehouse holds the goods");
        yes(world.cashbox.count(SettlementAuthorityHarness.COPPER) == 0, "the cashbox paid");
        yes(world.wallet.count(SettlementAuthorityHarness.COPPER) == 20, "the wallet holds the payment");
        DurableDemandBoardCatalog written = new DurableDemandBoardCatalog(service.board.storagePath());
        yes(written.acknowledged(id), "the board must know the delivery");
        yes(!Files.exists(service.pending.resolve(id + ".pending")), "the marker must be closed");

        // The same id again: nothing moves a second time.
        yes(coordinator.deliver(request, null) == DemandDeliveryCoordinator.Result.DUPLICATE,
                "the same delivery must not be repeated");
        yes(world.warehouse.count(SettlementAuthorityHarness.ORE) == 4, "the goods moved once");
        yes(world.wallet.count(SettlementAuthorityHarness.COPPER) == 20, "the coins moved once");
        System.out.println("  ok: the delivery moves the goods and the coins exactly once");
    }

    static void deliveryCrashMatrix() throws Exception {
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
            String where = entry[0] + "#" + entry[1];
            Service service = service("kingdom-delivery-crash-");
            World world = world();
            UUID id = UUID.randomUUID();
            CrashPlan plan = new CrashPlan(entry[0], Integer.parseInt(entry[1]));
            SettlementAuthority authority = service.authority(plan);
            // The crash happens inside the authority. The coordinator turns it
            // into a review, the harness needs the crash itself.
            DemandSettlementDeliveryAdapter adapter = service.adapter(authority, world);
            boolean crashed = false;
            try {
                adapter.settle(service.listing, delivery(service, id));
            } catch (Crash expected) {
                crashed = true;
            }
            yes(crashed, where + ": the crash must happen");
            plan.simulatePowerLoss(authority.journalPath());

            // The restart: a new authority reads the log, the board is read again.
            SettlementAuthority restarted = service.authority(null);
            restarted.recover(world.registry());
            DurableDemandBoardCatalog fresh = new DurableDemandBoardCatalog(service.board.storagePath());

            // The goods and the money are neither created nor lost.
            yes(world.goods() == 20, where + ": the goods are conserved, were " + world.goods());
            yes(world.money() == 20, where + ": the money is conserved, was " + world.money());

            // The repeat of the same id never moves the goods twice.
            DemandDeliveryCoordinator afterRestart = new DemandDeliveryCoordinator(
                    fresh, service.pending, service.adapter(restarted, world));
            DemandDeliveryCoordinator.Result repeated = afterRestart.deliver(delivery(service, id), null);
            yes(repeated == DemandDeliveryCoordinator.Result.DUPLICATE
                            || repeated == DemandDeliveryCoordinator.Result.NEEDS_REVIEW
                            || repeated == DemandDeliveryCoordinator.Result.COMMITTED,
                    where + ": the repeated delivery must be safe, was " + repeated);
            yes(world.goods() == 20, where + ": the goods are conserved after the repeat");
            yes(world.money() == 20, where + ": the money is conserved after the repeat");
            long delivered = world.warehouse.count(SettlementAuthorityHarness.ORE);
            yes(delivered == 0 || delivered == 4, where + ": the goods moved at most once");
            // The board is never ahead of the world.
            DurableDemandBoardCatalog written = new DurableDemandBoardCatalog(service.board.storagePath());
            if (delivered == 0) {
                yes(!written.acknowledged(id), where + ": the board must not know an undecided delivery");
            } else {
                yes(written.acknowledged(id), where + ": the board must know the finished delivery");
            }
        }
        System.out.println("  ok: " + points.size() + " crash points of the delivery, no creation, no loss, no double apply");
    }

    static void theMarkersOfTheOlderVersionsBlockTheDelivery() throws Exception {
        Service service = service("kingdom-delivery-legacy-");
        World world = world();
        SettlementAuthority authority = service.authority(null);

        // A marker of the older coordinator: the enterprise is blocked.
        Path marker = service.pending.resolve("legacy.pending");
        Files.writeString(marker, String.join("\n", UUID.randomUUID().toString(),
                service.listingId.toString(), service.enterprise.toString(), "2", "5"),
                java.nio.charset.StandardCharsets.US_ASCII);
        DemandDeliveryCoordinator coordinator = new DemandDeliveryCoordinator(
                service.board, service.pending, service.adapter(authority, world));
        yes(coordinator.deliver(delivery(service, UUID.randomUUID()), null)
                        == DemandDeliveryCoordinator.Result.NEEDS_REVIEW,
                "an open marker of the older version must block the delivery");
        yes(Files.exists(marker), "the marker of the older version must never be deleted");

        // An unfinished transaction of the older gate blocks everything.
        Files.delete(marker);
        Path transaction = service.transactions.resolve("old.txn");
        Files.writeString(transaction, "PREPARED\n" + UUID.randomUUID() + "\n",
                java.nio.charset.StandardCharsets.UTF_8);
        yes(coordinator.deliver(delivery(service, UUID.randomUUID()), null)
                        == DemandDeliveryCoordinator.Result.NEEDS_REVIEW,
                "an unfinished transaction of the older gate must block the delivery");
        yes(Files.exists(transaction), "the transaction of the older version must never be deleted");
        yes(world.goods() == 20, "nothing moved");
        yes(world.money() == 20, "no money moved");
        System.out.println("  ok: the markers of the older versions block the delivery and are kept");
    }

    static void theBoardIsAcknowledgedOnlyAfterTheReceipt() throws Exception {
        Service service = service("kingdom-delivery-board-");
        World world = world();
        SettlementAuthority authority = service.authority(null);
        DemandSettlementDeliveryAdapter adapter = service.adapter(authority, world);
        UUID id = UUID.randomUUID();
        var request = delivery(service, id);
        yes(adapter.settle(service.listing, request).status()
                        == DemandSettlementDeliveryAdapter.Status.COMMITTED,
                "the delivery must commit");

        // The board is lost, the world is not: the receipt is durable.
        Files.delete(service.board.storagePath());
        DurableDemandBoardCatalog fresh = new DurableDemandBoardCatalog(service.board.storagePath());
        fresh.upsert(service.listing);
        SettlementAuthority restarted = service.authority(null);
        restarted.recover(world.registry());
        DemandDeliveryCoordinator afterRestart = new DemandDeliveryCoordinator(
                fresh, service.pending, service.adapter(restarted, world));
        yes(afterRestart.deliver(request, null) == DemandDeliveryCoordinator.Result.DUPLICATE,
                "the board must be brought up to date without moving the goods again");
        yes(world.warehouse.count(SettlementAuthorityHarness.ORE) == 4, "the goods moved once");
        yes(world.wallet.count(SettlementAuthorityHarness.COPPER) == 20, "the coins moved once");
        System.out.println("  ok: the board is acknowledged only after the receipt");
    }

    public static void run() throws Exception {
        deliveryThroughTheCoordinator();
        deliveryCrashMatrix();
        theBoardIsAcknowledgedOnlyAfterTheReceipt();
        theMarkersOfTheOlderVersionsBlockTheDelivery();
    }
}
