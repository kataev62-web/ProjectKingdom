package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import ru.projectkingdom.administration.KingdomAdministrationData;

/**
 * Public municipal land-sale reservations. Reserved land is NOT yet owned:
 * ownership transfers only at the local town hall after real cash is deposited.
 * All recovery-uncertain payments stay locked for manual review, never double-charge.
 */
public final class KingdomLandReservations extends SavedData {
    private static final String KEY="kingdom_land_reservations_v1";
    public static final long DURATION_MS=24L*60*60*1000;
    public enum State { RESERVED, SETTLING, PAID, NEEDS_REVIEW }
    public enum Result { BOUGHT, NOT_RESERVED, WRONG_TOWN, EXPIRED, CHANGED_OFFER,
        NO_VAULT, NO_CASH_OR_ROOM, NEEDS_REVIEW }
    public record Reservation(UUID id,UUID buyer,String buyerName,UUID townId,
                              String dimension,int x,int z,long price,long until,State state) {}
    private final Map<UUID,Reservation> entries=new LinkedHashMap<>();
    public static KingdomLandReservations get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(KingdomLandReservations::load,KingdomLandReservations::new,KEY);
    }
    private static String key(String dimension,int x,int z) {return dimension+"|"+x+"|"+z;}
    public Collection<Reservation> all() {return List.copyOf(entries.values());}
    public List<Reservation> reservedForTown(UUID townId,long now) {
        var result=new ArrayList<Reservation>();
        for(var value:entries.values())if(value.townId().equals(townId)
                && (value.state()==State.RESERVED&&value.until()>now
                    ||value.state()==State.SETTLING||value.state()==State.NEEDS_REVIEW))
            result.add(value);
        return List.copyOf(result);
    }
    public Reservation buyerReservation(UUID townId,UUID buyer,long now) {
        for(var res:entries.values())
            if(res.townId().equals(townId)&&res.buyer().equals(buyer)
                    &&res.state()==State.RESERVED&&res.until()>now)return res;
        return null;
    }
    public boolean reserve(ServerPlayer actor,String dimension,int x,int z) {
        if(actor==null||dimension==null||!dimension.equals(actor.level().dimension().location().toString()))
            return false;
        var civics=KingdomCivicsData.get(actor.getServer());
        var plot=civics.plot(actor.serverLevel(),new net.minecraft.core.BlockPos(x<<4,64,z<<4));
        if(plot==null)return false;
        var town=civics.townById(plot.town());
        var offer=civics.saleOffer(dimension,x,z);
        // Government must explicitly have listed this parcel. Never auto-sell
        // privately titled lots, public infrastructure, or sovereign capital land.
        if(town==null||offer==null||!offer.seller().equals(town.mayor())
                ||!civics.canBuySale(actor.getUUID(),dimension,x,z))return false;
        long now=System.currentTimeMillis();
        for(var value:entries.values())if(value.dimension().equals(dimension)
                &&value.x()==x&&value.z()==z&&value.state()!=State.PAID
                &&(value.state()!=State.RESERVED||value.until()>now))return false;
        long expiry=now+DURATION_MS;
        var res=new Reservation(UUID.randomUUID(),actor.getUUID(),actor.getScoreboardName(),
                town.id(),dimension,x,z,offer.priceCopper(),expiry,State.RESERVED);
        entries.put(res.id(),res);setDirty();
        var mayor=actor.getServer().getPlayerList().getPlayer(town.mayor());
        if(mayor!=null)mayor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[Ратуша] Забронирована земля "+x+", "+z+" игроком "+res.buyerName()+
                ". Покупатель должен оплатить "+res.price()+" медных в вашей ратуше."));
        return true;
    }
    private void mark(Reservation original,State state){
        entries.put(original.id(),new Reservation(original.id(),original.buyer(),original.buyerName(),
            original.townId(),original.dimension(),original.x(),original.z(),
            original.price(),original.until(),state));
        setDirty();
    }
    public Result payAtHall(ServerPlayer buyer,UUID townId) {
        if(buyer==null||townId==null)return Result.WRONG_TOWN;
        long now=System.currentTimeMillis();
        Reservation res=buyerReservation(townId,buyer.getUUID(),now);
        if(res==null)return Result.NOT_RESERVED;
        if(res.until()<=now)return Result.EXPIRED;
        var civics=KingdomCivicsData.get(buyer.getServer());
        var plot=civics.plot(buyer.serverLevel(),new net.minecraft.core.BlockPos(res.x()<<4,64,res.z()<<4));
        var town=civics.townById(townId);
        if(plot==null||town==null||!plot.town().equals(townId)
                ||!buyer.serverLevel().dimension().location().toString().equals(res.dimension()))
            return Result.WRONG_TOWN;
        var offer=civics.saleOffer(res.dimension(),res.x(),res.z());
        if(offer==null||!offer.seller().equals(town.mayor())||offer.priceCopper()!=res.price()
                ||!civics.canBuySale(buyer.getUUID(),res.dimension(),res.x(),res.z()))
            return Result.CHANGED_OFFER;
        var admin=KingdomAdministrationData.get(buyer.getServer());
        Container vault=KingdomPhysicalCash.townVault(buyer,townId,AdministrativeBindingRole.TREASURY_VAULT);
        if(vault==null)return Result.NO_VAULT;
        if(res.price()<1||res.price()>1_000_000
                ||town.treasury()>Long.MAX_VALUE-res.price()
                ||admin.treasuryReserve(townId)>Long.MAX_VALUE-res.price())
            return Result.NO_CASH_OR_ROOM;
        // Persist a review-required intent before touching the cash or title.
        mark(res,State.SETTLING);
        if(!KingdomPhysicalCash.deposit(buyer,vault,res.price())){
            mark(res,State.RESERVED);
            return Result.NO_CASH_OR_ROOM;
        }
        if(!civics.completeSale(buyer.getUUID(),res.dimension(),res.x(),res.z())){
            // Fail closed: funds may already be durable. Never silently retry.
            mark(res,State.NEEDS_REVIEW);
            return Result.NEEDS_REVIEW;
        }
        if(!civics.creditTownLandRevenue(townId,res.price())){
            mark(res,State.NEEDS_REVIEW);
            return Result.NEEDS_REVIEW;
        }
        mark(res,State.PAID);
        return Result.BOUGHT;
    }
    private static KingdomLandReservations load(CompoundTag root){
        var data=new KingdomLandReservations();
        ListTag rows=root.getList("Reservations",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++){
            var tag=rows.getCompound(i);
            if(!tag.hasUUID("Id")||!tag.hasUUID("Buyer")||!tag.hasUUID("Town"))continue;
            State state;
            try{state=State.valueOf(tag.getString("State"));}catch(IllegalArgumentException ex){continue;}
            var res=new Reservation(tag.getUUID("Id"),tag.getUUID("Buyer"),tag.getString("Name"),
                tag.getUUID("Town"),tag.getString("Dimension"),tag.getInt("X"),tag.getInt("Z"),
                tag.getLong("Price"),tag.getLong("Until"),state);
            data.entries.put(res.id(),res);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        var rows=new ListTag();
        for(var res:entries.values()){
            if(res.state()==State.PAID)continue;
            var tag=new CompoundTag();
            tag.putUUID("Id",res.id());tag.putUUID("Buyer",res.buyer());
            tag.putString("Name",res.buyerName());tag.putUUID("Town",res.townId());
            tag.putString("Dimension",res.dimension());tag.putInt("X",res.x());tag.putInt("Z",res.z());
            tag.putLong("Price",res.price());tag.putLong("Until",res.until());
            tag.putString("State",res.state().name());rows.add(tag);
        }
        root.put("Reservations",rows);return root;
    }
}
