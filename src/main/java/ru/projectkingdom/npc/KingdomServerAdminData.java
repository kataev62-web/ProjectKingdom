package ru.projectkingdom.npc;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Operator-4 gated administrative work mode. This flag never grants OP. */
public final class KingdomServerAdminData extends SavedData {
    private static final String KEY="kingdom_server_admin_v1";
    private final Set<UUID> enabled=new HashSet<>();
    private final ArrayDeque<String> audit=new ArrayDeque<>();

    public static KingdomServerAdminData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomServerAdminData::load,KingdomServerAdminData::new,KEY);
    }
    public boolean enabled(UUID player){return player!=null&&enabled.contains(player);}
    public boolean set(UUID player,boolean active){
        if(player==null)return false;
        boolean changed=active?enabled.add(player):enabled.remove(player);
        if(changed)setDirty();
        return changed;
    }
    public void record(UUID actor,String action){
        if(actor==null||action==null||action.isBlank())return;
        String safe=action.replaceAll("[\\r\\n\\t]"," ");
        if(safe.length()>220)safe=safe.substring(0,220);
        while(audit.size()>=256)audit.removeFirst();
        audit.addLast(System.currentTimeMillis()+" "+actor+" "+safe);
        setDirty();
    }
    public java.util.List<String> audit(int limit){
        var lines=new java.util.ArrayList<>(audit);
        return java.util.List.copyOf(lines.subList(Math.max(0,lines.size()-Math.max(0,Math.min(50,limit))),lines.size()));
    }
    public static KingdomServerAdminData load(CompoundTag tag){
        var data=new KingdomServerAdminData();
        ListTag list=tag.getList("Active",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++)if(list.getCompound(i).hasUUID("Player"))
            data.enabled.add(list.getCompound(i).getUUID("Player"));
        ListTag history=tag.getList("Audit",Tag.TAG_STRING);
        for(int i=Math.max(0,history.size()-256);i<history.size();i++)
            data.audit.addLast(history.getString(i));
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag active=new ListTag();
        for(UUID id:enabled){var row=new CompoundTag();row.putUUID("Player",id);active.add(row);}
        tag.put("Active",active);
        ListTag history=new ListTag();
        for(String line:audit)history.add(net.minecraft.nbt.StringTag.valueOf(line));
        tag.put("Audit",history);
        return tag;
    }
}
