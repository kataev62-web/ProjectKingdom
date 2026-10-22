package ru.projectkingdom.administration;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class AdministrativeCoreItemData {
    public static final String TOWN_ID="KingdomAdministrativeTown";
    public static final String OWNER="KingdomAdministrativeOwner";
    public static final String KIND="KingdomAdministrativeKind";
    public record Bound(UUID townId,UUID owner,AdministrativeKind kind){}
    private AdministrativeCoreItemData(){}
    public static Bound read(ItemStack stack){
        CompoundTag tag=stack.getTag();
        if(tag==null||!tag.hasUUID(TOWN_ID)||!tag.hasUUID(OWNER))return null;
        AdministrativeKind kind=AdministrativeKind.byId(tag.getString(KIND)).orElse(null);
        return kind==null?null:new Bound(tag.getUUID(TOWN_ID),tag.getUUID(OWNER),kind);
    }
    public static void write(ItemStack stack,UUID townId,UUID owner,AdministrativeKind kind){
        if(stack==null||townId==null||owner==null||kind==null)throw new IllegalArgumentException("Invalid administrative core");
        CompoundTag tag=stack.getOrCreateTag();tag.putUUID(TOWN_ID,townId);tag.putUUID(OWNER,owner);tag.putString(KIND,kind.id());
    }
}
