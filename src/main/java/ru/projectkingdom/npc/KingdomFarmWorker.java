package ru.projectkingdom.npc;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Farm employee. The field plan is authoritative; workers never clear land,
 * generate seeds or accelerate growth artificially.
 */
public final class KingdomFarmWorker {
    private KingdomFarmWorker(){}

    static UUID farm(ServerLevel level,KingdomNpcData.Worker w) {
        UUID personal=KingdomNpcFarmOwner.ownedFarm(level.getServer(),w);
        if(personal!=null)return personal;
        if(w.employer==null||!KingdomNpcWorkday.employed(level.getServer(),w))return null;
        var enterprises=KingdomEnterpriseData.get(level.getServer());
        for(var employment:KingdomEnterprisePayrollData.get(level.getServer()).all()) {
            if(!employment.active()||!employment.worker().equals(w.entityId)
                ||enterprises.kind(employment.enterprise())!=EnterpriseKind.FARM
                ||(!w.workplaceId.isBlank()&&!w.workplaceId.equals(employment.enterprise().toString())))
                continue;
            var firm=enterprises.find(employment.enterprise());
            var core=enterprises.core(employment.enterprise());
            if(firm!=null&&firm.owner().equals(w.employer)
                    &&core!=null&&core.active()&&core.kind()==EnterpriseKind.FARM)
                return firm.id();
        }
        return null;
    }
    private static boolean seed(ItemStack stack,List<KingdomFarmFieldData.Field> fields) {
        if(stack.isEmpty())return false;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return fields.stream().anyMatch(f->f.enabled()&&id.equals(f.seedId()));
    }
    private static int carried(KingdomNpcData.Worker worker) {
        return worker.fieldInventory.stream().mapToInt(ItemStack::getCount).sum();
    }
    private static boolean hasProduce(KingdomNpcData.Worker w,List<KingdomFarmFieldData.Field> fields) {
        for(var item:w.fieldInventory)if(!seed(item,fields))return true;
        return false;
    }
    private static boolean hasSeed(KingdomNpcData.Worker worker,String seedId) {
        return worker.fieldInventory.stream().anyMatch(stack->!stack.isEmpty()
            && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(seedId));
    }
    private static boolean storeNear(ServerLevel level,Villager npc,BlockPos pos){
        return npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=6.25;
    }
    /** Pull at most one REAL stack of 16 seeds from the assigned RAW container. */
    private static boolean refill(ServerLevel level,Villager npc,KingdomNpcData.Worker w,String seedId){
        if(seedId.isBlank()||KingdomNpcFarmOwner.workOwner(level,w)==null||!w.hasSource)return false;
        BlockPos pos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(!level.hasChunkAt(pos)
            ||!KingdomTerritories.get(level.getServer()).usable(level,pos,KingdomNpcFarmOwner.workOwner(level,w),"raw")
            ||!(level.getBlockEntity(pos) instanceof Container raw))return false;
        if(!storeNear(level,npc,pos)){
            npc.getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.75);
            return false;
        }
        for(int i=0;i<raw.getContainerSize();i++){
            ItemStack available=raw.getItem(i);
            if(available.isEmpty()||!raw.canTakeItem(raw,i,available)
                ||!seedId.equals(BuiltInRegistries.ITEM.getKey(available.getItem()).toString()))continue;
            int n=Math.min(Math.min(16,available.getCount()),NpcFieldWork.MAX_CARRIED-carried(w));
            if(n<=0)return false;
            ItemStack taken=raw.removeItem(i,n);
            if(taken.isEmpty())return false;
            raw.setChanged();NpcFieldWork.put(w,taken);
            KingdomNpcData.get(level.getServer()).setDirty();return true;
        }
        return false;
    }
    /** Bring a real N/P/K supplement from RAW warehouse, never invent nutrients. */
    private static boolean fertilize(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                     BlockPos cropPos,BlockState crop) {
        if(!storeNear(level,npc,cropPos)) {
            npc.getNavigation().moveTo(cropPos.getX()+.5,cropPos.getY(),cropPos.getZ()+.5,.75);
            return false;
        }
        for(ItemStack held:w.fieldInventory) {
            if(!KingdomFarmNutrition.recognized(held))continue;
            if(KingdomFarmNutrition.apply(level,cropPos,crop,held)){
                w.fieldInventory.removeIf(ItemStack::isEmpty);
                w.energy=Math.max(0,w.energy-1);
                KingdomNpcData.get(level.getServer()).setDirty();
                return true;
            }
        }
        BlockPos store=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(!level.hasChunkAt(store)||!KingdomTerritories.get(level.getServer())
                .usable(level,store,KingdomNpcFarmOwner.workOwner(level,w),"raw")
                ||!(level.getBlockEntity(store) instanceof Container raw))return false;
        if(!storeNear(level,npc,store)){
            npc.getNavigation().moveTo(store.getX()+.5,store.getY(),store.getZ()+.5,.75);
            return false;
        }
        return KingdomFarmNutrition.withdraw(level,cropPos,crop,raw,w);
    }

    private static boolean cropMatches(BlockState state,KingdomFarmFieldData.Field field){
        return KingdomFarmFieldData.cropId(field.seedId()).equals(
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
            && KingdomTfcFieldRules.simpleCrop(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                state.getBlock().getClass().getName());
    }
    private static boolean mature(BlockState state) {
        for(var p:state.getProperties())
            if("age".equals(p.getName())&&p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age){
                int last=age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(-1);
                return last>=0 && state.getValue(age)==last;
            }
        return false;
    }
    private static boolean harvest(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                   BlockPos pos,BlockState crop,KingdomFarmFieldData.Field field) {
        BlockEntity entity=level.getBlockEntity(pos);
        List<ItemStack> drops=Block.getDrops(crop,level,pos,entity,npc,ItemStack.EMPTY);
        if(drops.isEmpty()||!NpcFieldWork.canFit(w,drops))return false;
        if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),3))return false;
        for(ItemStack item:drops)NpcFieldWork.put(w,item);
        boolean switchCrop=KingdomFarmAutonomy.leaveForRotation(level,w,field);
        ItemStack replanted=switchCrop?ItemStack.EMPTY:NpcFieldWork.takeSeedById(w,field.seedId());
        BlockState seedling=crop.getBlock().defaultBlockState();
        var climate=KingdomFarmClimate.read(level,pos,seedling);
        if(!replanted.isEmpty()){
            if(climate.suitable() && seedling.canSurvive(level,pos)
                && level.setBlock(pos,seedling,3)) {
                // One real seed was consumed from the harvest.
            } else NpcFieldWork.put(w,replanted);
        }
        w.fieldHarvested+=drops.stream().mapToInt(ItemStack::getCount).sum();
        w.skillXp++;w.energy=Math.max(0,w.energy-1);w.workUnits++;
        KingdomNpcData.get(level.getServer()).setDirty();return true;
    }
    private static boolean plant(ServerLevel level,KingdomNpcData.Worker w,
                                 BlockPos pos,KingdomFarmFieldData.Field field,BlockState seedling){
        if(!seedling.canSurvive(level,pos)||!level.getBlockState(pos).isAir()
                ||!KingdomFarmClimate.read(level,pos,seedling).suitable())return false;
        ItemStack used=NpcFieldWork.takeSeedById(w,field.seedId());
        if(used.isEmpty())return false;
        if(!level.setBlock(pos,seedling,3)){
            NpcFieldWork.put(w,used);return false;
        }
        w.workUnits++;w.energy=Math.max(0,w.energy-1);
        w.skillXp++;KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
    public static BlockPos site(ServerLevel level,KingdomNpcData.Worker w) {
        UUID id=farm(level,w);if(id==null)return null;
        var fields=KingdomFarmFieldData.get(level.getServer()).of(id);
        BlockPos preferred=null;double best=Double.MAX_VALUE;
        boolean owner=KingdomNpcFarmOwner.ownedFarm(level.getServer(),w)!=null;
        for(var field:fields)if(field.enabled()
            &&field.dimension().equals(w.dimension)
            && (owner || !field.seedId().isBlank())){
            var pos=field.centerCrop();
            if(!level.hasChunkAt(pos))continue;
            double d=pos.distSqr(new BlockPos(w.hasHome?w.homeX:w.sourceX,
                w.hasHome?w.homeY:w.sourceY,w.hasHome?w.homeZ:w.sourceZ));
            if(d<best){best=d;preferred=pos;}
        }
        return preferred;
    }
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w) {
        if(w==null||!"farmer".equals(w.job)||KingdomNpcFarmOwner.workOwner(level,w)==null
            ||!w.dimension.equals(level.dimension().location().toString())
            ||!KingdomNpcDuty.autonomousOrder(w.order)
            ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
            ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
            ||!w.hasSource||!w.hasDestination)return false;
        UUID enterprise=farm(level,w);if(enterprise==null)return false;
        // Decisions are cheap, bounded and only applied to an actually NPC-owned farm.
        if(level.getServer().getTickCount()%1200==0
                && KingdomNpcFarmOwner.ownedFarm(level.getServer(),w)!=null)
            KingdomFarmAutonomy.review(level,w,enterprise);
        var planned=KingdomFarmFieldData.get(level.getServer()).of(enterprise).stream()
            .filter(f->f.enabled()&&!f.seedId().isBlank()
                &&f.dimension().equals(w.dimension)).toList();
        if(planned.isEmpty())return false;
        if(hasProduce(w,planned)&&carried(w)>128)
            return NpcFieldWork.deposit(level,npc,w);
        var land=KingdomTerritories.get(level.getServer());
        // At most 8 × 256 loaded crop cells per invocation (once per 40 ticks/NPC).
        BlockPos harvestTarget=null,plantTarget=null,nutritionTarget=null;
        BlockState harvestState=null,plantState=null,nutritionState=null;
        KingdomFarmFieldData.Field harvestField=null,plantField=null;
        double harvestDistance=Double.MAX_VALUE,plantDistance=Double.MAX_VALUE,nutritionDistance=Double.MAX_VALUE;
        for(var field:planned) {
            ResourceLocation cropKey=ResourceLocation.tryParse(KingdomFarmFieldData.cropId(field.seedId()));
            if(cropKey==null||!BuiltInRegistries.BLOCK.containsKey(cropKey))continue;
            BlockState desired=BuiltInRegistries.BLOCK.get(cropKey).defaultBlockState();
            if(!KingdomTfcFieldRules.simpleCrop(KingdomFarmFieldData.cropId(field.seedId()),
                    desired.getBlock().getClass().getName()))continue;
            boolean restingForRotation=KingdomFarmAutonomy.leaveForRotation(level,w,field);
            for(int x=field.minX();x<=field.maxX();x++)for(int z=field.minZ();z<=field.maxZ();z++){
                BlockPos soil=new BlockPos(x,field.y(),z),crop=soil.above();
                if(!level.hasChunkAt(crop)||!land.canUse(level,soil,KingdomNpcFarmOwner.workOwner(level,w)))continue;
                BlockState found=level.getBlockState(crop);
                double distance= crop.distSqr(npc.blockPosition());
                if(cropMatches(found,field)&&mature(found)&&distance<harvestDistance) {
                    harvestTarget=crop;harvestState=found;harvestField=field;harvestDistance=distance;
                }else if(cropMatches(found,field) && !mature(found)
                    && distance<nutritionDistance
                    && KingdomFarmClimate.read(level,crop,found).primaryNutrient()>=0f
                    && KingdomFarmClimate.read(level,crop,found).primaryNutrient()<0.35f){
                    nutritionTarget=crop;nutritionState=found;nutritionDistance=distance;
                }else if(!restingForRotation && found.isAir() && desired.canSurvive(level,crop)
                    && distance<plantDistance
                    && KingdomFarmClimate.read(level,crop,desired).suitable()){
                    plantTarget=crop;plantState=desired;plantField=field;plantDistance=distance;
                }
            }
        }
        if(harvestTarget!=null){
            if(!storeNear(level,npc,harvestTarget)){
                npc.getNavigation().moveTo(harvestTarget.getX()+.5,
                    harvestTarget.getY(),harvestTarget.getZ()+.5,.75);return false;
            }
            if(!level.hasChunkAt(harvestTarget)||!land.canUse(level,harvestTarget,KingdomNpcFarmOwner.workOwner(level,w))
                ||!cropMatches(level.getBlockState(harvestTarget),harvestField)
                ||!mature(level.getBlockState(harvestTarget)))return false;
            return harvest(level,npc,w,harvestTarget,level.getBlockState(harvestTarget),harvestField);
        }
        if(plantTarget!=null){
            // Check REAL climate before spending any seed or walking to the plot.
            if(!KingdomFarmClimate.read(level,plantTarget,plantState).suitable())return false;
            if(!hasSeed(w,plantField.seedId()))
                return refill(level,npc,w,plantField.seedId());
            if(!storeNear(level,npc,plantTarget)){
                npc.getNavigation().moveTo(plantTarget.getX()+.5,plantTarget.getY(),
                    plantTarget.getZ()+.5,.75);return false;
            }
            if(!level.hasChunkAt(plantTarget)||!land.canUse(level,plantTarget,KingdomNpcFarmOwner.workOwner(level,w)))
                return false;
            return plant(level,w,plantTarget,plantField,plantState);
        }
        if(nutritionTarget!=null && level.hasChunkAt(nutritionTarget)
                && land.canUse(level,nutritionTarget,KingdomNpcFarmOwner.workOwner(level,w)))
            return fertilize(level,npc,w,nutritionTarget,nutritionState);
        return hasProduce(w,planned)&&NpcFieldWork.deposit(level,npc,w);
    }
}
