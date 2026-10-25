package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Assign paid, ready orders only to real, loaded, idle courier NPCs. */
public final class KingdomCourierDispatch {
    private KingdomCourierDispatch() {}
    public static int tick(MinecraftServer server) {
        var orders = KingdomSupplyOrders.get(server);
        var npcData = KingdomNpcData.get(server);
        Set<UUID> busy = new HashSet<>();
        for(var o:orders.all()) {
            if(o.courier != null && o.state != KingdomSupplyOrders.State.CANCELLED
                    && o.state != KingdomSupplyOrders.State.DELIVERED) busy.add(o.courier);
        }
        int assigned=0;
        for(var o:orders.all()) {
            if((o.mode != KingdomSupplyOrders.Mode.COURIER
                    && o.mode != KingdomSupplyOrders.Mode.INTERNAL
                    && o.mode != KingdomSupplyOrders.Mode.MUNICIPAL)
                    || o.state != KingdomSupplyOrders.State.OPEN
                    || o.courier != null || !o.deliveryPaid
                    || (o.goodsEscrow<=0 && !o.goodsPaid && !o.buyer.equals(o.seller))) continue;
            // Choose the closest idle courier in the same dimension. Never load
            // distant chunks just to search for an NPC.
            ServerLevel level=null;
            for(ServerLevel candidate:server.getAllLevels())
                if(candidate.dimension().location().toString().equals(o.dimension)) {level=candidate;break;}
            if(level==null)continue;
            if(o.mode==KingdomSupplyOrders.Mode.INTERNAL
                    && !KingdomEnterpriseInternalSupply.authorized(level,o))continue;
            if(o.mode==KingdomSupplyOrders.Mode.MUNICIPAL
                    && !KingdomMunicipalProcurement.authorized(level,o))continue;
            KingdomNpcData.Worker closest=null;
            double closestDistance=Double.MAX_VALUE;
            for(var w:npcData.all()) {
                if(!"courier".equals(w.job) || w.contractPaused || w.ageYears < 18
                        || !w.cargo.isEmpty() || w.cartCargoCount>0 || w.cartCargoOrder!=null
                        || busy.contains(w.entityId)
                        || !o.dimension.equals(w.dimension)
                        || !KingdomNpcDuty.mayExecute(w.job, w.order, w.autonomousIntent,
                            KingdomNpcWorkday.employed(server, w))) continue;
                var entity=level.getEntity(w.entityId);
                if(!(entity instanceof Villager villager) || !villager.isAlive())continue;
                double distance=villager.distanceToSqr(o.source.getX()+0.5,
                        o.source.getY()+0.5,o.source.getZ()+0.5);
                if(distance<closestDistance) {closestDistance=distance;closest=w;}
            }
            if(closest!=null && orders.assign(o.id,closest.entityId)) {
                closest.order="courier";
                npcData.setDirty();
                busy.add(closest.entityId);
                assigned++;
            }
        }
        // Release couriers whose deliveries have finished, without touching their cargo.
        for(var w:npcData.all()) {
            if(!"courier".equals(w.order) || !w.cargo.isEmpty() || w.cartCargoCount>0 || w.cartCargoOrder!=null
                        || busy.contains(w.entityId))continue;
            w.order="wander"; // Return to the shared daily schedule after completing an order.
            npcData.setDirty();
        }
        return assigned;
    }
}
