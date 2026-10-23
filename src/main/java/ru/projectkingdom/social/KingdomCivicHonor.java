package ru.projectkingdom.social;

/** Official municipal distinctions, not noble titles, political offices or property rights. */
public enum KingdomCivicHonor {
    SERVICE("За труд",30,3),
    MERIT("За заслуги",60,4),
    DISTINCTION("Почётный знак",100,5);
    private final String russian;private final int reputation;private final int rank;
    KingdomCivicHonor(String russian,int reputation,int rank){
        this.russian=russian;this.reputation=reputation;this.rank=rank;
    }
    public String russian(){return russian;}
    public int requiredReputation(){return reputation;}
    public int requiredRank(){return rank;}
    public boolean eligible(int socialRank,int cityReputation){
        return socialRank>=rank&&cityReputation>=reputation;
    }
}
