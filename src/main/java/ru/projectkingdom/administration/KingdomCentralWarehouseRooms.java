package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomContainerAccess;

/**
 * Physical city warehouse with multiple independent 3D rooms.
 * Reads only loaded, real ItemStacks. TFC food NBT is never rewritten.
 *
 * IMPORTANT: RoomKind.COLD is a logistical zoning designation, not a fabricated
 * TFC refrigeration bonus. Actual decay benefits require authentic TFC addon
 * cellar/climate-station equipment and its own working game mechanics.
 */
public final class KingdomCentralWarehouseRooms extends SavedData {
    private static final String KEY="kingdom_central_warehouse_rooms_v1";
    public static final int MAX_ROOMS=16,MAX_XZ=17,MAX_Y=9,MAX_STORAGES=128;
    public enum Kind {
        GENERAL("Обычный склад"), FOOD("Продовольствие"), COLD("Холодильная камера");
        private final String title;
        Kind(String title){this.title=title;}
        public String title(){return title;}
    }
    public record Room(UUID id,UUID town,String dimension,BlockPos min,BlockPos max,Kind kind) {
        public boolean contains(BlockPos pos){
            return pos.getX()>=min.getX()&&pos.getX()<=max.getX()
                &&pos.getY()>=min.getY()&&pos.getY()<=max.getY()
                &&pos.getZ()>=min.getZ()&&pos.getZ()<=max.getZ();
        }
        public boolean overlaps(Room other){
            return dimension.equals(other.dimension())
                &&min.getX()<=other.max().getX()&&max.getX()>=other.min().getX()
                &&min.getY()<=other.max().getY()&&max.getY()>=other.min().getY()
                &&min.getZ()<=other.max().getZ()&&max.getZ()>=other.min().getZ();
        }
        public int volume(){return (max.getX()-min.getX()+1)*(max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1);}
        public String dimensions(){return (max.getX()-min.getX()+1)+"×"+(max.getY()-min.getY()+1)+"×"+(max.getZ()-min.getZ()+1);}
    }
    public enum Result {
        FIRST_CORNER, CREATED, NO_SESSION, NO_RIGHTS, WRONG_DIMENSION,
        TOO_FAR, TOO_LARGE, ROOM_LIMIT, OVERLAP, FOREIGN_TOWN, UNLOADED,
        NO_STORAGE, STORAGE_LIMIT, INVALID_DOUBLE_CHEST, BANK_OVERLAP, RESERVED_STORAGE
    }
    public static String message(Result status){
        return switch(status){
            case FIRST_CORNER->"Первый угол отмечен. Пустой рукой нажмите ПКМ по противоположному углу пола (высота 3 блока) или по диагонали на нужной высоте.";
            case CREATED->"Вторая точка отмечена, комната центрального склада создана. Сундуки и бочки внутри включены в учёт.";
            case NO_SESSION->"Откройте центральный склад управляющим и выберите тип новой комнаты.";
            case NO_RIGHTS->"Разметка доступна мэру, основателю или назначенному управляющему складом.";
            case WRONG_DIMENSION->"Оба угла должны быть в одном измерении, где расположен центральный склад.";
            case TOO_FAR->"Выберите углы не дальше 32 блоков от здания склада, находясь рядом с ними.";
            case TOO_LARGE->"Длина и ширина комнаты: от 2 до 17 блоков; высота: от 3 до 9 блоков. Оба угла включаются в размер.";
            case ROOM_LIMIT->"Достигнут предел 16 комнат центрального склада.";
            case OVERLAP->"Комната пересекается с уже зарегистрированным помещением.";
            case FOREIGN_TOWN->"Все участки под комнатой должны принадлежать городу центрального склада.";
            case UNLOADED->"Один из чанков комнаты не загружен; подойдите к складу и повторите.";
            case NO_STORAGE->"В комнате нет подходящих сундуков или бочек. Сначала установите хранилище.";
            case STORAGE_LIMIT->"В одной комнате допускается не более 128 отдельных хранилищ.";
            case INVALID_DOUBLE_CHEST->"Двойной сундук должен целиком находиться внутри одной комнаты.";
            case BANK_OVERLAP->"Здесь расположена банковская комната; смешивать продукты с деньгами нельзя.";
            case RESERVED_STORAGE->"Этот сундук уже привязан к предприятию или другой городской службе.";
        };
    }
    private record Session(UUID town,String dimension,BlockPos desk,Kind kind,BlockPos first){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private final Map<UUID,List<Room>> byTown=new LinkedHashMap<>();

    public static KingdomCentralWarehouseRooms get(MinecraftServer server){
        return server.overworld().getDataStorage()
            .computeIfAbsent(KingdomCentralWarehouseRooms::load,KingdomCentralWarehouseRooms::new,KEY);
    }
    public List<Room> rooms(UUID town){return List.copyOf(byTown.getOrDefault(town,List.of()));}
    public boolean hasSession(UUID player){return SESSIONS.containsKey(player);}
    public static void cancel(UUID player){SESSIONS.remove(player);}
    public Room at(ServerLevel level,BlockPos position){
        String dimension=level.dimension().location().toString();
        for(var rooms:byTown.values())for(Room room:rooms)
            if(room.dimension().equals(dimension)&&room.contains(position))return room;
        return null;
    }
    private static boolean canManage(ServerPlayer player,UUID town){
        var city=KingdomCivicsData.get(player.getServer()).townById(town);
        if(city==null)return false;
        var admin=KingdomAdministrationData.get(player.getServer());
        return AdministrativePermissionService.mayManage(player.getUUID(),admin.founder(town),
            city.mayor(),admin.roles(town,player.getUUID()),AdministrativeKind.CENTRAL_WAREHOUSE);
    }
    public boolean begin(ServerPlayer player,Kind kind){
        if(player==null||kind==null||!(player.containerMenu instanceof AdministrativeCoreMenu menu)
            ||menu.kind()!=AdministrativeKind.CENTRAL_WAREHOUSE||!menu.manager()||!menu.stillValid(player)
            ||!canManage(player,menu.townId()))return false;
        SESSIONS.put(player.getUUID(),new Session(menu.townId(),
            player.level().dimension().location().toString(),menu.corePos(),kind,null));
        return true;
    }
    public Result select(ServerPlayer player,BlockPos position){
        Session session=SESSIONS.get(player.getUUID());
        if(session==null)return Result.NO_SESSION;
        if(!canManage(player,session.town())){
            SESSIONS.remove(player.getUUID());return Result.NO_RIGHTS;
        }
        ServerLevel level=player.serverLevel();
        if(!level.dimension().location().toString().equals(session.dimension())){
            SESSIONS.remove(player.getUUID());return Result.WRONG_DIMENSION;
        }
        if(position.distSqr(player.blockPosition())>64
                ||position.distSqr(session.desk())>32*32){
            SESSIONS.remove(player.getUUID());return Result.TOO_FAR;
        }
        if(session.first()==null){
            SESSIONS.put(player.getUUID(),new Session(session.town(),session.dimension(),
                session.desk(),session.kind(),position.immutable()));
            return Result.FIRST_CORNER;
        }
        BlockPos first=session.first();
        // Two opposite floor corners define a three-block-high room by default.
        // Keep the first corner while an invalid second corner is retried.
        int bottom=Math.min(first.getY(),position.getY());
        int top=first.getY()==position.getY()?bottom+2:Math.max(first.getY(),position.getY());
        BlockPos min=new BlockPos(Math.min(first.getX(),position.getX()),
            bottom,Math.min(first.getZ(),position.getZ()));
        BlockPos max=new BlockPos(Math.max(first.getX(),position.getX()),
            top,Math.max(first.getZ(),position.getZ()));
        Room proposed=new Room(UUID.randomUUID(),session.town(),session.dimension(),min,max,session.kind());
        if(max.getX()-min.getX()+1>MAX_XZ||max.getZ()-min.getZ()+1>MAX_XZ
            ||max.getY()-min.getY()+1>MAX_Y
            ||max.getX()-min.getX()<1||max.getZ()-min.getZ()<1||max.getY()-min.getY()<2)
            return Result.TOO_LARGE;
        if(rooms(session.town()).size()>=MAX_ROOMS)return Result.ROOM_LIMIT;
        if(proposed.contains(session.desk()))return Result.OVERLAP;
        for(var rooms:byTown.values())for(Room room:rooms)
            if(proposed.overlaps(room))return Result.OVERLAP;
        var bank=KingdomBankVaultRooms.get(player.getServer()).at(level,min);
        if(bank!=null)return Result.BANK_OVERLAP;
        // Avoid intersecting ANY part of a bank room, not just one corner.
        for(BlockPos cell:BlockPos.betweenClosed(min,max))
            if(KingdomBankVaultRooms.get(player.getServer()).at(level,cell)!=null)
                return Result.BANK_OVERLAP;
        var civics=KingdomCivicsData.get(player.getServer());
        for(int x=min.getX();x<=max.getX();x++)
        for(int z=min.getZ();z<=max.getZ();z++){
            var city=civics.townAt(level,new BlockPos(x,min.getY(),z));
            if(city==null||!session.town().equals(city.id()))return Result.FOREIGN_TOWN;
        }
        Result checked=validate(level,proposed);
        if(checked!=Result.CREATED)return checked;
        if(reservedStorage(level,proposed))return Result.RESERVED_STORAGE;
        byTown.computeIfAbsent(session.town(),id->new ArrayList<>()).add(proposed);
        setDirty();
        SESSIONS.remove(player.getUUID());
        return Result.CREATED;
    }
    private boolean reservedStorage(ServerLevel level,Room room){
        var administration=KingdomAdministrationData.get(level.getServer());
        for(var town:KingdomCivicsData.get(level.getServer()).towns())
            for(var role:AdministrativeBindingRole.values())
                for(var bound:administration.containerBindings(town.id(),role)){
                    if(role==AdministrativeBindingRole.WAREHOUSE_STORAGE&&town.id().equals(room.town()))
                        continue; // preserve old warehouse chests on migration
                    if(room.dimension().equals(bound.dimension())
                        &&room.contains(BlockPos.of(bound.packedPos())))return true;
                }
        var businesses=ru.projectkingdom.npc.KingdomEnterpriseData.get(level.getServer());
        for(var firm:businesses.all())
            for(var role:ru.projectkingdom.enterprise.EnterpriseBindingRole.values()){
                var bound=businesses.storageBinding(firm.id(),role);
                if(bound!=null&&room.dimension().equals(bound.dimension())
                    &&room.contains(bound.pos()))return true;
            }
        return false;
    }
    public Result validate(ServerLevel level,Room room){
        Set<BlockPos> seen=new HashSet<>();
        int containers=0;
        for(int x=room.min().getX();x<=room.max().getX();x++)
        for(int z=room.min().getZ();z<=room.max().getZ();z++)
            if(!level.hasChunkAt(new BlockPos(x,room.min().getY(),z)))return Result.UNLOADED;
        for(BlockPos cell:BlockPos.betweenClosed(room.min(),room.max())){
            if(seen.contains(cell))continue;
            if(!level.getBlockState(cell).hasBlockEntity())continue;
            Container content=KingdomContainerAccess.resolve(level,cell);
            if(content==null)continue; // ovens, controllers, food shelves may not implement Container
            if(level.getBlockState(cell).getBlock() instanceof ChestBlock){
                var state=level.getBlockState(cell);
                if(state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE && content.getContainerSize()<54)
                    return Result.INVALID_DOUBLE_CHEST;
                for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
                    BlockPos opposite=cell.relative(direction);
                    if(KingdomContainerAccess.samePhysicalStorage(level,cell,opposite)){
                        if(!room.contains(opposite))return Result.INVALID_DOUBLE_CHEST;
                        seen.add(opposite.immutable());
                    }
                }
            }
            seen.add(cell.immutable());
            containers++;
            if(containers>MAX_STORAGES)return Result.STORAGE_LIMIT;
        }
        return containers==0?Result.NO_STORAGE:Result.CREATED;
    }

    /** Only complete loaded real inventories. A partial room never appears as empty stock. */
    public record RoomStock(Room room,boolean loaded,int containers,int stacks,long itemCount,
                            Map<String,Long> byItem,boolean cellarControllerPresent){
        public RoomStock { byItem=Map.copyOf(byItem); }
    }
    public RoomStock inspect(ServerLevel level,Room room){
        if(!level.dimension().location().toString().equals(room.dimension())
                ||validate(level,room)!=Result.CREATED)
            return new RoomStock(room,false,0,0,0,Map.of(),false);
        Map<String,Long> items=new LinkedHashMap<>();
        Set<BlockPos> seen=new HashSet<>();
        int containers=0,stacks=0;
        long total=0;
        boolean station=false;
        for(BlockPos pos:BlockPos.betweenClosed(room.min(),room.max())){
            var state=level.getBlockState(pos);
            String blockId=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if(blockId.equals("firmalife:climate_station"))station=true;
            if(seen.contains(pos)||!state.hasBlockEntity())continue;
            Container container=KingdomContainerAccess.resolve(level,pos);
            if(container==null)continue;
            if(state.getBlock() instanceof ChestBlock)
                for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
                    BlockPos other=pos.relative(direction);
                    if(KingdomContainerAccess.samePhysicalStorage(level,pos,other))seen.add(other.immutable());
                }
            seen.add(pos.immutable());containers++;
            for(int i=0;i<container.getContainerSize();i++){
                ItemStack stack=container.getItem(i);
                if(stack.isEmpty())continue;
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    (long)stack.getCount(),Long::sum);
                stacks++;total+=stack.getCount();
            }
        }
        return new RoomStock(room,true,containers,stacks,total,items,station);
    }
    private static KingdomCentralWarehouseRooms load(CompoundTag root){
        var data=new KingdomCentralWarehouseRooms();
        for(Tag part:root.getList("Rooms",Tag.TAG_COMPOUND)){
            CompoundTag tag=(CompoundTag)part;
            if(!tag.hasUUID("Id")||!tag.hasUUID("Town"))continue;
            Kind kind;try{kind=Kind.valueOf(tag.getString("Kind"));}catch(IllegalArgumentException ex){continue;}
            BlockPos min=BlockPos.of(tag.getLong("Min")),max=BlockPos.of(tag.getLong("Max"));
            if(min.getX()>max.getX()||min.getY()>max.getY()||min.getZ()>max.getZ()
                ||max.getX()-min.getX()>=MAX_XZ||max.getY()-min.getY()>=MAX_Y
                ||max.getZ()-min.getZ()>=MAX_XZ)continue;
            var room=new Room(tag.getUUID("Id"),tag.getUUID("Town"),
                tag.getString("Dimension"),min,max,kind);
            var list=data.byTown.computeIfAbsent(room.town(),id->new ArrayList<>());
            if(list.size()<MAX_ROOMS && list.stream().noneMatch(room::overlaps))list.add(room);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag all=new ListTag();
        for(var rooms:byTown.values())for(Room room:rooms){
            CompoundTag tag=new CompoundTag();
            tag.putUUID("Id",room.id());tag.putUUID("Town",room.town());
            tag.putString("Dimension",room.dimension());
            tag.putLong("Min",room.min().asLong());tag.putLong("Max",room.max().asLong());
            tag.putString("Kind",room.kind().name());all.add(tag);
        }
        root.put("Rooms",all);return root;
    }
}
