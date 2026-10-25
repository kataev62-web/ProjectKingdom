package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable server audit for OP-only deregistration; never erases past business trades. */
public final class KingdomAdminEnterpriseAudit extends SavedData {
    private static final String KEY="kingdom_admin_enterprise_audit_v1";
    public record Entry(UUID operator,UUID enterprise,UUID originalOwner,UUID town,UUID nation,
                        String enterpriseName,long gameDay) {}
    private final List<Entry> entries=new ArrayList<>();
    public static KingdomAdminEnterpriseAudit get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomAdminEnterpriseAudit::load,KingdomAdminEnterpriseAudit::new,KEY);
    }
    public List<Entry> entries(){return List.copyOf(entries);}
    public void recordVerified(Entry entry){
        if(entry==null||entry.operator()==null||entry.enterprise()==null||entry.town()==null)return;
        entries.add(entry);
        setDirty();
    }
    private static KingdomAdminEnterpriseAudit load(CompoundTag root){
        var result=new KingdomAdminEnterpriseAudit();
        ListTag list=root.getList("Entries",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var t=list.getCompound(i);
            if(!t.hasUUID("Operator")||!t.hasUUID("Enterprise")||!t.hasUUID("Owner")||!t.hasUUID("Town"))continue;
            result.entries.add(new Entry(t.getUUID("Operator"),t.getUUID("Enterprise"),
                    t.getUUID("Owner"),t.getUUID("Town"),t.hasUUID("Nation")?t.getUUID("Nation"):null,
                    t.getString("Name"),Math.max(0,t.getLong("Day"))));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag list=new ListTag();
        for(var e:entries){
            CompoundTag tag=new CompoundTag();tag.putUUID("Operator",e.operator());
            tag.putUUID("Enterprise",e.enterprise());tag.putUUID("Owner",e.originalOwner());
            tag.putUUID("Town",e.town());if(e.nation()!=null)tag.putUUID("Nation",e.nation());
            tag.putString("Name",e.enterpriseName());tag.putLong("Day",e.gameDay());
            list.add(tag);
        }
        root.put("Entries",list);return root;
    }
}
