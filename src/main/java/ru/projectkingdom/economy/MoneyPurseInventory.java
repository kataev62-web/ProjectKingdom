package ru.projectkingdom.economy;

/** Inventory rules shared by the item and server transaction adapter. */
public final class MoneyPurseInventory {
    public static final int EMPTY_STACK_LIMIT = 16;

    private MoneyPurseInventory() {}

    public static int maxStackSize(MoneyPurse purse) {
        return purse.coinCount() == 0 ? EMPTY_STACK_LIMIT : 1;
    }
}
