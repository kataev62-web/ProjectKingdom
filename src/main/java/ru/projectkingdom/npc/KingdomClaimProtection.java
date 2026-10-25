package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.administration.AdministrativeCoreBlockEntity;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;

/** Server-side protection for city claims; wilderness is unaffected. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomClaimProtection {
    private KingdomClaimProtection() {}
    private static boolean denied(Player player,BlockPos pos,KingdomCivicsData.Action action) {
        if(!(player instanceof ServerPlayer p)||p.hasPermissions(2))return false;
        ServerLevel level=p.serverLevel();
        KingdomCivicsData data=KingdomCivicsData.get(p.getServer());
        String dimension=level.dimension().location().toString();
        // A lawful room tenancy is more specific than the surrounding chunk
        // owner/tenant. This is NOT a lock-picking or criminal-access mechanic.
        var room=ru.projectkingdom.property.KingdomRentalsData.get(p.getServer())
                .roomAccess(p.getUUID(),dimension,pos,action,System.currentTimeMillis());
        if(room==ru.projectkingdom.property.KingdomRentalsData.Access.DENIED)return true;
        if(room==ru.projectkingdom.property.KingdomRentalsData.Access.ALLOWED)return false;
        // Shared corridors are not free-loot zones. A lawful resident may
        // operate passage blocks outside the boundaries of their room, but
        // NOT arbitrary chests, furnaces, machines, or warehouse containers.
        if(action==KingdomCivicsData.Action.INTERACT
                && ru.projectkingdom.property.KingdomRentalsData.get(p.getServer())
                        .residentOfBuilding(p.getUUID(),dimension,pos,System.currentTimeMillis())
                && level.hasChunkAt(pos)){
            var block=level.getBlockState(pos).getBlock();
            if(block instanceof net.minecraft.world.level.block.DoorBlock
                    ||block instanceof net.minecraft.world.level.block.TrapDoorBlock
                    ||block instanceof net.minecraft.world.level.block.FenceGateBlock
                    ||block instanceof net.minecraft.world.level.block.ButtonBlock
                    ||block instanceof net.minecraft.world.level.block.LeverBlock)
                return false;
        }
        if(data.plot(level,pos)==null)return false;
        return !data.allowed(p.getUUID(),dimension,pos.getX(),pos.getZ(),action);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if(denied(event.getPlayer(),event.getPos(),KingdomCivicsData.Action.BREAK))event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if(event.getEntity() instanceof Player player && denied(player,event.getPos(),KingdomCivicsData.Action.BUILD))event.setCanceled(true);
    }
    /** Access to the exact active town hall is public, not a general claim permission. */
    private static boolean publicTownHall(ServerPlayer player, BlockPos pos) {
        ServerLevel level=player.serverLevel();
        if(!level.hasChunkAt(pos))return false;
        if(!(level.getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core)
                ||!core.bound()||(core.kind()!=AdministrativeKind.TOWN_HALL
                    && !ru.projectkingdom.administration.AdministrativePermissionService.mayUsePublic(core.kind())))
            return false;
        var civics=KingdomCivicsData.get(player.getServer());
        var town=civics.townAt(level,pos);
        if(town==null||!town.id().equals(core.townId()))return false;
        var binding=KingdomAdministrationData.get(player.getServer())
                .core(town.id(),core.kind());
        return binding!=null
                && binding.dimension().equals(level.dimension().location().toString())
                && binding.packedPos()==pos.asLong();
    }
    /** Guests may reach a REAL active reception, never the hotel's storage or private rooms. */
    private static boolean publicReception(ServerPlayer player, BlockPos pos) {
        var level=player.serverLevel();
        if(!level.hasChunkAt(pos))return false;
        if(!(level.getBlockEntity(pos) instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity core)
                ||!core.bound()||(core.kind()!=ru.projectkingdom.enterprise.EnterpriseKind.HOTEL
                &&core.kind()!=ru.projectkingdom.enterprise.EnterpriseKind.TAVERN))return false;
        var firms=KingdomEnterpriseData.get(player.getServer());
        var binding=firms.core(core.enterpriseId());
        var firm=firms.find(core.enterpriseId());
        if(binding==null||firm==null||!binding.active()
                ||!firm.owner().equals(core.owner())||binding.kind()!=core.kind()
                ||!binding.dimension().equals(level.dimension().location().toString())
                ||binding.packedPos()!=pos.asLong())return false;
        // A reception inside somebody's private room is never public.
        var rentals=ru.projectkingdom.property.KingdomRentalsData.get(player.getServer());
        return rentals.atRoom(level.dimension().location().toString(),pos)==null;
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getEntity() instanceof ServerPlayer player
                && (publicTownHall(player,event.getPos())||publicReception(player,event.getPos())))
            return;
        if(denied(event.getEntity(),event.getPos(),KingdomCivicsData.Action.INTERACT))event.setCanceled(true);
    }
    @SubscribeEvent public static void explode(ExplosionEvent.Detonate event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        KingdomCivicsData data=KingdomCivicsData.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos->data.plot(level,pos)!=null);
    }
}
