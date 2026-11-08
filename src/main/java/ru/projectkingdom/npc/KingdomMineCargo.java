package ru.projectkingdom.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Mine cargo accounting: construction consumables must never be counted as
 * finished ore, nor emptied into the finished-goods warehouse on delivery.
 */
public final class KingdomMineCargo {
    private KingdomMineCargo() {}

    public static boolean isSupplyId(String id) {
        return "minecraft:torch".equals(id)
            || "minecraft:ladder".equals(id)
            || id.startsWith("tfc:wood/planks/")
            || id.startsWith("minecraft:") && id.endsWith("_planks")
            || KingdomMineSupportPlan.supports(id);
    }

    public static boolean isSupply(ItemStack item) {
        return !item.isEmpty() && (
            isSupplyId(BuiltInRegistries.ITEM.getKey(item.getItem()).toString())
            || KingdomMineTools.matches(item,KingdomMineTools.Tool.SHOVEL)
            || KingdomMineTools.matches(item,KingdomMineTools.Tool.PICKAXE));
    }

    public static int minedCount(KingdomNpcData.Worker worker) {
        int total=0;
        for(ItemStack item:worker.fieldInventory)
            if(!isSupply(item))total+=item.getCount();
        return total;
    }
}