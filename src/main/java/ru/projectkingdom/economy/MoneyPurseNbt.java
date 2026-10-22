package ru.projectkingdom.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Versioned NBT codec for one purse, including lossless legacy migration. */
public final class MoneyPurseNbt {
    public static final String VERSION = "KingdomPurseVersion";
    public static final String COPPER = "KingdomCopperCoins";
    public static final String SILVER = "KingdomSilverCoins";
    public static final String GOLD = "KingdomGoldCoins";
    public static final String LEGACY_OVERFLOW = "KingdomLegacyOverflow";
    public static final String LEGACY_BALANCE = "KingdomCopperBalance";
    private static final int CURRENT_VERSION = 1;

    private MoneyPurseNbt() {}

    public static MoneyPurse read(ItemStack stack) {
        return stack.hasTag() ? readMigrating(stack.getOrCreateTag()) : MoneyPurse.empty();
    }

    public static MoneyPurse readMigrating(CompoundTag tag) {
        if (!tag.contains(VERSION) && tag.contains(LEGACY_BALANCE)) {
            MoneyPurse migrated = LegacyPurseMigration.fromCopperValue(Math.max(0, tag.getLong(LEGACY_BALANCE)));
            write(tag, migrated);
            tag.remove(LEGACY_BALANCE);
            return migrated;
        }
        long copper = Math.max(0, tag.getLong(COPPER));
        long silver = Math.max(0, tag.getLong(SILVER));
        long gold = Math.max(0, tag.getLong(GOLD));
        boolean overflow = tag.getBoolean(LEGACY_OVERFLOW);
        try {
            long count = Math.addExact(Math.addExact(copper, silver), gold);
            return new MoneyPurse(copper, silver, gold, overflow || count > MoneyPurse.MAX_COINS);
        } catch (ArithmeticException corrupt) {
            return MoneyPurse.empty();
        }
    }

    public static void write(ItemStack stack, MoneyPurse purse) {
        CompoundTag tag = stack.getOrCreateTag();
        write(tag, purse);
        if (tag.isEmpty()) stack.setTag(null);
    }

    public static void write(CompoundTag tag, MoneyPurse purse) {
        tag.remove(LEGACY_BALANCE);
        if (purse.coinCount() == 0) {
            tag.remove(VERSION); tag.remove(COPPER); tag.remove(SILVER); tag.remove(GOLD);
            tag.remove(LEGACY_OVERFLOW);
            return;
        }
        tag.putInt(VERSION, CURRENT_VERSION);
        tag.putLong(COPPER, purse.copper());
        tag.putLong(SILVER, purse.silver());
        tag.putLong(GOLD, purse.gold());
        tag.putBoolean(LEGACY_OVERFLOW, purse.legacyOverflow());
    }
}
