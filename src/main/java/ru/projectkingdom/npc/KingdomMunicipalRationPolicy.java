package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.administration.*;

/**
 * Public food rations are opt-in by the town's authorized warehouse manager.
 * This policy never grants land, items, money or citizenship.
 */
public final class KingdomMunicipalRationPolicy extends SavedData {
    private static final String KEY="kingdom_municipal_rations_v1";
    private final Set<UUID> towns=new LinkedHashSet<>();
    public static KingdomMunicipalRationPolicy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomMunicipalRationPolicy::load,KingdomMunicipalRationPolicy::new,KEY);
    }
    public boolean enabled(UUID town){return town!=null&&towns.contains(town);}
    public boolean setAuthorized(ServerPlayer player,UUID town,boolean enabled){
        if(player==null||town==null)return false;
        var civics=KingdomCivicsData.get(player.getServer());
        var city=civics.townById(town);
        if(city==null||!civics.isMember(town,player.getUUID()))return false;
        var admin=KingdomAdministrationData.get(player.getServer());
        if(!AdministrativePermissionService.mayManage(player.getUUID(),
                admin.founder(town),city.mayor(),admin.roles(town,player.getUUID()),
                AdministrativeKind.CENTRAL_WAREHOUSE))return false;
        var reserves=KingdomMunicipalFoodReserves.get(player.getServer());
        if(reserves.policy(town)==null)return false;
        if(enabled?towns.add(town):towns.remove(town))setDirty();
        return true;
    }
    static KingdomMunicipalRationPolicy load(CompoundTag tag){
        var data=new KingdomMunicipalRationPolicy();
        for(Tag row:tag.getList("Towns",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)row;
            if(t.hasUUID("Town"))data.towns.add(t.getUUID("Town"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var list=new ListTag();
        for(UUID town:towns){
            var t=new CompoundTag();t.putUUID("Town",town);list.add(t);
        }
        tag.put("Towns",list);return tag;
    }
}
