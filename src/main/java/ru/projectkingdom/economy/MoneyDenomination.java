package ru.projectkingdom.economy;

/** Physical Project Kingdom coin denominations, valued in copper units. */
public enum MoneyDenomination {
    COPPER(1), SILVER(100), GOLD(10_000);

    private final long copperValue;

    MoneyDenomination(long copperValue) {
        this.copperValue = copperValue;
    }

    public long copperValue() {
        return copperValue;
    }
}
