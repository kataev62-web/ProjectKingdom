package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomContainerAccess;
import ru.projectkingdom.npc.KingdomCurrency;
import ru.projectkingdom.npc.KingdomPhysicalCash;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;

/**
 * One spatially defined, protected bank vault per city.
 * A vault is an inclusive three-dimensional room built by players, not a
 * virtual balance. Each loaded vanilla chest/barrel in that room contributes
 * real coin slots to a flattened transaction-only Container.
 *
 * Transactions fail CLOSED when any room chunk is absent, when a double chest
 * crosses the boundary or when an internal chest contains noncoin items.
 * The existing BANK_VAULT chest must remain inside the first room.
 */
public final class KingdomBankVaultRooms extends SavedData {
    private static final String KEY="kingdom_bank_vault_rooms_v1";
    public static final int MAX_XZ=17,MAX_Y=9,MAX_CONTAINERS=64;
    public record Room(UUID town,String dimension,BlockPos min,BlockPos max) {
        public boolean contains(BlockPos pos) {
            return pos.getX()>=min.getX()&&pos.getX()<=max.getX()
                &&pos.getY()>=min.getY()&&pos.getY()<=max.getY()
                &&pos.getZ()>=min.getZ()&&pos.getZ()<=max.getZ();
        }
        public boolean contains(Room other){
            return dimension.equals(other.dimension())&&contains(other.min())&&contains(other.max());
        }
        public boolean overlaps(Room other){
            return dimension.equals(other.dimension())
                &&min.getX()<=other.max().getX()&&max.getX()>=other.min().getX()
                &&min.getY()<=other.max().getY()&&max.getY()>=other.min().getY()
                &&min.getZ()<=other.max().getZ()&&max.getZ()>=other.min().getZ();
        }
        public int volume() {
            return (max.getX()-min.getX()+1)*(max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1);
        }
    }
    public enum Result {
        FIRST_CORNER, CREATED, EXPANDED, NO_SESSION, NO_RIGHTS, WRONG_DIMENSION,
        TOO_FAR, TOO_LARGE, FOREIGN_PLOT, OVERLAP, BANK_DESK_INSIDE, LEGACY_CHEST_OUTSIDE,
        EXISTING_ROOM_NOT_INCLUDED, UNLOADED, NO_CHESTS, TOO_MANY_CHESTS,
        INVALID_STORAGE, AUTOMATION_NEARBY
    }
    public static String message(Result result){
        return switch(result){
            case FIRST_CORNER->"Первый угол отмечен. Нажмите ПКМ по противоположному углу комнаты с пустой рукой.";
            case CREATED->"Банковская комната закреплена. Все сундуки и бочки внутри объединены в один резерв.";
            case EXPANDED->"Границы банковского хранилища расширены без потери старых сундуков.";
            case NO_SESSION->"Откройте банк управляющим и нажмите «Отметить комнату хранилища».";
            case NO_RIGHTS->"Границы банковской комнаты может менять только управляющий банка.";
            case WRONG_DIMENSION->"Оба угла комнаты должны находиться в том же измерении, что и банк.";
            case TOO_FAR->"Подойдите к выбранному углу; граница комнаты должна быть рядом с банком.";
            case TOO_LARGE->"Два угла должны задавать объём минимум 3×3×3 и максимум 17×9×17. Выбирайте противоположные углы на разной высоте.";
            case FOREIGN_PLOT->"Комната должна целиком находиться на земле этого города.";
            case OVERLAP->"Комната пересекается с банковским хранилищем другого города.";
            case BANK_DESK_INSIDE->"Стойка банка должна находиться снаружи защищённой комнаты, чтобы посетители могли пользоваться банком.";
            case LEGACY_CHEST_OUTSIDE->"Старый банковский сундук должен остаться внутри новой комнаты — выберите другие углы.";
            case EXISTING_ROOM_NOT_INCLUDED->"При расширении старое хранилище должно полностью оставаться внутри новых границ.";
            case UNLOADED->"Не все чанки комнаты загружены. Подойдите к хранилищу и повторите.";
            case NO_CHESTS->"В комнате должен стоять хотя бы один настоящий сундук или бочка.";
            case TOO_MANY_CHESTS->"В одной банковской комнате поддерживается не более 64 отдельных хранилищ.";
            case INVALID_STORAGE->"Проверьте сундуки: внутри должны лежать только монеты Kingdom; двойной сундук не должен выходить за границу комнаты.";
            case AUTOMATION_NEARBY->"У банковского сундука обнаружена воронка. Уберите воронки внутри и возле хранилища: автоматическая выдача монет запрещена.";
        };
    }
    private record Session(UUID town,String dimension,BlockPos bank,BlockPos first) {}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private final Map<UUID,Room> rooms=new HashMap<>();

    public static KingdomBankVaultRooms get(MinecraftServer server){
        return server.overworld().getDataStorage()
            .computeIfAbsent(KingdomBankVaultRooms::load,KingdomBankVaultRooms::new,KEY);
    }
    public Room room(UUID town){return rooms.get(town);}
    public boolean hasSession(UUID player){return SESSIONS.containsKey(player);}
    public static void cancel(UUID player){SESSIONS.remove(player);}
    public Room at(ServerLevel level,BlockPos pos){
        String dim=level.dimension().location().toString();
        for(Room room:rooms.values())if(room.dimension().equals(dim)&&room.contains(pos))return room;
        return null;
    }
    private static boolean canManage(ServerPlayer player,UUID town){
        var city=KingdomCivicsData.get(player.getServer()).townById(town);
        if(city==null)return false;
        var data=KingdomAdministrationData.get(player.getServer());
        return AdministrativePermissionService.mayManage(player.getUUID(),
            data.founder(town),city.mayor(),data.roles(town,player.getUUID()),AdministrativeKind.BANK);
    }
    public boolean begin(ServerPlayer player){
        if(!(player.containerMenu instanceof AdministrativeCoreMenu menu)
                ||menu.kind()!=AdministrativeKind.BANK||!menu.manager()||!menu.stillValid(player)
                ||!canManage(player,menu.townId()))return false;
        SESSIONS.put(player.getUUID(),new Session(menu.townId(),
            player.level().dimension().location().toString(),menu.corePos(),null));
        return true;
    }
    public Result select(ServerPlayer player,BlockPos pos){
        Session session=SESSIONS.get(player.getUUID());
        if(session==null)return Result.NO_SESSION;
        if(!canManage(player,session.town())){
            SESSIONS.remove(player.getUUID());return Result.NO_RIGHTS;
        }
        ServerLevel level=player.serverLevel();
        if(!session.dimension().equals(level.dimension().location().toString())){
            SESSIONS.remove(player.getUUID());return Result.WRONG_DIMENSION;
        }
        if(pos.distSqr(player.blockPosition())>64
                ||pos.distSqr(session.bank())>32*32){
            SESSIONS.remove(player.getUUID());return Result.TOO_FAR;
        }
        if(session.first()==null){
            SESSIONS.put(player.getUUID(),new Session(session.town(),session.dimension(),session.bank(),pos.immutable()));
            return Result.FIRST_CORNER;
        }
        // Keep the first point until the geometry and all real containers validate.
        // Invalid second clicks can be retried without losing the whole session.
        BlockPos p=session.first();
        BlockPos min=new BlockPos(Math.min(p.getX(),pos.getX()),
            Math.min(p.getY(),pos.getY()),Math.min(p.getZ(),pos.getZ()));
        BlockPos max=new BlockPos(Math.max(p.getX(),pos.getX()),
            Math.max(p.getY(),pos.getY()),Math.max(p.getZ(),pos.getZ()));
        Room proposed=new Room(session.town(),session.dimension(),min,max);
        if(proposed.contains(session.bank()))return Result.BANK_DESK_INSIDE;
        if(max.getX()-min.getX()+1>MAX_XZ || max.getZ()-min.getZ()+1>MAX_XZ
                ||max.getY()-min.getY()+1>MAX_Y
                ||max.getX()-min.getX()<2||max.getZ()-min.getZ()<2||max.getY()-min.getY()<2)
            return Result.TOO_LARGE;
        Room old=rooms.get(session.town());
        if(old!=null&&!proposed.contains(old))return Result.EXISTING_ROOM_NOT_INCLUDED;
        for(Room other:rooms.values())
            if(!other.town().equals(session.town())&&proposed.overlaps(other))return Result.OVERLAP;
        var oldBindings=KingdomAdministrationData.get(player.getServer()).containerBindings(
            session.town(),AdministrativeBindingRole.BANK_VAULT);
        if(oldBindings.size()>1)return Result.LEGACY_CHEST_OUTSIDE;
        if(oldBindings.size()==1 && (!oldBindings.get(0).dimension().equals(session.dimension())
                ||!proposed.contains(BlockPos.of(oldBindings.get(0).packedPos()))))
            return Result.LEGACY_CHEST_OUTSIDE;
        var civics=KingdomCivicsData.get(player.getServer());
        // Every cell must remain in the host city, not only the clicked corners.
        for(int x=min.getX();x<=max.getX();x++)
        for(int z=min.getZ();z<=max.getZ();z++){
            if(!session.town().equals(civics.townAt(level,new BlockPos(x,min.getY(),z))==null
                    ?null:civics.townAt(level,new BlockPos(x,min.getY(),z)).id()))
                return Result.FOREIGN_PLOT;
        }
        for(int x=min.getX();x<=max.getX();x+=16)
        for(int z=min.getZ();z<=max.getZ();z+=16)
            if(!level.hasChunkAt(new BlockPos(x,min.getY(),z)))return Result.UNLOADED;
        if(!level.hasChunkAt(max))return Result.UNLOADED;
        // Test with the candidate room before publishing its protected boundary.
        Scan inventory=scan(level,proposed);
        if(inventory.error()!=null)return inventory.error();
        if(conflictingBindings(level,proposed))return Result.INVALID_STORAGE;
        rooms.put(session.town(),proposed);setDirty();
        SESSIONS.remove(player.getUUID());
        return old==null?Result.CREATED:Result.EXPANDED;
    }

    /** Returns a complete physical vault, or null if any part is unsafe/unloaded. */
    public Container vault(ServerLevel level,UUID town){
        Room room=rooms.get(town);
        if(room==null||!room.dimension().equals(level.dimension().location().toString()))
            return null;
        Scan scan=scan(level,room);
        return scan.error()==null?scan.container():null;
    }
    public long availableCopper(ServerLevel level,UUID town){
        Container vault=vault(level,town);
        return vault==null?-1:KingdomPhysicalCash.value(vault);
    }
    /** No municipal marketplace or private business may reuse the bank vault's chests. */
    private static boolean conflictingBindings(ServerLevel level,Room room){
        var administration=KingdomAdministrationData.get(level.getServer());
        var civic=KingdomCivicsData.get(level.getServer());
        for(var city:civic.towns())for(var role:AdministrativeBindingRole.values()){
            if(role==AdministrativeBindingRole.BANK_VAULT && city.id().equals(room.town()))continue;
            for(var bound:administration.containerBindings(city.id(),role))
                if(room.dimension().equals(bound.dimension())&&room.contains(BlockPos.of(bound.packedPos())))
                    return true;
        }
        var firms=KingdomEnterpriseData.get(level.getServer());
        for(var business:firms.all())for(var role:EnterpriseBindingRole.values()){
            var bound=firms.storageBinding(business.id(),role);
            if(bound!=null&&room.dimension().equals(bound.dimension())&&room.contains(bound.pos()))
                return true;
        }
        return false;
    }
    private record Scan(Container container,Result error){}
    private static Scan scan(ServerLevel level,Room room){
        // Every room block must be loaded so unloaded money cannot be omitted.
        for(int x=room.min().getX();x<=room.max().getX();x++)
        for(int z=room.min().getZ();z<=room.max().getZ();z++)
            if(!level.hasChunkAt(new BlockPos(x,room.min().getY(),z)))
                return new Scan(null,Result.UNLOADED);
        List<Container> contents=new ArrayList<>();
        Set<BlockPos> seen=new HashSet<>();
        for(BlockPos pos:BlockPos.betweenClosed(room.min(),room.max())){
            if(seen.contains(pos))continue;
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof ChestBlock)
                    &&!(state.getBlock() instanceof BarrelBlock))continue;
            for(Direction direction:Direction.values()){
                BlockPos neighbor=pos.relative(direction);
                if(!level.hasChunkAt(neighbor))return new Scan(null,Result.UNLOADED);
                if(level.getBlockState(neighbor).getBlock() instanceof HopperBlock)
                    return new Scan(null,Result.AUTOMATION_NEARBY);
            }
            Container container=KingdomContainerAccess.resolve(level,pos);
            if(container==null)return new Scan(null,Result.INVALID_STORAGE);
            seen.add(pos.immutable());
            if(state.getBlock() instanceof ChestBlock){
                // A double chest must be wholly inside the protected room.
                if(state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                        &&container.getContainerSize()<54)
                    return new Scan(null,Result.INVALID_STORAGE);
                for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
                    BlockPos other=pos.relative(direction);
                    if(KingdomContainerAccess.samePhysicalStorage(level,pos,other)){
                        if(!room.contains(other))return new Scan(null,Result.INVALID_STORAGE);
                        seen.add(other.immutable());
                    }
                }
            }
            for(int i=0;i<container.getContainerSize();i++){
                ItemStack stack=container.getItem(i);
                if(!stack.isEmpty()&&KingdomCurrency.value(stack)<=0)
                    return new Scan(null,Result.INVALID_STORAGE);
            }
            contents.add(container);
            if(contents.size()>MAX_CONTAINERS)return new Scan(null,Result.TOO_MANY_CHESTS);
        }
        if(contents.isEmpty())return new Scan(null,Result.NO_CHESTS);
        return new Scan(new VaultContainer(contents),null);
    }

    private static final class VaultContainer implements Container {
        private final List<Container> chests;
        private final int size;
        VaultContainer(List<Container> chests){
            this.chests=List.copyOf(chests);
            int sum=0;for(Container chest:chests)sum+=chest.getContainerSize();
            size=sum;
        }
        private record Slot(Container chest,int index){}
        private Slot slot(int index){
            if(index<0||index>=size)throw new IndexOutOfBoundsException("Bank room slot: "+index);
            for(Container chest:chests){
                if(index<chest.getContainerSize())return new Slot(chest,index);
                index-=chest.getContainerSize();
            }
            throw new IndexOutOfBoundsException();
        }
        @Override public int getContainerSize(){return size;}
        @Override public int getMaxStackSize(){
            int max=64;for(Container c:chests)max=Math.min(max,c.getMaxStackSize());return max;
        }
        @Override public boolean isEmpty(){
            for(Container c:chests)if(!c.isEmpty())return false;return true;
        }
        @Override public ItemStack getItem(int index){
            Slot slot=slot(index);return slot.chest().getItem(slot.index());
        }
        @Override public ItemStack removeItem(int index,int amount){
            Slot slot=slot(index);return slot.chest().removeItem(slot.index(),amount);
        }
        @Override public ItemStack removeItemNoUpdate(int index){
            Slot slot=slot(index);return slot.chest().removeItemNoUpdate(slot.index());
        }
        @Override public void setItem(int index,ItemStack item){
            Slot slot=slot(index);slot.chest().setItem(slot.index(),item);
        }
        @Override public boolean canPlaceItem(int index,ItemStack item){
            Slot slot=slot(index);
            return (item.isEmpty()||KingdomCurrency.value(item)>0)
                && slot.chest().canPlaceItem(slot.index(),item);
        }
        @Override public boolean stillValid(Player player){return true;} // never exposed as clickable menu
        @Override public void clearContent(){throw new UnsupportedOperationException("Never clear a bank vault");}
        @Override public void setChanged(){for(Container c:chests)c.setChanged();}
    }

    private static KingdomBankVaultRooms load(CompoundTag root){
        var state=new KingdomBankVaultRooms();
        ListTag rows=root.getList("Rooms",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++){
            CompoundTag tag=rows.getCompound(i);
            if(!tag.hasUUID("Town"))continue;
            BlockPos min=BlockPos.of(tag.getLong("Min")),max=BlockPos.of(tag.getLong("Max"));
            if(min.getX()>max.getX()||min.getY()>max.getY()||min.getZ()>max.getZ()
                ||max.getX()-min.getX()>=MAX_XZ
                ||max.getZ()-min.getZ()>=MAX_XZ
                ||max.getY()-min.getY()>=MAX_Y)continue;
            UUID town=tag.getUUID("Town");
            state.rooms.put(town,new Room(town,tag.getString("Dimension"),min,max));
        }
        return state;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag rows=new ListTag();
        for(Room r:rooms.values()){
            CompoundTag tag=new CompoundTag();
            tag.putUUID("Town",r.town());tag.putString("Dimension",r.dimension());
            tag.putLong("Min",r.min().asLong());tag.putLong("Max",r.max().asLong());rows.add(tag);
        }
        root.put("Rooms",rows);return root;
    }
}
