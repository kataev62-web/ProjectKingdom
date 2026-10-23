package ru.projectkingdom.social;

import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.administration.MunicipalRole;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomNpcData;

/**
 * Server-authoritative read-only portrait on the FIRST spread of the player book.
 * Economic rank, legal title and municipal office are three different facts.
 * Recomputed when opening the book; never grants permissions to the client.
 */
public record KingdomSocialBookSnapshot(int rank,int cityReputation,String city,
                                         int enterprises,int hiredWorkers,int ownedPlots,
                                         String civicHonor,String nationalHonor,
                                         boolean founder,boolean municipalOfficial,
                                         String nation,String office,String legalTitle) {
    public KingdomSocialBookSnapshot(int rank,int cityReputation,String city,
                                     int enterprises,int hiredWorkers,int ownedPlots,
                                     String civicHonor,String nationalHonor,
                                     boolean founder,boolean municipalOfficial) {
        this(rank,cityReputation,city,enterprises,hiredWorkers,ownedPlots,civicHonor,
                nationalHonor,founder,municipalOfficial,"Нет","Нет","Нет");
    }

    public static KingdomSocialBookSnapshot empty() {
        return new KingdomSocialBookSnapshot(1,0,"Нет гражданства",0,0,0,
                "Нет","Нет",false,false,"Нет","Нет","Нет");
    }
    public static KingdomSocialBookSnapshot of(ServerPlayer player) {
        if(player==null)return empty();
        var server=player.getServer();
        UUID person=player.getUUID();
        var civics=KingdomCivicsData.get(server);
        var rank=KingdomSocialData.get(server).rank(person);
        var town=civics.townOf(person);
        int firms=KingdomEnterpriseData.get(server).ownedAll(person).size();
        int hired=(int)Math.min(Integer.MAX_VALUE,KingdomNpcData.get(server).all().stream()
                .filter(w->person.equals(w.employer)).count());
        int plots=(int)Math.min(Integer.MAX_VALUE,
                civics.purchasedPlotOwnerCounts().getOrDefault(person,0L));
        if(town==null)return new KingdomSocialBookSnapshot(rank.level(),0,"Вне поселения",
                firms,hired,plots,"Нет","Нет",false,false,"Нет","Нет","Нет");

        var administration=KingdomAdministrationData.get(server);
        boolean founder=person.equals(administration.founder(town.id()));
        boolean mayor=person.equals(town.mayor());
        Set<MunicipalRole> roles=administration.roles(town.id(),person);
        var nation=civics.nationOf(person);
        boolean ruler=nation!=null&&person.equals(nation.ruler());
        int reputation=KingdomReputationData.get(server).score(town.id(),person);
        var honor=KingdomCivicHonorsData.get(server).best(town.id(),person);
        String realmHonor=nation!=null
                &&KingdomNationalHonorsData.get(server).award(nation.id(),person)!=null
                ?"Орден королевства":"Нет";
        return new KingdomSocialBookSnapshot(rank.level(),reputation,town.name(),firms,hired,plots,
                honor==null?"Нет":honor.russian(),realmHonor,founder,mayor||hasMunicipalOffice(roles),
                nation==null?"Нет государства":nation.name(),
                officeNames(mayor,roles),ruler?"Правитель государства":founder?"Основатель поселения":"Нет");
    }

    /** Display role names come from the live town roster, not social rank or cached status. */
    public static String officeNames(boolean mayor,Set<MunicipalRole> roles) {
        StringJoiner names=new StringJoiner(", ");
        if(mayor)names.add("Мэр");
        if(roles!=null)for(MunicipalRole role:MunicipalRole.values()) {
            if(role==MunicipalRole.MAYOR||!roles.contains(role))continue;
            names.add(switch(role){
                case TREASURER->"Казначей";
                case LAND_MANAGER->"Земельный управляющий";
                case JUDGE->"Судья";
                case GUARD_CHIEF->"Начальник стражи";
                case WAREHOUSE_MANAGER->"Управляющий складом";
                case MAYOR->throw new IllegalStateException("Handled above");
            });
        }
        String value=names.toString();
        return value.isBlank()?"Нет":value;
    }
    private static boolean hasMunicipalOffice(Set<MunicipalRole> roles) {
        return roles!=null&&roles.stream().anyMatch(r->r!=MunicipalRole.MAYOR);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(rank);buf.writeVarInt(cityReputation);buf.writeUtf(city,64);
        buf.writeVarInt(enterprises);buf.writeVarInt(hiredWorkers);buf.writeVarInt(ownedPlots);
        buf.writeUtf(civicHonor,64);buf.writeUtf(nationalHonor,64);
        buf.writeBoolean(founder);buf.writeBoolean(municipalOfficial);
        buf.writeUtf(nation,64);buf.writeUtf(office,192);buf.writeUtf(legalTitle,64);
    }
    public static KingdomSocialBookSnapshot read(FriendlyByteBuf buf) {
        return new KingdomSocialBookSnapshot(
                Math.max(1,Math.min(9,buf.readVarInt())),
                Math.max(0,Math.min(200,buf.readVarInt())),buf.readUtf(64),
                Math.max(0,buf.readVarInt()),Math.max(0,buf.readVarInt()),
                Math.max(0,buf.readVarInt()),buf.readUtf(64),buf.readUtf(64),
                buf.readBoolean(),buf.readBoolean(),
                buf.readUtf(64),buf.readUtf(192),buf.readUtf(64));
    }
    /** The first page shows the primary role; the full office list remains in server snapshot. */
    public String officeSummary(){
        if(office==null||office.isBlank())return "Нет";
        String[] active=office.split(", ");
        return active.length>1?active[0]+" (ещё "+(active.length-1)+")":office;
    }
    public String rankName(){return KingdomSocialRank.fromLevel(rank).displayName();}
    public String standing(){return KingdomReputationPolicy.standing(cityReputation);}
}
