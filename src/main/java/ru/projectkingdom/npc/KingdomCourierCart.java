package ru.projectkingdom.npc;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/** Optional visual accompaniment for original AstikorCarts; never creates or teleports items. */
public final class KingdomCourierCart {
    private KingdomCourierCart() {}
    private static final String OWNER_KEY="ProjectKingdomCourier";

    private static boolean isOriginalCart(Entity e) {
        ResourceLocation id=BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return id!=null && "astikorcarts".equals(id.getNamespace())
                && id.getPath().contains("cart");
    }

    /** Pair an existing, unclaimed nearby cart. No cart is spawned or silently stolen. */
    public static boolean pair(ServerLevel level,Villager courier,KingdomNpcData.Worker worker) {
        if(worker.courierCart!=null)return get(level,worker)!=null;
        Entity candidate=level.getEntities(courier,
                new AABB(courier.blockPosition()).inflate(5),
                e->isOriginalCart(e) && !e.getPersistentData().hasUUID(OWNER_KEY))
                .stream().min(Comparator.comparingDouble(courier::distanceToSqr)).orElse(null);
        if(candidate==null)return false;
        candidate.getPersistentData().putUUID(OWNER_KEY,courier.getUUID());
        worker.courierCart=candidate.getUUID();
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }

    public static Entity get(ServerLevel level,KingdomNpcData.Worker worker) {
        if(worker.courierCart==null)return null;
        Entity e=level.getEntity(worker.courierCart);
        if(e==null || !isOriginalCart(e) || !e.getPersistentData().hasUUID(OWNER_KEY)
                || !worker.entityId.equals(e.getPersistentData().getUUID(OWNER_KEY)))return null;
        return e;
    }

    /** Slow steering rather than teleportation. NPC stops if cart falls too far behind. */
    public static boolean accompany(ServerLevel level,Villager courier,KingdomNpcData.Worker worker) {
        if(worker.courierCart==null)return true; // cart optional
        Entity cart=get(level,worker);
        if(cart==null){courier.getNavigation().stop();return false;}
        double distance=cart.distanceToSqr(courier);
        if(distance>400){courier.getNavigation().stop();return false;}
        if(distance>6.25 && cart.onGround()) {
            Vec3 delta=courier.position().subtract(cart.position());
            Vec3 horizontal=new Vec3(delta.x,0,delta.z);
            if(horizontal.lengthSqr()>0.01) {
                Vec3 velocity=horizontal.normalize().scale(Math.min(0.19,horizontal.length()*0.09));
                cart.setDeltaMovement(velocity.x,cart.getDeltaMovement().y,velocity.z);
                cart.hurtMarked=true;
            }
        }
        if(distance>64){courier.getNavigation().stop();return false;}
        return true;
    }

    /** Only the cart's REAL Forge inventory counts as transport space. */
    public static IItemHandler inventory(ServerLevel level,KingdomNpcData.Worker worker) {
        Entity cart=get(level,worker);
        return cart==null?null:cart.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
    }

    public static boolean empty(IItemHandler inv) {
        if(inv==null)return false;
        for(int i=0;i<inv.getSlots();i++)if(!inv.getStackInSlot(i).isEmpty())return false;
        return true;
    }

    /** Number of identical items that a real cart can accept, simulated without mutations. */
    public static int capacity(IItemHandler inv,ItemStack sample,int maximum) {
        if(inv==null || sample.isEmpty())return 0;
        int remaining=maximum;
        for(int i=0;i<inv.getSlots() && remaining>0;i++) {
            ItemStack test=sample.copy();test.setCount(Math.min(remaining,test.getMaxStackSize()));
            ItemStack rest=inv.insertItem(i,test,true);
            remaining-=test.getCount()-rest.getCount();
        }
        return maximum-remaining;
    }

    /** Insert only what physically fits. Caller must retain any remainder. */
    public static int load(IItemHandler inv,ItemStack stack) {
        if(inv==null || stack.isEmpty())return 0;
        int inserted=0;
        for(int i=0;i<inv.getSlots() && !stack.isEmpty();i++) {
            int n=Math.min(stack.getCount(),stack.getMaxStackSize());
            ItemStack portion=stack.copy();portion.setCount(n);
            ItemStack rest=inv.insertItem(i,portion,false);
            int accepted=n-rest.getCount();
            if(accepted>0){stack.shrink(accepted);inserted+=accepted;}
        }
        return inserted;
    }

    /** Extract only the assigned item, never other items in the cart. */
    public static ItemStack unload(IItemHandler inv,ItemStack sample,int maximum) {
        if(inv==null || sample.isEmpty() || maximum<=0)return ItemStack.EMPTY;
        for(int i=0;i<inv.getSlots();i++) {
            ItemStack present=inv.getStackInSlot(i);
            if(!present.isEmpty() && ItemStack.isSameItemSameTags(present,sample))
                return inv.extractItem(i,Math.min(maximum,present.getCount()),false);
        }
        return ItemStack.EMPTY;
    }


    /** Verify that a persisted cart batch is still physically present. Never reconstruct missing items. */
    public static boolean hasCargo(IItemHandler inv, ItemStack sample, int required) {
        if(inv==null || sample.isEmpty() || required<=0)return false;
        long found=0;
        for(int i=0;i<inv.getSlots();i++) {
            ItemStack stack=inv.getStackInSlot(i);
            if(ItemStack.isSameItemSameTags(stack,sample))found+=stack.getCount();
            if(found>=required)return true;
        }
        return false;
    }

    /** Unpair only an empty cart; never release a cart with an outstanding shipment. */
    public static void unpair(ServerLevel level,KingdomNpcData.Worker worker) {
        Entity cart=get(level,worker);
        if(worker.cartCargoCount>0 || !worker.cargo.isEmpty())return;
        if(cart!=null && !empty(inventory(level,worker)))return;
        if(cart!=null)cart.getPersistentData().remove(OWNER_KEY);
        if(cart!=null){worker.courierCart=null;KingdomNpcData.get(level.getServer()).setDirty();}
    }
}
