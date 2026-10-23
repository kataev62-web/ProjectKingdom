package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import ru.projectkingdom.administration.KingdomAdministrationData;

/**
 * Server-thread settlement of a purse against real coin ItemStacks in a loaded vault.
 * The world container is authoritative for cash: no virtual reserve, fallback
 * minting or chunk loading. Coin value is expressed in copper units.
 *
 * This is an in-memory atomic action, NOT a crash-atomic two-file journal.
 */
public final class KingdomPhysicalCash {
    private KingdomPhysicalCash() {}
    private static final long MAX=1_000_000L;
    private static final Item[] COINS={KingdomCurrency.GOLD.get(),KingdomCurrency.SILVER.get(),KingdomCurrency.COPPER.get()};
    private static final long[] VALUES={10_000L,100L,1L};

    public static Container townVault(ServerPlayer actor, java.util.UUID town,
            AdministrativeBindingRole role) {
        if(actor==null||town==null||role==null)return null;
        var bindings=KingdomAdministrationData.get(actor.getServer()).containerBindings(town,role);
        if(bindings.size()!=1)return null;
        var binding=bindings.get(0);
        var level=actor.serverLevel();
        if(!binding.dimension().equals(level.dimension().location().toString()))return null;
        BlockPos pos=BlockPos.of(binding.packedPos());
        if(!level.hasChunkAt(pos))return null;
        // This vault was previously bound at its actual city service by an authorised clerk.
        var entity=level.getBlockEntity(pos);
        return entity instanceof Container c?c:null;
    }

    public static long value(Container vault) {
        if(vault==null)return -1;
        long sum=0;
        for(int i=0;i<vault.getContainerSize();i++) {
            var stack=vault.getItem(i);
            if(stack.isEmpty())continue;
            long coin=KingdomCurrency.value(stack);
            if(coin==0)continue;
            try{sum=Math.addExact(sum,Math.multiplyExact(coin,stack.getCount()));}
            catch(ArithmeticException overflow){return -1;}
        }
        return sum;
    }

    private static List<ItemStack> snapshot(Container container) {
        var result=new ArrayList<ItemStack>(container.getContainerSize());
        for(int i=0;i<container.getContainerSize();i++)result.add(container.getItem(i).copy());
        return result;
    }
    private static void apply(Container container,List<ItemStack> next) {
        for(int i=0;i<next.size();i++)container.setItem(i,next.get(i));
        container.setChanged();
    }
    private static boolean insert(Container container,List<ItemStack> slots,Item coin,long total){
        long remaining=total;
        for(int i=0;i<slots.size()&&remaining>0;i++){
            ItemStack present=slots.get(i);
            if(!present.is(coin))continue;
            int available=Math.min(present.getMaxStackSize(),container.getMaxStackSize())-present.getCount();
            if(available<=0)continue;
            int move=(int)Math.min(remaining,available);
            present.grow(move);remaining-=move;
        }
        for(int i=0;i<slots.size()&&remaining>0;i++){
            if(!slots.get(i).isEmpty())continue;
            ItemStack single=new ItemStack(coin);
            int move=(int)Math.min(remaining,Math.min(single.getMaxStackSize(),container.getMaxStackSize()));
            if(move<=0)return false;
            single.setCount(move);
            if(!container.canPlaceItem(i,single))continue;
            slots.set(i,single);remaining-=move;
        }
        return remaining==0;
    }
    private static List<ItemStack> planDeposit(Container vault,long amount) {
        if(vault==null||amount<1||amount>MAX)return null;
        var slots=snapshot(vault);
        long rest=amount;
        for(int kind=0;kind<VALUES.length;kind++){
            long count=rest/VALUES[kind];rest%=VALUES[kind];
            if(count>0&&!insert(vault,slots,COINS[kind],count))return null;
        }
        return slots;
    }
    /** Removes ONLY existing coins; no denomination change can appear from thin air. */
    private static List<ItemStack> planWithdrawal(Container vault,long amount){
        if(vault==null||amount<1||amount>MAX)return null;
        long[] available={0,0,0};
        for(int i=0;i<vault.getContainerSize();i++)for(int k=0;k<COINS.length;k++)
            if(vault.getItem(i).is(COINS[k]))available[k]+=vault.getItem(i).getCount();
        long[] selection=null;
        long maxGold=Math.min(available[0],amount/VALUES[0]);
        for(long gold=maxGold;gold>=0&&selection==null;gold--){
            long afterGold=amount-gold*VALUES[0];
            long maxSilver=Math.min(available[1],afterGold/VALUES[1]);
            long minSilver=Math.max(0,(afterGold-available[2]+99)/100);
            if(minSilver>maxSilver)continue;
            long silver=maxSilver,copper=afterGold-silver*100;
            if(copper<=available[2])selection=new long[]{gold,silver,copper};
        }
        if(selection==null)return null;
        var slots=snapshot(vault);
        for(int k=0;k<COINS.length;k++) {
            long need=selection[k];
            for(int i=0;i<slots.size()&&need>0;i++){
                ItemStack stack=slots.get(i);
                if(!stack.is(COINS[k]))continue;
                int take=(int)Math.min(need,stack.getCount());
                stack.shrink(take);need-=take;
            }
            if(need!=0)return null;
        }
        return slots;
    }
    /** Trusted NPC settlement: the payroll layer validates the recipient's physical purse
     * capacity before taking actual coins from the company's protected container. */
    static boolean takeForNpc(Container vault,long amount) {
        List<ItemStack> plan=planWithdrawal(vault,amount);
        if(plan==null)return false;
        apply(vault,plan);
        return true;
    }
    /** Verify space before removing funds from a player's real wallet. */
    public static boolean deposit(ServerPlayer payer,Container vault,long amount){
        if(payer==null||KingdomCurrency.balance(payer)<amount)return false;
        List<ItemStack> plan=planDeposit(vault,amount);
        if(plan==null||!KingdomCurrency.debit(payer,amount))return false;
        apply(vault,plan);
        return true;
    }
    /** Withdraw only exact coins held in the vault, then credit the real purse. */
    public static boolean withdraw(ServerPlayer receiver,Container vault,long amount){
        if(receiver==null)return false;
        List<ItemStack> plan=planWithdrawal(vault,amount);
        if(plan==null||!KingdomCurrency.credit(receiver,amount))return false;
        apply(vault,plan);
        return true;
    }
}
