package ru.projectkingdom.social;

/** Deterministic scoring policy. Only authoritative committed server events earn points. */
public final class KingdomReputationPolicy {
    public enum Event { TAX_PAYMENT, EARNED_WAGE, LAND_PURCHASE }
    public static final int MAX_SCORE=200;
    public static final int DAILY_GAIN_CAP=8;
    private KingdomReputationPolicy() {}

    public static int earned(Event event,long copper) {
        if(event==null)return 0;
        return switch(event) {
            case TAX_PAYMENT -> copper>=100?4:copper>=10?2:0;
            case EARNED_WAGE -> copper>=100?2:copper>=10?1:0;
            case LAND_PURCHASE -> copper>0?8:0;
        };
    }
    public static String standing(int points) {
        if(points>=100)return "Выдающийся гражданин";
        if(points>=60)return "Почётный член общества";
        if(points>=30)return "Уважаемый горожанин";
        if(points>=10)return "Известный горожанин";
        return "Неизвестный житель";
    }
}
