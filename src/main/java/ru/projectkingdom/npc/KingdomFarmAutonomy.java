package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The NPC-owned FARM chooses seeds from REAL local stock. Autonomous planner
 * is separated from planting; it never spawns supplies, ignores unowned plots,
 * and cannot override player-managed farms.
 *
 * Full fields cannot change crops mid-growth. Next plan is chosen only when
 * the entire TFC field is empty, with a stability bonus for existing crop.
 */
public final class KingdomFarmAutonomy {
    private KingdomFarmAutonomy(){}

    public record Option(String seedId,int availableSeeds,int finishedFood,int ownerDiet,
                         boolean suitable,float soilNutrient,boolean currentCrop) {}
    public static String choose(List<Option> options){
        String best="";int bestScore=Integer.MIN_VALUE;
        for(Option o:options){
            if(o.availableSeeds()<2||!o.suitable()||o.seedId().isBlank())continue;
            // A hungry community values scarce food and nutrition more than market surplus.
            int scarcity=Math.max(0,64-Math.min(64,o.finishedFood()));
            int deficiency=Math.max(0,80-Math.min(100,Math.max(0,o.ownerDiet())));
            int supply=Math.min(24,o.availableSeeds());
            int fertility=Math.round(Math.min(1,Math.max(0,o.soilNutrient()))*20);
            int score=scarcity*2+deficiency*3+supply+fertility
                    +(o.currentCrop()?18:0);
            if(score>bestScore||(score==bestScore&&o.seedId().compareTo(best)<0)){
                bestScore=score;best=o.seedId();
            }
        }
        return best;
    }

    public static int nutritionGroup(String seedId) {
        if(seedId==null)return -1;
        String id=seedId.startsWith("tfc:seeds/")?seedId.substring(10):"";
        return switch(id) {
            case "barley","oat","rye","wheat" -> KingdomNpcNutrition.GRAIN;
            case "beet","cabbage","carrot","garlic","potato","onion","soybean",
                 "squash" -> KingdomNpcNutrition.VEGETABLES;
            default -> -1; // Fruit trees, livestock and dairy need other professions.
        };
    }

    private static int count(Container chest,String item){
        int total=0;
        for(int i=0;i<chest.getContainerSize();i++){
            ItemStack in=chest.getItem(i);
            if(!in.isEmpty() && item.equals(BuiltInRegistries.ITEM.getKey(in.getItem()).toString()))
                total+=in.getCount();
        }
        return total;
    }
    private static String harvestItemId(String seedId){
        if(!seedId.startsWith("tfc:seeds/"))return "";
        return "tfc:food/"+seedId.substring(10);
    }
    private static boolean emptyField(ServerLevel level,KingdomFarmFieldData.Field field){
        for(int x=field.minX();x<=field.maxX();x++)for(int z=field.minZ();z<=field.maxZ();z++){
            BlockPos soil=new BlockPos(x,field.y(),z);
            if(!level.hasChunkAt(soil))return false;
            var above=level.getBlockState(soil.above());
            // Never change the crop preference over a player's existing plants.
            if(!above.isAir())return false;
        }
        return true;
    }
    private static float nutrient(ServerLevel level,BlockPos pos,BlockState plant){
        var reading=KingdomFarmClimate.read(level,pos,plant);
        return reading.available()&&reading.primaryNutrient()>=0?reading.primaryNutrient():0f;
    }

    /**
     * Runs at most once per 1200 server ticks from an NPC who demonstrably owns
     * the recorded, active farm. RAW supplies the seeds, FINISHED supplies
     * production stock. Both chests must have their explicit ownership role.
     */
    public static boolean review(ServerLevel level,KingdomNpcData.Worker owner,UUID farm) {
        if(farm==null||owner==null || !farm.equals(KingdomNpcFarmOwner.ownedFarm(level.getServer(),owner))
                ||!owner.hasSource||!owner.hasDestination)return false;
        BlockPos rawPos=new BlockPos(owner.sourceX,owner.sourceY,owner.sourceZ);
        BlockPos finishedPos=new BlockPos(owner.destX,owner.destY,owner.destZ);
        var land=KingdomTerritories.get(level.getServer());
        if(!level.hasChunkAt(rawPos)||!level.hasChunkAt(finishedPos)
                ||!land.usable(level,rawPos,owner.entityId,"raw")
                ||!land.usable(level,finishedPos,owner.entityId,"finished")
                ||!(level.getBlockEntity(rawPos) instanceof Container raw)
                ||!(level.getBlockEntity(finishedPos) instanceof Container finished)
                ||raw==finished)return false;
        boolean changed=false;
        var fields=KingdomFarmFieldData.get(level.getServer());
        // Crop candidates MUST already exist physically as seeds in the owner's warehouse.
        Map<String,Integer> seedStock=new TreeMap<>();
        for(int i=0;i<raw.getContainerSize();i++){
            ItemStack stack=raw.getItem(i);
            if(stack.isEmpty())continue;
            String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if(KingdomFarmFieldData.validSeed(id) && nutritionGroup(id)>=0)
                seedStock.merge(id,stack.getCount(),Integer::sum);
        }
        if(seedStock.isEmpty())return false;
        for(var field:fields.of(farm)){
            if(!field.enabled() || !field.dimension().equals(owner.dimension)
                    || !emptyField(level,field))continue;
            // Choose among known supported crops; preserve field's real climate limits.
            List<Option> candidates=new ArrayList<>();
            for(var entry:seedStock.entrySet()){
                ResourceLocation key=ResourceLocation.tryParse(KingdomFarmFieldData.cropId(entry.getKey()));
                if(key==null||!BuiltInRegistries.BLOCK.containsKey(key))continue;
                BlockState plant=BuiltInRegistries.BLOCK.get(key).defaultBlockState();
                BlockPos soil=field.centerCrop().below();
                if(!land.canUse(level,soil,owner.entityId)
                    ||!plant.canSurvive(level,soil.above()))continue;
                var climate=KingdomFarmClimate.read(level,soil.above(),plant);
                if(!climate.available())continue;
                int group=nutritionGroup(entry.getKey());
                candidates.add(new Option(entry.getKey(),entry.getValue(),
                    count(finished,harvestItemId(entry.getKey())),
                    KingdomNpcNutrition.nutrient(owner,group),
                    climate.suitable(),nutrient(level,soil.above(),plant),
                    entry.getKey().equals(field.seedId())));
            }
            String chosen=choose(candidates);
            if(!chosen.isBlank() && !chosen.equals(field.seedId())
                    && fields.chooseForOwnerNpc(level,owner.entityId,farm,field.id(),chosen))changed=true;
        }
        return changed;
    }
}
