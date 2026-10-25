package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.ModList;

/**
 * Collects only unfertilized real TFC eggs from real TFC nest boxes on a
 * REGISTERED, owned pen. TFC itself lays eggs and controls pregnancy/hatching.
 * Never generates eggs, never modifies a bird's production timer.
 */
public final class KingdomTfcNestWork {
    private KingdomTfcNestWork(){}
    private static boolean loaded,compatible;
    private static Method eggGet, isFertilized;
    private static boolean init(){
        if(loaded)return compatible;
        loaded=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try{
            Class<?> cap=Class.forName("net.dries007.tfc.common.capabilities.egg.EggCapability");
            Class<?> egg=Class.forName("net.dries007.tfc.common.capabilities.egg.IEgg");
            eggGet=cap.getMethod("get",ItemStack.class);
            isFertilized=egg.getMethod("isFertilized");
            compatible=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC nest egg adapter disabled: "+ex);
        }
        return compatible;
    }

    /** Unfertilized, unspoiled: excludes eggs that could hatch into livestock. */
    public static boolean edibleEgg(ItemStack stack){
        if(stack==null||stack.isEmpty()||!init()
                ||KingdomNpcNutrition.spoiled(stack))return false;
        try{
            Object egg=eggGet.invoke(null,stack);
            return egg!=null && !Boolean.TRUE.equals(isFertilized.invoke(egg));
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
    private static boolean inPen(BlockPos block,KingdomRanchPenData.Pen pen){
        return block.getX()>=pen.minX()&&block.getX()<=pen.maxX()
            &&block.getZ()>=pen.minZ()&&block.getZ()<=pen.maxZ()
            &&Math.abs(block.getY()-pen.y())<=3;
    }

    /** Read-only: how many fresh unfertilized eggs exist in real loaded nests. */
    public static int availableEggs(ServerLevel level,KingdomRanchPenData.Pen pen){
        if(!init()||!pen.dimension().equals(level.dimension().location().toString()))return 0;
        int eggs=0;
        for(int x=pen.minX();x<=pen.maxX();x++)for(int z=pen.minZ();z<=pen.maxZ();z++)
            for(int y=pen.y()-2;y<=pen.y()+3;y++){
                BlockPos pos=new BlockPos(x,y,z);
                if(!level.hasChunkAt(pos)||!inPen(pos,pen)
                    ||!"tfc:nest_box".equals(BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(pos).getBlock()).toString()))continue;
                var nest=level.getBlockEntity(pos);
                if(nest==null)continue;
                try{
                    Object inventory=nest.getClass().getMethod("getInventory").invoke(nest);
                    if(!(inventory instanceof IItemHandler handler))continue;
                    for(int slot=0;slot<handler.getSlots();slot++)
                        if(edibleEgg(handler.getStackInSlot(slot)))eggs++;
                }catch(ReflectiveOperationException|RuntimeException ignored){}
            }
        return eggs;
    }

    /** Work one nest slot at a time; return true only after a real item transfer. */
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                               KingdomRanchPenData.Pen pen,Container output){
        if(!init()||!pen.enabled()
                ||!pen.dimension().equals(level.dimension().location().toString())
                ||!KingdomRanchPolicyData.get(level.getServer()).policy(pen.id()).collectEggs())
            return false;
        var owner=KingdomNpcFarmOwner.workOwner(level,worker);
        if(owner==null)return false;
        var claims=KingdomTerritories.get(level.getServer());
        // Bounded to 16x16x7 without ever requesting a chunk load.
        for(int x=pen.minX();x<=pen.maxX();x++)
            for(int z=pen.minZ();z<=pen.maxZ();z++)
                for(int y=pen.y()-2;y<=pen.y()+3;y++){
            BlockPos pos=new BlockPos(x,y,z);
            if(!inPen(pos,pen)||!level.hasChunkAt(pos)
                    ||!claims.canUse(level,pos,owner)
                    ||!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                        .toString().equals("tfc:nest_box"))continue;
            var nest=level.getBlockEntity(pos);
            if(nest==null)continue;
            IItemHandler inventory;
            try{
                Object raw=nest.getClass().getMethod("getInventory").invoke(nest);
                if(!(raw instanceof IItemHandler handler))continue;
                inventory=handler;
            }catch(ReflectiveOperationException|RuntimeException ex){continue;}
            for(int i=0;i<inventory.getSlots();i++){
                ItemStack current=inventory.getStackInSlot(i).copy();
                if(!edibleEgg(current)||!KingdomLivestockStorage.canInsert(output,current)
                        ||!NpcFieldWork.canFit(worker,java.util.List.of(current)))continue;
                if(npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>6.25){
                    npc.getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.75);
                    return false;
                }
                ItemStack simulated=inventory.extractItem(i,1,true);
                if(!edibleEgg(simulated)||!ItemStack.isSameItemSameTags(simulated,current))
                    continue;
                ItemStack actual=inventory.extractItem(i,1,false);
                if(actual.isEmpty())return false;
                // Preserve the real egg even if a mod modified the item between operations.
                NpcFieldWork.put(worker,actual);
                nest.setChanged();
                worker.workUnits++;
                worker.fieldHarvested+=actual.getCount();
                worker.skillXp++;
                worker.energy=Math.max(0,worker.energy-1);
                KingdomNpcData.get(level.getServer()).setDirty();
                return true;
            }
        }
        return false;
    }
}
