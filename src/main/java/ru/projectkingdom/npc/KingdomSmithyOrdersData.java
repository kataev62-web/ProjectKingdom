package ru.projectkingdom.npc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * One bounded, owner-approved, real TFC-anvil production order per smithy.
 * Progress means physical delivery to the finished warehouse, NOT simulated craft.
 * In-flight output stays in the genuine TFC anvil and is never stored as free items.
 */
public final class KingdomSmithyOrdersData extends SavedData {
    private static final String KEY="kingdom_smithy_orders_v1";
    public static final int MAX_QUANTITY=64;
    public enum Phase { ACTIVE, WORKING, READY, PAUSED, COMPLETE }
    public record Order(UUID enterprise,UUID worker,String recipe,String input,int requested,
                        int delivered,Phase phase,String pendingItem,int pendingCount,
                        String message) {
        public Order {
            if(enterprise==null||worker==null||recipe==null||recipe.isBlank()
                    ||input==null||input.isBlank()||requested<1||requested>MAX_QUANTITY
                    ||delivered<0||delivered>requested||phase==null
                    ||pendingItem==null||pendingCount<0||pendingCount>requested
                    ||(phase==Phase.READY && (pendingItem.isBlank()||pendingCount==0))
                    ||(phase!=Phase.READY && (!pendingItem.isBlank()||pendingCount!=0)))
                throw new IllegalArgumentException("Invalid physical smithy order");
            message=message==null?"":message.substring(0,Math.min(130,message.length()));
        }
        public boolean finished(){return phase==Phase.COMPLETE;}
        public boolean actionable(){return phase==Phase.ACTIVE||phase==Phase.WORKING||phase==Phase.READY;}
        public int remaining(){return requested-delivered;}
        private Order change(Phase next,int made,String item,int count,String note){
            return new Order(enterprise,worker,recipe,input,requested,made,next,item,count,note);
        }
    }
    private final Map<UUID,Order> orders=new LinkedHashMap<>();
    public KingdomSmithyOrdersData(){}
    public static KingdomSmithyOrdersData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomSmithyOrdersData::load,KingdomSmithyOrdersData::new,KEY);
    }
    public Order order(UUID enterprise){return orders.get(enterprise);}
    /** Replacements are possible only after the previous order reached its warehouse. */
    public boolean create(UUID enterprise,UUID worker,String recipe,String input,int quantity){
        var prior=order(enterprise);
        if(prior!=null&&!prior.finished())return false;
        try {
            Order next=new Order(enterprise,worker,recipe,input,quantity,0,
                    Phase.ACTIVE,"",0,"Заказ сохранён. Ожидание реального сырья TFC.");
            orders.put(enterprise,next);setDirty();return true;
        }catch(IllegalArgumentException ex){return false;}
    }
    public boolean belongs(UUID enterprise,UUID worker){
        Order o=order(enterprise);
        return o!=null&&o.worker().equals(worker)&&!o.finished();
    }
    public boolean working(UUID enterprise,UUID worker){
        Order o=order(enterprise);
        if(o==null||!o.worker().equals(worker)||!o.actionable()||o.phase()==Phase.READY)return false;
        if(o.phase()!=Phase.WORKING){
            orders.put(enterprise,o.change(Phase.WORKING,o.delivered(),"",0,"Ковка выполняется TFC."));
            setDirty();
        }
        return true;
    }
    public boolean ready(UUID enterprise,UUID worker,String actualItem,int count){
        Order o=order(enterprise);
        if(o==null||!o.worker().equals(worker)||!o.actionable()
                ||o.phase()==Phase.READY||actualItem==null||actualItem.isBlank()
                ||count<=0||count>o.remaining())return false;
        orders.put(enterprise,o.change(Phase.READY,o.delivered(),actualItem,count,
                "Настоящее изделие TFC готово; ожидает места в складе продукции."));
        setDirty();return true;
    }
    /** Called only AFTER the native anvil output was physically inserted into the warehouse. */
    public boolean delivered(UUID enterprise,UUID worker,String actualItem,int count){
        Order o=order(enterprise);
        if(o==null||!o.worker().equals(worker)||o.phase()!=Phase.READY
                ||!o.pendingItem().equals(actualItem)||o.pendingCount()!=count)return false;
        int made=o.delivered()+count;
        Phase next=made==o.requested()?Phase.COMPLETE:Phase.ACTIVE;
        orders.put(enterprise,o.change(next,made,"",0,
                next==Phase.COMPLETE?"Заказ завершён: все изделия доставлены на склад."
                        :"Изделие доставлено; ожидается следующая заготовка."));
        setDirty();return true;
    }
    /** Pausing with in-flight work leaves all physical items untouched, no cancellation. */
    public boolean togglePause(UUID enterprise){
        Order o=order(enterprise);
        if(o==null||o.finished()||o.phase()==Phase.READY)return false;
        if(o.phase()==Phase.PAUSED){
            orders.put(enterprise,o.change(Phase.ACTIVE,o.delivered(),"",0,"Заказ возобновлён."));
        }else{
            orders.put(enterprise,o.change(Phase.PAUSED,o.delivered(),"",0,
                    "Заказ приостановлен. Сырьё остаётся в настоящих машинах TFC."));
        }
        setDirty();return true;
    }
    public void note(UUID enterprise,UUID worker,String message){
        Order o=order(enterprise);
        if(o==null||!o.worker().equals(worker)||o.finished()||message==null)return;
        String compact=message.substring(0,Math.min(130,message.length()));
        if(o.message().equals(compact))return;
        orders.put(enterprise,o.change(o.phase(),o.delivered(),o.pendingItem(),
                o.pendingCount(),compact));
        setDirty();
    }
    public static KingdomSmithyOrdersData load(CompoundTag root){
        var data=new KingdomSmithyOrdersData();
        ListTag rows=root.getList("Orders",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size()&&i<10000;i++){
            CompoundTag t=rows.getCompound(i);
            if(!t.hasUUID("Enterprise")||!t.hasUUID("Worker"))continue;
            try{
                Phase phase=Phase.valueOf(t.getString("Phase"));
                var order=new Order(t.getUUID("Enterprise"),t.getUUID("Worker"),
                    t.getString("Recipe"),t.getString("Input"),t.getInt("Requested"),
                    t.getInt("Delivered"),phase,t.getString("PendingItem"),
                    t.getInt("PendingCount"),t.getString("Message"));
                data.orders.put(order.enterprise(),order);
            }catch(IllegalArgumentException ignored){}
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag rows=new ListTag();
        for(Order o:orders.values()){
            CompoundTag t=new CompoundTag();
            t.putUUID("Enterprise",o.enterprise());t.putUUID("Worker",o.worker());
            t.putString("Recipe",o.recipe());t.putString("Input",o.input());
            t.putInt("Requested",o.requested());t.putInt("Delivered",o.delivered());
            t.putString("Phase",o.phase().name());t.putString("PendingItem",o.pendingItem());
            t.putInt("PendingCount",o.pendingCount());t.putString("Message",o.message());
            rows.add(t);
        }
        root.put("Orders",rows);return root;
    }
}
