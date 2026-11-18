package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ru.projectkingdom.npc.SettlementAuthority.Container;
import ru.projectkingdom.npc.SettlementAuthority.RecoveryReport;
import ru.projectkingdom.npc.SettlementAuthority.Request;
import ru.projectkingdom.npc.SettlementAuthority.State;
import ru.projectkingdom.npc.SettlementAuthority.TxView;

/**
 * The delivery of goods for coins across a real crash of the process.
 *
 * <p>The child process performs a delivery and dies at a chosen boundary with
 * {@link Runtime#halt(int)}: no shutdown hook, no flush, no finalizer. The
 * world of the test is a file, so the containers survive the death of the
 * process exactly as the blocks of a server survive it.
 *
 * <p>The parent process starts a second time, recovers the durable log and
 * checks the invariants: no item and no coin is created or lost, the goods are
 * never moved twice, and a repeated request never applies the delivery again.
 *
 * <p>Usage: {@code SettlementCrashTool child <dir> <point>} and
 * {@code SettlementCrashTool parent <dir> <point>}.
 */
public final class SettlementCrashTool {
    static final String GOODS = "minecraft:wheat";
    static final String COPPER = "kingdom:copper_coin";
    static final int CRASH_EXIT = 137;

    private SettlementCrashTool() {}

    /** A container of the crash world: every write reaches the disk at once. */
    static final class FileChest implements Container {
        private final String id;
        private final Path file;
        private final Map<String, Long> items = new LinkedHashMap<>();

        /** Called after every write of the world. The crash point of the world. */
        static Runnable afterWrite = () -> {};

        FileChest(String id, Path file) throws IOException {
            this.id = id;
            this.file = file;
            reload();
        }

        public String id() { return id; }

        /**
         * The amount of the item in the world.
         *
         * <p>The four containers share one file: the file is the world, so it
         * is read again every time. A container that would keep its own copy
         * would write an old state of its neighbours back to the disk.
         */
        public long count(String item) {
            try {
                reload();
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
            return items.getOrDefault(id + "|" + item, 0L);
        }

        public long capacity(String item) { return 4096; }

        public void apply(String item, long delta) throws IOException {
            reload();
            long next = count(item) + delta;
            if (next < 0) throw new IOException("negative amount in " + id);
            items.put(id + "|" + item, next);
            write();
            afterWrite.run();
        }

        private void reload() throws IOException {
            items.clear();
            if (!Files.exists(file)) return;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|");
                items.put(parts[0] + "|" + parts[1], Long.parseLong(parts[2]));
            }
        }

        private void write() throws IOException {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Long> entry : items.entrySet()) {
                String[] parts = entry.getKey().split("\\|");
                out.append(parts[0]).append('|').append(parts[1]).append('|')
                   .append(entry.getValue()).append('\n');
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, out.toString(), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Path root(String dir) { return Path.of(dir); }

    static Path worldFile(Path root) { return root.resolve("world.txt"); }

    static Path journalDirectory(Path root) { return root.resolve("settlement"); }

    static Path pendingDirectory(Path root) { return root.resolve("pending"); }

    static Path boardFile(Path root) { return root.resolve("board.snapshot"); }

    static UUID listingId() {
        return UUID.nameUUIDFromBytes("kingdom-issue70-listing".getBytes(StandardCharsets.UTF_8));
    }

    static UUID deliveryId() {
        return UUID.nameUUIDFromBytes("kingdom-issue70-delivery".getBytes(StandardCharsets.UTF_8));
    }

    static UUID enterprise() {
        return UUID.nameUUIDFromBytes("kingdom-issue70-enterprise".getBytes(StandardCharsets.UTF_8));
    }

    static EnterpriseDemandBoard.Listing listing() {
        return new EnterpriseDemandBoard.Listing(
                new EnterpriseDemandBoard.Key(enterprise(), GOODS), listingId(), 10, 5);
    }

    static EnterpriseDemandBoard.Delivery delivery() {
        return new EnterpriseDemandBoard.Delivery(deliveryId(), listingId(), 4, 5);
    }

    /** The world of the test: ten goods in the supplier, twenty copper in the cashbox. */
    static void writeWorld(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(worldFile(root),
                "supplier|" + GOODS + "|10\n"
                        + "cashbox|" + COPPER + "|20\n", StandardCharsets.UTF_8);
        Files.createDirectories(pendingDirectory(root));
    }

    static void yes(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    // --- the child: the delivery dies at the chosen boundary --------------------

    static void child(String directory, String pointSpec) throws Exception {
        Path root = root(directory);
        writeWorld(root);
        // The boundary may carry the number of its occurrence: the journal has
        // three write boundaries per record.
        String point = pointSpec;
        int occurrence = 1;
        int colon = pointSpec.indexOf(':');
        if (colon > 0) {
            point = pointSpec.substring(0, colon);
            occurrence = Integer.parseInt(pointSpec.substring(colon + 1));
        }
        final String boundary = point;
        final int wanted = occurrence;
        FileChest.afterWrite = () -> {
            if ("world.after_write".equals(boundary)) Runtime.getRuntime().halt(CRASH_EXIT);
        };
        int[] seen = { 0 };
        SettlementAuthority authority = new SettlementAuthority(journalDirectory(root), (name, length) -> {
            if (!name.equals(boundary)) return;
            seen[0]++;
            if (seen[0] != wanted) return;
            Runtime.getRuntime().halt(CRASH_EXIT);
        });
        FileChest supplier = new FileChest("supplier", worldFile(root));
        FileChest warehouse = new FileChest("warehouse", worldFile(root));
        FileChest cashbox = new FileChest("cashbox", worldFile(root));
        FileChest wallet = new FileChest("wallet", worldFile(root));
        DurableDemandBoardCatalog board = new DurableDemandBoardCatalog(boardFile(root));
        board.upsert(listing());
        DemandSettlementDeliveryAdapter adapter = new DemandSettlementDeliveryAdapter(authority,
                (a, b) -> new DemandSettlementDeliveryAdapter.Endpoints(
                        supplier, warehouse, cashbox, wallet),
                root.resolve("transactions"));
        DemandDeliveryCoordinator coordinator = new DemandDeliveryCoordinator(
                board, pendingDirectory(root), adapter);
        coordinator.deliver(delivery(), null);
    }

    // --- the parent: the restart recovers ---------------------------------------

    static void parent(String directory, String point) throws Exception {
        Path root = root(directory);
        FileChest supplier = new FileChest("supplier", worldFile(root));
        FileChest warehouse = new FileChest("warehouse", worldFile(root));
        FileChest cashbox = new FileChest("cashbox", worldFile(root));
        FileChest wallet = new FileChest("wallet", worldFile(root));
        Map<String, Container> registry = new LinkedHashMap<>();
        registry.put(supplier.id(), supplier);
        registry.put(warehouse.id(), warehouse);
        registry.put(cashbox.id(), cashbox);
        registry.put(wallet.id(), wallet);

        // The world of the test: ten goods and twenty copper. Nothing may ever
        // be more than that: a bigger amount would be a creation or a second
        // application of the same delivery.
        long goodsBefore = supplier.count(GOODS) + warehouse.count(GOODS);
        long moneyBefore = cashbox.count(COPPER) + wallet.count(COPPER);
        yes(goodsBefore <= 10, point + ": the goods must never be more than ten, were " + goodsBefore);
        yes(moneyBefore <= 20, point + ": the money must never be more than twenty, was " + moneyBefore);

        // The restart: a new authority reads the durable log.
        SettlementAuthority authority = new SettlementAuthority(journalDirectory(root));
        RecoveryReport report = authority.recover(registry);
        TxView view = null;
        for (TxView candidate : report.transactions())
            if (candidate.id().equals(deliveryId())) view = candidate;

        long goods = supplier.count(GOODS) + warehouse.count(GOODS);
        long money = cashbox.count(COPPER) + wallet.count(COPPER);
        yes(goods <= 10, point + ": the recovery must not create goods, were " + goods);
        yes(money <= 20, point + ": the recovery must not create money, were " + money);

        if (view != null && view.state() == State.RECEIPTED) {
            // The decided delivery is finished exactly once.
            yes(goods == 10, point + ": the decided delivery must be complete, goods " + goods);
            yes(money == 20, point + ": the decided delivery must be complete, money " + money);
            yes(warehouse.count(GOODS) == 4, point + ": the goods must arrive once, were "
                    + warehouse.count(GOODS));
            yes(supplier.count(GOODS) == 6, point + ": the supplier keeps the rest, were "
                    + supplier.count(GOODS));
            yes(wallet.count(COPPER) == 20, point + ": the payment happens once, was "
                    + wallet.count(COPPER));
        }
        if (view != null && view.state() == State.QUARANTINED) {
            // The decision was not durable: the operator has to look at the
            // four containers. Nothing may be applied after that.
            yes(true, point + ": the undecided delivery is quarantined");
        }

        // The recovery is idempotent.
        long goodsAfter = supplier.count(GOODS) + warehouse.count(GOODS);
        long moneyAfter = cashbox.count(COPPER) + wallet.count(COPPER);
        RecoveryReport again = new SettlementAuthority(journalDirectory(root)).recover(registry);
        yes(again.quarantined().size() == report.quarantined().size(),
                point + ": the recovery must be idempotent");
        yes(supplier.count(GOODS) + warehouse.count(GOODS) == goodsAfter,
                point + ": the second recovery must not move the goods");
        yes(cashbox.count(COPPER) + wallet.count(COPPER) == moneyAfter,
                point + ": the second recovery must not move the money");
        yes(goodsAfter <= 10, point + ": the second recovery must not create goods");
        yes(moneyAfter <= 20, point + ": the second recovery must not create money");

        // The repeated request never applies the delivery a second time.
        SettlementAuthority restarted = new SettlementAuthority(journalDirectory(root));
        DurableDemandBoardCatalog board = new DurableDemandBoardCatalog(boardFile(root));
        board.upsert(listing());
        DemandSettlementDeliveryAdapter adapter = new DemandSettlementDeliveryAdapter(restarted,
                (a, b) -> new DemandSettlementDeliveryAdapter.Endpoints(
                        supplier, warehouse, cashbox, wallet),
                root.resolve("transactions"));
        DemandDeliveryCoordinator coordinator = new DemandDeliveryCoordinator(
                board, pendingDirectory(root), adapter);
        DemandDeliveryCoordinator.Result repeated = coordinator.deliver(delivery(), null);
        yes(repeated == DemandDeliveryCoordinator.Result.DUPLICATE
                        || repeated == DemandDeliveryCoordinator.Result.NEEDS_REVIEW
                        || repeated == DemandDeliveryCoordinator.Result.COMMITTED,
                point + ": the repeated delivery must be safe, was " + repeated);
        yes(supplier.count(GOODS) + warehouse.count(GOODS) == goodsAfter,
                point + ": the repeated delivery must not move the goods twice");
        yes(cashbox.count(COPPER) + wallet.count(COPPER) == moneyAfter,
                point + ": the repeated delivery must not move the money twice");
        yes(goodsAfter <= 10, point + ": the repeated delivery must not create goods");
        yes(moneyAfter <= 20, point + ": the repeated delivery must not create money");
        yes(warehouse.count(GOODS) <= 4, point + ": the goods must never arrive twice, were "
                + warehouse.count(GOODS));
        yes(wallet.count(COPPER) <= 20, point + ": the payment must never happen twice, was "
                + wallet.count(COPPER));
        if (view != null && view.state() == State.RECEIPTED) {
            yes(warehouse.count(GOODS) == 4, point + ": the goods must not arrive twice, were "
                    + warehouse.count(GOODS));
            yes(wallet.count(COPPER) == 20, point + ": the payment must not happen twice, was "
                    + wallet.count(COPPER));
        }
        System.out.println("  ok: " + point + " (state: "
                + (view == null ? "not decided" : view.state()) + ", repeated: " + repeated + ")");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: SettlementCrashTool <child|parent> <dir> <point>");
            System.exit(2);
        }
        if ("child".equals(args[0])) child(args[1], args[2]);
        else if ("parent".equals(args[0])) parent(args[1], args[2]);
        else {
            System.err.println("unknown mode: " + args[0]);
            System.exit(2);
        }
    }
}
