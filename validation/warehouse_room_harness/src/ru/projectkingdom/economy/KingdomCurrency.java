package ru.projectkingdom.economy;

import net.minecraft.world.item.Item;

public class KingdomCurrency {
    public static void register() {}
    public static java.util.Optional<Item> COPPER = java.util.Optional.empty();
    public static boolean credit(net.minecraft.server.level.ServerPlayer p, int amount) { return true; }
}