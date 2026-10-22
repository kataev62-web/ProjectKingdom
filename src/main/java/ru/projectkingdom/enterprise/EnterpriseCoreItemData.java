package ru.projectkingdom.enterprise;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Stable identity stored on a relocated enterprise-core item. */
public final class EnterpriseCoreItemData {
    public static final String ENTERPRISE_ID="KingdomEnterpriseId";
    public static final String OWNER="KingdomEnterpriseOwner";
    public static final String KIND="KingdomEnterpriseKind";
    public record Bound(UUID enterpriseId,UUID owner,EnterpriseKind kind){}
    private EnterpriseCoreItemData(){}

    public static Bound read(ItemStack stack){
        CompoundTag tag=stack.getTag();
        if(tag==null||!tag.hasUUID(ENTERPRISE_ID)||!tag.hasUUID(OWNER))return null;
        EnterpriseKind kind=EnterpriseKind.byId(tag.getString(KIND));
        return kind.placeable()?new Bound(tag.getUUID(ENTERPRISE_ID),tag.getUUID(OWNER),kind):null;
    }
    public static void write(ItemStack stack,UUID enterprise,UUID owner,EnterpriseKind kind){
        if(stack==null||enterprise==null||owner==null||kind==null||!kind.placeable())
            throw new IllegalArgumentException("Invalid bound enterprise core");
        CompoundTag tag=stack.getOrCreateTag();tag.putUUID(ENTERPRISE_ID,enterprise);tag.putUUID(OWNER,owner);tag.putString(KIND,kind.id());
    }
}
