package ru.projectkingdom.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomNpcData;

/**
 * First settlers immigrate via a real registered tavern, not virtual population.
 * This never spawns money, materials, professions, private property, or buildings.
 */
public final class KingdomTavernImmigration {
    private KingdomTavernImmigration() {}

    public static final int ALPHA_NPC_CAP=50;
    public static final int MAX_TAVERN_RESIDENTS=4;
    public static final long ARRIVAL_INTERVAL_TICKS=24000L; // One Minecraft day
    public static final int BED_RADIUS=10;
    public record Capacity(int beds,int residents,int places,boolean tavernReady,String message) {}

    /** Calls may be made from a GUI; never intentionally load any chunk. */
    public static Capacity inspect(MinecraftServer server, UUID enterprise){
        if(server==null||enterprise==null)return new Capacity(0,0,0,false,"Нет таверны");
        var firms=KingdomEnterpriseData.get(server);
        var binding=firms.core(enterprise);
        var firm=firms.find(enterprise);
        if(firm==null||binding==null||!binding.active()
                ||firms.kind(enterprise)!=EnterpriseKind.TAVERN
                ||binding.kind()!=EnterpriseKind.TAVERN
                ||!firm.owner().equals(binding.owner()))
            return new Capacity(0,0,0,false,"Нет действующей таверны");
        ServerLevel level=null;
        for(ServerLevel candidate:server.getAllLevels())
            if(candidate.dimension().location().toString().equals(binding.dimension())) {
                level=candidate;break;
            }
        if(level==null)return new Capacity(0,0,0,false,"Измерение недоступно");
        BlockPos core=BlockPos.of(binding.packedPos());
        if(!level.hasChunkAt(core)||!(level.getBlockEntity(core) instanceof EnterpriseCoreBlockEntity block)
                ||!block.bound()||!enterprise.equals(block.enterpriseId())
                ||!firm.owner().equals(block.owner())||block.kind()!=EnterpriseKind.TAVERN)
            return new Capacity(0,0,0,false,"Загрузите настоящую стойку таверны");
        var civics=KingdomCivicsData.get(server);
        var town=civics.townAt(level,core);
        if(town==null||!town.mayor().equals(firm.owner()))
            return new Capacity(0,0,0,false,"Таверна должна быть в поселении основателя");
        int beds=countBeds(level,core,firm.owner());
        int people=countResidents(server,town.id());
        int places=Math.min(MAX_TAVERN_RESIDENTS,beds);
        return new Capacity(beds,people,places,true,beds==0
                ?"Для переселенцев нужны кровати под крышей на своей земле"
                :people>=places?"Все спальные места заняты":"Ожидаются переселенцы");
    }

    public static int countResidents(MinecraftServer server,UUID town){
        if(server==null||town==null)return 0;
        int n=0;
        for(var worker:KingdomNpcData.get(server).all())
            if(town.toString().equals(worker.settlement))n++;
        return n;
    }

    /** Vanilla + TerraFirmaCraft straw-and-hide beds, without hard mod linkage. */
    public static boolean isSleepingPlace(net.minecraft.world.level.block.state.BlockState state){
        if(state.getBlock() instanceof BedBlock)
            return state.getValue(BedBlock.PART)==BedPart.HEAD;
        var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(!"tfc:thatch_bed".equals(id.toString()))return false;
        for(var property:state.getProperties())
            if("part".equals(property.getName()))
                return "head".equals(state.getValue(property).toString());
        return false;
    }

    /** Count only owned, sheltered, unloaded-safe bed HEAD blocks (one per bed). */
    private static int countBeds(ServerLevel level,BlockPos tavern,UUID owner) {
        var civics=KingdomCivicsData.get(level.getServer());
        String dim=level.dimension().location().toString();
        int beds=0;
        for(int dx=-BED_RADIUS;dx<=BED_RADIUS;dx++){
            for(int dz=-BED_RADIUS;dz<=BED_RADIUS;dz++){
                for(int dy=-3;dy<=4;dy++){
                    BlockPos pos=tavern.offset(dx,dy,dz);
                    if(!level.hasChunkAt(pos)||!owner.equals(civics.plotOwner(dim,
                            pos.getX()>>4,pos.getZ()>>4)))continue;
                    var state=level.getBlockState(pos);
                    if(!isSleepingPlace(state))continue;
                    if(civics.leaseContract(dim,pos.getX()>>4,pos.getZ()>>4)!=null)continue;
                    if(ru.projectkingdom.property.KingdomRentalsData.get(level.getServer())
                            .atRoom(dim,pos)!=null)continue;
                    // A bare bed in a field must not be treated as an inn.
                    boolean roof=false;
                    for(int h=2;h<=4;h++){
                        var overhead=pos.above(h);
                        if(level.hasChunkAt(overhead)
                                &&!level.getBlockState(overhead).isAir()){roof=true;break;}
                    }
                    if(roof)beds++;
                    if(beds>=MAX_TAVERN_RESIDENTS)return beds;
                }
            }
        }
        return beds;
    }

    /** Server-only, bounded, no chunk forcing. A nearby player witnesses arrival. */
    public static void tick(MinecraftServer server){
        if(server==null||server.getTickCount()%200!=0)return;
        var npcDb=KingdomNpcData.get(server);
        if(npcDb.workers.size()>=ALPHA_NPC_CAP)return;
        var arrivals=KingdomTavernArrivals.get(server);
        var companies=KingdomEnterpriseData.get(server);
        var civics=KingdomCivicsData.get(server);
        for(var firm:companies.all()){
            if(companies.kind(firm.id())!=EnterpriseKind.TAVERN)continue;
            var binding=companies.core(firm.id());
            if(binding==null||!binding.active())continue;
            ServerLevel level=null;
            for(ServerLevel candidate:server.getAllLevels())
                if(candidate.dimension().location().toString().equals(binding.dimension())){
                    level=candidate;break;
                }
            if(level==null)continue;
            BlockPos tavern=BlockPos.of(binding.packedPos());
            if(!level.hasChunkAt(tavern))continue;
            ServerPlayer witness=null;
            for(ServerPlayer player:level.players())
                if(player.distanceToSqr(tavern.getX()+.5,tavern.getY()+.5,tavern.getZ()+.5)<=64*64){
                    witness=player;break;
                }
            if(witness==null)continue;
            var town=civics.townAt(level,tavern);
            if(town==null||!firm.owner().equals(town.mayor()))continue;
            var available=inspect(server,firm.id());
            if(!available.tavernReady()||available.places()<=available.residents())continue;
            long tick=server.overworld().getGameTime();
            if(!arrivals.ready(town.id(),tick))continue;
            BlockPos spawn=safeSpawn(level,tavern,town.id());
            if(spawn==null)continue;
            Villager villager=EntityType.VILLAGER.create(level);
            if(villager==null)continue;
            villager.moveTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,level.random.nextFloat()*360f,0);
            villager.setPersistenceRequired();
            String name="Переселенец "+(available.residents()+1);
            villager.setCustomName(Component.literal(name));
            villager.setCustomNameVisible(true);
            if(!level.noCollision(villager))continue;
            // Ensure no NPC entity or citizen record is created on a failed spawn.
            if(!level.addFreshEntity(villager))continue;
            var created=npcDb.add(villager.getUUID(),name,binding.dimension(),level.getDayTime()/24000L);
            created.settlement=town.id().toString();
            created.order="wander";
            created.hunger=80;
            created.morale=65;
            created.employer=null; // free citizen, not an unpaid tavern employee
            npcDb.setDirty();
            civics.joinTown(villager.getUUID(),town.id());
            arrivals.record(town.id(),tick);
            witness.sendSystemMessage(Component.literal("[Kingdom] В таверну «"+firm.name()
                    +"» прибыл новый житель: "+name+". Население "+(available.residents()+1)
                    +"/"+available.places()+"."));
            break; // At most ONE physical NPC per scan across the whole server.
        }
    }
    private static BlockPos safeSpawn(ServerLevel level,BlockPos tavern,UUID town){
        var civic=KingdomCivicsData.get(level.getServer());
        for(int r=2;r<=6;r++)
            for(int dx=-r;dx<=r;dx++)
                for(int dz=-r;dz<=r;dz++){
                    if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
                    BlockPos candidate=tavern.offset(dx,0,dz);
                    if(!level.hasChunkAt(candidate)||!level.hasChunkAt(candidate.above())
                            ||!level.hasChunkAt(candidate.below()))continue;
                    var plot=civic.plot(level,candidate);
                    if(plot==null||!town.equals(plot.town()))continue;
                    if(!level.getBlockState(candidate).isAir()
                            ||!level.getBlockState(candidate.above()).isAir())continue;
                    var floor=candidate.below();
                    if(!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))continue;
                    if(!level.getWorldBorder().isWithinBounds(candidate))continue;
                    return candidate;
                }
        return null;
    }
}
