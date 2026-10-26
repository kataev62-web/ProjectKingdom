package ru.projectkingdom.social;

/**
 * Gameplay progression/eligibility tier, not proof of a legal title or public office.
 * Nobility, founding rights and sovereignty require separate authoritative records.
 */
public enum KingdomSocialRank {
    COMMONER(1, "Простолюдин"),
    FREE_CITIZEN(2, "Свободный горожанин"),
    WEALTHY_CITIZEN(3, "Состоятельный горожанин"),
    HONORED_CITIZEN(4, "Почётный гражданин"),
    LANDOWNER(5, "Землевладелец"),
    SETTLEMENT_FOUNDER(6, "Основатель поселения"),
    BARON(7, "Барон"),
    HIGH_NOBILITY(8, "Граф / герцог"),
    SOVEREIGN(9, "Король");

    private final int level;
    private final String displayName;
    KingdomSocialRank(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }
    public int level() { return level; }
    public String displayName() { return displayName; }
    public boolean atLeast(KingdomSocialRank required) {
        return required != null && level >= required.level;
    }
    public static KingdomSocialRank fromLevel(int level) {
        for (KingdomSocialRank rank : values()) if (rank.level == level) return rank;
        return COMMONER;
    }
}
