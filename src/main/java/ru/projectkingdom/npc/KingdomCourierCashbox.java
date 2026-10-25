package ru.projectkingdom.npc;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Physical cash handover: never pays from a remote bank account.
 * The seller puts a dedicated chest/barrel directly above their trade chest.
 * Entire payout must fit before any escrow is released.
 */
public final class KingdomCourierCashbox {
    private KingdomCourierCashbox() {}

    /** Check seller cashbox capacity without inserting or minting a coin. */
    public static boolean canDeposit(Container cashbox,long copper) {
        if(cashbox==null||copper<=0||copper>1_000_000)return false;
        List<ItemStack> proposed=new ArrayList<>();
        for(int i=0;i<cashbox.getContainerSize();i++)proposed.add(cashbox.getItem(i).copy());
        long remainder=copper;
        remainder=place(proposed,cashbox,KingdomCurrency.GOLD.get(),remainder,10000);
        remainder=place(proposed,cashbox,KingdomCurrency.SILVER.get(),remainder,100);
        remainder=place(proposed,cashbox,KingdomCurrency.COPPER.get(),remainder,1);
        return remainder==0;
    }

    public static boolean deposit(Container cashbox, long copper) {
        if(cashbox==null || copper<=0 || copper>1_000_000)return false;
        List<ItemStack> proposed=new ArrayList<>();
        for(int i=0;i<cashbox.getContainerSize();i++)proposed.add(cashbox.getItem(i).copy());
        long remainder=copper;
        remainder=place(proposed,cashbox,KingdomCurrency.GOLD.get(),remainder,10000);
        remainder=place(proposed,cashbox,KingdomCurrency.SILVER.get(),remainder,100);
        remainder=place(proposed,cashbox,KingdomCurrency.COPPER.get(),remainder,1);
        if(remainder!=0)return false;
        // All-or-nothing on the single Minecraft server thread.
        for(int i=0;i<proposed.size();i++)cashbox.setItem(i,proposed.get(i));
        cashbox.setChanged();
        return true;
    }

    private static long place(List<ItemStack> slots,Container box,Item coin,long amount,long value) {
        long count=amount/value;
        if(count==0)return amount;
        for(int i=0;i<slots.size()&&count>0;i++) {
            ItemStack stack=slots.get(i);
            if(!stack.is(coin))continue;
            int free=Math.max(0,Math.min(stack.getMaxStackSize(),box.getMaxStackSize())-stack.getCount());
            int add=(int)Math.min(count,free);
            if(add>0){stack.grow(add);count-=add;}
        }
        for(int i=0;i<slots.size()&&count>0;i++) {
            if(!slots.get(i).isEmpty())continue;
            ItemStack stack=new ItemStack(coin);
            if(!box.canPlaceItem(i,stack))continue;
            int add=(int)Math.min(count,Math.min(stack.getMaxStackSize(),box.getMaxStackSize()));
            stack.setCount(add);slots.set(i,stack);count-=add;
        }
        return amount-(amount/value-count)*value;
    }
}
