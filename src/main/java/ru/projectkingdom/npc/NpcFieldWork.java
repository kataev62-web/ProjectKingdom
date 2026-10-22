package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-side, physical actions only. Limited to LOADED chunks and bounded search.
 * Vanilla crop implementation. TFC/Crea​te require recipe/station-specific adapters.
 */
public final class NpcFieldWork {
    private NpcFieldWork() {}
    public static final int MAX_CARRIED = 9 * 64;
    private static final int RADIUS = 6;

    private static boolean eligible(KingdomNpcData.Worker w) {
        return w.ageYears >= 18 && w.hunger >= 20 && w.energy >= 15 && !w.contractPaused;
    }
    private static boolean land(ServerLevel level, BlockPos pos, KingdomNpcData.Worker w) {
        UUID owner = KingdomTerritories.get(level.getServer()).owner(level,pos);
        // Employees never work outside their employer's land. Independents never take from claimed plots.
        return w.employer == null ? owner == null : w.employer.equals(owner);
    }
    private static int carried(KingdomNpcData.Worker w) {
        int total=0; for(ItemStack stack:w.fieldInventory)total+=stack.getCount(); return total;
    }
    private static boolean canFit(KingdomNpcData.Worker w,List<ItemStack> drops) {
        int n=carried(w);for(ItemStack stack:drops)n+=stack.getCount();return n<=MAX_CARRIED;
    }
    private static void put(KingdomNpcData.Worker w, ItemStack item) {
        if(item.isEmpty())return;
        ItemStack remaining=item.copy();
        for(ItemStack stack:w.fieldInventory)if(ItemStack.isSameItemSameTags(stack,remaining)){
            int add=Math.min(stack.getMaxStackSize()-stack.getCount(),remaining.getCount());
            if(add>0){stack.grow(add);remaining.shrink(add);}if(remaining.isEmpty())return;
        }
        while(!remaining.isEmpty()){
            int add=Math.min(remaining.getCount(),remaining.getMaxStackSize());
            ItemStack next=remaining.copy();next.setCount(add);w.fieldInventory.add(next);remaining.shrink(add);
        }
    }
    private static ItemStack takeOne(KingdomNpcData.Worker w, net.minecraft.world.item.Item item) {
        for(int i=0;i<w.fieldInventory.size();i++){
            ItemStack s=w.fieldInventory.get(i);
            if(s.is(item)) { ItemStack one=s.split(1);if(s.isEmpty())w.fieldInventory.remove(i);return one; }
        }
        return ItemStack.EMPTY;
    }
    private static net.minecraft.world.item.Item replantSeed(BlockState state) {
        if(state.is(Blocks.WHEAT))return Items.WHEAT_SEEDS;
        if(state.is(Blocks.BEETROOTS))return Items.BEETROOT_SEEDS;
        if(state.is(Blocks.CARROTS))return Items.CARROT;
        if(state.is(Blocks.POTATOES))return Items.POTATO;
        return null;
    }
    private static boolean isCandidate(BlockState state,KingdomNpcData.Worker w) {
        if("farmer".equals(w.job))return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        if("lumberjack".equals(w.job))return state.is(BlockTags.LOGS);
        if("miner".equals(w.job))return state.is(BlockTags.MINEABLE_WITH_PICKAXE) &&
              (state.is(Blocks.STONE)||state.is(Blocks.DEEPSLATE)||state.is(Blocks.COAL_ORE)||state.is(Blocks.IRON_ORE));
        return false;
    }
    private static boolean toolOk(BlockState state, KingdomNpcData.Worker w) {
        if(state.getBlock() instanceof CropBlock)return true;
        if(state.is(BlockTags.LOGS))return w.fieldTool.getItem() instanceof AxeItem;
        return w.fieldTool.getItem() instanceof PickaxeItem && w.fieldTool.isCorrectToolForDrops(state);
    }
    /** Borrow the actual tool from the designated RAW warehouse; never create tools. */
    private static boolean borrowTool(ServerLevel level, Villager npc, KingdomNpcData.Worker w) {
        if(!w.fieldTool.isEmpty())return true;
        if(w.employer==null || !w.hasSource)return false;
        BlockPos pos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(!level.hasChunkAt(pos)||!KingdomTerritories.get(level.getServer()).usable(level,pos,w.employer,"raw"))return false;
        if(npc.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>6.25){npc.getNavigation().moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0.8);return false;}
        BlockEntity be=level.getBlockEntity(pos);if(!(be instanceof Container c))return false;
        for(int i=0;i<c.getContainerSize();i++){
            ItemStack stack=c.getItem(i);
            if(("lumberjack".equals(w.job)&&stack.getItem() instanceof AxeItem ||
                "miner".equals(w.job)&&stack.getItem() instanceof PickaxeItem) && c.canTakeItem(c,i,stack)){
                w.fieldTool=c.removeItem(i,1);c.setChanged();KingdomNpcData.get(level.getServer()).setDirty();return !w.fieldTool.isEmpty();
            }
        }
        return false;
    }
    /** Route harvested stock into a marked finished warehouse. Unemployed keep it in a persistent personal inventory. */
    private static boolean deposit(ServerLevel level,Villager npc, KingdomNpcData.Worker w){
        if(w.fieldInventory.isEmpty() || w.employer==null || !w.hasDestination)return false;
        BlockPos pos=new BlockPos(w.destX,w.destY,w.destZ);
        if(!level.hasChunkAt(pos)||!KingdomTerritories.get(level.getServer()).usable(level,pos,w.employer,"finished"))return false;
        if(npc.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>6.25){npc.getNavigation().moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0.8);return false;}
        BlockEntity be=level.getBlockEntity(pos);if(!(be instanceof Container c))return false;
        boolean moved=false;
        for(int inv=0;inv<w.fieldInventory.size();){
            ItemStack s=w.fieldInventory.get(inv);
            for(int slot=0;slot<c.getContainerSize()&&!s.isEmpty();slot++){
                if(!c.canPlaceItem(slot,s))continue;
                ItemStack cur=c.getItem(slot);
                if(cur.isEmpty()){
                    int n=Math.min(s.getCount(),Math.min(s.getMaxStackSize(),c.getMaxStackSize()));
                    ItemStack movedStack=s.copy();movedStack.setCount(n);c.setItem(slot,movedStack);s.shrink(n);moved=true;
                }else if(ItemStack.isSameItemSameTags(cur,s)){
                    int n=Math.min(s.getCount(),Math.min(cur.getMaxStackSize(),c.getMaxStackSize())-cur.getCount());
                    if(n>0){cur.grow(n);s.shrink(n);moved=true;}
                }
            }
            if(s.isEmpty())w.fieldInventory.remove(inv);else inv++;
        }
        if(moved){c.setChanged();KingdomNpcData.get(level.getServer()).setDirty();}return moved;
    }
    /** Execute ONE bounded action; called per NPC once every 2 seconds, never on unloaded chunks. */
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w) {
        if(!eligible(w)||!w.dimension.equals(level.dimension().location().toString())||"stay".equals(w.order)||"haul".equals(w.order)||"follow".equals(w.order))return false;
        if(w.employer!=null && !"work".equals(w.order))return false;
        if(!"farmer".equals(w.job)&&!"lumberjack".equals(w.job)&&!"miner".equals(w.job))return false;
        if(w.employer!=null && !w.fieldInventory.isEmpty()){
            boolean deposited=deposit(level,npc,w);
            if(deposited || carried(w)>=MAX_CARRIED/2)return deposited;
        }
        if(carried(w)>MAX_CARRIED-64)return false;
        if(!"farmer".equals(w.job)&& !borrowTool(level,npc,w))return false;
        BlockPos center=npc.blockPosition();BlockPos target=null;
        double best=Double.MAX_VALUE;
        // Finite scan; never force-load chunks or harvest other owners' property.
        for(BlockPos p:BlockPos.betweenClosed(center.offset(-RADIUS,-2,-RADIUS),center.offset(RADIUS,2,RADIUS))){
            if(!level.hasChunkAt(p)||!land(level,p,w))continue;
            BlockState state=level.getBlockState(p);
            if(!isCandidate(state,w)||!toolOk(state,w))continue;
            double d=p.distSqr(center);if(d<best){best=d;target=p.immutable();}
        }
        if(target==null)return false;
        if(npc.distanceToSqr(target.getX()+0.5,target.getY()+0.5,target.getZ()+0.5)>6.25){
            npc.getNavigation().moveTo(target.getX()+0.5,target.getY(),target.getZ()+0.5,0.75);return false;
        }
        if(!level.hasChunkAt(target)||!land(level,target,w))return false;
        BlockState state=level.getBlockState(target);if(!isCandidate(state,w)||!toolOk(state,w))return false;
        BlockEntity be=level.getBlockEntity(target);
        ItemStack tool=w.fieldTool.isEmpty()?ItemStack.EMPTY:w.fieldTool;
        List<ItemStack> drops=Block.getDrops(state,level,target,be,npc,tool);
        if(drops.isEmpty()||!canFit(w,drops))return false;
        // Atomic server-side action: only break AFTER validating output capacity.
        if(!level.setBlock(target,Blocks.AIR.defaultBlockState(),3))return false;
        for(ItemStack drop:drops)put(w,drop);
        if(state.getBlock() instanceof CropBlock){
            var seed=replantSeed(state);
            if(seed!=null && !takeOne(w,seed).isEmpty())level.setBlock(target,state.getBlock().defaultBlockState(),3);
        }else if(!w.fieldTool.isEmpty()){
            w.fieldTool.hurt(1,level.random,null);
            if(w.fieldTool.getDamageValue()>=w.fieldTool.getMaxDamage())w.fieldTool=ItemStack.EMPTY;
        }
        w.fieldHarvested+=drops.stream().mapToInt(ItemStack::getCount).sum();
        w.skillXp++;w.energy=Math.max(0,w.energy-1);
        return true;
    }
}
