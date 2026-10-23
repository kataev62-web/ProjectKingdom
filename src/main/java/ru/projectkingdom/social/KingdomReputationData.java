package ru.projectkingdom.social;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Local city reputation, not a global character class or an NPC's personal attitude.
 * All writes must be invoked from a committed server transaction; no client-side setter.
 */
public final class KingdomReputationData extends SavedData {
    private static final String KEY="kingdom_reputation_v1";
    private static final int MAX_RECEIPTS=128;
    private record Key(UUID town,UUID person) {}
    private static final class Entry {
        int score;
        long lastDay=-1;
        int today;
        final LinkedHashSet<UUID> receipts=new LinkedHashSet<>();
    }
    private final Map<Key,Entry> entries=new LinkedHashMap<>();

    public static KingdomReputationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomReputationData::load, KingdomReputationData::new, KEY);
    }
    public int score(UUID town,UUID person) {
        if(town==null||person==null)return 0;
        Entry e=entries.get(new Key(town,person));
        return e==null?0:e.score;
    }
    /**
     * Verified action receipt is bound to actor + municipality.
     * Daily gain cap is shared by all event types, preventing split-payment farming.
     */
    public boolean recordVerified(UUID town, UUID person,KingdomReputationPolicy.Event event,
                                  UUID receipt,long amountCopper,long gameDay) {
        if(town==null||person==null||receipt==null||gameDay<0)return false;
        int points=KingdomReputationPolicy.earned(event,amountCopper);
        if(points<=0)return false;
        Key key=new Key(town,person);
        Entry entry=entries.computeIfAbsent(key,unused->new Entry());
        if(entry.receipts.contains(receipt))return false;
        long day=Math.max(0,gameDay);
        if(day>entry.lastDay){entry.lastDay=day;entry.today=0;}
        if(entry.today>=KingdomReputationPolicy.DAILY_GAIN_CAP
                ||entry.score>=KingdomReputationPolicy.MAX_SCORE)return false;
        int actual=Math.min(points,Math.min(KingdomReputationPolicy.DAILY_GAIN_CAP-entry.today,
                KingdomReputationPolicy.MAX_SCORE-entry.score));
        if(actual<=0)return false;
        entry.score+=actual;entry.today+=actual;
        entry.receipts.add(receipt);
        while(entry.receipts.size()>MAX_RECEIPTS)entry.receipts.remove(entry.receipts.iterator().next());
        setDirty();
        return true;
    }
    private static KingdomReputationData load(CompoundTag root) {
        KingdomReputationData d=new KingdomReputationData();
        ListTag list=root.getList("Entries",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            CompoundTag row=list.getCompound(i);
            if(!row.hasUUID("Town")||!row.hasUUID("Person"))continue;
            Key key=new Key(row.getUUID("Town"),row.getUUID("Person"));
            Entry entry=new Entry();
            entry.score=Math.max(0,Math.min(KingdomReputationPolicy.MAX_SCORE,row.getInt("Score")));
            entry.today=Math.max(0,Math.min(KingdomReputationPolicy.DAILY_GAIN_CAP,row.getInt("Today")));
            entry.lastDay=row.contains("LastDay")?Math.max(-1,row.getLong("LastDay")):-1;
            ListTag receipts=row.getList("Receipts",Tag.TAG_COMPOUND);
            int from=Math.max(0,receipts.size()-MAX_RECEIPTS);
            for(int j=from;j<receipts.size();j++){
                CompoundTag tag=receipts.getCompound(j);
                if(tag.hasUUID("Id"))entry.receipts.add(tag.getUUID("Id"));
            }
            d.entries.put(key,entry);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag entriesTag=new ListTag();
        for(var item:entries.entrySet()){
            CompoundTag row=new CompoundTag();
            row.putUUID("Town",item.getKey().town());
            row.putUUID("Person",item.getKey().person());
            Entry value=item.getValue();
            row.putInt("Score",value.score);
            row.putLong("LastDay",value.lastDay);
            row.putInt("Today",value.today);
            ListTag receipts=new ListTag();
            for(UUID id:value.receipts){CompoundTag tag=new CompoundTag();tag.putUUID("Id",id);receipts.add(tag);}
            row.put("Receipts",receipts);
            entriesTag.add(row);
        }
        root.put("Entries",entriesTag);
        return root;
    }
}
