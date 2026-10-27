package ru.projectkingdom.settlement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.npc.KingdomNpcMod;

/** Real server-only public/uncharted land behavior. No fake or duplicated loot. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomOpenLandEvents {
    private KingdomOpenLandEvents(){}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void breakOnOpenLand(BlockEvent.BreakEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)
                ||!(event.getPlayer() instanceof ServerPlayer player)
                ||event.isCanceled())return;
        var pos=event.getPos();
        if(!KingdomTemporaryLand.temporaryArea(level,pos,player))return;
        BlockState state=level.getBlockState(pos);
        // An unregistered charter must be recoverable by its actual placer.
        // Otherwise a rejected founding site becomes a permanent world obstacle.
        if(level.getBlockEntity(pos) instanceof KingdomSettlementCharterEntity marker){
            if(!marker.founded()&&player.getUUID().equals(marker.placer()))return;
            event.setCanceled(true);return;
        }
        // A yet-unfounded town hall can be recovered by its original owner.
        if(level.getBlockEntity(pos) instanceof
                ru.projectkingdom.administration.AdministrativeCoreBlockEntity hall
                &&state.is(ru.projectkingdom.administration.KingdomAdministrationRegistry.TOWN_HALL.get())){
            if(!hall.bound()&&player.getUUID().equals(hall.placer()))return;
            event.setCanceled(true);return;
        }
        if(!KingdomTemporaryLand.safeToBreak(level,pos,state)){event.setCanceled(true);return;}
        // Vanilla break drops / XP do NOT run for this action. Ordinary land
        // is destructible but cannot become a free mining/farming resource.
        event.setCanceled(true);
        KingdomTemporaryBlocks.get(player.getServer()).forget(level,pos);
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void placeOnOpenLand(BlockEvent.EntityPlaceEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)
                ||!(event.getEntity() instanceof ServerPlayer player)
                ||event.isCanceled())return;
        var pos=event.getPos();
        if(!KingdomTemporaryLand.temporaryArea(level,pos,player))return;
        var state=level.getBlockState(pos);
        if(KingdomTemporaryLand.permanentCharter(state)){
            var civic=ru.projectkingdom.npc.KingdomCivicsData.get(player.getServer());
            var legacy=ru.projectkingdom.npc.KingdomTerritories.get(player.getServer());
            String dim=level.dimension().location().toString();
            if(civic.plot(level,pos)!=null || legacy.legacyClaimOwner(dim,pos.getX()>>4,pos.getZ()>>4)!=null)
                event.setCanceled(true);
            return;
        }
        var temporary=KingdomTemporaryBlocks.get(player.getServer());
        if(!KingdomTemporaryLand.safeTemporaryPlacement(level,pos,state)
                ||!temporary.track(level,pos))event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null||server.getTickCount()%20!=0)return;
        KingdomTemporaryBlocks.get(server).tick(server);
    }
}
