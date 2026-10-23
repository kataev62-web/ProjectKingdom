package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side territory and designated storage registry. Default deny for NPC inventory access. */
public final class KingdomTerritories extends SavedData {
    private static final String DATA = "kingdom_npc_territories_v1";
    private final Map<String, UUID> claims = new LinkedHashMap<>();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    public record Node(UUID owner, String role) {}
    private static String chunkKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + (pos.getX() >> 4) + ":" + (pos.getZ() >> 4);
    }
    private static String nodeKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.asLong();
    }
    public static KingdomTerritories get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KingdomTerritories::load, KingdomTerritories::new, DATA);
    }
    public boolean claim(ServerLevel level, BlockPos pos, UUID owner) {
        String key=chunkKey(level,pos);
        if (claims.containsKey(key)) return claims.get(key).equals(owner);
        claims.put(key,owner); setDirty(); return true;
    }
    public UUID owner(ServerLevel level, BlockPos pos) { return claims.get(chunkKey(level,pos)); }
    public boolean canUse(ServerLevel level, BlockPos pos, UUID owner) {
        return owner!=null && owner.equals(owner(level,pos));
    }
    public boolean register(ServerLevel level, BlockPos pos, UUID owner, String role) {
        if (!canUse(level,pos,owner) || !(role.equals("raw")||role.equals("finished")||role.equals("workstation"))) return false;
        Node existing=nodes.get(nodeKey(level,pos));
        if(existing!=null && !existing.owner().equals(owner))return false;
        nodes.put(nodeKey(level,pos),new Node(owner,role));setDirty();return true;
    }
    public boolean usable(ServerLevel level, BlockPos pos, UUID owner, String role) {
        Node node=nodes.get(nodeKey(level,pos));
        return canUse(level,pos,owner) && node!=null && node.owner().equals(owner) && node.role().equals(role);
    }
    public String role(ServerLevel level,BlockPos pos) {
        Node node=nodes.get(nodeKey(level,pos));return node==null?"не назначено":node.role();
    }
    public UUID protectedStorageOwner(ServerLevel level, BlockPos pos) {
        Node node=nodes.get(nodeKey(level,pos));
        return node != null && ("raw".equals(node.role()) || "finished".equals(node.role()))
                ? node.owner() : null;
    }
    private static KingdomTerritories load(CompoundTag tag) {
        KingdomTerritories data=new KingdomTerritories();
        ListTag cs=tag.getList("Claims",Tag.TAG_COMPOUND);
        for(int i=0;i<cs.size();i++){CompoundTag row=cs.getCompound(i);if(row.hasUUID("Owner"))data.claims.put(row.getString("Key"),row.getUUID("Owner"));}
        ListTag ns=tag.getList("Nodes",Tag.TAG_COMPOUND);
        for(int i=0;i<ns.size();i++){CompoundTag row=ns.getCompound(i);if(row.hasUUID("Owner"))data.nodes.put(row.getString("Key"),new Node(row.getUUID("Owner"),row.getString("Role")));}
        return data;
    }
    @Override public CompoundTag save(CompoundTag out) {
        ListTag cs=new ListTag();for(var c:claims.entrySet()){CompoundTag t=new CompoundTag();t.putString("Key",c.getKey());t.putUUID("Owner",c.getValue());cs.add(t);}out.put("Claims",cs);
        ListTag ns=new ListTag();for(var n:nodes.entrySet()){CompoundTag t=new CompoundTag();t.putString("Key",n.getKey());t.putUUID("Owner",n.getValue().owner());t.putString("Role",n.getValue().role());ns.add(t);}out.put("Nodes",ns);return out;
    }
}
