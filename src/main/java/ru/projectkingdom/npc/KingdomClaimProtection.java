package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
    private static final TagKey<Block> TFC_FELLABLE_LOGS=TagKey.create(
            Registries.BLOCK,new ResourceLocation("tfc","logs_that_log"));

    /** TFC fellable trunks and vanilla/log leaves must be protected even on public city plots. */
    public static boolean isTreePart(BlockState state){
        return state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.is(TFC_FELLABLE_LOGS);
    }

    /**
     * TFC can remove many connected logs from a single axe hit without raising a
     * separate player BreakEvent for every log. Preflight connected tree wood:
     * reject felling when it would cross into a plot the player cannot harvest.
     *
     * Loaded chunks only, bounded radius/count: never generate terrain or
     * scan an unbounded forest. An unknown connected section fails closed.
     */
    private static boolean treeFellingTouchesForeignLand(ServerPlayer player,BlockPos origin) {
        var level=player.serverLevel();
        if(!level.hasChunkAt(origin)||!level.getBlockState(origin).is(TFC_FELLABLE_LOGS))return false;
        var queue=new ArrayDeque<BlockPos>();
        Set<BlockPos> visited=new HashSet<>();
        queue.add(origin.immutable());
        var civics=KingdomCivicsData.get(player.getServer());
        String dim=level.dimension().location().toString();
        while(!queue.isEmpty()) {
            if(visited.size()>=512)return true;
            BlockPos pos=queue.removeFirst();
            if(!visited.add(pos))continue;
            if(!level.hasChunkAt(pos))return true;
            var state=level.getBlockState(pos);
            if(!state.is(TFC_FELLABLE_LOGS))continue;
            if(denied(player,pos,KingdomCivicsData.Action.BREAK))return true;
            // Tree leaves overhanging somebody else's land must not be felled either.
            for(Direction side:Direction.values()){
                BlockPos foliage=pos.relative(side);
                if(!level.hasChunkAt(foliage))return true;
                if(level.getBlockState(foliage).is(BlockTags.LEAVES)
                        &&denied(player,foliage,KingdomCivicsData.Action.BREAK))return true;
            }
            // TFC branches may travel diagonally, so include all touching wood.
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dy==0&&dz==0)continue;
                BlockPos next=pos.offset(dx,dy,dz);
                if(Math.abs(next.getX()-origin.getX())>12
                        ||Math.abs(next.getZ()-origin.getZ())>12
                        ||Math.abs(next.getY()-origin.getY())>32)continue;
                if(!level.hasChunkAt(next))return true;
                if(!visited.contains(next)&&level.getBlockState(next).is(TFC_FELLABLE_LOGS))
                    queue.addLast(next.immutable());
            }
        }
        return false;
    }
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
        // Public municipal land never grants visitors the right to cut trees:
        // the temporary no-loot excavation exception is for non-tree blocks.
        if((action==KingdomCivicsData.Action.BREAK||action==KingdomCivicsData.Action.BUILD)
                &&level.hasChunkAt(pos)&&isTreePart(level.getBlockState(pos))){
            if(data.plot(level,pos)!=null)
                return !data.allowed(p.getUUID(),dimension,pos.getX(),pos.getZ(),action);
            UUID legacyOwner=KingdomTerritories.get(p.getServer()).owner(level,pos);
            if(legacyOwner!=null)return !legacyOwner.equals(p.getUUID());
        }
        // Visitors may place only short-lived inert blocks or break ordinary
        // natural materials without loot on state public or unclaimed land.
        // Their actions are handled by KingdomOpenLandEvents after this check.
        if(action==KingdomCivicsData.Action.BUILD||action==KingdomCivicsData.Action.BREAK)
            if(ru.projectkingdom.settlement.KingdomTemporaryLand.temporaryArea(level,pos,p))return false;
        if(data.plot(level,pos)==null){
            UUID legacyOwner=KingdomTerritories.get(p.getServer()).owner(level,pos);
            return legacyOwner!=null&&!legacyOwner.equals(p.getUUID());
        }
        return !data.allowed(p.getUUID(),dimension,pos.getX(),pos.getZ(),action);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if(event.getPlayer() instanceof ServerPlayer player
                &&treeFellingTouchesForeignLand(player,event.getPos())){
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[Земля] Дерево пересекает чужой участок. Рубка запрещена."));
            return;
        }
        if(event.getLevel() instanceof ServerLevel level
                &&level.getBlockEntity(event.getPos()) instanceof ru.projectkingdom.settlement.KingdomSettlementCharterEntity charter
                &&charter.founded()){
            event.setCanceled(true);return;
        }
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
    /** Hotel guests may open COMMON entrance doors, not private room doors. */
    private static boolean publicHospitalityPassage(ServerPlayer player,BlockPos pos) {
        var level=player.serverLevel();
        if(!level.hasChunkAt(pos))return false;
        var block=level.getBlockState(pos).getBlock();
        if(!(block instanceof net.minecraft.world.level.block.DoorBlock
                ||block instanceof net.minecraft.world.level.block.TrapDoorBlock
                ||block instanceof net.minecraft.world.level.block.FenceGateBlock))return false;
        var rentals=ru.projectkingdom.property.KingdomRentalsData.get(player.getServer());
        String dim=level.dimension().location().toString();
        var building=rentals.atBuilding(dim,pos);
        if(building==null||rentals.atRoom(dim,pos)!=null)return false;
        UUID enterprise=rentals.receptionForBuilding(building.id());
        if(enterprise==null)return false;
        var companies=KingdomEnterpriseData.get(player.getServer());
        var firm=companies.find(enterprise);
        var registered=companies.core(enterprise);
        if(firm==null||registered==null||!registered.active()
                ||(registered.kind()!=ru.projectkingdom.enterprise.EnterpriseKind.HOTEL
                &&registered.kind()!=ru.projectkingdom.enterprise.EnterpriseKind.TAVERN)
                ||!firm.owner().equals(building.owner())
                ||!registered.dimension().equals(dim))return false;
        BlockPos reception=BlockPos.of(registered.packedPos());
        if(!building.bounds().contains(reception)||!level.hasChunkAt(reception))return false;
        return level.getBlockEntity(reception) instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity core
                &&core.bound()&&enterprise.equals(core.enterpriseId())
                &&registered.kind()==core.kind()&&firm.owner().equals(core.owner());
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getEntity() instanceof ServerPlayer player
                && (publicTownHall(player,event.getPos())||publicReception(player,event.getPos())
                    ||publicHospitalityPassage(player,event.getPos())))
            return;
        if(denied(event.getEntity(),event.getPos(),KingdomCivicsData.Action.INTERACT))event.setCanceled(true);
    }
    @SubscribeEvent public static void explode(ExplosionEvent.Detonate event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        KingdomCivicsData data=KingdomCivicsData.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos->data.plot(level,pos)!=null);
    }
}
