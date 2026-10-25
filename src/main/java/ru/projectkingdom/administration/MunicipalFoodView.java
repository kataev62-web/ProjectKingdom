package ru.projectkingdom.administration;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.*;

/**
 * Limited, read-only snapshot for the parchment administration menu.
 * Never trusts client-side values for money, stock or privileges.
 */
public record MunicipalFoodView(boolean configured,boolean complete,boolean canConfigure,
                                int residents,long day,int coverage,int edible,int rotten,
                                long target,int missing,List<Integer> groups,
                                List<Offer> offers,List<String> shipments,String notice) {
    public record Offer(String itemId,long unitPrice) {}
    public MunicipalFoodView {
        groups=List.copyOf(groups);offers=List.copyOf(offers);shipments=List.copyOf(shipments);
        if(offers.size()>5||shipments.size()>4||groups.size()>5
                ||groups.size()!=5||notice.length()>110)throw new IllegalArgumentException("food menu bounds");
    }
    public static MunicipalFoodView empty() {
        return new MunicipalFoodView(false,false,false,0,-1,0,0,0,0,0,
                List.of(0,0,0,0,0),List.of(),List.of(),"");
    }
    public static MunicipalFoodView snapshot(ServerPlayer player,UUID town,String notice) {
        var civics=KingdomCivicsData.get(player.getServer());
        var city=civics.townById(town);
        if(city==null||!civics.isMember(town,player.getUUID()))return empty();
        var admin=KingdomAdministrationData.get(player.getServer());
        boolean edit=AdministrativePermissionService.mayManage(
                player.getUUID(),admin.founder(town),city.mayor(),
                admin.roles(town,player.getUUID()),AdministrativeKind.CENTRAL_WAREHOUSE);
        var reserve=KingdomMunicipalFoodReserves.get(player.getServer());
        var policy=reserve.policy(town);
        var forecast=policy==null?null:reserve.evaluate(player.getServer(),town);
        var market=KingdomMunicipalProcurement.availableFoods(player.serverLevel(),town,5);
        List<Offer> offers=new ArrayList<>();
        for(String item:market) {
            var p=KingdomMunicipalProcurement.select(player.serverLevel(),town,item,1,town);
            if(p!=null)offers.add(new Offer(item,p.price()));
        }
        List<String> shipments=new ArrayList<>();
        var orders=KingdomSupplyOrders.get(player.getServer());
        for(var receipt:KingdomMunicipalProcurementData.get(player.getServer()).all()) {
            if(!town.equals(receipt.town()))continue;
            var order=receipt.deliveryId()==null?null:orders.find(receipt.deliveryId());
            String state=order==null?receipt.phase().name()
                :order.state.name()+" "+order.delivered+"/"+order.count;
            String text=receipt.item()+" ×"+receipt.amount()+" — "+state;
            if(text.length()>90)text=text.substring(0,90);
            shipments.add(text);
        }
        if(shipments.size()>4)shipments=new ArrayList<>(
                shipments.subList(shipments.size()-4,shipments.size()));
        return new MunicipalFoodView(policy!=null,forecast!=null&&forecast.complete(),
                edit,policy==null?0:policy.residents(),
                forecast==null?-1:forecast.day(),
                forecast==null?0:forecast.daysToCover(),
                forecast==null?0:forecast.edible(),forecast==null?0:forecast.rotten(),
                forecast==null?0:forecast.target(),forecast==null?0:forecast.missing(),
                forecast==null?List.of(0,0,0,0,0):forecast.groups(),
                offers,shipments,notice==null?"":notice.substring(0,Math.min(110,notice.length())));
    }
    public static void write(FriendlyByteBuf b,MunicipalFoodView v){
        b.writeBoolean(v.configured);b.writeBoolean(v.complete);b.writeBoolean(v.canConfigure);
        b.writeVarInt(v.residents);b.writeLong(v.day);b.writeVarInt(v.coverage);
        b.writeVarInt(v.edible);b.writeVarInt(v.rotten);b.writeLong(v.target);
        b.writeVarInt(v.missing);
        for(int i=0;i<5;i++)b.writeVarInt(v.groups.get(i));
        b.writeVarInt(v.offers.size());
        for(var offer:v.offers){b.writeUtf(offer.itemId(),128);b.writeLong(offer.unitPrice());}
        b.writeVarInt(v.shipments.size());
        for(String row:v.shipments)b.writeUtf(row,96);
        b.writeUtf(v.notice,110);
    }
    public static MunicipalFoodView read(FriendlyByteBuf b){
        boolean configured=b.readBoolean(),complete=b.readBoolean(),canConfigure=b.readBoolean();
        int residents=b.readVarInt();long day=b.readLong();int coverage=b.readVarInt();
        int edible=b.readVarInt(),rotten=b.readVarInt();long target=b.readLong();int missing=b.readVarInt();
        List<Integer> groups=new ArrayList<>();
        for(int i=0;i<5;i++)groups.add(b.readVarInt());
        int count=b.readVarInt();if(count<0||count>5)throw new IllegalArgumentException("offers");
        List<Offer> offers=new ArrayList<>();
        for(int i=0;i<count;i++)offers.add(new Offer(b.readUtf(128),b.readLong()));
        int shipments=b.readVarInt();if(shipments<0||shipments>4)throw new IllegalArgumentException("shipments");
        List<String> orders=new ArrayList<>();
        for(int i=0;i<shipments;i++)orders.add(b.readUtf(96));
        return new MunicipalFoodView(configured,complete,canConfigure,residents,day,
                coverage,edible,rotten,target,missing,groups,offers,orders,b.readUtf(110));
    }
}
