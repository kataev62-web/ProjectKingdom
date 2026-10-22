package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;

/** Fail-closed bridge from a board listing to a physical item-for-coin exchange.
 * A pending marker is deliberately retained on all failures; manual reconciliation
 * must establish the physical outcome before an operator clears it.
 */
public final class DemandDeliveryCoordinator {
    public enum Result { COMMITTED, DUPLICATE, INVALID, PRICE_TOO_HIGH, INSUFFICIENT_COINS, NEEDS_REVIEW }
    public interface PhysicalExchange {
        /** Count actual copper coins in the enterprise's physical cashbox. */
        long availableCopper(UUID enterprise) throws IOException;
        /** Exchange real goods for real coins. Must reject missing goods, coins, or capacity. */
        boolean exchange(EnterpriseDemandBoard.Listing listing, EnterpriseDemandBoard.Delivery delivery) throws IOException;
        /** Verify BOTH inventories and physical coin movements after the exchange. */
        boolean verified(EnterpriseDemandBoard.Listing listing, EnterpriseDemandBoard.Delivery delivery) throws IOException;
    }
    private final DurableDemandBoardCatalog board;
    private final Path pendingDirectory;
    public DemandDeliveryCoordinator(DurableDemandBoardCatalog board, Path pendingDirectory) {
        this.board = Objects.requireNonNull(board);
        this.pendingDirectory = Objects.requireNonNull(pendingDirectory);
    }
    private boolean hasPendingForEnterprise(UUID enterprise) throws IOException {
        if (!Files.isDirectory(pendingDirectory)) return false;
        try (var files = Files.newDirectoryStream(pendingDirectory, "*.pending")) {
            for (Path path : files) {
                // Corrupt or unrecognized markers are fail-closed, not silently ignored.
                var lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.US_ASCII);
                if (lines.size() != 5) return true;
                try {
                    UUID.fromString(lines.get(0));
                    UUID.fromString(lines.get(1));
                    UUID owner = UUID.fromString(lines.get(2));
                    if (owner.equals(enterprise)) return true;
                } catch (IllegalArgumentException ex) { return true; }
            }
        }
        return false;
    }
    public synchronized Result deliver(EnterpriseDemandBoard.Delivery delivery, PhysicalExchange physical) throws IOException {
        Objects.requireNonNull(delivery); Objects.requireNonNull(physical);
        if (board.acknowledged(delivery.deliveryId())) return Result.DUPLICATE;
        // A crash or failed exchange must never be retried blindly.
        Path marker = pendingDirectory.resolve(delivery.deliveryId() + ".pending");
        if (Files.exists(marker)) return Result.NEEDS_REVIEW;
        EnterpriseDemandBoard.Listing listing = board.page(0, 50).stream()
                .filter(x -> x.listingId().equals(delivery.listingId())).findFirst().orElse(null);
        // Large boards may have >50 entries: scan all pages.
        if (listing == null) {
            for (int offset = 50; offset < board.size(); offset += 50) {
                listing = board.page(offset, 50).stream().filter(x -> x.listingId().equals(delivery.listingId())).findFirst().orElse(null);
                if (listing != null) break;
            }
        }
        if (listing == null) return Result.INVALID;
        // Cross-coordinator/process exclusion. The lock covers physical writes AND
        // durable board acknowledgement; never trust instance-level synchronized alone.
        Files.createDirectories(pendingDirectory);
        Path lockPath = pendingDirectory.resolve(listing.key().enterprise() + ".lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock enterpriseLock;
            try { enterpriseLock = lockChannel.tryLock(); }
            catch (OverlappingFileLockException ex) { return Result.NEEDS_REVIEW; }
            if (enterpriseLock == null) return Result.NEEDS_REVIEW;
            try (enterpriseLock) {
                return deliverLocked(delivery, physical, listing, marker);
            }
        }
    }
    private Result deliverLocked(EnterpriseDemandBoard.Delivery delivery, PhysicalExchange physical,
                                 EnterpriseDemandBoard.Listing listing, Path marker) throws IOException {
        // Re-read the durable board after acquiring the enterprise-wide lock.
        // Another coordinator may have acknowledged a delivery in the meantime.
        DurableDemandBoardCatalog fresh = new DurableDemandBoardCatalog(board.storagePath());
        if (fresh.acknowledged(delivery.deliveryId())) return Result.DUPLICATE;
        EnterpriseDemandBoard.Listing current = null;
        for (int offset = 0; offset < fresh.size(); offset += 50) {
            current = fresh.page(offset, 50).stream()
                    .filter(x -> x.listingId().equals(delivery.listingId())).findFirst().orElse(null);
            if (current != null) break;
        }
        if (current == null || !current.key().enterprise().equals(listing.key().enterprise())) return Result.INVALID;
        listing = current;
        if (Files.exists(marker)) return Result.NEEDS_REVIEW;
        // An unresolved transfer for this enterprise can have changed its cashbox or stock.
        // Reject ALL subsequent deliveries until an operator reconciles the pending transfer.
        if (hasPendingForEnterprise(listing.key().enterprise())) return Result.NEEDS_REVIEW;
        long coins = physical.availableCopper(listing.key().enterprise());
        var decision = EnterpriseDemandBoard.check(listing, delivery, java.util.Set.of(), coins);
        switch (decision) {
            case INVALID -> { return Result.INVALID; }
            case PRICE_TOO_HIGH -> { return Result.PRICE_TOO_HIGH; }
            case INSUFFICIENT_COINS -> { return Result.INSUFFICIENT_COINS; }
            case DUPLICATE -> { return Result.DUPLICATE; }
            case ACCEPT -> { }
        }
        Files.createDirectories(pendingDirectory);
        byte[] intent = (delivery.deliveryId()+"\n"+listing.listingId()+"\n"+listing.key().enterprise()+"\n"+
                delivery.quantity()+"\n"+delivery.unitCopper()+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(intent)); channel.force(true);
        } catch (FileAlreadyExistsException ex) { return Result.NEEDS_REVIEW; }
        // Deliberately do not delete the marker on failure or on success until board acknowledgement is durable.
        if (!physical.exchange(listing, delivery) || !physical.verified(listing, delivery)) return Result.NEEDS_REVIEW;
        fresh.acknowledgeCommitted(delivery);
        Files.delete(marker);
        return Result.COMMITTED;
    }
}
