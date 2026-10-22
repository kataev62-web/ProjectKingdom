import ru.projectkingdom.economy.*;

public final class MoneyPurseContractTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        MoneyPurse purse = new MoneyPurse(40, 12, 3, false);
        check(purse.coinCount() == 55, "coin count");
        check(purse.valueCopper() == 31_240, "copper value");
        check(MoneyDenomination.SILVER.copperValue() == 100, "silver value");
        check(MoneyDenomination.GOLD.copperValue() == 10_000, "gold value");
        check(!purse.canAccept(MoneyDenomination.COPPER, 946), "capacity");
        MoneyPurse migrated = LegacyPurseMigration.fromCopperValue(10_000_001);
        check(migrated.valueCopper() == 10_000_001, "legacy value preserved");
        check(migrated.legacyOverflow(), "legacy overflow marker");
        System.out.println("PASS money purse denomination, capacity, and migration contract");
    }
}
