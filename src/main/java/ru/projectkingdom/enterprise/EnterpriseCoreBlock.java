package ru.projectkingdom.enterprise;

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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomTerritories;

/** Shared secure core block parameterized by one private enterprise type. */
public final class EnterpriseCoreBlock extends Block implements EntityBlock {
    private final EnterpriseKind kind;
    public EnterpriseCoreBlock(EnterpriseKind kind){
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.5f).sound(SoundType.WOOD));
        if(kind==null||!kind.placeable())throw new IllegalArgumentException("Core kind must be placeable");
        this.kind=kind;
    }
    public EnterpriseKind kind(){return kind;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new EnterpriseCoreBlockEntity(pos,state);}

    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!(level instanceof ServerLevel server)||!(placer instanceof ServerPlayer player))return;
        var data=KingdomEnterpriseData.get(server.getServer());
        EnterpriseCoreItemData.Bound bound=EnterpriseCoreItemData.read(stack);
        UUIDHolder identity;
        if(bound==null){
            if(!KingdomTerritories.get(server.getServer()).canUse(server,pos,player.getUUID())){
                reject(server,pos,player,stack,"Сначала зарегистрируйте свою территорию.");return;
            }
            var firm=data.create(player.getUUID(),kind.defaultPrefix()+" "+(data.ownedAll(player.getUUID()).size()+1),kind);
            if(firm==null){reject(server,pos,player,stack,"Предприятие не создано: достигнут лимит.");return;}
            identity=new UUIDHolder(firm.id(),firm.owner());
        }else{
            if(!bound.owner().equals(player.getUUID())||bound.kind()!=kind){reject(server,pos,player,stack,"Это ядро принадлежит другому владельцу или типу.");return;}
            identity=new UUIDHolder(bound.enterpriseId(),bound.owner());
        }
        var activation=data.activateCore(player.getUUID(),identity.enterprise(),kind,server.dimension().location().toString(),pos.asLong());
        if(activation!=EnterpriseActivation.ACTIVATED){reject(server,pos,player,stack,"Ядро не активировано: "+activation);return;}
        if(server.getBlockEntity(pos) instanceof EnterpriseCoreBlockEntity core)core.bind(identity.enterprise(),identity.owner(),kind);
    }

    private void reject(ServerLevel level,BlockPos pos,ServerPlayer player,ItemStack placed,String reason){
        if(!player.getAbilities().instabuild){ItemStack returned=new ItemStack(this);var bound=EnterpriseCoreItemData.read(placed);if(bound!=null)EnterpriseCoreItemData.write(returned,bound.enterpriseId(),bound.owner(),bound.kind());player.addItem(returned);}
        level.removeBlock(pos,false);player.sendSystemMessage(Component.literal("[Project Kingdom] "+reason));
    }

    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(level instanceof ServerLevel server&&server.getBlockEntity(pos) instanceof EnterpriseCoreBlockEntity core&&core.bound()){
            KingdomEnterpriseData.get(server.getServer()).suspendCore(player.getUUID(),core.enterpriseId(),server.dimension().location().toString(),pos.asLong());
        }
        super.playerWillDestroy(level,pos,state,player);
    }

    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){
        BlockEntity entity=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        net.minecraft.world.entity.Entity actor=builder.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if(entity instanceof EnterpriseCoreBlockEntity core&&core.bound()&&actor instanceof Player player&&core.owner().equals(player.getUUID())){
            ItemStack result=new ItemStack(this);EnterpriseCoreItemData.write(result,core.enterpriseId(),core.owner(),core.kind());return List.of(result);
        }
        return List.of();
    }

    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof EnterpriseCoreBlockEntity core&&core.bound())
            EnterpriseCoreMenu.open(server,core);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private record UUIDHolder(java.util.UUID enterprise,java.util.UUID owner){}
}
