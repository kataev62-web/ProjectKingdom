package ru.projectkingdom.settlement;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable 10-second temporary public/wilderness placements, without chunk loading. */
public final class KingdomTemporaryBlocks extends SavedData {
    private static final String ID="kingdom_temporary_public_blocks_v1";
    public static final long LIFETIME_MS=10_000L;
    public static final int MAX_ACTIVE=8192;
    private record Entry(String dim,long pos,String block,long expire) {}
    private final Map<String,Entry> pending=new LinkedHashMap<>();
    public static KingdomTemporaryBlocks get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomTemporaryBlocks::load,KingdomTemporaryBlocks::new,ID);
    }
    private static String key(String dim,BlockPos pos){return dim+":"+pos.asLong();}
    public int count(){return pending.size();}
    public boolean track(ServerLevel level,BlockPos pos){
        if(pending.size()>=MAX_ACTIVE)return false;
        var state=level.getBlockState(pos);
        if(!KingdomTemporaryLand.safeTemporaryPlacement(level,pos,state))return false;
        String type=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        pending.put(key(level.dimension().location().toString(),pos),
                new Entry(level.dimension().location().toString(),pos.asLong(),type,
                        System.currentTimeMillis()+LIFETIME_MS));
        setDirty();return true;
    }
    public void forget(ServerLevel level,BlockPos pos){
        if(pending.remove(key(level.dimension().location().toString(),pos))!=null)setDirty();
    }
    public void tick(MinecraftServer server){
        long now=System.currentTimeMillis();
        boolean changed=false;
        Iterator<Entry> scan=pending.values().iterator();
        int processed=0;
        while(scan.hasNext()&&processed<512){
            Entry entry=scan.next();
            if(entry.expire()>now)continue;
            ServerLevel level=null;
            for(var candidate:server.getAllLevels())
                if(entry.dim().equals(candidate.dimension().location().toString())){level=candidate;break;}
            if(level==null){scan.remove();changed=true;processed++;continue;}
            BlockPos pos=BlockPos.of(entry.pos());
            if(!level.hasChunkAt(pos))continue; // persisted until chunk loads, no forced load
            var state=level.getBlockState(pos);
            if(level.getBlockEntity(pos)==null
                    &&entry.block().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                    &&state.getDestroySpeed(level,pos)>=0){
                // setBlock with no loot: do not destroy placed blocks with drops.
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            }
            scan.remove();processed++;changed=true;
        }
        if(changed)setDirty();
    }
    public static KingdomTemporaryBlocks load(CompoundTag root){
        var data=new KingdomTemporaryBlocks();
        var list=root.getList("Pending",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size()&&i<MAX_ACTIVE;i++){
            var tag=list.getCompound(i);
            String dim=tag.getString("Dim"),block=tag.getString("Block");
            if(dim.isBlank()||ResourceLocation.tryParse(block)==null)continue;
            long packed=tag.getLong("Pos");
            data.pending.put(dim+":"+packed,new Entry(dim,packed,block,tag.getLong("Expires")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();
        for(Entry entry:pending.values()){
            var tag=new CompoundTag();
            tag.putString("Dim",entry.dim());tag.putLong("Pos",entry.pos());
            tag.putString("Block",entry.block());tag.putLong("Expires",entry.expire());
            list.add(tag);
        }
        root.put("Pending",list);return root;
    }
}
