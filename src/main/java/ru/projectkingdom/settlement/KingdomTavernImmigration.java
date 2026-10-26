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
    public static final int MAX_SETTLEMENT_RESIDENTS=32;
    public static final long ARRIVAL_INTERVAL_TICKS=24000L; // One Minecraft day
    public static final int BED_RADIUS=10;
    public record Capacity(int beds,int residents,int places,int meals,boolean tavernReady,String message) {}

    /** Calls may be made from a GUI; never intentionally load any chunk. */
    public static Capacity inspect(MinecraftServer server, UUID enterprise){
        if(server==null||enterprise==null)return new Capacity(0,0,0,0,false,"Нет таверны");
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
        if(town==null||!firm.owner().equals(civics.plotOwner(binding.dimension(),
                core.getX()>>4,core.getZ()>>4)))
            return new Capacity(0,0,0,false,"Таверна должна стоять на земле своего владельца в городе");
        int beds=countBeds(level,core,firm.owner());
        int people=countResidents(server,town.id());
        int places=Math.min(MAX_SETTLEMENT_RESIDENTS,beds);
        var food=KingdomTavernProvisions.inspect(level,enterprise);
        return new Capacity(beds,people,places,food.meals(),true,beds==0
                ?"Для переселенцев нужны кровати под крышей на своей земле"
                :people>=places?"Все спальные места заняты"
                :!food.ready()?food.issue():"Есть еда и кровати: ожидаются переселенцы");
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
    private static List<BlockPos> sleepingPlaces(ServerLevel level,BlockPos tavern,UUID owner) {
        var civics=KingdomCivicsData.get(level.getServer());
        String dim=level.dimension().location().toString();
        List<BlockPos> beds=new ArrayList<>();
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
                    if(roof)beds.add(pos.immutable());
                    if(beds.size()>=MAX_TAVERN_RESIDENTS)
                return withIndependentHousing(level,tavern,owner,beds);
                }
            }
        }
        return withIndependentHousing(level,tavern,owner,beds);
    }

    /**
     * A hamlet uses up to four tavern bunks. Every additional bunk must be
     * sheltered inside a DIFFERENT, completed construction project on the
     * same town's titled land. Moving a loose bed into a field does not count.
     * Schematics serve as an immutable footprint, no chunks are force-loaded.
     */
    private static List<BlockPos> withIndependentHousing(ServerLevel level,BlockPos tavern,
                                                         UUID owner,List<BlockPos> existing){
        var civic=KingdomCivicsData.get(level.getServer());
        var town=civic.townAt(level,tavern);
        if(town==null)return existing;
        var houseRegistry=ru.projectkingdom.property.KingdomRentalsData.get(level.getServer());
        var inn=houseRegistry.atBuilding(level.dimension().location().toString(),tavern);
        var plans=ru.projectkingdom.npc.KingdomConstructionData.get(level.getServer());
        for(var project:plans.all()){
            if(existing.size()>=MAX_SETTLEMENT_RESIDENTS)break;
            if(project.status()!=ru.projectkingdom.npc.KingdomConstructionData.Status.COMPLETED
                    ||!project.dimension().equals(level.dimension().location().toString()))continue;
            BlockPos origin=new BlockPos(project.x(),project.y(),project.z());
            if(origin.distSqr(tavern)<144 || origin.distSqr(tavern)>4096)continue;
            var projectTown=civic.townAt(level,origin);
            if(projectTown==null||!town.id().equals(projectTown.id()))continue;
            if(inn!=null && inn.bounds().contains(origin))continue;
            var design=ru.projectkingdom.npc.KingdomConstructionPlans.resolve(
                    level.getServer(),project.design());
            if(design==null||design.blocks().isEmpty())continue;
            int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
            int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(var cell:design.blocks()){
                minX=Math.min(minX,project.x()+cell.x());maxX=Math.max(maxX,project.x()+cell.x());
                minY=Math.min(minY,project.y()+cell.y());maxY=Math.max(maxY,project.y()+cell.y());
                minZ=Math.min(minZ,project.z()+cell.z());maxZ=Math.max(maxZ,project.z()+cell.z());
            }
            // A large frozen schematic is not allowed to cause a world-wide bed scan.
            if(maxX-minX>31||maxZ-minZ>31||maxY-minY>15)continue;
            for(int x=minX;x<=maxX && existing.size()<MAX_SETTLEMENT_RESIDENTS;x++)
                for(int z=minZ;z<=maxZ && existing.size()<MAX_SETTLEMENT_RESIDENTS;z++)
                    for(int y=minY;y<=maxY && existing.size()<MAX_SETTLEMENT_RESIDENTS;y++){
                        BlockPos pos=new BlockPos(x,y,z);
                        if(!level.hasChunkAt(pos)||existing.contains(pos)
                                ||!owner.equals(civic.plotOwner(project.dimension(),x>>4,z>>4)))continue;
                        if(!isSleepingPlace(level.getBlockState(pos)))continue;
                        if(civic.leaseContract(project.dimension(),x>>4,z>>4)!=null)continue;
                        if(houseRegistry.atRoom(project.dimension(),pos)!=null)continue;
                        boolean roof=false;
                        for(int h=2;h<=4;h++)if(level.hasChunkAt(pos.above(h))
                                &&!level.getBlockState(pos.above(h)).isAir()){roof=true;break;}
                        if(roof)existing.add(pos.immutable());
                    }
        }
        return existing;
    }

    private static int countBeds(ServerLevel level,BlockPos tavern,UUID owner){
        return sleepingPlaces(level,tavern,owner).size();
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
            if(town==null||!firm.owner().equals(civics.plotOwner(binding.dimension(),
                    tavern.getX()>>4,tavern.getZ()>>4)))continue;
            var available=inspect(server,firm.id());
            if(!available.tavernReady()||available.places()<=available.residents()
                    ||available.meals()<KingdomTavernProvisions.MIN_MEALS_TO_ATTRACT)continue;
            long tick=server.overworld().getGameTime();
            if(!arrivals.ready(town.id(),tick))continue;
            BlockPos spawn=safeSpawn(level,tavern,town.id());
            if(spawn==null)continue;
            // Never assign the same physical bunk to multiple NPCs.
            BlockPos home=null;
            for(BlockPos bed:sleepingPlaces(level,tavern,firm.owner())){
                boolean taken=false;
                for(var worker:npcDb.all()){
                    if(town.id().toString().equals(worker.settlement)&&worker.hasHome
                            &&worker.homeX==bed.getX()&&worker.homeY==bed.getY()
                            &&worker.homeZ==bed.getZ()) {taken=true;break;}
                }
                if(!taken){home=bed;break;}
            }
            if(home==null)continue;
            // Reserve one edible REAL ItemStack, not a virtual visitor ration.
            // The registration below happens only after one piece of food is
            // successfully removed from the tavern's active food store.
            var serving=KingdomTavernProvisions.choose(level,firm.id());
            if(serving==null)continue;
            Villager villager=EntityType.VILLAGER.create(level);
            if(villager==null)continue;
            villager.moveTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,level.random.nextFloat()*360f,0);
            villager.setPersistenceRequired();
            int nextId=npcDb.nextId;
            boolean woman=(nextId&1)==0;
            String[] first=woman?new String[]{"Марья","Анна","Дарья","Аглая","Варвара","Василиса"}
                    :new String[]{"Мирон","Егор","Тихон","Степан","Илья","Фёдор"};
            String[] families=woman?new String[]{"Соколова","Лескова","Мельникова","Кузнецова"}
                    :new String[]{"Соколов","Лесков","Мельников","Кузнецов"};
            String family=families[(nextId/first.length)%families.length];
            String name=first[nextId%first.length]+" "+family;
            villager.setCustomName(Component.literal(name));
            villager.setCustomNameVisible(true);
            if(!level.noCollision(villager))continue;
            // Ensure no NPC entity or citizen record is created on a failed spawn.
            if(!level.addFreshEntity(villager))continue;
            if(!KingdomTavernProvisions.consume(serving)){
                villager.discard();continue;
            }
            var created=npcDb.add(villager.getUUID(),name,binding.dimension(),level.getDayTime()/24000L);
            created.settlement=town.id().toString();
            created.familyName=family;
            created.gender=woman?"female":"male";
            created.ageYears=20+(nextId%35);
            created.hasHome=true;
            created.homeX=home.getX();
            created.homeY=home.getY();
            created.homeZ=home.getZ();
            created.order="wander";
            created.hunger=80;
            created.morale=65;
            created.job="free"; // no profession is assigned on immigration
            created.employer=null; // free citizen, not an unpaid tavern employee
            if(serving.nutrition()!=null)
                ru.projectkingdom.npc.KingdomNpcNutrition.eat(created,serving.nutrition());
            npcDb.setDirty();
            civics.joinTown(villager.getUUID(),town.id());
            ru.projectkingdom.npc.KingdomFemaleOutfitSync.notifyChanged(server,created);
            arrivals.record(town.id(),tick);
            witness.sendSystemMessage(Component.literal("[Kingdom] В таверну «"+firm.name()
                    +"» прибыл новый житель: "+name+". Население "+(available.residents()+1)
                    +"/"+available.places()+". В таверне осталось еды: "+(available.meals()-1)+"."));
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
