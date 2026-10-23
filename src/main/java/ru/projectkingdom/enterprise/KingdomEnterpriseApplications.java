package ru.projectkingdom.enterprise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomEnterprisePayrollData;

/** Applications are requests, not automatic contracts or sources of money. */
public final class KingdomEnterpriseApplications extends SavedData {
    private static final String KEY="kingdom_enterprise_applications_v1";
    public record Application(UUID enterprise, UUID player, String name) {}
    private final Map<UUID,LinkedHashMap<UUID,Application>> byEnterprise=new LinkedHashMap<>();
    public static KingdomEnterpriseApplications get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomEnterpriseApplications::load, KingdomEnterpriseApplications::new, KEY);
    }
    public boolean request(MinecraftServer server, UUID enterprise, UUID applicant, String name) {
        var firm=KingdomEnterpriseData.get(server).find(enterprise);
        if(firm==null || applicant==null || firm.owner().equals(applicant))return false;
        var current=KingdomEnterprisePayrollData.get(server).contract(enterprise,applicant);
        if(current!=null && current.active())return false;
        var requests=byEnterprise.computeIfAbsent(enterprise,unused->new LinkedHashMap<>());
        if(requests.containsKey(applicant) || requests.size()>=20)return false;
        String safe=name==null?"Игрок":name.replaceAll("[\\p{Cntrl}\\u00a7]","").trim();
        if(safe.length()>32)safe=safe.substring(0,32);
        requests.put(applicant,new Application(enterprise,applicant,safe));
        setDirty();return true;
    }
    public boolean pending(UUID enterprise, UUID applicant) {
        return byEnterprise.getOrDefault(enterprise,new LinkedHashMap<>()).containsKey(applicant);
    }
    public List<Application> applications(UUID enterprise) {
        return List.copyOf(byEnterprise.getOrDefault(enterprise,new LinkedHashMap<>()).values());
    }
    /** Employer explicitly approves a request; pay starts accruing as a normal debt. */
    public boolean acceptFirst(MinecraftServer server, UUID owner, UUID enterprise, long copperPerDay) {
        var firm=KingdomEnterpriseData.get(server).find(enterprise);
        if(firm==null || !firm.owner().equals(owner) || copperPerDay<1 || copperPerDay>10000)return false;
        var applications=byEnterprise.get(enterprise);
        if(applications==null || applications.isEmpty())return false;
        var first=applications.values().iterator().next();
        if(!KingdomEnterprisePayrollData.get(server).hire(server,owner,enterprise,first.player(),copperPerDay))return false;
        applications.remove(first.player());
        if(applications.isEmpty())byEnterprise.remove(enterprise);
        setDirty();return true;
    }
    public void cancelForClosedEnterprise(UUID enterprise){
        if(enterprise!=null&&byEnterprise.remove(enterprise)!=null)setDirty();
    }
    private static KingdomEnterpriseApplications load(CompoundTag root) {
        var result=new KingdomEnterpriseApplications();
        ListTag data=root.getList("Applications",Tag.TAG_COMPOUND);
        for(int i=0;i<data.size();i++){
            var row=data.getCompound(i);
            if(!row.hasUUID("Enterprise") || !row.hasUUID("Player"))continue;
            UUID firm=row.getUUID("Enterprise"), player=row.getUUID("Player");
            var list=result.byEnterprise.computeIfAbsent(firm,unused->new LinkedHashMap<>());
            if(list.size()<20)list.putIfAbsent(player,new Application(firm,player,row.getString("Name")));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag rows=new ListTag();
        for(var requests:byEnterprise.values())for(var application:requests.values()) {
            var row=new CompoundTag();
            row.putUUID("Enterprise",application.enterprise());
            row.putUUID("Player",application.player());
            row.putString("Name",application.name());
            rows.add(row);
        }
        root.put("Applications",rows);return root;
    }
}
