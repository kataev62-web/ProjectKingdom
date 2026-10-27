package ru.projectkingdom.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persist approved settlement ranks; eligibility is recomputed from real conditions. */
public final class KingdomSettlementRanks extends SavedData {
    private static final String KEY="kingdom_settlement_ranks_v1";
    private final Map<UUID,KingdomSettlementGrowth.Rank> ranks=new LinkedHashMap<>();

    public static KingdomSettlementRanks get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomSettlementRanks::fromTag,KingdomSettlementRanks::new,KEY);
    }

    public void initializeHamlet(UUID town){
        if(town!=null&&!ranks.containsKey(town)){
            ranks.put(town,KingdomSettlementGrowth.Rank.HAMLET);setDirty();
        }
    }

    /** Preserve a legacy settlement's already visible automatic rank on first load. */
    public KingdomSettlementGrowth.Rank current(UUID town,KingdomSettlementGrowth.Rank legacyEligible){
        if(town==null)return KingdomSettlementGrowth.Rank.HAMLET;
        var rank=ranks.get(town);
        if(rank==null){
            rank=legacyEligible==null?KingdomSettlementGrowth.Rank.HAMLET:legacyEligible;
            ranks.put(town,rank);setDirty();
        }
        return rank;
    }

    /** Exactly one authorized rank increase at a time; no silent downgrades. */
    public boolean promote(UUID town,KingdomSettlementGrowth.Rank current,
                           KingdomSettlementGrowth.Rank next){
        if(town==null||current==null||next==null||next.ordinal()!=current.ordinal()+1
                ||ranks.get(town)!=current)return false;
        ranks.put(town,next);setDirty();return true;
    }

    public static KingdomSettlementRanks fromTag(CompoundTag tag){
        var result=new KingdomSettlementRanks();
        ListTag rows=tag.getList("Towns",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++){
            var row=rows.getCompound(i);if(!row.hasUUID("Id"))continue;
            int value=row.getInt("Rank");
            if(value>=0&&value<KingdomSettlementGrowth.Rank.values().length)
                result.ranks.put(row.getUUID("Id"),KingdomSettlementGrowth.Rank.values()[value]);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag rows=new ListTag();
        for(var entry:ranks.entrySet()){
            CompoundTag row=new CompoundTag();row.putUUID("Id",entry.getKey());
            row.putInt("Rank",entry.getValue().ordinal());rows.add(row);
        }
        tag.put("Towns",rows);return tag;
    }
}
