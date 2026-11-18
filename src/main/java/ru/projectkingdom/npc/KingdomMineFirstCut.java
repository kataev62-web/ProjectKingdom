package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ONE manual acceptance action, not a production mine.
 * The nearest adjacent real dirt/soil block requires a shovel; natural stone
 * and ore require a pickaxe. There is NO mandatory soil-before-stone order.
 * No enterprise, payroll, food, shaft, supports, logistics or schedule gating.
 * Only place a test JAR in a disposable test-world copy.
 */
public final class KingdomMineFirstCut {
    private KingdomMineFirstCut() {}

    private record Target(BlockPos pos, KingdomMineTools.Tool tool, double distance) {}

    private static void status(ServerLevel level,KingdomNpcData.Worker worker,String message){
        if(!message.equals(worker.mineStatus)){
            worker.mineStatus=message;
            KingdomNpcData.get(level.getServer()).setDirty();
        }
    }

    /** Search at feet and directly adjacent exposed ground; never break under NPC. */
    private static Target nearest(ServerLevel level,Villager npc) {
        BlockPos center=npc.blockPosition();
        Target best=null;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
            for(int dz=-1;dz<=1;dz++) {
                if(dx==0&&dz==0)continue;
                BlockPos pos=center.offset(dx,dy,dz);
                if(!level.hasChunkAt(pos)||level.getBlockEntity(pos)!=null)continue;
                BlockState state=level.getBlockState(pos);
                if(state.isAir()||!state.getFluidState().isEmpty()
                    ||state.getDestroySpeed(level,pos)<0)continue;
                KingdomMineTools.Tool needed=KingdomMineTools.required(state);
                if(needed==KingdomMineTools.Tool.BLOCKED)continue;
                double d=npc.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
                if(d>9)continue;
                if(best==null||d<best.distance())best=new Target(pos,needed,d);
            }
        return best;
    }

    /** The actual menu action runs its first work tick immediately, synchronously. */
    public static String tryNow(ServerPlayer player,KingdomNpcData.Worker worker) {
        if(player==null||worker==null||!"miner".equals(worker.job))
            return "Нужна профессия «Шахтёр»";
        var entity=player.serverLevel().getEntity(worker.entityId);
        if(!(entity instanceof Villager npc)||!npc.isAlive())
            return "NPC отсутствует в загруженном чанке";
        tick(player.serverLevel(),npc,worker);
        return worker.mineStatus;
    }

    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker) {
        if(worker==null||npc==null||!"miner".equals(worker.job)
            ||!"work".equals(worker.order))return false;
        Target target=nearest(level,npc);
        if(target==null){
            status(level,worker,"Тест: нет грунта или камня рядом с NPC. "
                +"Поставь блок сбоку на уровне ног.");
            return false;
        }
        KingdomMineTools.Tool need=target.tool();
        if(!KingdomMineTools.equip(level,npc,worker,need)){
            status(level,worker,need==KingdomMineTools.Tool.SHOVEL
                ?"Нужна настоящая лопата в инвентаре NPC"
                :"Нужна настоящая кирка в инвентаре NPC");
            return false;
        }
        BlockState state=level.getBlockState(target.pos());
        if(state.isAir()||KingdomMineTools.required(state)!=need
            ||!worker.fieldTool.isCorrectToolForDrops(state)){
            status(level,worker,"Инструмент не подходит к выбранному блоку: "
                +BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            return false;
        }
        var drops=Block.getDrops(state,level,target.pos(),
            level.getBlockEntity(target.pos()),npc,worker.fieldTool);
        if(!level.setBlock(target.pos(),Blocks.AIR.defaultBlockState(),3)){
            status(level,worker,"Не удалось физически разрушить блок "
                +target.pos().toShortString());
            return false;
        }
        npc.swing(InteractionHand.MAIN_HAND);
        for(ItemStack stack:drops)if(!stack.isEmpty())
            Block.popResource(level,target.pos(),stack.copy());
        worker.fieldTool.hurt(1,level.random,null);
        if(worker.fieldTool.isEmpty()||worker.fieldTool.getDamageValue()
            >=worker.fieldTool.getMaxDamage())worker.fieldTool=ItemStack.EMPTY;
        worker.order="stay"; // No second or hidden quarry step: test only one block.
        String tool=need==KingdomMineTools.Tool.SHOVEL?"лопатой":"киркой";
        status(level,worker,"РЕАЛЬНО ВЫКОПАЛ "
            +BuiltInRegistries.BLOCK.getKey(state.getBlock())+" "+tool);
        worker.skillXp++;
        worker.fieldHarvested+=drops.stream().mapToInt(ItemStack::getCount).sum();
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
}
