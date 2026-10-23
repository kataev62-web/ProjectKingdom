package ru.projectkingdom.social;

/** Economic requirements for promotion at the town hall. Values are provisional copper units. */
public final class KingdomSocialPromotionRules {
    public record Requirements(KingdomSocialRank target, long feeCopper, int minEnterprises,
                               int minPersonalWorkers, int minPurchasedPlots, boolean jobOrEnterprise) {}
    public record Progress(long ownedEnterprises, long hiredWorkers, long purchasedPlots,
                           boolean employed, long taxDebt, long physicalBalance) {}
    public record Evaluation(Requirements requirements, Progress progress, boolean ready) {}
    private KingdomSocialPromotionRules() {}

    /** Levels 6..9 are NOT purchasable: settlement foundation, nobility and sovereignty are separate. */
    public static Requirements forCurrent(KingdomSocialRank current) {
        if (current == null) return null;
        return switch (current) {
            case COMMONER -> new Requirements(KingdomSocialRank.FREE_CITIZEN,100,0,0,0,true);
            case FREE_CITIZEN -> new Requirements(KingdomSocialRank.WEALTHY_CITIZEN,250,1,1,0,false);
            case WEALTHY_CITIZEN -> new Requirements(KingdomSocialRank.HONORED_CITIZEN,600,2,4,0,false);
            case HONORED_CITIZEN -> new Requirements(KingdomSocialRank.LANDOWNER,1200,3,8,1,false);
            default -> null;
        };
    }
    public static Evaluation evaluate(KingdomSocialRank current, Progress progress) {
        Requirements r = forCurrent(current);
        if (r == null || progress == null) return new Evaluation(null,progress,false);
        boolean achievements = progress.ownedEnterprises() >= r.minEnterprises()
                && progress.hiredWorkers() >= r.minPersonalWorkers()
                && progress.purchasedPlots() >= r.minPurchasedPlots()
                && (!r.jobOrEnterprise() || progress.employed() || progress.ownedEnterprises() > 0);
        boolean ready = achievements && progress.taxDebt() == 0
                && progress.physicalBalance() >= r.feeCopper();
        return new Evaluation(r,progress,ready);
    }
}
