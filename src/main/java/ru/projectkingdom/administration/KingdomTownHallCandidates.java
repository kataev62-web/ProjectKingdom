package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomNpcData;
import ru.projectkingdom.npc.KingdomIdentityNames;

/**
 * Server-owned picker of real registered citizens and NPCs living in this city.
 * It is a display snapshot only; the command handler always resolves a fresh candidate.
 */
public final class KingdomTownHallCandidates {
    public static final int LIMIT = 256;
    public record Candidate(UUID id,String name,boolean npc,int roleMask,boolean mayor) {
        public Candidate {
            if(id==null || name==null)throw new IllegalArgumentException("Invalid town hall candidate");
            if(name.length()>48){
                int end=Character.isHighSurrogate(name.charAt(47))?47:48;
                name=name.substring(0,end);
            }
        }
    }
    private KingdomTownHallCandidates(){}

    public static List<Candidate> snapshot(MinecraftServer server,UUID townId) {
        if(server==null||townId==null)return List.of();
        KingdomCivicsData civics=KingdomCivicsData.get(server);
        var town=civics.townById(townId);
        if(town==null)return List.of();
        var admin=KingdomAdministrationData.get(server);
        var workers=KingdomNpcData.get(server).all();
        Set<UUID> localIds=new HashSet<>();
        for(int page=0;page<32;page++){
            List<UUID> residents=civics.townMembers(townId,page,32);
            localIds.addAll(residents);
            if(residents.size()<32)break;
        }
        List<Candidate> result=new ArrayList<>();
        // Named NPCs come from a real server-owned worker identity, not from client input.
        for(var worker:workers){
            if(worker.entityId==null || worker.ageYears<18)continue;
            if(!localIds.contains(worker.entityId) && !npcLivesInTown(server,townId,worker))continue;
            localIds.remove(worker.entityId);
            result.add(candidate(admin,town,worker.entityId,KingdomIdentityNames.name(server,worker.entityId),true));
        }
        for(UUID id:localIds){
            String label=KingdomIdentityNames.name(server,id);
            result.add(candidate(admin,town,id,label,false));
        }
        result.sort(Comparator.comparing(Candidate::npc).thenComparing(c->c.name().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(c->c.id().toString()));
        if(result.size()>LIMIT)return List.copyOf(result.subList(0,LIMIT));
        return List.copyOf(result);
    }
    private static Candidate candidate(KingdomAdministrationData admin,KingdomCivicsData.Town town,
                                       UUID id,String name,boolean npc){
        int mask=0;
        for(MunicipalRole role:admin.roles(town.id(),id))mask|=1<<role.ordinal();
        return new Candidate(id,name==null||name.isBlank()?"Безымянный NPC":name,npc,
                mask,id.equals(town.mayor()));
    }
    private static boolean npcLivesInTown(MinecraftServer server,UUID townId,KingdomNpcData.Worker npc) {
        var town=KingdomCivicsData.get(server).townById(townId);
        if(town==null)return false;
        if(town.name().equalsIgnoreCase(npc.settlement))return true;
        // An NPC without a marked home is still selectable if the actual loaded
        // entity is currently inside this city's legally claimed territory.
        for(var level:server.getAllLevels()){
            if(!level.dimension().location().toString().equals(npc.dimension))continue;
            if(npc.hasHome){
                var home=KingdomCivicsData.get(server).townAt(level,
                        new BlockPos(npc.homeX,npc.homeY,npc.homeZ));
                if(home!=null&&townId.equals(home.id()))return true;
            }
            var entity=level.getEntity(npc.entityId);
            if(entity!=null){
                var at=KingdomCivicsData.get(server).townAt(level,entity.blockPosition());
                if(at!=null&&townId.equals(at.id()))return true;
            }
        }
        return false;
    }
    /** Checks a fresh server snapshot; stale client menus cannot invent or transfer recipients. */
    public static boolean eligible(MinecraftServer server,UUID townId,UUID id){
        if(server==null||townId==null||id==null)return false;
        if(KingdomCivicsData.get(server).isMember(townId,id))return true;
        for(var worker:KingdomNpcData.get(server).all())
            if(id.equals(worker.entityId)&&worker.ageYears>=18)
                return npcLivesInTown(server,townId,worker);
        return false;
    }
    public static boolean enrollLocalNpc(MinecraftServer server,UUID townId,UUID id){
        var civics=KingdomCivicsData.get(server);
        if(civics.isMember(townId,id))return true;
        for(var worker:KingdomNpcData.get(server).all())
            if(id.equals(worker.entityId)&&worker.ageYears>=18
                    &&npcLivesInTown(server,townId,worker))
                return civics.joinTown(id,townId);
        return false;
    }
    public static void write(FriendlyByteBuf buf,List<Candidate> rows){
        int count=Math.min(LIMIT,rows.size());
        buf.writeVarInt(count);
        for(int i=0;i<count;i++){
            var row=rows.get(i);
            buf.writeUUID(row.id());buf.writeUtf(row.name(),48);
            buf.writeBoolean(row.npc());buf.writeVarInt(row.roleMask());buf.writeBoolean(row.mayor());
        }
    }
    public static List<Candidate> read(FriendlyByteBuf buf){
        int count=buf.readVarInt();
        if(count<0||count>LIMIT)throw new IllegalArgumentException("Town hall picker overflow");
        List<Candidate> rows=new ArrayList<>(count);
        for(int i=0;i<count;i++)
            rows.add(new Candidate(buf.readUUID(),buf.readUtf(48),buf.readBoolean(),
                    buf.readVarInt(),buf.readBoolean()));
        return List.copyOf(rows);
    }
}
