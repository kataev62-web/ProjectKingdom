package ru.projectkingdom.social;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomNpcData;

/**
 * Read-only player-book projection. The server computes this once per book opening.
 * No client-controlled rank, title, funds, award or town ownership is accepted.
 */
public record KingdomSocialBookSnapshot(int rank,int cityReputation,String city,
                                         int enterprises,int hiredWorkers,int ownedPlots,
                                         String civicHonor,String nationalHonor,
                                         boolean founder,boolean municipalOfficial) {
    public static KingdomSocialBookSnapshot empty() {
        return new KingdomSocialBookSnapshot(1,0,"Нет гражданства",0,0,0,"Нет","Нет",false,false);
    }
    public static KingdomSocialBookSnapshot of(ServerPlayer player) {
        if(player==null)return empty();
        var server=player.getServer();
        UUID person=player.getUUID();
        var civic=KingdomCivicsData.get(server);
        var rank=KingdomSocialData.get(server).rank(person);
        var town=civic.townOf(person);
        int firms=KingdomEnterpriseData.get(server).ownedAll(person).size();
        int hired=(int)Math.min(Integer.MAX_VALUE,KingdomNpcData.get(server).all().stream()
                .filter(w->person.equals(w.employer)).count());
        int plots=(int)Math.min(Integer.MAX_VALUE,civic.purchasedPlotOwnerCounts().getOrDefault(person,0L));
        if(town==null)return new KingdomSocialBookSnapshot(rank.level(),0,"Вне поселения",
                firms,hired,plots,"Нет","Нет",false,false);
        int reputation=KingdomReputationData.get(server).score(town.id(),person);
        var honor=KingdomCivicHonorsData.get(server).best(town.id(),person);
        var nation=civic.nationOf(person);
        String realmHonor=nation!=null&&KingdomNationalHonorsData.get(server).award(nation.id(),person)!=null
                ?"Орден королевства":"Нет";
        var administration=ru.projectkingdom.administration.KingdomAdministrationData.get(server);
        boolean founder=person.equals(administration.founder(town.id()));
        boolean mayor=person.equals(town.mayor());
        boolean officer=!administration.roles(town.id(),person).isEmpty();
        return new KingdomSocialBookSnapshot(rank.level(),reputation,town.name(),firms,hired,plots,
                honor==null?"Нет":honor.russian(),realmHonor,founder,mayor||officer);
    }
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(rank);buf.writeVarInt(cityReputation);buf.writeUtf(city,64);
        buf.writeVarInt(enterprises);buf.writeVarInt(hiredWorkers);buf.writeVarInt(ownedPlots);
        buf.writeUtf(civicHonor,64);buf.writeUtf(nationalHonor,64);
        buf.writeBoolean(founder);buf.writeBoolean(municipalOfficial);
    }
    public static KingdomSocialBookSnapshot read(FriendlyByteBuf buf) {
        return new KingdomSocialBookSnapshot(
                Math.max(1,Math.min(9,buf.readVarInt())),
                Math.max(0,Math.min(200,buf.readVarInt())),buf.readUtf(64),
                Math.max(0,buf.readVarInt()),Math.max(0,buf.readVarInt()),Math.max(0,buf.readVarInt()),
                buf.readUtf(64),buf.readUtf(64),buf.readBoolean(),buf.readBoolean());
    }
    public String rankName(){return KingdomSocialRank.fromLevel(rank).displayName();}
    public String standing(){return KingdomReputationPolicy.standing(cityReputation);}
}
