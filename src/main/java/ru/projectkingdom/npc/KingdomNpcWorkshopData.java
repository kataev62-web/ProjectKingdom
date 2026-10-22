package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** A small, persistent, explicit work plan for one worker at one business.
 * Recipes are copied from a VALID server-side crafting recipe at the station.
 * The plan never includes virtual inventory, cash or free output.
 */
public final class KingdomNpcWorkshopData extends SavedData {
    private static final String KEY="kingdom_npc_workshops_v1";
    public record Plan(UUID worker, UUID enterprise, String recipe, String output,
                       List<String> grid, int low, int high, long duration, long salePrice) {
        public Plan {
            grid=List.copyOf(grid);
            if(grid.size()!=9 || low<0 || high<=low || high>2304 || duration<100 || duration>24000
                    ||salePrice<1||salePrice>100000)
                throw new IllegalArgumentException("Invalid workshop plan");
        }
        public Plan(UUID worker,UUID enterprise,String recipe,String output,
                    List<String> grid,int low,int high,long duration){
            this(worker,enterprise,recipe,output,grid,low,high,duration,10);
        }
    }
    public record Progress(UUID worker, long readyAt, String recipe, boolean running) {}
    private final Map<UUID,Plan> plans=new LinkedHashMap<>();
    private final Map<UUID,Progress> progress=new LinkedHashMap<>();
    private final Map<UUID,Boolean> enabled=new LinkedHashMap<>();
    public static KingdomNpcWorkshopData get(MinecraftServer s){
        return s.overworld().getDataStorage().computeIfAbsent(KingdomNpcWorkshopData::load,KingdomNpcWorkshopData::new,KEY);
    }
    public Plan plan(UUID worker){return plans.get(worker);}
    public Collection<Plan> plans(){return Collections.unmodifiableCollection(plans.values());}
    public Progress progress(UUID worker){return progress.get(worker);}
    public boolean enabled(UUID worker){return enabled.getOrDefault(worker,false);}
    /** All authorisation and server recipe validation happens BEFORE this trusted entry point. */
    void configure(Plan p){
        plans.put(p.worker(),p);progress.remove(p.worker());enabled.put(p.worker(),false);setDirty();
    }
    void setEnabled(UUID worker,boolean value){
        if(enabled(worker)!=value){enabled.put(worker,value);setDirty();}
    }
    /** UI owner permissions are checked by the caller, never taken from client input. */
    public boolean rotatePrice(UUID worker){
        var p=plans.get(worker);if(p==null)return false;
        long price=p.salePrice()==10?50:p.salePrice()==50?100:p.salePrice()==100?500:10;
        plans.put(worker,new Plan(p.worker(),p.enterprise(),p.recipe(),p.output(),p.grid(),
                p.low(),p.high(),p.duration(),price));setDirty();return true;
    }
    public boolean rotateStock(UUID worker){
        var p=plans.get(worker);if(p==null)return false;
        int low=p.low()==4?8:p.low()==8?16:p.low()==16?32:4;
        int high=low*8;
        plans.put(worker,new Plan(p.worker(),p.enterprise(),p.recipe(),p.output(),p.grid(),
                low,high,p.duration(),p.salePrice()));
        progress.remove(worker);enabled.put(worker,false);setDirty();return true;
    }
    void start(UUID worker,String recipe,long readyAt){
        progress.put(worker,new Progress(worker,readyAt,recipe,true));setDirty();
    }
    void clear(UUID worker){if(progress.remove(worker)!=null)setDirty();}
    /** Existing worker and job identity are never inferred from an untrusted NBT plan. */
    private static KingdomNpcWorkshopData load(CompoundTag root){
        var d=new KingdomNpcWorkshopData();
        for(Tag raw:root.getList("Plans",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)raw;
            if(!t.hasUUID("Worker")||!t.hasUUID("Firm"))continue;
            var items=new ArrayList<String>();
            for(int i=0;i<9;i++)items.add(t.getString("S"+i));
            try {
                var p=new Plan(t.getUUID("Worker"),t.getUUID("Firm"),t.getString("Recipe"),
                    t.getString("Output"),items,t.getInt("Low"),t.getInt("High"),t.getLong("Duration"),
                        t.contains("SalePrice",Tag.TAG_LONG)?t.getLong("SalePrice"):10L);
                if(p.recipe().isBlank()||p.output().isBlank())continue;
                d.plans.put(p.worker(),p);d.enabled.put(p.worker(),t.getBoolean("Enabled"));
            }catch(IllegalArgumentException ignored){}
        }
        for(Tag raw:root.getList("Progress",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)raw;
            if(!t.hasUUID("Worker"))continue;
            UUID id=t.getUUID("Worker");var p=d.plans.get(id);
            if(p!=null && p.recipe().equals(t.getString("Recipe")))
                d.progress.put(id,new Progress(id,Math.max(0,t.getLong("Ready")),p.recipe(),true));
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root){
        var records=new ListTag();
        for(Plan p:plans.values()){
            var t=new CompoundTag();t.putUUID("Worker",p.worker());t.putUUID("Firm",p.enterprise());
            t.putString("Recipe",p.recipe());t.putString("Output",p.output());
            for(int i=0;i<9;i++)t.putString("S"+i,p.grid().get(i));
            t.putInt("Low",p.low());t.putInt("High",p.high());t.putLong("Duration",p.duration());
            t.putLong("SalePrice",p.salePrice());
            t.putBoolean("Enabled",enabled(p.worker()));records.add(t);
        }root.put("Plans",records);
        records=new ListTag();for(Progress p:progress.values()){
            var t=new CompoundTag();t.putUUID("Worker",p.worker());t.putLong("Ready",p.readyAt());
            t.putString("Recipe",p.recipe());records.add(t);
        }root.put("Progress",records);return root;
    }
}
