package ru.projectkingdom.enterprise;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomTerritories;

/**
 * Player-triggered physical-building registration around an installed enterprise
 * core. Building outlines are proven by bounded, loaded-only flood-fill; the
 * saved record is an acknowledgement, not a replacement for the actual blocks.
 *
 * Current design: a single-floor 3..20-block-wide room, continuous solid floor,
 * two-block-high side walls (with doors, glass or fences), at least one window,
 * and a non-flammable/non-air (material) roof within eight blocks. No auto-build.
 */
public final class EnterpriseBuildingRegistration extends SavedData {
    private static final String KEY="kingdom_enterprise_buildings_v1";
    private static final int RADIUS=16;
    private static final int MAX_AREA=400;
    private static final int MAX_HEIGHT=8;
    private static final TagKey<net.minecraft.world.level.block.Block> FORGE_GLASS=
            TagKey.create(Registries.BLOCK,new ResourceLocation("forge","glass"));
    private static final TagKey<net.minecraft.world.level.block.Block> FORGE_GLASS_PANES=
            TagKey.create(Registries.BLOCK,new ResourceLocation("forge","glass_panes"));
    private static final TagKey<net.minecraft.world.level.block.Block> FORGE_FENCES=
            TagKey.create(Registries.BLOCK,new ResourceLocation("forge","fences"));

    public enum Tile { AIR, SOLID, WINDOW, DOOR, MACHINE, FORGE, UNLOADED }
    @FunctionalInterface public interface Cells { Tile at(int x,int y,int z); }
    public enum Status {
        REGISTERED("Здание зарегистрировано"),
        NO_CORE("Нет действующего блока предприятия"),
        NO_ACCESS("Регистрация доступна только владельцу"),
        NO_ROOM("Нет закрытого помещения вокруг блока"),
        TOO_LARGE("Здание: область больше 64×48×64 или 32768 блоков"),
        TOO_SMALL("Минимальный размер здания: 2×2×3 блока"),
        NO_FLOOR("В полу есть отверстие или вместо пола неполный блок"),
        NO_WALLS("Стены не замкнуты: заделайте проёмы дверями, окнами или забором"),
        NO_ROOF("Нет сплошной крыши над помещением"),
        NO_WINDOWS("Нужно хотя бы одно окно: стекло, стеклянная панель либо забор"),
        UNLOADED("Не все чанки здания загружены"),
        FOREIGN_LAND("Здание выходит за пределы собственной земли"),
        OVERLAP("Это помещение уже зарегистрировано другим предприятием");
        private final String message;
        Status(String m){message=m;}
        public String message(){return message;}
    }
    public record Room(UUID enterprise,String dimension,long core,int minX,int minY,int minZ,
                       int maxX,int maxY,int maxZ,int windows,int interiorArea) {
        public boolean contains(BlockPos pos){return pos!=null&&pos.getX()>=minX&&pos.getX()<=maxX
                &&pos.getY()>=minY&&pos.getY()<=maxY&&pos.getZ()>=minZ&&pos.getZ()<=maxZ;}
        public String dimensions(){return (maxX-minX+1)+"×"+(maxY-minY+1)+"×"+(maxZ-minZ+1);}
        public boolean overlaps(Room other){
            return other!=null&&dimension.equals(other.dimension)
                    &&minX<=other.maxX&&maxX>=other.minX
                    &&minY<=other.maxY&&maxY>=other.minY
                    &&minZ<=other.maxZ&&maxZ>=other.minZ;
        }
    }
    public record Check(Status status,Room room){
        public boolean success(){return status==Status.REGISTERED&&room!=null;}
    }
    private final Map<UUID,Room> rooms=new LinkedHashMap<>();
    public EnterpriseBuildingRegistration(){}
    public static EnterpriseBuildingRegistration get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                EnterpriseBuildingRegistration::load,EnterpriseBuildingRegistration::new,KEY);
    }
    public Room room(UUID enterprise){return rooms.get(enterprise);}
    public void remove(UUID enterprise){if(rooms.remove(enterprise)!=null)setDirty();}
    public String status(ServerPlayer viewer,UUID enterprise,BlockPos core){
        Room saved=room(enterprise);
        if(saved==null)return "Здание: не зарегистрировано";
        var binding=KingdomEnterpriseData.get(viewer.getServer()).core(enterprise);
        return binding!=null&&binding.active()&&binding.packedPos()==core.asLong()
                &&saved.core()==core.asLong()
                ?"Здание: привязано по углам ("+saved.dimensions()+")"
                :"Здание: ядро перенесено, требуется новая привязка";
    }

    /** Validates exact core ID, ownership, structure, city plot and nonoverlap. */
    public Check register(ServerPlayer player,UUID enterprise,BlockPos core){
        if(player==null||enterprise==null||core==null)return new Check(Status.NO_CORE,null);
        var data=KingdomEnterpriseData.get(player.getServer());
        if(!data.mayManage(player.getUUID(),enterprise))return new Check(Status.NO_ACCESS,null);
        var binding=data.core(enterprise);
        if(binding==null||!binding.active()
                ||!binding.dimension().equals(player.serverLevel().dimension().location().toString())
                ||binding.packedPos()!=core.asLong()
                ||!player.serverLevel().hasChunkAt(core)
                ||!(player.serverLevel().getBlockEntity(core) instanceof EnterpriseCoreBlockEntity be)
                ||!be.bound()||!enterprise.equals(be.enterpriseId())
                ||!player.getUUID().equals(be.owner()))
            return new Check(Status.NO_CORE,null);
        Check result=inspect(player.serverLevel(),enterprise,core);
        if(!result.success())return result;
        var civic=KingdomCivicsData.get(player.getServer());
        var legacy=KingdomTerritories.get(player.getServer());
        Room room=result.room();
        // Every chunk that contains the room or its perimeter must have matching ownership.
        for(int cx=room.minX>>4;cx<=room.maxX>>4;cx++)
            for(int cz=room.minZ>>4;cz<=room.maxZ>>4;cz++){
                BlockPos sample=new BlockPos(cx*16+8,core.getY(),cz*16+8);
                if(!player.serverLevel().hasChunkAt(sample))return new Check(Status.UNLOADED,null);
                String dim=player.serverLevel().dimension().location().toString();
                UUID owner=civic.plotOwner(dim,cx,cz);
                if(owner==null)owner=legacy.legacyClaimOwner(dim,cx,cz);
                if(!player.getUUID().equals(owner))return new Check(Status.FOREIGN_LAND,null);
            }
        for(Room other:rooms.values())if(!other.enterprise.equals(enterprise)&&room.overlaps(other))
            return new Check(Status.OVERLAP,null);
        rooms.put(enterprise,room);setDirty();
        return result;
    }

    /** Registered area: all interior blocks, TFC racks, beds and machinery belong to the site.
     * No item is moved, no NBT is copied, and unloaded chunks always fail closed. */
    public Check registerSelected(ServerPlayer actor,UUID enterprise,BlockPos first,BlockPos second){
        if(actor==null||enterprise==null||first==null||second==null)
            return new Check(Status.NO_CORE,null);
        var enterprises=KingdomEnterpriseData.get(actor.getServer());
        var firm=enterprises.find(enterprise);
        if(firm==null||!enterprises.mayManage(actor.getUUID(),enterprise))
            return new Check(Status.NO_ACCESS,null);
        var binding=enterprises.core(enterprise);
        ServerLevel level=actor.serverLevel();
        String dim=level.dimension().location().toString();
        if(binding==null||!binding.active()||!dim.equals(binding.dimension())
                ||!firm.owner().equals(binding.owner()))
            return new Check(Status.NO_CORE,null);
        BlockPos core=BlockPos.of(binding.packedPos());
        if(!level.hasChunkAt(core)
                ||!(level.getBlockEntity(core) instanceof EnterpriseCoreBlockEntity entity)
                ||!entity.bound()||!enterprise.equals(entity.enterpriseId())
                ||!actor.getUUID().equals(entity.owner()))
            return new Check(Status.NO_CORE,null);
        int x1=Math.min(first.getX(),second.getX()),x2=Math.max(first.getX(),second.getX());
        int y1=Math.min(first.getY(),second.getY()),y2=Math.max(first.getY(),second.getY());
        int z1=Math.min(first.getZ(),second.getZ()),z2=Math.max(first.getZ(),second.getZ());
        long dx=(long)x2-x1+1,dy=(long)y2-y1+1,dz=(long)z2-z1+1;
        if(dx>64||dy>48||dz>64||dx*dy*dz>32768)return new Check(Status.TOO_LARGE,null);
        if(dx<2||dy<3||dz<2)return new Check(Status.TOO_SMALL,null);
        Room room=new Room(enterprise,dim,core.asLong(),x1,y1,z1,x2,y2,z2,0,(int)(dx*dz));
        if(!room.contains(core))return new Check(Status.NO_CORE,null);
        var civics=KingdomCivicsData.get(actor.getServer());
        var legacy=KingdomTerritories.get(actor.getServer());
        for(int cx=x1>>4;cx<=x2>>4;cx++)for(int cz=z1>>4;cz<=z2>>4;cz++){
            BlockPos sample=new BlockPos(cx*16+8,core.getY(),cz*16+8);
            if(!level.hasChunkAt(sample))return new Check(Status.UNLOADED,null);
            UUID titled=civics.plotOwner(dim,cx,cz);
            if(titled!=null){
                if(!actor.getUUID().equals(titled)
                        ||civics.leaseContract(dim,cx,cz)!=null)
                    return new Check(Status.FOREIGN_LAND,null);
            }else if(!actor.getUUID().equals(legacy.legacyClaimOwner(dim,cx,cz)))
                return new Check(Status.FOREIGN_LAND,null);
        }
        for(Room prior:rooms.values())if(!enterprise.equals(prior.enterprise())&&room.overlaps(prior))
            return new Check(Status.OVERLAP,null);
        // A landlord cannot reclaim a prepaid tenant's room by redefining the
        // surrounding enterprise boundary. Expired rentals need lawful turnover.
        var rentals=ru.projectkingdom.property.KingdomRentalsData.get(actor.getServer());
        var bounds=new ru.projectkingdom.property.KingdomRentalsData.Bounds(
                x1,y1,z1,x2,y2,z2);
        for(var rented:rentals.rooms())if(rented.reserved()){
            var building=rentals.building(rented.buildingId());
            if(building!=null&&dim.equals(building.dimension())
                    &&rented.bounds().overlaps(bounds))
                return new Check(Status.NO_ACCESS,null);
        }
        rooms.put(enterprise,room);setDirty();
        // Register in the existing property ledger when possible; never create a
        // tenant, charge a rent or transfer ownership merely by drawing corners.
        if(rentals.atBuilding(dim,core)==null){
            rentals.registerBuilding(civics,actor.getUUID(),dim,
                    ("Предприятие: "+firm.name()).substring(0,
                            Math.min(48,("Предприятие: "+firm.name()).length())),bounds);
        }
        return new Check(Status.REGISTERED,room);
    }

    /** Statistics only; all compatible inventories stay physical and in their actual blocks. */
    public record Contents(int beds,int inventories,int tfcBlocks) {}
    public static Contents contents(ServerLevel level,Room room){
        if(level==null||room==null||!room.dimension().equals(level.dimension().location().toString()))
            return new Contents(0,0,0);
        int beds=0,containers=0,tfc=0;
        for(int x=room.minX();x<=room.maxX();x++)
            for(int z=room.minZ();z<=room.maxZ();z++)
                for(int y=room.minY();y<=room.maxY();y++){
                    BlockPos at=new BlockPos(x,y,z);
                    if(!level.hasChunkAt(at))continue;
                    var block=level.getBlockState(at).getBlock();
                    if(block instanceof net.minecraft.world.level.block.BedBlock)beds++;
                    if(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(block).getNamespace().equals("tfc"))tfc++;
                    if(ru.projectkingdom.npc.KingdomContainerAccess.resolve(level,at)!=null)
                        containers++;
                }
        return new Contents(beds,containers,tfc);
    }

    public Check inspect(ServerLevel level,UUID enterprise,BlockPos core){
        if(level==null||enterprise==null||core==null)return new Check(Status.NO_CORE,null);
        Cells cells=(x,y,z)->tile(level,new BlockPos(x,y,z));
        return verify(cells,enterprise,level.dimension().location().toString(),core);
    }

    private static Tile tile(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return Tile.UNLOADED;
        BlockState state=level.getBlockState(pos);
        if(state.getBlock() instanceof DoorBlock)return Tile.DOOR;
        String blockId=net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString();
        if("tfc:charcoal_forge".equals(blockId))return Tile.FORGE;
        if(blockId.startsWith("tfc:metal/anvil/")
                ||state.getBlock() instanceof EnterpriseCoreBlock
                ||state.getBlock() instanceof net.minecraft.world.level.block.CraftingTableBlock
                ||level.getBlockEntity(pos) instanceof net.minecraft.world.Container)
            return Tile.MACHINE;
        if(state.is(BlockTags.FENCES)||state.is(FORGE_FENCES)
                ||state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock||state.is(FORGE_GLASS_PANES)
                ||state.is(FORGE_GLASS)
                ||state.getBlock() instanceof net.minecraft.world.level.block.AbstractGlassBlock)
            return Tile.WINDOW;
        VoxelShape shape=state.getCollisionShape(level,pos);
        if(state.isAir()||shape.isEmpty())return Tile.AIR;
        return Tile.SOLID;
    }

    /**
     * Deterministic bounded flood-fill at the production core's walkable Y.
     * A closed door counts as a wall; a fence or glass counts as a window.
     * Returning UNLOADED never force-loads chunks.
     */
    public static Check verify(Cells cells,UUID enterprise,String dimension,BlockPos core){
        if(cells==null||enterprise==null||dimension==null||core==null)
            return new Check(Status.NO_CORE,null);
        int baseY=core.getY();
        Set<Long> visited=new HashSet<>();
        ArrayDeque<BlockPos> todo=new ArrayDeque<>();
        todo.add(core.immutable());
        int minX=core.getX(),maxX=minX,minZ=core.getZ(),maxZ=minZ;
        int maxRoofY=baseY+2;
        while(!todo.isEmpty()){
            BlockPos p=todo.removeFirst();
            long key=p.asLong();
            if(visited.contains(key))continue;
            if(Math.abs(p.getX()-core.getX())>RADIUS
                    ||Math.abs(p.getZ()-core.getZ())>RADIUS ||visited.size()>=MAX_AREA)
                return new Check(Status.TOO_LARGE,null);
            Tile ground=cells.at(p.getX(),baseY,p.getZ());
            Tile upper=cells.at(p.getX(),baseY+1,p.getZ());
            if(ground==Tile.UNLOADED||upper==Tile.UNLOADED)
                return new Check(Status.UNLOADED,null);
            // The unique core occupies the central floor tile, but is interior.
            boolean isCore=p.getX()==core.getX()&&p.getZ()==core.getZ();
            if(!isCore&&!(ground==Tile.AIR||ground==Tile.MACHINE||ground==Tile.FORGE))continue;
            if(upper!=Tile.AIR)return new Check(Status.NO_ROOM,null);
            visited.add(key);
            minX=Math.min(minX,p.getX());maxX=Math.max(maxX,p.getX());
            minZ=Math.min(minZ,p.getZ());maxZ=Math.max(maxZ,p.getZ());
            Tile floor=cells.at(p.getX(),baseY-1,p.getZ());
            if(floor==Tile.UNLOADED)return new Check(Status.UNLOADED,null);
            if(floor!=Tile.SOLID)return new Check(Status.NO_FLOOR,null);
            boolean roof=false;
            for(int height=2;height<=MAX_HEIGHT;height++){
                Tile ceiling=cells.at(p.getX(),baseY+height,p.getZ());
                if(ceiling==Tile.UNLOADED)return new Check(Status.UNLOADED,null);
                if(ceiling==Tile.SOLID){roof=true;maxRoofY=Math.max(maxRoofY,baseY+height);break;}
            }
            // A charcoal forge needs a real open chimney: this single shaft
            // is not a missing roof, provided the rest of the room is covered.
            if(!roof&&ground!=Tile.FORGE)return new Check(Status.NO_ROOF,null);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(Math.abs(dx)+Math.abs(dz)!=1)continue;
                BlockPos neighbor=p.offset(dx,0,dz);
                Tile lower=cells.at(neighbor.getX(),baseY,neighbor.getZ());
                Tile top=cells.at(neighbor.getX(),baseY+1,neighbor.getZ());
                if(lower==Tile.UNLOADED||top==Tile.UNLOADED)
                    return new Check(Status.UNLOADED,null);
                if((lower==Tile.AIR||lower==Tile.MACHINE||lower==Tile.FORGE)
                        &&top==Tile.AIR) {
                    // An already-visited interior cell is NOT a broken wall.
                    if(!visited.contains(neighbor.asLong()))todo.addLast(neighbor);
                } else if(lower==Tile.AIR||top==Tile.AIR)
                    return new Check(Status.NO_WALLS,null);
            }
        }
        if(visited.size()<9 ||maxX-minX<2||maxZ-minZ<2)
            return new Check(Status.TOO_SMALL,null);
        int windows=0,opaqueWalls=0;
        for(long cell:visited){
            BlockPos at=BlockPos.of(cell);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(Math.abs(dx)+Math.abs(dz)!=1)continue;
                BlockPos outside=at.offset(dx,0,dz);
                if(visited.contains(outside.asLong()))continue;
                Tile low=cells.at(outside.getX(),baseY,outside.getZ());
                Tile high=cells.at(outside.getX(),baseY+1,outside.getZ());
                if(low==Tile.UNLOADED||high==Tile.UNLOADED)
                    return new Check(Status.UNLOADED,null);
                if(low==Tile.AIR||high==Tile.AIR)
                    return new Check(Status.NO_WALLS,null);
                if(low==Tile.WINDOW||high==Tile.WINDOW)windows++;
                if(low==Tile.SOLID||high==Tile.SOLID)opaqueWalls++;
            }
        }
        if(opaqueWalls<4)return new Check(Status.NO_WALLS,null);
        if(windows==0)return new Check(Status.NO_WINDOWS,null);
        Room room=new Room(enterprise,dimension,core.asLong(),minX-1,baseY-1,minZ-1,
                maxX+1,maxRoofY,maxZ+1,windows,visited.size());
        return new Check(Status.REGISTERED,room);
    }

    private static EnterpriseBuildingRegistration load(CompoundTag root){
        var data=new EnterpriseBuildingRegistration();
        ListTag entries=root.getList("Rooms",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size()&&i<10000;i++){
            var t=entries.getCompound(i);
            if(!t.hasUUID("Enterprise"))continue;
            try {
                Room room=new Room(t.getUUID("Enterprise"),t.getString("Dim"),t.getLong("Core"),
                        t.getInt("MinX"),t.getInt("MinY"),t.getInt("MinZ"),
                        t.getInt("MaxX"),t.getInt("MaxY"),t.getInt("MaxZ"),
                        t.getInt("Windows"),t.getInt("Area"));
                if(room.dimension!=null&&!room.dimension.isBlank()&&room.minX<=room.maxX
                        &&room.minY<=room.maxY&&room.minZ<=room.maxZ)
                    data.rooms.put(room.enterprise,room);
            }catch(RuntimeException ignored){}
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag entries=new ListTag();
        for(Room room:rooms.values()){
            var t=new CompoundTag();
            t.putUUID("Enterprise",room.enterprise);t.putString("Dim",room.dimension);
            t.putLong("Core",room.core);t.putInt("MinX",room.minX);t.putInt("MinY",room.minY);
            t.putInt("MinZ",room.minZ);t.putInt("MaxX",room.maxX);t.putInt("MaxY",room.maxY);
            t.putInt("MaxZ",room.maxZ);t.putInt("Windows",room.windows);
            t.putInt("Area",room.interiorArea);entries.add(t);
        }
        root.put("Rooms",entries);return root;
    }
}
