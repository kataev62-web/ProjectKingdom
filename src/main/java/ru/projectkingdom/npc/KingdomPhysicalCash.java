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
import ru.projectkingdom.administration.KingdomBankVaultRooms;

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
        if(role==AdministrativeBindingRole.BANK_VAULT){
            var rooms=KingdomBankVaultRooms.get(actor.getServer());
            if(rooms.room(town)!=null)
                return rooms.vault(actor.serverLevel(),town);
        }
        var bindings=KingdomAdministrationData.get(actor.getServer()).containerBindings(town,role);
        if(bindings.size()!=1)return null;
        var binding=bindings.get(0);
        var level=actor.serverLevel();
        if(!binding.dimension().equals(level.dimension().location().toString()))return null;
        BlockPos pos=BlockPos.of(binding.packedPos());
        if(!level.hasChunkAt(pos))return null;
        // This vault was previously bound at its actual city service by an authorised clerk.
        return KingdomContainerAccess.resolve(level,pos);
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
        // Multi-chest bank rooms may contain thousands of slots. Do not rewrite
        // unmodified inventories, especially modded chests or unloaded save data.
        boolean changed=false;
        for(int i=0;i<next.size();i++){
            if(net.minecraft.world.item.ItemStack.matches(container.getItem(i),next.get(i)))continue;
            container.setItem(i,next.get(i));
            changed=true;
        }
        if(changed)container.setChanged();
    }
    private static boolean insert(Container container,List<ItemStack> slots,Item coin,long total){
        long remaining=total;
        for(int i=0;i<slots.size()&&remaining>0;i++){
            ItemStack present=slots.get(i);
            if(!present.is(coin) || present.hasTag())continue;
            int available=Math.min(present.getMaxStackSize(),container.getMaxStackSize())-present.getCount();
            if(available<=0)continue;
            int move=(int)Math.min(remaining,available);
            ItemStack candidate=present.copy();candidate.grow(move);
            if(!container.canPlaceItem(i,candidate))continue;
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
            if(vault.getItem(i).is(COINS[k]) && !vault.getItem(i).hasTag())available[k]+=vault.getItem(i).getCount();
        PhysicalCoinSelection.Coins selection = PhysicalCoinSelection.select(
                amount, available[0], available[1], available[2]);
        if(selection==null)return null;
        long[] selected={selection.gold(),selection.silver(),selection.copper()};
        var slots=snapshot(vault);
        for(int k=0;k<COINS.length;k++) {
            long need=selected[k];
            for(int i=0;i<slots.size()&&need>0;i++){
                ItemStack stack=slots.get(i);
                if(!stack.is(COINS[k]) || stack.hasTag())continue;
                int take=(int)Math.min(need,stack.getCount());
                stack.shrink(take);need-=take;
            }
            if(need!=0)return null;
        }
        return slots;
    }
    /**
     * Deposits exactly the coin denominations removed by planWithdrawal.
     * A 100-copper move may not silently replace 100 copper coins with 1 silver coin.
     * Tagged/NBT coins are not fungible with ordinary coins and must be reconciled.
     */
    private static List<ItemStack> planExactMoveDeposit(
            Container source, List<ItemStack> afterSource, Container target) {
        if(source==null||afterSource==null||target==null
                ||afterSource.size()!=source.getContainerSize())return null;
        long[] moved={0,0,0};
        for(int i=0;i<afterSource.size();i++){
            ItemStack before=source.getItem(i), after=afterSource.get(i);
            if(before.getCount()==after.getCount())continue;
            if(before.getCount()<after.getCount()||before.hasTag())return null;
            boolean found=false;
            for(int k=0;k<COINS.length;k++)if(before.is(COINS[k])){
                moved[k]+=before.getCount()-after.getCount();
                found=true;break;
            }
            if(!found)return null;
        }
        List<ItemStack> targetSlots=snapshot(target);
        for(int k=0;k<COINS.length;k++)
            if(moved[k]>0&&!insert(target,targetSlots,COINS[k],moved[k]))return null;
        return targetSlots;
    }
    /** Reject any unknown backing or alias, including two wrappers over one chest. */
    static boolean independentPhysicalSlots(Container source,Container target) {
        if(source==null||target==null||source==target
                ||source.getContainerSize()<1||target.getContainerSize()<1)return false;
        List<KingdomPhysicalSlotGuard.Slot> a=new ArrayList<>();
        List<KingdomPhysicalSlotGuard.Slot> b=new ArrayList<>();
        for(int i=0;i<source.getContainerSize();i++){
            var key=KingdomContainerAccess.physicalSlot(source,i);
            if(key==null)return false;
            a.add(key);
        }
        for(int i=0;i<target.getContainerSize();i++){
            var key=KingdomContainerAccess.physicalSlot(target,i);
            if(key==null)return false;
            b.add(key);
        }
        return KingdomPhysicalSlotGuard.disjoint(a,b);
    }

    /** Check full slot snapshots, not just total copper value: NBT must not change. */
    private static boolean matches(Container container,List<ItemStack> expected) {
        if(container.getContainerSize()!=expected.size())return false;
        for(int i=0;i<expected.size();i++)
            if(!ItemStack.matches(container.getItem(i),expected.get(i)))return false;
        return true;
    }

    /** Preserve each physical denomination individually, not only total face value. */
    private static long[] coinCounts(List<ItemStack> a,List<ItemStack> b) {
        long[] result=new long[COINS.length];
        for(List<ItemStack> stacks:List.of(a,b))
            for(ItemStack stack:stacks)
                for(int k=0;k<COINS.length;k++)
                    if(stack.is(COINS[k]))result[k]=Math.addExact(result[k],stack.getCount());
        return result;
    }

    /**
     * Try to restore exact NBT snapshots on an in-memory write failure.
     * If any wrapper refuses restoration, throw instead of reporting false success
     * or retrying an uncertain operation.
     */
    private static void rollbackOrQuarantine(Container source,List<ItemStack> oldSource,
                                              Container target,List<ItemStack> oldTarget,
                                              RuntimeException cause) {
        RuntimeException rollbackProblem=null;
        try{apply(target,oldTarget);}catch(RuntimeException failure){rollbackProblem=failure;}
        try{apply(source,oldSource);}catch(RuntimeException failure){
            if(rollbackProblem==null)rollbackProblem=failure;
            else rollbackProblem.addSuppressed(failure);
        }
        boolean restored=false;
        try{restored=matches(source,oldSource)&&matches(target,oldTarget);}
        catch(RuntimeException failure){
            if(rollbackProblem==null)rollbackProblem=failure;
            else rollbackProblem.addSuppressed(failure);
        }
        if(!restored||rollbackProblem!=null){
            IllegalStateException unsafe=new IllegalStateException(
                    "Physical cash transfer rollback is uncertain: lock both containers for operator reconciliation",cause);
            if(rollbackProblem!=null)unsafe.addSuppressed(rollbackProblem);
            throw unsafe;
        }
    }

    /** Transfer physical coin items only between provably disjoint storage slots. */
    public static boolean canMove(Container source,Container target,long amount) {
        if(!independentPhysicalSlots(source,target))return false;
        List<ItemStack> taken=planWithdrawal(source,amount);
        return taken!=null&&planExactMoveDeposit(source,taken,target)!=null;
    }
    public static boolean move(Container source,Container target,long amount) {
        if(!independentPhysicalSlots(source,target))return false;
        List<ItemStack> oldSource=snapshot(source),oldTarget=snapshot(target);
        List<ItemStack> taken=planWithdrawal(source,amount);
        List<ItemStack> added=planExactMoveDeposit(source,taken,target);
        if(taken==null||added==null)return false;
        if(!matches(source,oldSource)||!matches(target,oldTarget))return false;
        if(!java.util.Arrays.equals(coinCounts(oldSource,oldTarget),coinCounts(taken,added)))
            return false;
        try{
            apply(source,taken);
            if(!matches(source,taken)||!matches(target,oldTarget))
                throw new IllegalStateException("Physical source changed or storage wrappers alias");
            apply(target,added);
            if(!matches(source,taken)||!matches(target,added)
                    ||!java.util.Arrays.equals(coinCounts(oldSource,oldTarget),
                            coinCounts(snapshot(source),snapshot(target))))
                throw new IllegalStateException("Physical cash movement was not conserved");
            return true;
        }catch(RuntimeException unsafe){
            rollbackOrQuarantine(source,oldSource,target,oldTarget,unsafe);
            return false;
        }
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
