package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kingdom city resident lifecycle. Loaded entities are authoritative for death;
 * absence alone does NOT prove death because villagers are saved in unloaded chunks.
 * The city claim registry, not a radius around the tavern, defines city limits.
 */
public final class KingdomResidentSafety {
    private static final Map<UUID,Long> CONFIRMATIONS=new HashMap<>();
    private KingdomResidentSafety() {}

    public static UUID assignedCity(KingdomCivicsData civics,KingdomNpcData.Worker worker){
        if(worker==null||civics==null)return null;
        var membership=civics.townOf(worker.entityId);
        if(membership!=null)return membership.id();
        try{
            UUID id=UUID.fromString(worker.settlement);
            if(civics.townById(id)!=null)return id;
        }catch(IllegalArgumentException ignored){}
        return null;
    }
    /** Only explicitly registered Kingdom villagers enter the citizen census and borders. */
    public static void assignLocalCitizen(MinecraftServer server,ServerLevel level,Villager npc,
                                          KingdomNpcData.Worker worker,KingdomNpcData registry){
        if(server==null||level==null||npc==null||worker==null)return;
        var civics=KingdomCivicsData.get(server);
        if(assignedCity(civics,worker)!=null)return;
        var town=civics.townAt(level,npc.blockPosition());
        if(town==null&&worker.hasHome)
            town=civics.townAt(level,new BlockPos(worker.homeX,worker.homeY,worker.homeZ));
        if(town==null)return;
        if(civics.joinTown(npc.getUUID(),town.id())||civics.isMember(town.id(),npc.getUUID())){
            worker.settlement=town.id().toString();
            registry.setDirty();
        }
    }

    public static boolean inside(KingdomCivicsData civics,ServerLevel level,BlockPos pos,UUID town){
        if(town==null||civics==null||level==null||pos==null)return false;
        var plot=civics.plot(level,pos);
        return plot!=null&&town.equals(plot.town());
    }
    public static void recordSafe(KingdomNpcData.Worker w,BlockPos pos,KingdomNpcData registry){
        if(w==null||pos==null||registry==null)return;
        if(w.hasLastSafe&&w.lastSafeX==pos.getX()
                &&w.lastSafeY==pos.getY()&&w.lastSafeZ==pos.getZ())return;
        w.hasLastSafe=true;w.lastSafeX=pos.getX();w.lastSafeY=pos.getY();w.lastSafeZ=pos.getZ();
        registry.setDirty();
    }

    /** Registered NPCs in unloaded chunks remain residents. Do not declare them dead. */
    public static boolean countsAsPresent(MinecraftServer server,KingdomNpcData.Worker w){
        if(server==null||w==null)return false;
        for(ServerLevel level:server.getAllLevels()){
            if(!level.dimension().location().toString().equals(w.dimension))continue;
            var entity=level.getEntity(w.entityId);
            if(entity instanceof Villager villager)return villager.isAlive()&&!villager.isRemoved();
            // For never-observed old saves, a loaded home with no actual entity is
            // UNVERIFIED, not a living citizen. Still keep the record for safe audit.
            BlockPos last=w.hasLastSafe?new BlockPos(w.lastSafeX,w.lastSafeY,w.lastSafeZ)
                    :w.hasHome?new BlockPos(w.homeX,w.homeY,w.homeZ):null;
            return last!=null&&!level.hasChunkAt(last);
        }
        return false;
    }

    private static boolean landing(ServerLevel level,Villager npc,BlockPos pos){
        if(!level.hasChunkAt(pos)||!level.hasChunkAt(pos.below())||!level.hasChunkAt(pos.above()))
            return false;
        if(!level.getWorldBorder().isWithinBounds(pos))return false;
        BlockState floor=level.getBlockState(pos.below());
        return floor.isFaceSturdy(level,pos.below(),Direction.UP)
                &&level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                &&level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty();
    }

    /** Return true if the NPC was redirected and its normal job path must pause. */
    public static boolean confine(MinecraftServer server,ServerLevel level,Villager npc,
                                   KingdomNpcData.Worker worker,KingdomNpcData registry){
        var civics=KingdomCivicsData.get(server);
        UUID city=assignedCity(civics,worker);
        if(city==null){
            assignLocalCitizen(server,level,npc,worker,registry);
            city=assignedCity(civics,worker);
        }
        if(city==null)return false; // True wilderness residents have no city limits.
        BlockPos here=npc.blockPosition();
        boolean inCity=inside(civics,level,here,city);
        if(inCity){
            // Vanilla wander goal can try to step into another city/wilderness.
            // Stop and steer home BEFORE the path leaves the registered chunk.
            var move=npc.getDeltaMovement();
            BlockPos predicted=BlockPos.containing(npc.getX()+move.x*3,
                    npc.getY(),npc.getZ()+move.z*3);
            boolean nearEdge=false;
            for(int dx=-2;dx<=2&&!nearEdge;dx++)for(int dz=-2;dz<=2;dz++)
                if(!inside(civics,level,here.offset(dx,0,dz),city)){nearEdge=true;break;}
            if(!nearEdge){
                if(landing(level,npc,here))recordSafe(worker,here,registry);
                return false;
            }
            boolean crossing=!inside(civics,level,predicted,city);
            var path=npc.getNavigation().getPath();
            if(path!=null&&!path.isDone() && path.getNextNodeIndex()<path.getNodeCount()){
                var next=path.getNextNodePos();
                if(!inside(civics,level,next,city))crossing=true;
            }
            if(crossing){
                npc.getNavigation().stop();
                npc.setDeltaMovement(0,npc.getDeltaMovement().y,0);
                // Avoid a freeze right on the edge: resume towards a known safe point.
                if(worker.hasLastSafe && (worker.lastSafeX!=here.getX()||worker.lastSafeZ!=here.getZ())){
                    var safe=new BlockPos(worker.lastSafeX,worker.lastSafeY,worker.lastSafeZ);
                    if(inside(civics,level,safe,city)&&level.hasChunkAt(safe))
                        npc.getNavigation().moveTo(safe.getX()+0.5,safe.getY(),safe.getZ()+0.5,0.75);
                }
                return true;
            }
            return false;
        }
        npc.getNavigation().stop();
        npc.setDeltaMovement(0,npc.getDeltaMovement().y,0);
        // Returning an escaped resident uses only a previously observed safe
        // location or an owned home. Never generate chunks or teleport to strangers.
        BlockPos safe=worker.hasLastSafe?new BlockPos(worker.lastSafeX,worker.lastSafeY,worker.lastSafeZ):null;
        if(safe==null||!inside(civics,level,safe,city)||!landing(level,npc,safe)){
            BlockPos home=worker.hasHome?new BlockPos(worker.homeX,worker.homeY,worker.homeZ):null;
            safe=null;
            if(home!=null){
                for(int dx=-2;dx<=2&&safe==null;dx++)
                    for(int dz=-2;dz<=2&&safe==null;dz++)
                        for(int dy=-1;dy<=1;dy++){
                            var trial=home.offset(dx,dy,dz);
                            if(inside(civics,level,trial,city)&&landing(level,npc,trial)){
                                safe=trial;break;
                            }
                        }
            }
        }
        if(safe!=null){
            npc.teleportTo(safe.getX()+0.5,safe.getY(),safe.getZ()+0.5);
            npc.setDeltaMovement(0,0,0);
            recordSafe(worker,safe,registry);
        }else npc.setNoAi(true); // Fail closed until the home chunk is loaded.
        return true;
    }

    /**
     * Confirmed death drops the NPC's real carried items exactly once. A missing
     * legacy record with possessions cannot be silently discarded by an audit.
     */
    private static boolean hasCarriedItems(KingdomNpcData.Worker worker){
        return worker!=null&&(!worker.cargo.isEmpty()||!worker.fieldTool.isEmpty()
                ||!worker.fieldInventory.isEmpty()||!worker.wagePurse.isEmpty());
    }
    public static boolean retire(ServerLevel level,Villager npc){
        return npc!=null&&retire(level,npc.getUUID(),npc);
    }
    public static boolean retire(ServerLevel level,UUID uuid){
        return retire(level,uuid,null);
    }
    private static boolean retire(ServerLevel level,UUID uuid,Villager npc){
        if(level==null||uuid==null)return false;
        MinecraftServer server=level.getServer();
        if(server==null)return false;
        var db=KingdomNpcData.get(server);
        KingdomNpcData.Worker worker=null;
        for(var w:db.all())if(uuid.equals(w.entityId)){worker=w;break;}
        if(worker==null)return false;
        if(npc==null&&hasCarriedItems(worker))return false;
        if(npc!=null){
            if(!worker.cargo.isEmpty())npc.spawnAtLocation(worker.cargo.copy());
            if(!worker.fieldTool.isEmpty())npc.spawnAtLocation(worker.fieldTool.copy());
            for(var stack:worker.fieldInventory)if(!stack.isEmpty())npc.spawnAtLocation(stack.copy());
            for(var stack:worker.wagePurse)if(!stack.isEmpty())npc.spawnAtLocation(stack.copy());
        }
        db.removeByEntity(uuid);
        KingdomEnterprisePayrollData.get(server).retireDeceasedNpc(server,uuid);
        KingdomCivicsData.get(server).removeDeceasedNpc(uuid);
        ru.projectkingdom.administration.KingdomAdministrationData.get(server).removeDeceasedNpcRoles(uuid);
        return true;
    }

    /**
     * Recovery for pre-fix worlds with ghost registrations. Only an OP may prune
     * a record after a second confirmation, and ONLY if its LAST KNOWN chunk
     * is loaded and the actual UUID is missing. Never infer death from an unloaded chunk.
     */
    public static void audit(ServerPlayer operator,boolean repair){
        if(operator==null||operator.getServer()==null)return;
        var server=operator.getServer();
        var db=KingdomNpcData.get(server);
        int alive=0,unloaded=0,missing=0,unverifiable=0;
        var suspects=new ArrayList<UUID>();
        for(var w:new ArrayList<>(db.all())){
            ServerLevel level=null;
            for(var l:server.getAllLevels())
                if(l.dimension().location().toString().equals(w.dimension)){level=l;break;}
            if(level==null){unverifiable++;continue;}
            var e=level.getEntity(w.entityId);
            if(e instanceof Villager v&&v.isAlive()){alive++;continue;}
            BlockPos last=w.hasLastSafe?new BlockPos(w.lastSafeX,w.lastSafeY,w.lastSafeZ)
                    :w.hasHome?new BlockPos(w.homeX,w.homeY,w.homeZ):null;
            if(last==null){unverifiable++;continue;}
            if(!level.hasChunkAt(last)){unloaded++;continue;}
            missing++;suspects.add(w.entityId);
        }
        operator.sendSystemMessage(Component.literal("[Kingdom NPC] Живых загруженных: "+alive
                +", выгруженных: "+unloaded+", отсутствуют в загруженной последней точке: "
                +missing+", без достаточных данных: "+unverifiable+"."));
        if(!repair)return;
        long now=server.overworld().getGameTime();
        Long prior=CONFIRMATIONS.get(operator.getUUID());
        if(prior==null||now>prior||now<prior-600){
            CONFIRMATIONS.put(operator.getUUID(),now+600);
            operator.sendSystemMessage(Component.literal("[Kingdom NPC] ВНИМАНИЕ: отсутствие в последней точке "
                    +"не доказывает смерть — NPC мог уйти в выгруженный чанк. "
                    +"После проверки повторите /knpc residents repair в течение 30 секунд, чтобы удалить "
                    +suspects.size()+" неподтверждённых записей. Сделайте копию мира."));
            return;
        }
        CONFIRMATIONS.remove(operator.getUUID());
        int removed=0;
        for(UUID id:suspects)if(retire(operator.serverLevel(),id))removed++;
        operator.sendSystemMessage(Component.literal("[Kingdom NPC] Удалены записи: "+removed
                +". Неизвестные/выгруженные NPC и записи с реальными вещами не затронуты."));
    }
}
