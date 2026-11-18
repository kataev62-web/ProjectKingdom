package ru.projectkingdom.npc;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Isolated manual acceptance of the FIRST real miner action only.
 *
 * A miner told to "Work" breaks at most one nearby dirt block with a real
 * shovel, then one nearby rock block with a real pickaxe, and stops. Tools are
 * physical items already in the NPC inventory/linked raw depot; the drops
 * become actual ItemEntity drops at the broken block. No shaft, salary, food,
 * supplies or navigation checks are prerequisites to this two-block probe.
 *
 * This is not the production mine or a claim of full TFC gameplay acceptance.
 * Use only on a test-world copy. Main is not modified.
 */
public final class KingdomMineFirstCut {
    private KingdomMineFirstCut() {}
    private static final Map<UUID, EnumSet<KingdomMineTools.Tool>> DONE =
        new ConcurrentHashMap<>();

    public static void start(UUID npcId) {
        if (npcId != null) DONE.remove(npcId);
    }

    private static void status(ServerLevel level, KingdomNpcData.Worker worker,
                               String message) {
        if (!message.equals(worker.mineStatus)) {
            worker.mineStatus=message;
            KingdomNpcData.get(level.getServer()).setDirty();
        }
    }

    private static BlockPos nearest(ServerLevel level, Villager npc,
                                    KingdomMineTools.Tool required) {
        BlockPos center=npc.blockPosition();
        BlockPos best=null;
        double distance=Double.MAX_VALUE;
        // Only adjacent, explicitly staged solid blocks at the villager's feet
        // or head height. Never mine the ground UNDER the villager.
        for(int dy=0;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            if(Math.abs(dx)+Math.abs(dz)!=1)continue;
            BlockPos pos=center.offset(dx,dy,dz);
            if(!level.hasChunkAt(pos) || level.getBlockEntity(pos)!=null)continue;
            BlockState state=level.getBlockState(pos);
            if(state.isAir() || !state.getFluidState().isEmpty()
                    ||state.getDestroySpeed(level,pos)<0
                    ||KingdomMineTools.required(state)!=required)continue;
            double d=npc.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
            if(d<=6.25 && d<distance){best=pos;distance=d;}
        }
        return best;
    }

    public static boolean tick(ServerLevel level, Villager npc,
                               KingdomNpcData.Worker worker) {
        if(worker==null ||npc==null ||!"miner".equals(worker.job)
                ||!"work".equals(worker.order))return false;
        var done=DONE.computeIfAbsent(worker.entityId,
            key->EnumSet.noneOf(KingdomMineTools.Tool.class));
        var need=done.contains(KingdomMineTools.Tool.SHOVEL)
            ?KingdomMineTools.Tool.PICKAXE:KingdomMineTools.Tool.SHOVEL;
        if(done.contains(KingdomMineTools.Tool.PICKAXE)) {
            worker.order="stay";
            status(level,worker,"Тест завершён: грунт — лопата, камень — кирка");
            return false;
        }
        BlockPos target=nearest(level,npc,need);
        if(target==null){
            status(level,worker,need==KingdomMineTools.Tool.SHOVEL
                ?"Тест 1: поставь блок грунта вплотную к шахтёру"
                :"Тест 2: поставь блок твёрдой породы вплотную к шахтёру");
            return false;
        }
        if(!KingdomMineTools.equip(level,npc,worker,need)){
            status(level,worker,need==KingdomMineTools.Tool.SHOVEL
                ?"Тест 1: дай шахтёру настоящую лопату"
                :"Тест 2: дай шахтёру настоящую кирку");
            return false;
        }
        BlockState state=level.getBlockState(target);
        if(KingdomMineTools.required(state)!=need
                ||!worker.fieldTool.isCorrectToolForDrops(state)){
            status(level,worker,"Инструмент не может добыть этот блок");
            return false;
        }
        var drops=Block.getDrops(state,level,target,level.getBlockEntity(target),
                npc,worker.fieldTool);
        if(!level.setBlock(target,Blocks.AIR.defaultBlockState(),3)){
            status(level,worker,"Блок не удалось разрушить");
            return false;
        }
        npc.swing(InteractionHand.MAIN_HAND);
        for(ItemStack stack:drops) {
            if(!stack.isEmpty())Block.popResource(level,target,stack.copy());
        }
        worker.fieldTool.hurt(1,level.random,null);
        if(worker.fieldTool.isEmpty()||worker.fieldTool.getDamageValue()
                >=worker.fieldTool.getMaxDamage())worker.fieldTool=ItemStack.EMPTY;
        done.add(need);
        if(need==KingdomMineTools.Tool.PICKAXE) {
            worker.order="stay";
            status(level,worker,"ТЕСТ ПРОЙДЕН: лопатой выкопал грунт, киркой разбил камень");
        }else status(level,worker,"Грунт выкопан лопатой. Теперь поставь рядом камень");
        worker.skillXp++;
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
}
