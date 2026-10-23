package ru.projectkingdom.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.npc.KingdomCivicsData;

/** Municipal awards and citizen petitions. All authority is validated at the server boundary. */
public final class KingdomCivicService {
    public record Snapshot(int reputation,int socialLevel,String bestHonor,
                           String petitionLine,UUID pendingId,String pendingLine,boolean council,boolean sovereign) {
        public static Snapshot empty(){return new Snapshot(0,1,"Нет","Нет",null,"Нет",false,false);}
        public void write(FriendlyByteBuf out) {
            out.writeVarInt(reputation);out.writeVarInt(socialLevel);
            out.writeUtf(bestHonor,64);out.writeUtf(petitionLine,96);
            out.writeBoolean(pendingId!=null);if(pendingId!=null)out.writeUUID(pendingId);
            out.writeUtf(pendingLine,96);out.writeBoolean(council);out.writeBoolean(sovereign);
        }
        public static Snapshot read(FriendlyByteBuf in) {
            int reputation=in.readVarInt(),rank=in.readVarInt();
            String honor=in.readUtf(64),petition=in.readUtf(96);
            UUID pending=in.readBoolean()?in.readUUID():null;
            return new Snapshot(reputation,rank,honor,petition,pending,in.readUtf(96),in.readBoolean(),in.readBoolean());
        }
    }
    private KingdomCivicService(){}

    public static boolean mayCouncil(ServerPlayer player,UUID townId){
        if(player==null||townId==null)return false;
        var server=player.getServer();
        var town=KingdomCivicsData.get(server).townById(townId);
        if(town==null)return false;
        UUID person=player.getUUID();
        UUID founder=KingdomAdministrationData.get(server).founder(townId);
        return person.equals(founder)||person.equals(town.mayor());
    }
    public static Snapshot preview(ServerPlayer player,UUID townId){
        if(player==null||townId==null)return Snapshot.empty();
        var server=player.getServer();
        var civics=KingdomCivicsData.get(server);
        if(!civics.isMember(townId,player.getUUID()))return Snapshot.empty();
        var town=civics.townById(townId);
        if(town==null)return Snapshot.empty();
        UUID person=player.getUUID();
        int reputation=KingdomReputationData.get(server).score(townId,person);
        var rank=KingdomSocialData.get(server).rank(person);
        var honorary=KingdomCivicHonorsData.get(server).best(townId,person);
        String honor=honorary==null?"Нет":honorary.russian();
        var realm=civics.nationOf(person);
        if(realm!=null&&KingdomNationalHonorsData.get(server).award(realm.id(),person)!=null)
            honor=(honorary==null?"":"Город: "+honor+"; ")+"Орден королевства";
        var petitions=KingdomCitizenPetitionsData.get(server);
        var mine=petitions.forMember(townId,person,1);
        String myLine=mine.isEmpty()?"Нет":mine.get(0).topic().russian()+" — "+status(mine.get(0).status());
        boolean council=mayCouncil(player,townId);
        boolean sovereign=realm!=null&&town.nation()!=null
                &&town.nation().equals(realm.id())&&person.equals(realm.ruler());
        var queue=council?petitions.pending(townId,KingdomCitizenPetitionsData.MAX_ACTIVE_PER_TOWN).stream()
                .filter(p->!person.equals(p.petitioner())).limit(1).toList()
                :List.<KingdomCitizenPetitionsData.Petition>of();
        UUID pending=queue.isEmpty()?null:queue.get(0).id();
        String pendingLine=queue.isEmpty()?"Обращений нет":queue.get(0).topic().russian()
                +" | "+queue.get(0).petitioner().toString().substring(0,8);
        return new Snapshot(reputation,rank.level(),honor,myLine,pending,pendingLine,council,sovereign);
    }
    public static String status(KingdomCitizenPetitionsData.Status status) {
        return switch(status) {
            case PENDING->"на рассмотрении";
            case APPROVED->"одобрено";
            case REJECTED->"отклонено";
        };
    }

    /** Across all towns in a state; protects against a ruler awarding a new unknown resident. */
    public static int nationStanding(net.minecraft.server.MinecraftServer server,UUID nation,UUID person){
        if(server==null||nation==null||person==null)return 0;
        int total=0;
        for(var town:KingdomCivicsData.get(server).towns()){
            if(!nation.equals(town.nation()))continue;
            total=Math.min(200,total+KingdomReputationData.get(server).score(town.id(),person));
            if(total==200)break;
        }
        return total;
    }
    public static boolean awardRealm(ServerPlayer actor,UUID townId,UUID recipient){
        if(actor==null||townId==null||recipient==null)return false;
        var server=actor.getServer();
        var civics=KingdomCivicsData.get(server);
        var office=civics.townById(townId);
        if(office==null||office.nation()==null)return false;
        var realm=civics.nationOf(actor.getUUID());
        var targetRealm=civics.nationOf(recipient);
        if(realm==null||targetRealm==null||!realm.id().equals(office.nation())
                ||!realm.id().equals(targetRealm.id())||!actor.getUUID().equals(realm.ruler()))return false;
        int rank=KingdomSocialData.get(server).rank(recipient).level();
        int standing=nationStanding(server,realm.id(),recipient);
        boolean ok=KingdomNationalHonorsData.get(server).grantVerified(realm.id(),
                recipient,actor.getUUID(),rank,standing,
                Math.max(0,server.overworld().getDayTime()/24000L));
        if(!ok)return false;
        KingdomAdministrationData.get(server).record(townId,actor.getUUID(),null,
                "honor.realm",actor.level().getGameTime());
        return true;
    }

    public static boolean file(ServerPlayer actor,UUID townId,KingdomCitizenPetitionsData.Topic topic){
        if(actor==null||townId==null||topic==null)return false;
        var server=actor.getServer();
        if(!KingdomCivicsData.get(server).isMember(townId,actor.getUUID()))return false;
        var record=KingdomCitizenPetitionsData.get(server).fileVerified(
                townId,actor.getUUID(),topic,
                KingdomSocialData.get(server).rank(actor.getUUID()).level(),
                KingdomReputationData.get(server).score(townId,actor.getUUID()),
                Math.max(0,server.overworld().getDayTime()/24000L));
        if(record==null)return false;
        KingdomAdministrationData.get(server).record(townId,actor.getUUID(),null,
                "petition.file."+topic.name(),actor.level().getGameTime());
        return true;
    }
    public static boolean review(ServerPlayer actor,UUID townId,UUID expectedId,boolean approve){
        if(actor==null||expectedId==null||!mayCouncil(actor,townId))return false;
        var server=actor.getServer();
        var record=KingdomCitizenPetitionsData.get(server).reviewOldest(townId,expectedId,
                actor.getUUID(),approve,Math.max(0,server.overworld().getDayTime()/24000L));
        if(record==null)return false;
        KingdomAdministrationData.get(server).record(townId,actor.getUUID(),null,
                "petition."+record.status().name().toLowerCase(),actor.level().getGameTime());
        return true;
    }
    public static boolean award(ServerPlayer actor,UUID townId,UUID recipient,KingdomCivicHonor honor){
        if(actor==null||townId==null||recipient==null||honor==null||!mayCouncil(actor,townId))return false;
        var server=actor.getServer();var civics=KingdomCivicsData.get(server);
        if(!civics.isMember(townId,recipient))return false;
        int rank=KingdomSocialData.get(server).rank(recipient).level();
        int reputation=KingdomReputationData.get(server).score(townId,recipient);
        boolean ok=KingdomCivicHonorsData.get(server).awardVerified(townId,recipient,honor,
                actor.getUUID(),rank,reputation,
                Math.max(0,server.overworld().getDayTime()/24000L));
        if(!ok)return false;
        KingdomAdministrationData.get(server).record(townId,actor.getUUID(),null,
                "honor.grant."+honor.name().toLowerCase(),actor.level().getGameTime());
        return true;
    }
}
