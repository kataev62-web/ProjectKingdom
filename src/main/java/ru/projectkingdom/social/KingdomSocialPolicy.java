package ru.projectkingdom.social;

/** Pure, server-side game rules. Never grants titles, payments, plots or offices. */
public final class KingdomSocialPolicy {
    public record Privileges(int maxEnterprises, int maxPersonalWorkers, int maxPurchasedPlots,
                             boolean mayBuyLand, boolean mayHoldAdministration,
                             boolean mayFoundSettlement) {}
    private KingdomSocialPolicy() {}

    public static Privileges privileges(KingdomSocialRank rank) {
        if (rank == null) rank = KingdomSocialRank.COMMONER;
        return switch (rank) {
            case COMMONER -> new Privileges(1, 2, 0, false, false, false);
            case FREE_CITIZEN -> new Privileges(2, 5, 1, true, false, false);
            case WEALTHY_CITIZEN -> new Privileges(4, 12, 3, true, true, false);
            case HONORED_CITIZEN -> new Privileges(8, 20, 6, true, true, false);
            case LANDOWNER -> new Privileges(12, 35, 12, true, true, true);
            case SETTLEMENT_FOUNDER -> new Privileges(16, 50, 24, true, true, true);
            case BARON -> new Privileges(16, 60, 32, true, true, true);
            case HIGH_NOBILITY -> new Privileges(16, 80, 64, true, true, true);
            case SOVEREIGN -> new Privileges(16, 100, 128, true, true, true);
        };
    }

    /** Counts existing property without confiscating it after a rank change. */
    public static boolean canOpenEnterprise(KingdomSocialRank rank, long existing) {
        return existing >= 0 && existing < privileges(rank).maxEnterprises();
    }
    public static boolean canHirePersonalWorker(KingdomSocialRank rank, long existing) {
        return existing >= 0 && existing < privileges(rank).maxPersonalWorkers();
    }
    public static boolean canPurchasePlot(KingdomSocialRank rank, long existingPersonalPlots) {
        Privileges p = privileges(rank);
        return p.mayBuyLand() && existingPersonalPlots >= 0 && existingPersonalPlots < p.maxPurchasedPlots();
    }
    public static boolean mayHoldAdministrativeOffice(KingdomSocialRank rank) {
        return privileges(rank).mayHoldAdministration();
    }
    /** Founder is a persistent, unique identity. Rank alone never grants a second founding. */
    public static boolean canFoundSettlement(KingdomSocialRank rank, boolean alreadyFounded) {
        return privileges(rank).mayFoundSettlement() && !alreadyFounded;
    }
}
