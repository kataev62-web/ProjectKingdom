package ru.projectkingdom.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Live player-to-NPC 36-slot exchange using vanilla four-row chest screen.
 * The slots are actual saved worker.fieldInventory, not decorative GUI icons.
 * Exactly one viewer may edit an NPC; physical worker ticks pause while open.
 */
public final class KingdomNpcBagMenu extends ChestMenu {
    private static final Map<UUID,UUID> OPEN=new ConcurrentHashMap<>();
    private final ServerPlayer viewer;
    private final int npcId;
    private final UUID entityId;
    private final BagSlots bag;

    public static boolean isOpen(UUID entity){return entity!=null&&OPEN.containsKey(entity);}

    public static boolean open(ServerPlayer viewer,KingdomNpcData.Worker worker){
        if(viewer==null||worker==null||worker.entityId==null
            ||worker.employer==null||(!viewer.hasPermissions(2)
                &&!viewer.getUUID().equals(worker.employer))
            ||worker.fieldInventory.size()>36
            ||!viewer.level().dimension().location().toString().equals(worker.dimension))
            return false;
        var entity=viewer.serverLevel().getEntity(worker.entityId);
        if(entity==null||!entity.isAlive()||viewer.distanceToSqr(entity)>64)return false;
        if(OPEN.putIfAbsent(worker.entityId,viewer.getUUID())!=null)return false;
        try{
            var open=viewer.openMenu(new SimpleMenuProvider(
                (id,inventory,player)->new KingdomNpcBagMenu(id,inventory,viewer,worker),
                Component.literal("Инвентарь NPC: "+worker.name)));
            if(open.isEmpty()){
                OPEN.remove(worker.entityId,viewer.getUUID());
                return false;
            }
            return true;
        }catch(RuntimeException error){
            OPEN.remove(worker.entityId,viewer.getUUID());
            throw error;
        }
    }

    private KingdomNpcBagMenu(int id,Inventory inventory,ServerPlayer viewer,
                               KingdomNpcData.Worker worker){
        this(id,inventory,viewer,worker,new BagSlots(viewer.getServer(),worker));
    }

    private KingdomNpcBagMenu(int id,Inventory inventory,ServerPlayer viewer,
                              KingdomNpcData.Worker worker,BagSlots bag){
        super(MenuType.GENERIC_9x4,id,inventory,bag,4);
        this.viewer=viewer;
        this.npcId=worker.id;
        this.entityId=worker.entityId;
        this.bag=bag;
    }

    public boolean isForNpc(int id){return this.npcId==id;}

    @Override public boolean stillValid(Player player){
        if(player!=viewer)return false;
        var worker=KingdomNpcData.get(viewer.getServer()).workers.get(npcId);
        if(worker==null||!entityId.equals(worker.entityId)
            ||worker.employer==null
            ||(!viewer.hasPermissions(2)&&!viewer.getUUID().equals(worker.employer))
            ||!worker.dimension.equals(viewer.level().dimension().location().toString()))
            return false;
        var entity=viewer.serverLevel().getEntity(entityId);
        return entity!=null&&entity.isAlive()&&viewer.distanceToSqr(entity)<=64;
    }

    @Override public void removed(Player player){
        bag.flush();
        OPEN.remove(entityId,viewer.getUUID());
        super.removed(player);
    }

    /** A fixed 36-slot GUI, persisted back as the NPC's compact saved list. */
    private static final class BagSlots extends SimpleContainer {
        private final MinecraftServer server;
        private final KingdomNpcData.Worker worker;
        private boolean loading=true;
        BagSlots(MinecraftServer server,KingdomNpcData.Worker worker){
            super(36);
            this.server=server;
            this.worker=worker;
            for(int i=0;i<worker.fieldInventory.size()&&i<36;i++)
                super.setItem(i,worker.fieldInventory.get(i).copy());
            loading=false;
        }
        @Override public void setChanged(){
            super.setChanged();
            flush();
        }
        void flush(){
            if(loading)return;
            worker.fieldInventory.clear();
            for(int i=0;i<36;i++){
                var slot=super.getItem(i);
                if(!slot.isEmpty())worker.fieldInventory.add(slot.copy());
            }
            KingdomNpcData.get(server).setDirty();
        }
    }
}
