package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * The single owner of a physical delivery: the settlement authority.
 *
 * <p>The adapter never moves an item and never decides anything itself. It
 * resolves the four containers of the delivery, checks the preconditions that
 * the board cannot know, and hands the decision to the
 * {@link SettlementAuthority}, which writes the durable record before the first
 * container is touched.
 *
 * <p>Everything that is not certain ends in {@code NEEDS_REVIEW}: an
 * unreadable container, a missing right, a chunk that is not loaded, a damaged
 * journal, an unfinished transaction of the older gate or goods of more than one
 * state. The delivery is never guessed.
 */
public final class DemandSettlementDeliveryAdapter {
    /** The four physical containers of one delivery. */
    public record Endpoints(SettlementAuthority.Container supplier,
                            SettlementAuthority.Container warehouse,
                            SettlementAuthority.Container cashbox,
                            SettlementAuthority.Container wallet,
                            String goodsVariant) {
        public Endpoints {
            Objects.requireNonNull(supplier, "supplier");
            Objects.requireNonNull(warehouse, "warehouse");
            Objects.requireNonNull(cashbox, "cashbox");
            Objects.requireNonNull(wallet, "wallet");
            Objects.requireNonNull(goodsVariant, "goodsVariant");
            if (supplier.id().equals(warehouse.id()) || supplier.id().equals(cashbox.id())
                    || supplier.id().equals(wallet.id()) || warehouse.id().equals(cashbox.id())
                    || warehouse.id().equals(wallet.id()) || cashbox.id().equals(wallet.id()))
                throw new IllegalArgumentException("the four containers must be distinct");
        }

        /** The four containers without a state: only items without a tag are delivered. */
        public Endpoints(SettlementAuthority.Container supplier,
                         SettlementAuthority.Container warehouse,
                         SettlementAuthority.Container cashbox,
                         SettlementAuthority.Container wallet) {
            this(supplier, warehouse, cashbox, wallet, "");
        }
    }

    /**
     * Resolves the containers of a delivery.
     *
     * <p>The resolver returns null when a container cannot be used: the right
     * is missing, the chunk is not loaded or the container is gone. The
     * delivery is not started then.
     */
    public interface Resolver {
        Endpoints resolve(EnterpriseDemandBoard.Listing listing,
                          EnterpriseDemandBoard.Delivery delivery) throws IOException;
    }

    /** The outcome of one delivery. */
    public enum Status { COMMITTED, DUPLICATE, REJECTED, NEEDS_REVIEW }

    /** The outcome of one delivery with the reason of a refusal. */
    public record Result(Status status, String reason) {
        public static Result committed() { return new Result(Status.COMMITTED, ""); }
        public static Result duplicate() { return new Result(Status.DUPLICATE, ""); }
        public static Result rejected(String reason) { return new Result(Status.REJECTED, reason); }
        public static Result review(String reason) { return new Result(Status.NEEDS_REVIEW, reason); }
        public boolean settled() { return status == Status.COMMITTED || status == Status.DUPLICATE; }
    }

    private final SettlementAuthority authority;
    private final Resolver resolver;
    private final Path legacyTransactions;

    public DemandSettlementDeliveryAdapter(SettlementAuthority authority, Resolver resolver) {
        this(authority, resolver, null);
    }

    /**
     * @param legacyTransactions directory of the older settlement gate. An
     *        unfinished transaction of that gate blocks every delivery: the
     *        containers it may have touched are unknown. The files are never
     *        deleted here.
     */
    public DemandSettlementDeliveryAdapter(SettlementAuthority authority, Resolver resolver,
                                          Path legacyTransactions) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.legacyTransactions = legacyTransactions;
    }

    /** The authority that owns the decision. */
    public SettlementAuthority authority() { return authority; }

    /**
     * Whether the delivery was already finished.
     *
     * <p>A delivery with a durable receipt must not be delivered again: only
     * the board may still lag behind.
     */
    public boolean settled(UUID deliveryId) throws IOException {
        SettlementAuthority.State state = authority.stateOf(deliveryId);
        return state == SettlementAuthority.State.RECEIPTED;
    }

    /**
     * Settles one delivery.
     *
     * <p>The caller must hold the exclusive right of the enterprise. The
     * method itself is idempotent for the id of the delivery: the authority
     * knows a finished delivery and refuses to repeat it.
     */
    public Result settle(EnterpriseDemandBoard.Listing listing,
                         EnterpriseDemandBoard.Delivery delivery) throws IOException {
        if (listing == null || delivery == null) return Result.rejected("INVALID");
        if (!listing.listingId().equals(delivery.listingId())) return Result.rejected("INVALID");
        if (delivery.quantity() <= 0 || delivery.unitCopper() <= 0) return Result.rejected("INVALID");
        if (delivery.quantity() > listing.remaining()) return Result.rejected("INVALID");
        if (delivery.unitCopper() > listing.maximumUnitCopper()) return Result.rejected("PRICE_TOO_HIGH");
        // A damaged log means that records are missing: the state of the world
        // is unknown, so nothing may be decided.
        if (authority.journalDamaged()) return Result.review("journal_damaged");
        // An unfinished transaction of the older gate may have touched the very
        // containers of this delivery.
        if (legacyTransactionOpen()) return Result.review("legacy_transaction_open");
        Endpoints endpoints;
        try {
            endpoints = resolver.resolve(listing, delivery);
        } catch (IOException | RuntimeException ex) {
            return Result.review("container_unavailable");
        }
        if (endpoints == null) return Result.review("container_unavailable");
        // The state of the offered goods is named by the seller, not guessed:
        // a perishable item of TerraFirmaCraft has a freshness, and the
        // delivery must move exactly the goods that were offered.
        String variant = endpoints.goodsVariant();
        SettlementAuthority.Request request = new SettlementAuthority.Request(delivery.deliveryId(),
                listing.key().item(), variant, delivery.quantity(), delivery.unitCopper(),
                endpoints.supplier(), endpoints.warehouse(), endpoints.cashbox(), endpoints.wallet());
        SettlementAuthority.Outcome outcome;
        try {
            outcome = authority.settle(request);
        } catch (IOException ex) {
            // The authority could not decide. The delivery stays open.
            return Result.review("authority_failed");
        }
        switch (outcome) {
            case COMMITTED: return Result.committed();
            case DUPLICATE: return Result.duplicate();
            case REJECTED: return Result.rejected(rejectionReason(delivery.deliveryId()));
            case QUARANTINED: return Result.review("quarantined");
            case INCONSISTENT:
            default: return Result.review("inconsistent_state");
        }
    }

    /** The reason of the refusal of the authority. */
    private String rejectionReason(UUID deliveryId) {
        for (SettlementAuthority.TxView view : authority.transactions())
            if (view.id().equals(deliveryId)) return view.problem();
        return "INVALID";
    }

    /**
     * Whether the older gate left an unfinished transaction.
     *
     * <p>The marker is only read. It is the operator who closes it, after the
     * four containers were checked by hand.
     */
    private boolean legacyTransactionOpen() throws IOException {
        if (legacyTransactions == null || !Files.isDirectory(legacyTransactions)) return false;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(legacyTransactions, "*.txn")) {
            for (Path entry : entries) {
                String state = Files.readString(entry, StandardCharsets.UTF_8);
                if (state.startsWith("COMMITTED") || state.startsWith("REJECTED")) continue;
                return true;
            }
        }
        return false;
    }
}
