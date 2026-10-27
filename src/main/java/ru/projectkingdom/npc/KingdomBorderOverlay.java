package ru.projectkingdom.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * In-world outline for REAL Kingdom city claims. Server sends bounded particles
 * to the requesting player only; no chunks are loaded or modified by rendering.
 */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomBorderOverlay {
    private static final String SHOW="KingdomShowCityBorders";
    private static final int RADIUS_CHUNKS=2, PARTICLE_BUDGET=240;
    private static final Map<UUID,Position> LAST_VISIT=new ConcurrentHashMap<>();
    private record Position(String dimension,int cx,int cz,UUID town,String townName) {}

    private KingdomBorderOverlay(){}

    /** Stored in player NBT; enabling is optional and survives relogging. */
    public static boolean enabled(ServerPlayer player){
        return player.getPersistentData().getBoolean(SHOW);
    }
    public static boolean toggle(ServerPlayer player){
        boolean value=!enabled(player);
        player.getPersistentData().putBoolean(SHOW,value);
        player.displayClientMessage(Component.literal(value
                ? "[Kingdom] Границы городов показаны (G — скрыть)."
                : "[Kingdom] Границы городов скрыты (G — показать)."),true);
        return value;
    }
    /** Pure edge rule: draw borders ONLY between different registered territories. */
    public static boolean isOuterEdge(UUID city,UUID adjacent){
        return city!=null&&!city.equals(adjacent);
    }

    @SubscribeEvent
    public static void disconnect(PlayerEvent.PlayerLoggedOutEvent event){
        LAST_VISIT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        if(player.tickCount%10==0)notifyOnCrossing(player);
        if(!enabled(player)||player.tickCount%30!=0)return;
        drawNearby(player);
    }

    private static void notifyOnCrossing(ServerPlayer player){
        int cx=player.blockPosition().getX()>>4,cz=player.blockPosition().getZ()>>4;
        String dim=player.serverLevel().dimension().location().toString();
        var previous=LAST_VISIT.get(player.getUUID());
        if(previous!=null&&previous.cx()==cx&&previous.cz()==cz&&previous.dimension().equals(dim))return;
        var town=KingdomCivicsData.get(player.getServer()).townAt(player.serverLevel(),player.blockPosition());
        UUID townId=town==null?null:town.id();
        String townName=town==null?"":town.name();
        LAST_VISIT.put(player.getUUID(),new Position(dim,cx,cz,townId,townName));
        if(previous==null)return; // avoid repeated announcements on login
        if(java.util.Objects.equals(previous.town(),townId)
                &&previous.dimension().equals(dim))return;
        if(town!=null)
            player.displayClientMessage(Component.literal("Вы входите в город «"+townName+"»"),true);
        else if(previous.town()!=null)
            player.displayClientMessage(Component.literal("Вы покидаете город «"+previous.townName()+"»"),true);
    }

    private static void drawNearby(ServerPlayer player){
        ServerLevel level=player.serverLevel();
        var civic=KingdomCivicsData.get(player.getServer());
        var legacy=KingdomTerritories.get(player.getServer());
        var own=civic.townOf(player.getUUID());
        String dimension=level.dimension().location().toString();
        int centerX=player.chunkPosition().x,centerZ=player.chunkPosition().z;
        int sent=0;
        for(int cx=centerX-RADIUS_CHUNKS;cx<=centerX+RADIUS_CHUNKS;cx++)
        for(int cz=centerZ-RADIUS_CHUNKS;cz<=centerZ+RADIUS_CHUNKS;cz++){
            BlockPos sample=new BlockPos(cx*16+8,player.blockPosition().getY(),cz*16+8);
            if(!level.hasChunkAt(sample))continue;
            var plot=civic.plot(level,sample);
            UUID id=plot==null?legacy.legacyClaimOwner(dimension,cx,cz):plot.town();
            if(id==null)continue;
            boolean mine=plot!=null ? own!=null&&id.equals(own.id())
                    :id.equals(player.getUUID());
            boolean publicLand=plot!=null&&plot.zone()==KingdomCivicsData.Zone.PUBLIC;
            Vector3f rgb=mine?new Vector3f(0.12f,0.95f,0.28f)
                    :publicLand?new Vector3f(0.98f,0.77f,0.24f)
                    :new Vector3f(0.98f,0.22f,0.18f);
            var dust=new DustParticleOptions(rgb,1.0f);
            for(int side=0;side<4;side++){
                int dx=side==0?-1:side==1?1:0;
                int dz=side==2?-1:side==3?1:0;
                int nx=cx+dx,nz=cz+dz;
                var adjacent=civic.plot(level,new BlockPos(nx*16+8,sample.getY(),nz*16+8));
                UUID neighbor=adjacent==null?legacy.legacyClaimOwner(dimension,nx,nz):adjacent.town();
                if(!isOuterEdge(id,neighbor))continue;
                // Avoid duplicate coloured edges between two neighbouring cities.
                if(neighbor!=null&&(cx>nx||cx==nx&&cz>nz))continue;
                for(int step=0;step<=16&&sent<PARTICLE_BUDGET;step+=4){
                    int innerX=cx*16+(side==0?0:side==1?15:Math.min(step,15));
                    int innerZ=cz*16+(side==2?0:side==3?15:Math.min(step,15));
                    if(!level.hasChunkAt(new BlockPos(innerX,sample.getY(),innerZ)))continue;
                    double x=cx*16+(side==0?0.12:side==1?15.88:step);
                    double z=cz*16+(side==2?0.12:side==3?15.88:step);
                    double y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,innerX,innerZ)+0.16;
                    if(Math.abs(y-player.getY())>28)continue;
                    level.sendParticles(player,dust,false,x,y,z,1,0,0,0,0);sent++;
                    // Small bright vertical corner markers: much easier to see in grass.
                    if(step==0||step==16){
                        for(int height=2;height<=8&&sent<PARTICLE_BUDGET;height+=2){
                            level.sendParticles(player,dust,false,x,y+height,z,1,0,0,0,0);sent++;
                        }
                    }
                }
            }
        }
    }
}
