package ru.projectkingdom.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomTerritories;
import ru.projectkingdom.property.KingdomRentalsData;

/**
 * NO ownership is granted by wilderness/state access: temporary blocks expire,
 * normal mined resources give no loot, and protected assets cannot be griefed.
 */
public final class KingdomTemporaryLand {
    private KingdomTemporaryLand(){}
    public static boolean temporaryArea(ServerLevel level,BlockPos pos,ServerPlayer actor){
        if(actor==null||pos==null||level==null||actor.hasPermissions(2))return false;
        String dim=level.dimension().location().toString();
        var civic=KingdomCivicsData.get(level.getServer());
        if(KingdomRentalsData.get(level.getServer()).atRoom(dim,pos)!=null)return false;
        var plot=civic.plot(level,pos);
        if(plot==null){
            return KingdomTerritories.get(level.getServer()).legacyClaimOwner(dim,
                    pos.getX()>>4,pos.getZ()>>4)==null;
        }
        if(plot.tenant()!=null || plot.zone()!=KingdomCivicsData.Zone.PUBLIC)return false;
        var town=civic.townById(plot.town());
        if(town==null || !town.mayor().equals(civic.plotOwner(dim,pos.getX()>>4,pos.getZ()>>4)))
            return false; // privately sold chunks are never public excavation ground
        return !civic.allowed(actor.getUUID(),dim,pos.getX(),pos.getZ(),KingdomCivicsData.Action.BUILD);
    }
    /** Foundation block must be placeable on undeveloped land, not timed away. */
    public static boolean permanentCharter(BlockState state) {
        return state.getBlock() instanceof KingdomSettlementCharterBlock;
    }
    /** Items, functional tile entities, explosives and valuable cores are out of bounds. */
    public static boolean safeToBreak(ServerLevel level,BlockPos pos,BlockState state){
        return !state.isAir()&&!level.getBlockState(pos).isAir()
                &&state.getDestroySpeed(level,pos)>=0
                &&!(state.getBlock() instanceof EntityBlock)
                &&!(state.getBlock() instanceof TntBlock)
                &&!(state.getBlock() instanceof KingdomSettlementCharterBlock)
                &&!level.getBlockState(pos).is(ru.projectkingdom.administration.KingdomAdministrationRegistry.TOWN_HALL.get())
                &&level.getBlockEntity(pos)==null
                &&!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    .getNamespace().equals("kingdomnpc");
    }
    public static boolean safeTemporaryPlacement(ServerLevel level,BlockPos pos,BlockState state){
        return !state.isAir()&&state.getDestroySpeed(level,pos)>=0
                &&!(state.getBlock() instanceof EntityBlock)
                &&!(state.getBlock() instanceof TntBlock)
                &&level.getBlockEntity(pos)==null
                &&!permanentCharter(state)
                &&!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    .getNamespace().equals("kingdomnpc")
                &&state.isCollisionShapeFullBlock(level,pos);
    }
}
