package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;

/** Read-only, fail-closed view of registered + worker physical storage.
 * A missing, unloaded, or disputed container is UNKNOWN, never zero stock.
 * The same container is never double-counted. This does not load chunks.
 */
public final class KingdomEnterpriseStock {
    private KingdomEnterpriseStock(){}
    public record Snapshot(int physical,int incoming,boolean complete){
        public EnterpriseStockPolicy.StockState state(int inProduction){
            return new EnterpriseStockPolicy.StockState(physical,incoming,inProduction,complete);
        }
    }
    public static Snapshot inspect(ServerLevel level,UUID enterprise,String item,EnterpriseBindingRole role,BlockPos workerChest){
        Objects.requireNonNull(level);Objects.requireNonNull(enterprise);
        if(role!=EnterpriseBindingRole.RAW_STORAGE&&role!=EnterpriseBindingRole.FINISHED_STORAGE)
            throw new IllegalArgumentException("Stock roles only");
        if(item==null||item.isBlank())throw new IllegalArgumentException("item");
        var firms=KingdomEnterpriseData.get(level.getServer());
        var firm=firms.find(enterprise);
        if(firm==null)return new Snapshot(0,0,false);
        String dim=level.dimension().location().toString();
        Set<BlockPos> positions=new LinkedHashSet<>();
        var binding=firms.storageBinding(enterprise,role);
        if(binding!=null){
            if(!dim.equals(binding.dimension()))return new Snapshot(0,0,false);
            positions.add(binding.pos());
        }
        if(workerChest!=null)positions.add(workerChest.immutable());
        if(role==EnterpriseBindingRole.RAW_STORAGE) {
            var plans=KingdomNpcWorkshopData.get(level.getServer());
            for(var worker:KingdomNpcData.get(level.getServer()).all()) {
                if(!worker.hasSource || !dim.equals(worker.dimension)
                        || !firm.owner().equals(worker.employer))continue;
                var plan=plans.plan(worker.entityId);
                if(plan!=null && enterprise.equals(plan.enterprise()))
                    positions.add(new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ));
            }
        }
        if(positions.isEmpty())return new Snapshot(0,0,false);
        long total=0;
        String usage=role==EnterpriseBindingRole.RAW_STORAGE?"raw":"finished";
        for(BlockPos pos:positions){
            // Registered enterprise storage is authorized by its binding + land claim.
            // Legacy worker chests require their explicit raw/finished territory marker.
            boolean registered=binding!=null&&binding.pos().equals(pos);
            boolean allowed=registered
                    ? KingdomTerritories.get(level.getServer()).canUse(level,pos,firm.owner())
                    : KingdomTerritories.get(level.getServer()).usable(level,pos,firm.owner(),usage);
            if(!level.hasChunkAt(pos)||firms.storageClaimedByOther(enterprise,dim,pos)||!allowed)
                return new Snapshot(0,0,false);
            BlockEntity be=level.getBlockEntity(pos);
            if(!(be instanceof Container c))return new Snapshot(0,0,false);
            for(int i=0;i<c.getContainerSize();i++){
                ItemStack stack=c.getItem(i);
                if(!stack.isEmpty()&&item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                    total+=stack.getCount();
            }
        }
        long arriving=0;
        for(var o:KingdomSupplyOrders.get(level.getServer()).all()){
            if(!dim.equals(o.dimension)||!item.equals(o.itemId)
                    ||o.state==KingdomSupplyOrders.State.CANCELLED
                    ||o.state==KingdomSupplyOrders.State.DELIVERED)continue;
            if(o.pendingKind!=null&&(positions.contains(o.source)||positions.contains(o.destination)))
                return new Snapshot(0,0,false);
            if(positions.contains(o.destination)&&(enterprise.equals(o.buyer)||firm.owner().equals(o.buyer))) {
                // Internal goods already exist in this enterprise's source warehouse.
                // Count only cargo physically picked up and currently in transit,
                // otherwise an open transfer doubles the company's total stock.
                arriving+=o.mode==KingdomSupplyOrders.Mode.INTERNAL
                        ?Math.max(0,o.pickedUp-o.delivered)
                        :Math.max(0,o.count-o.delivered);
            }
        }
        return new Snapshot((int)Math.min(total,Integer.MAX_VALUE),
                (int)Math.min(arriving,Integer.MAX_VALUE),true);
    }
}
