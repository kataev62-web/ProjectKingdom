package ru.projectkingdom.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

public final class KingdomSettlementCharterBlock extends Block implements EntityBlock {
    public KingdomSettlementCharterBlock(){
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0f).sound(SoundType.WOOD));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new KingdomSettlementCharterEntity(pos,state);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level instanceof ServerLevel server && placer instanceof ServerPlayer player
                && server.getBlockEntity(pos) instanceof KingdomSettlementCharterEntity core)
            core.placedBy(player.getUUID());
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,
                                            Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(!level.isClientSide && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof KingdomSettlementCharterEntity core)
            KingdomSettlementMenu.open(server,core);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){
        BlockEntity entity=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        // Founding monuments are never duplicateable nor relocatable once bound.
        if(entity instanceof KingdomSettlementCharterEntity core && core.founded())return List.of();
        return List.of(new ItemStack(this));
    }
}
