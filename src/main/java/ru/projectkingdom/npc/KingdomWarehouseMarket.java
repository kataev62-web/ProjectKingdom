package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import ru.projectkingdom.administration.AdministrativeCoreBlockEntity;
import ru.projectkingdom.administration.AdministrativeCoreBinding;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;

/** Registered seller inventories; quotations always read actual loaded containers. */
public final class KingdomWarehouseMarket extends SavedData {
    private static final String KEY = "kingdom_warehouse_market_v1";
    public record Stall(UUID owner, String dimension, BlockPos pos, long copperPerItem) {}
    public record Quote(Stall stall, int available, String itemId, long totalCopper) {}
    public record InventorySnapshot(String dimension, BlockPos pos, Map<String,Integer> items) {
        public InventorySnapshot { items=Map.copyOf(items); }
    }
    private final Map<String, Stall> stalls = new LinkedHashMap<>();
    private final Map<String, UUID> desks = new LinkedHashMap<>();
    private static String key(String dim, BlockPos pos) { return dim + ":" + pos.asLong(); }
    public static KingdomWarehouseMarket get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KingdomWarehouseMarket::load, KingdomWarehouseMarket::new, KEY);
    }
    public boolean register(ServerLevel level, BlockPos pos, UUID owner, long price) {
        if(owner==null || price<1 || price>100000 || !level.hasChunkAt(pos))return false;
        if(!(level.getBlockEntity(pos) instanceof Container))return false;
        // A seller may publish only a container registered on their claimed land.
        KingdomTerritories territory=KingdomTerritories.get(level.getServer());
        if(!territory.canUse(level,pos,owner))return false;
        String dim=level.dimension().location().toString();
        Stall old=stalls.get(key(dim,pos));
        if(old!=null && !old.owner().equals(owner))return false;
        stalls.put(key(dim,pos),new Stall(owner,dim,pos.immutable(),price));setDirty();return true;
    }
    public boolean remove(ServerLevel level,BlockPos pos,UUID owner) {
        String k=key(level.dimension().location().toString(),pos);
        Stall old=stalls.get(k);
        if(old==null || !old.owner().equals(owner))return false;
        stalls.remove(k);setDirty();return true;
    }
    /** Register a physical logistics counter (barrel/chest) on land the operator may use. */
    public boolean registerDesk(ServerLevel level, BlockPos pos, UUID owner) {
        if(owner==null || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Container))return false;
        if(!KingdomTerritories.get(level.getServer()).canUse(level,pos,owner))return false;
        String id=key(level.dimension().location().toString(),pos);
        UUID previous=desks.get(id);
        if(previous!=null && !previous.equals(owner))return false;
        desks.put(id,owner);setDirty();return true;
    }
    /** Checkout is possible only at a registered, loaded physical logistics counter. */
    /** Find a real, loaded counter without force-loading chunks. */
    public BlockPos nearestDesk(ServerLevel level, BlockPos from, int maxDistance) {
        String dim=level.dimension().location().toString();
        BlockPos best=null;
        double bestDist=(double)maxDistance*maxDistance;
        for(String id:desks.keySet()) {
            if(!id.startsWith(dim+":"))continue;
            try {
                BlockPos pos=BlockPos.of(Long.parseLong(id.substring(dim.length()+1)));
                double distance=pos.distSqr(from);
                if(distance>bestDist || !level.hasChunkAt(pos)
                        || !(level.getBlockEntity(pos) instanceof Container))continue;
                best=pos;bestDist=distance;
            } catch(NumberFormatException ignored) {}
        }
        return best;
    }
    public boolean nearDesk(ServerLevel level, BlockPos playerPos) {
        String dim=level.dimension().location().toString();
        for(var entry:desks.entrySet()) {
            String id=entry.getKey();
            if(!id.startsWith(dim+":"))continue;
            long packed;
            try { packed=Long.parseLong(id.substring(dim.length()+1)); }
            catch(NumberFormatException e) {continue;}
            BlockPos pos=BlockPos.of(packed);
            if(pos.distSqr(playerPos)>64 || !level.hasChunkAt(pos))continue;
            if(level.getBlockEntity(pos) instanceof Container)return true;
        }
        return false;
    }
    /** A physical counter can accept goods into an adjacent buyer-owned container. */
    public BlockPos deliveryChest(ServerLevel level, BlockPos operator, UUID buyer) {
        String dim=level.dimension().location().toString();
        for(var entry:desks.entrySet()) {
            String key=entry.getKey();
            if(!key.startsWith(dim+":"))continue;
            BlockPos desk;
            try {desk=BlockPos.of(Long.parseLong(key.substring(dim.length()+1)));}
            catch(NumberFormatException ex){continue;}
            if(desk.distSqr(operator)>64 || !level.hasChunkAt(desk) || !(level.getBlockEntity(desk) instanceof Container))continue;
            for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos pos=desk.relative(direction);
                if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Container))continue;
                if(KingdomTerritories.get(level.getServer()).canUse(level,pos,buyer))return pos;
            }
        }
        return null;
    }
    public boolean isDesk(ServerLevel level,BlockPos pos) {
        return desks.containsKey(key(level.dimension().location().toString(),pos))
            && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Container;
    }
    public Collection<Stall> all() {return Collections.unmodifiableCollection(stalls.values());}
    /** Reads municipal stock only from containers that are loaded and still inside the owning town. */
    public List<InventorySnapshot> municipalInventory(ServerLevel level,UUID townId,AdministrativeBindingRole role) {
        if(level==null||townId==null||role==null
                ||(role!=AdministrativeBindingRole.WAREHOUSE_STORAGE&&role!=AdministrativeBindingRole.MARKET_STORAGE)
                ||!activeMunicipalCore(level,townId,role.kind()))return List.of();
        String dimension=level.dimension().location().toString();
        KingdomAdministrationData administration=KingdomAdministrationData.get(level.getServer());
        KingdomCivicsData civics=KingdomCivicsData.get(level.getServer());
        List<InventorySnapshot> snapshots=new ArrayList<>();
        for(KingdomAdministrationData.BoundContainer binding:administration.boundContainers(townId,role)) {
            if(!dimension.equals(binding.dimension()))continue;
            BlockPos pos=BlockPos.of(binding.packedPos());
            if(!level.hasChunkAt(pos))continue;
            var currentTown=civics.townAt(level,pos);
            if(currentTown==null||!townId.equals(currentTown.id()))continue;
            BlockEntity blockEntity=level.getBlockEntity(pos);
            if(!(blockEntity instanceof Container container))continue;
            Map<String,Integer> items=new LinkedHashMap<>();
            for(int slot=0;slot<container.getContainerSize();slot++) {
                ItemStack stack=container.getItem(slot);if(stack.isEmpty())continue;
                String itemId=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                items.merge(itemId,stack.getCount(),Integer::sum);
            }
            snapshots.add(new InventorySnapshot(dimension,pos.immutable(),items));
        }
        return List.copyOf(snapshots);
    }
    public List<Quote> cityMarketSearch(ServerLevel level,UUID townId,String itemId,int amount) {
        if(!activeMunicipalCore(level,townId,AdministrativeKind.CITY_MARKET))return List.of();
        return search(level,itemId,amount);
    }
    public UUID nearbyCityMarketTown(ServerLevel level,BlockPos from,int maxDistance){
        UUID selected=null;double best=(double)maxDistance*maxDistance;KingdomAdministrationData administration=KingdomAdministrationData.get(level.getServer());
        for(var town:KingdomCivicsData.get(level.getServer()).towns()){
            AdministrativeCoreBinding binding=administration.core(town.id(),AdministrativeKind.CITY_MARKET);
            if(binding==null||!binding.dimension().equals(level.dimension().location().toString()))continue;BlockPos pos=BlockPos.of(binding.packedPos());double distance=pos.distSqr(from);
            if(distance<=best&&activeMunicipalCore(level,town.id(),AdministrativeKind.CITY_MARKET)){selected=town.id();best=distance;}
        }return selected;
    }
    public BlockPos cityMarketDeliveryChest(ServerLevel level,BlockPos operator,UUID buyer){
        UUID townId=nearbyCityMarketTown(level,operator,8);if(townId==null)return null;
        AdministrativeCoreBinding binding=KingdomAdministrationData.get(level.getServer()).core(townId,AdministrativeKind.CITY_MARKET);BlockPos core=BlockPos.of(binding.packedPos());
        for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.values()){
            BlockPos pos=core.relative(direction);if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof Container))continue;
            if(KingdomTerritories.get(level.getServer()).canUse(level,pos,buyer))return pos;
        }return null;
    }
    public boolean activeMunicipalCore(ServerLevel level,UUID townId,AdministrativeKind kind) {
        if(level==null||townId==null||kind==null)return false;
        AdministrativeCoreBinding binding=KingdomAdministrationData.get(level.getServer()).core(townId,kind);
        if(binding==null||!binding.dimension().equals(level.dimension().location().toString()))return false;
        BlockPos pos=BlockPos.of(binding.packedPos());
        if(!level.hasChunkAt(pos))return false;
        if(!(level.getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core))return false;
        return core.bound()&&townId.equals(core.townId())&&kind==core.kind();
    }
    /** Secure checkout: physical wallet is debited once; goods remain in seller chest.
     * Seller settlement and courier assignment are separate required operations.
     */
    public KingdomSupplyOrders.Order checkout(net.minecraft.server.level.ServerPlayer buyer,
            String itemId,int amount,int quoteIndex,BlockPos destination,long deliveryFee) {
        if(amount<1||amount>2304||quoteIndex<0||deliveryFee<0||deliveryFee>1000000)return null;
        ServerLevel level=buyer.serverLevel();
        UUID marketTown=nearbyCityMarketTown(level,buyer.blockPosition(),8);if(marketTown==null)return null;
        if(!level.hasChunkAt(destination) || !(level.getBlockEntity(destination) instanceof Container))return null;
        if(!KingdomTerritories.get(buyer.getServer()).canUse(level,destination,buyer.getUUID()))return null;
        List<Quote> quotes=cityMarketSearch(level,marketTown,itemId,amount);
        if(quoteIndex>=quotes.size())return null;
        Quote quote=quotes.get(quoteIndex);
        if(quote.stall().pos().equals(destination))return null;
        long goods=quote.totalCopper();
        if(goods<1 || goods>1000000 || goods>Long.MAX_VALUE-deliveryFee)return null;
        long total=goods+deliveryFee;
        if(KingdomCurrency.balance(buyer)<total)return null;
        KingdomSupplyOrders orders=KingdomSupplyOrders.get(buyer.getServer());
        // Re-check stock and reservations on the server thread immediately before debit.
        if(cityMarketSearch(level,marketTown,itemId,amount).stream().noneMatch(q -> q.stall().equals(quote.stall()) && q.available()>=amount && q.totalCopper()==goods))return null;
        if(!KingdomCurrency.debit(buyer,total))return null;
        KingdomSupplyOrders.Order order=orders.create(buyer.getUUID(),quote.stall().owner(),
                level.dimension().location().toString(),itemId,quote.stall().pos(),destination,
                amount,goods,deliveryFee,KingdomSupplyOrders.Mode.COURIER);
        if(order==null || !orders.escrow(order.id,goods,deliveryFee)) {
            if(!KingdomCurrency.credit(buyer,total))throw new IllegalStateException("Cash rollback failed");
            return null;
        }
        buyer.getInventory().setChanged();return order;
    }
    public List<Quote> search(ServerLevel level,String itemId,int amount) {
        ResourceLocation id=ResourceLocation.tryParse(itemId);
        if(id==null || !BuiltInRegistries.ITEM.containsKey(id) || amount<1 || amount>2304)return List.of();
        List<Quote> results=new ArrayList<>();String dim=level.dimension().location().toString();
        for(Stall stall:stalls.values()) {
            if(!stall.dimension().equals(dim)||!level.hasChunkAt(stall.pos()))continue;
            BlockEntity be=level.getBlockEntity(stall.pos());
            if(!(be instanceof Container container))continue;
            int stock=0;
            for(int i=0;i<container.getContainerSize();i++) {
                ItemStack stack=container.getItem(i);
                if(!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id))stock+=stack.getCount();
            }
            int reserved=KingdomSupplyOrders.get(level.getServer()).reserved(dim,stall.pos(),itemId);
            int available=Math.max(0,stock-reserved);
            if(available>=amount)results.add(new Quote(stall,available,itemId,stall.copperPerItem()*amount));
        }
        results.sort(Comparator.comparingLong(Quote::totalCopper));return results;
    }
    private static KingdomWarehouseMarket load(CompoundTag root) {
        KingdomWarehouseMarket market=new KingdomWarehouseMarket();
        ListTag list=root.getList("Stalls",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            CompoundTag t=list.getCompound(i);
            if(!t.hasUUID("Owner"))continue;
            long price=t.getLong("Price");if(price<1 || price>100000)continue;
            String dim=t.getString("Dimension");if(dim.isBlank())continue;
            BlockPos pos=new BlockPos(t.getInt("X"),t.getInt("Y"),t.getInt("Z"));
            Stall s=new Stall(t.getUUID("Owner"),dim,pos,price);market.stalls.put(key(dim,pos),s);
        }
        ListTag deskList=root.getList("Desks",Tag.TAG_COMPOUND);
        for(int i=0;i<deskList.size();i++) {
            CompoundTag t=deskList.getCompound(i);
            if(t.hasUUID("Owner") && !t.getString("Dimension").isBlank())
                market.desks.put(key(t.getString("Dimension"),new BlockPos(t.getInt("X"),t.getInt("Y"),t.getInt("Z"))),t.getUUID("Owner"));
        }
        return market;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list=new ListTag();
        for(Stall s:stalls.values()) {
            CompoundTag t=new CompoundTag();t.putUUID("Owner",s.owner());t.putString("Dimension",s.dimension());
            t.putInt("X",s.pos().getX());t.putInt("Y",s.pos().getY());t.putInt("Z",s.pos().getZ());
            t.putLong("Price",s.copperPerItem());list.add(t);
        }root.put("Stalls",list);
        ListTag deskList=new ListTag();
        for(var entry:desks.entrySet()) {
            int split=entry.getKey().lastIndexOf(':');
            if(split<0)continue;
            try {
                BlockPos pos=BlockPos.of(Long.parseLong(entry.getKey().substring(split+1)));
                CompoundTag t=new CompoundTag();t.putUUID("Owner",entry.getValue());
                t.putString("Dimension",entry.getKey().substring(0,split));
                t.putInt("X",pos.getX());t.putInt("Y",pos.getY());t.putInt("Z",pos.getZ());deskList.add(t);
            }catch(NumberFormatException ignored){}
        }
        root.put("Desks",deskList);return root;
    }
}
