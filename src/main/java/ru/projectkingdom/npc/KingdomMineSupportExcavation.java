package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One safe, real pickaxe action per work tick to clear a TFC support frame.
 * A dangerous TFC block must NEVER be excavated just to prepare supports.
 */
public final class KingdomMineSupportExcavation {
    private KingdomMineSupportExcavation() {}

    public enum Result { CLEAR, EXCAVATED, WALKING, UNSAFE }

    public static Result tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                              KingdomMineSupportPlan.Frame frame) {
        if(frame==null || !KingdomMineSupportPlan.valid(frame))return Result.UNSAFE;
        BlockPos[] positions={
            frame.leftPost().above(2),frame.rightPost().above(2),
            frame.crossbeam(),frame.leftPost().above(),frame.rightPost().above(),
            frame.leftPost(),frame.rightPost()
        };
        for(BlockPos pos:positions) {
            if(!level.hasChunkAt(pos)||!level.hasChunkAt(pos.above()))
                return Result.UNSAFE;
            BlockState block=level.getBlockState(pos);
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(block.getBlock()).toString();
            if(block.isAir()
                ||id.startsWith("tfc:wood/vertical_support/")
                ||KingdomMineSupportSafety.isTfcHorizontalSupport(block))
                continue;
            if(!KingdomMineTools.equip(level,npc,worker,
                    KingdomMineTools.required(block)))return Result.UNSAFE;
            if(!KingdomMineWork.safeToBreak(level,pos,worker)
                ||KingdomMineSupportSafety.inspect(level,pos)
                    !=KingdomMineSupportSafety.Result.SAFE)return Result.UNSAFE;
            BlockPos approach=frame.walkway();
            if(npc.distanceToSqr(approach.getX()+0.5,approach.getY(),
                    approach.getZ()+0.5)>6.25) {
                npc.getNavigation().moveTo(approach.getX()+0.5,approach.getY(),
                    approach.getZ()+0.5,0.75);
                return Result.WALKING;
            }
            var drops=Block.getDrops(block,level,pos,level.getBlockEntity(pos),
                npc,worker.fieldTool);
            if(!NpcFieldWork.canFit(worker,drops))return Result.UNSAFE;
            if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),3))
                return Result.UNSAFE;
            for(ItemStack stack:drops)NpcFieldWork.put(worker,stack);
            worker.fieldTool.hurt(1,level.random,null);
            if(worker.fieldTool.isEmpty()||worker.fieldTool.getDamageValue()
                    >=worker.fieldTool.getMaxDamage())worker.fieldTool=ItemStack.EMPTY;
            worker.fieldHarvested+=drops.stream().mapToInt(ItemStack::getCount).sum();
            worker.skillXp++;
            worker.energy=Math.max(0,worker.energy-1);
            KingdomNpcData.get(level.getServer()).setDirty();
            return Result.EXCAVATED;
        }
        return Result.CLEAR;
    }
}
