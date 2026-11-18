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
 *
 * <p>With a settlement adapter the authority owns the exchange: the coordinator
 * never touches a container itself. The decision is durable before the first
 * item moves, the projection is idempotent, and a delivery that was already
 * finished is never moved again, even after a restart. The older exchange is
 * used only without an adapter and is not protected against a crash between the
 * exchange and the acknowledgement of the board.
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
    private final DemandSettlementDeliveryAdapter settlement;
    private volatile boolean legacyWarningShown;

    public DemandDeliveryCoordinator(DurableDemandBoardCatalog board, Path pendingDirectory) {
        this(board, pendingDirectory, null);
    }

    /**
     * A coordinator whose physical exchange belongs to the settlement authority.
     *
     * <p>The adapter is the only writer of the containers. A physical exchange
     * that is handed to {@link #deliver} afterwards is not used: two owners of
     * one delivery would move the goods twice.
     */
    public DemandDeliveryCoordinator(DurableDemandBoardCatalog board, Path pendingDirectory,
                                     DemandSettlementDeliveryAdapter settlement) {
        this.board = Objects.requireNonNull(board);
        this.pendingDirectory = Objects.requireNonNull(pendingDirectory);
        this.settlement = settlement;
    }
    private boolean hasPendingForEnterprise(UUID enterprise) throws IOException {
        return hasPendingForEnterprise(enterprise, null);
    }

    /**
     * Whether the enterprise has an unresolved transfer.
     *
     * <p>The marker of the given delivery is ignored when it is asked for: the
     * marker of the current attempt is not an unresolved transfer of another
     * one.
     */
    private boolean hasPendingForEnterprise(UUID enterprise, UUID ownDelivery) throws IOException {
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
                    if (owner.equals(enterprise)) {
                        UUID delivery = UUID.fromString(lines.get(0));
                        if (ownDelivery != null && ownDelivery.equals(delivery)) continue;
                        return true;
                    }
                } catch (IllegalArgumentException ex) { return true; }
            }
        }
        return false;
    }

    /**
     * Whether the authority already finished the delivery.
     *
     * <p>A delivery with a durable receipt moved the goods already. It must
     * not be moved again, only the board may be brought up to date.
     */
    private boolean alreadySettled(UUID deliveryId) throws IOException {
        if (settlement == null) return false;
        try {
            return settlement.settled(deliveryId);
        } catch (IOException ex) {
            // The state is unknown: the delivery is not repeated.
            return false;
        }
    }
    public synchronized Result deliver(EnterpriseDemandBoard.Delivery delivery, PhysicalExchange physical) throws IOException {
        Objects.requireNonNull(delivery);
        if (settlement == null) Objects.requireNonNull(physical);
        else if (physical != null && !legacyWarningShown) {
            // The authority is the only owner of the exchange. The older
            // exchange would move the goods a second time.
            legacyWarningShown = true;
            System.err.println("[kingdom] the settlement authority owns this delivery: "
                    + "the physical exchange of the coordinator is not used");
        }
        if (board.acknowledged(delivery.deliveryId())) return Result.DUPLICATE;
        // A crash or failed exchange must never be retried blindly. A delivery
        // that the authority already finished is not a blind retry: only the
        // board may lag behind, and the marker is closed below.
        Path marker = pendingDirectory.resolve(delivery.deliveryId() + ".pending");
        if (Files.exists(marker) && !alreadySettled(delivery.deliveryId())) return Result.NEEDS_REVIEW;
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
    /**
     * The delivery through the settlement authority.
     *
     * <p>The order is fixed: the authority decides and moves, the board is
     * acknowledged afterwards, the marker is closed last. A crash between the
     * steps leaves the marker behind, and the next attempt finishes the board
     * without moving the goods again.
     */
    private Result deliverThroughAuthority(EnterpriseDemandBoard.Delivery delivery,
                                           DurableDemandBoardCatalog fresh,
                                           EnterpriseDemandBoard.Listing listing,
                                           Path marker) throws IOException {
        DemandSettlementDeliveryAdapter.Result outcome;
        try {
            outcome = settlement.settle(listing, delivery);
        } catch (IOException | RuntimeException ex) {
            // The authority could not decide. The marker stays: the enterprise
            // is blocked until an operator reconciled the containers.
            return Result.NEEDS_REVIEW;
        }
        switch (outcome.status()) {
            case COMMITTED: {
                if (!acknowledge(fresh, delivery)) return Result.NEEDS_REVIEW;
                Files.deleteIfExists(marker);
                return Result.COMMITTED;
            }
            case DUPLICATE: {
                // The goods are already moved: only the board may lag behind.
                if (!acknowledge(fresh, delivery)) return Result.NEEDS_REVIEW;
                Files.deleteIfExists(marker);
                return Result.DUPLICATE;
            }
            case REJECTED: {
                // Nothing was moved and the decision is durable: the marker
                // may go, the authority refuses a repeat of this id.
                Files.deleteIfExists(marker);
                return mapRejection(outcome.reason());
            }
            case NEEDS_REVIEW:
            default: {
                // The marker stays: the enterprise is blocked until an
                // operator reconciled the containers.
                return Result.NEEDS_REVIEW;
            }
        }
    }

    /**
     * Acknowledges the delivery on the board.
     *
     * @return false when the board cannot be updated. The goods are moved
     *         already, so the delivery stays open for an operator.
     */
    private boolean acknowledge(DurableDemandBoardCatalog fresh,
                               EnterpriseDemandBoard.Delivery delivery) {
        try {
            fresh.acknowledgeCommitted(delivery);
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    /** The result of a refusal of the authority. */
    private static Result mapRejection(String reason) {
        if (reason == null) return Result.NEEDS_REVIEW;
        return switch (reason) {
            case "INSUFFICIENT_COINS" -> Result.INSUFFICIENT_COINS;
            case "PRICE_TOO_HIGH" -> Result.PRICE_TOO_HIGH;
            case "INSUFFICIENT_GOODS", "NO_GOODS_SPACE", "NO_COIN_SPACE", "INVALID" -> Result.INVALID;
            default -> Result.NEEDS_REVIEW;
        };
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
        if (settlement != null) {
            // The authority owns the exchange. The marker of an earlier attempt
            // is closed here, because the authority knows what happened.
            if (Files.exists(marker) && !alreadySettled(delivery.deliveryId())) return Result.NEEDS_REVIEW;
            if (hasPendingForEnterprise(listing.key().enterprise(), delivery.deliveryId())) return Result.NEEDS_REVIEW;
            return deliverThroughAuthority(delivery, fresh, listing, marker);
        }
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
