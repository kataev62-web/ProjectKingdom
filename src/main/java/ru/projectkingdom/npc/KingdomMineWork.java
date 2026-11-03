package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * First conservative MineColonies-style shaft executor for Kingdom.
 * A real MINE enterprise anchors a bounded 1x2 horizontal gallery.
 * Only naturally mineable rock/ore is removed, one block per work tick.
 * No simulated goods, remote block breaking, chunk loads or forced tunnelling.
 * Vertical shafts and structural supports remain a separate safety feature.\n * The second level uses a descending walkable stair, not a vertical drop.\n * Torches are installed from physical stock at predeclared gallery checkpoints.
 */
public final class KingdomMineWork {
    private KingdomMineWork() {}
    private static final double REACH_SQR = 6.25;

    static boolean naturalRock(String id) {
        return "minecraft:stone".equals(id) || "minecraft:deepslate".equals(id)
            || "minecraft:tuff".equals(id) || "minecraft:andesite".equals(id)
            || "minecraft:diorite".equals(id) || "minecraft:granite".equals(id)
            || id.startsWith("minecraft:") && id.endsWith("_ore")
            || id.startsWith("tfc:rock/raw/")
            || KingdomTfcFieldRules.ore(id);
    }

    private static boolean safeToBreak(ServerLevel level, BlockPos pos,
                                       KingdomNpcData.Worker worker) {
        if (!level.hasChunkAt(pos) || pos.getY() <= level.getMinBuildHeight() + 1
            || pos.getY() >= level.getMaxBuildHeight() - 3
            || !worker.employer.equals(KingdomTerritories.get(level.getServer()).owner(level,pos)))
            return false;
        BlockState state=level.getBlockState(pos);
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!naturalRock(id) || !state.is(BlockTags.MINEABLE_WITH_PICKAXE)
            || !state.getFluidState().isEmpty() || level.getBlockEntity(pos)!=null
            || state.getDestroySpeed(level,pos)<0) return false;
        if (worker.fieldTool.isEmpty()
            || !(worker.fieldTool.is(ItemTags.PICKAXES)
                 || worker.fieldTool.getItem() instanceof PickaxeItem)
            || !worker.fieldTool.isCorrectToolForDrops(state)) return false;
        // Never deliberately open a liquid pocket or let loose gravel/sand fall.
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbor=pos.relative(direction);
            if (!level.hasChunkAt(neighbor)
                || !level.getFluidState(neighbor).isEmpty()) return false;
        }
        BlockPos above=pos.above();
        return !(level.getBlockState(above).getBlock() instanceof FallingBlock);
    }

    /**
     * Finds the first unexcavated gallery cell. The previous cell must be open
     * and supported by a floor; the NPC must physically approach that cell.
     */
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker) {
        if (!"miner".equals(worker.job) || worker.employer==null
            || worker.contractPaused || !KingdomNpcWorkday.employed(level.getServer(),worker)
            || !worker.hasSource || !worker.hasDestination
            || !worker.dimension.equals(level.dimension().location().toString())
            || !"work".equals(worker.order) && !"wander".equals(worker.order))
            return false;

        UUID id;
        try { id=UUID.fromString(worker.workplaceId); }
        catch (IllegalArgumentException error) { return false; }
        var businesses=KingdomEnterpriseData.get(level.getServer());
        var mine=businesses.find(id);
        var binding=businesses.core(id);
        if (mine==null || binding==null || !binding.active()
            || businesses.kind(id)!=EnterpriseKind.MINE
            || !mine.owner().equals(worker.employer)
            || !binding.dimension().equals(worker.dimension)) return false;
        BlockPos core=BlockPos.of(binding.packedPos());
        if (!level.hasChunkAt(core)) return false;
        // This first gallery extends north of the mine core and has a hard limit.
        // The entrance itself must be prepared by the player (two air blocks).
        BlockPos entrance=core.north();
        if (!level.hasChunkAt(entrance) || !level.getBlockState(entrance).isAir()
            || !level.getBlockState(entrance.above()).isAir()) return false;
        int carried=worker.fieldInventory.stream().mapToInt(ItemStack::getCount).sum();
        // Depositing may require pathing: do not immediately override the route.
        if (carried >= 64) return NpcFieldWork.deposit(level,npc,worker);
        if (!NpcFieldWork.borrowTool(level,npc,worker)) return false;

        for (var cell : KingdomMineLevelPlan.cells(core)) {
            BlockPos floorCell=cell.target();
            BlockPos previous=cell.approach();
            if (!level.hasChunkAt(floorCell) || !level.hasChunkAt(previous)
                || !level.hasChunkAt(floorCell.below())
                || !level.hasChunkAt(previous.above())) return false;
            // Only horizontal moves or exactly one downward step are allowed.
            int deltaY=previous.getY()-floorCell.getY();
            if(deltaY<0 || deltaY>1 || previous.distManhattan(floorCell)!=1+deltaY)
                return false;
            // Do not enter a void, cave opening or flooded unsupported cell.
            if (level.getBlockState(floorCell.below()).isAir()
                || !level.getFluidState(floorCell.below()).isEmpty()) return false;
            if (!KingdomMineMaintenance.passable(level.getBlockState(previous))
                || !level.getBlockState(previous.above()).isAir()
                || level.getBlockState(previous.below()).isAir()
                || !level.getFluidState(previous).isEmpty()) return false;
            for (int height=0;height<2;height++) {
                BlockPos target=floorCell.above(height);
                if (KingdomMineMaintenance.passable(level.getBlockState(target))) {
                    if (!level.getFluidState(target).isEmpty()) return false;
                    continue;
                }
                if (!safeToBreak(level,target,worker)) return false;
                if (npc.distanceToSqr(previous.getX()+0.5,
                    previous.getY(),previous.getZ()+0.5)>REACH_SQR) {
                    npc.getNavigation().moveTo(previous.getX()+0.5,
                        previous.getY(),previous.getZ()+0.5,0.75);
                    return false;
                }
                BlockState state=level.getBlockState(target);
                var drops=Block.getDrops(state,level,target,level.getBlockEntity(target),
                    npc,worker.fieldTool);
                if (!NpcFieldWork.canFit(worker,drops)) return false;
                if (!level.setBlock(target,Blocks.AIR.defaultBlockState(),3)) return false;
                for (ItemStack stack:drops) NpcFieldWork.put(worker,stack);
                worker.fieldTool.hurt(1,level.random,null);
                if (worker.fieldTool.isEmpty() || worker.fieldTool.getDamageValue()
                    >=worker.fieldTool.getMaxDamage()) worker.fieldTool=ItemStack.EMPTY;
                worker.fieldHarvested+=drops.stream().mapToInt(ItemStack::getCount).sum();
                worker.skillXp++;
                worker.energy=Math.max(0,worker.energy-1);
                KingdomNpcData.get(level.getServer()).setDirty();
                return true;
            }
            // Light a fully excavated section before starting the next cell.
            // Stock and navigation are real: no torches = no further advance.
            BlockPos lightAnchor=floorCell.getY()==core.getY()
                ?core:floorCell.getY()==core.getY()-KingdomMineLevelPlan.LEVEL_DROP
                ?core.north(KingdomMineGalleryPlan.MAIN_LENGTH+
                    KingdomMineLevelPlan.LEVEL_DROP).below(KingdomMineLevelPlan.LEVEL_DROP)
                :null;
            if(KingdomMineMaintenance.lightPoint(lightAnchor,floorCell)) {
                var lighting=KingdomMineMaintenance.light(level,npc,worker,lightAnchor,floorCell);
                if(lighting==KingdomMineMaintenance.Result.PLACED)return true;
                if(lighting!=KingdomMineMaintenance.Result.READY)return false;
            }
        }
        return false;
    }
}
