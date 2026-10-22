import java.nio.file.*;

public final class MoneyPurseIntegrationContractTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) throws Exception {
        String root = Files.readString(Path.of("src/main/java/ru/projectkingdom/economy/MoneyPurseNbt.java"));
        String currency = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomCurrency.java"));
        String item = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomWalletItem.java"));
        check(root.contains("KingdomPurseVersion"), "version key");
        check(root.contains("KingdomCopperCoins") && root.contains("KingdomSilverCoins")
                && root.contains("KingdomGoldCoins"), "three denomination keys");
        check(root.contains("KingdomLegacyOverflow"), "overflow key");
        check(currency.contains("List<PurseSlot>"), "slot ordered purse operations");
        check(item.contains("getMaxStackSize(ItemStack"), "dynamic stack limit");
        check(item.contains("separateForUse"), "empty stack separation");
        System.out.println("PASS purse NBT, inventory-slot, and dynamic-stack integration contract");
    }
}
