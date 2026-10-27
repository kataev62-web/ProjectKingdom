package ru.projectkingdom.administration;

import java.util.List;
import java.util.UUID;
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
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import ru.projectkingdom.npc.KingdomCivicsData;

public final class AdministrativeCoreBlock extends Block implements EntityBlock {
    private final AdministrativeKind kind;
    public AdministrativeCoreBlock(AdministrativeKind kind){
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(4.0f).sound(SoundType.WOOD));
        if(kind==null)throw new IllegalArgumentException("kind");this.kind=kind;
    }
    public AdministrativeKind kind(){return kind;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AdministrativeCoreBlockEntity(pos,state);}

    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!(level instanceof ServerLevel server)||!(placer instanceof ServerPlayer player))return;
        KingdomCivicsData civics=KingdomCivicsData.get(server.getServer());
        KingdomCivicsData.Town town=civics.townAt(server,pos);
        // The town hall doubles as the founding block on unclaimed land.
        if(kind==AdministrativeKind.TOWN_HALL && town==null){
            if(server.getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core)
                core.markFounding(player.getUUID());
            return;
        }
        if(town==null||!civics.isMember(town.id(),player.getUUID())){reject(server,pos,player,stack,"Блок можно поставить только в своём городе.");return;}
        KingdomAdministrationData data=KingdomAdministrationData.get(server.getServer());
        UUID founder=data.founder(town.id());
        if(founder==null){reject(server,pos,player,stack,"У города не зарегистрирован основатель.");return;}
        if(kind==AdministrativeKind.TOWN_HALL&&!player.getUUID().equals(founder)){
            reject(server,pos,player,stack,"Ратушу может установить только основатель.");return;
        }
        if(kind!=AdministrativeKind.TOWN_HALL&&data.core(town.id(),AdministrativeKind.TOWN_HALL)==null){
            reject(server,pos,player,stack,"Сначала установите ратушу.");return;
        }
        AdministrativeCoreItemData.Bound bound=AdministrativeCoreItemData.read(stack);
        if(bound!=null&&(!bound.townId().equals(town.id())||bound.kind()!=kind||!bound.owner().equals(founder))){
            reject(server,pos,player,stack,"Этот блок привязан к другому городу или службе.");return;
        }
        AdministrativeCoreBinding old=data.core(town.id(),kind);
        if(old!=null){
            if(!old.dimension().equals(server.dimension().location().toString())){
                reject(server,pos,player,stack,"Прежний блок находится в другом измерении.");return;
            }
            BlockPos oldPos=BlockPos.of(old.packedPos());
            if(!server.hasChunkAt(oldPos)){
                reject(server,pos,player,stack,"Нельзя проверить прежний блок: его чанк не загружен.");return;
            }
            if(server.getBlockEntity(oldPos) instanceof AdministrativeCoreBlockEntity previous&&previous.bound()){
                reject(server,pos,player,stack,"Такая городская служба уже активна.");return;
            }
            if(!data.suspendCore(player.getUUID(),town.mayor(),old)){
                reject(server,pos,player,stack,"Нет прав заменить прежний блок.");return;
            }
        }
        AdministrativeCoreBinding binding=new AdministrativeCoreBinding(town.id(),kind,server.dimension().location().toString(),pos.asLong());
        if(!data.activateCore(player.getUUID(),town.mayor(),binding)){
            reject(server,pos,player,stack,"Нет прав активировать городскую службу.");return;
        }
        if(server.getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core)core.bind(town.id(),founder,kind);
    }
    private void reject(ServerLevel level,BlockPos pos,ServerPlayer player,ItemStack placed,String reason){
        if(!player.getAbilities().instabuild){
            ItemStack returned=new ItemStack(this);AdministrativeCoreItemData.Bound bound=AdministrativeCoreItemData.read(placed);
            if(bound!=null)AdministrativeCoreItemData.write(returned,bound.townId(),bound.owner(),bound.kind());player.addItem(returned);
        }
        level.removeBlock(pos,false);player.sendSystemMessage(Component.literal("[Project Kingdom] "+reason));
    }
    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(level instanceof ServerLevel server&&server.getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core&&core.bound()){
            var town=KingdomCivicsData.get(server.getServer()).townById(core.townId());
            if(town!=null)KingdomAdministrationData.get(server.getServer()).suspendCore(player.getUUID(),town.mayor(),
                    new AdministrativeCoreBinding(core.townId(),core.kind(),server.dimension().location().toString(),pos.asLong()));
        }
        super.playerWillDestroy(level,pos,state,player);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){
        BlockEntity entity=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        net.minecraft.world.entity.Entity actor=builder.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if(entity instanceof AdministrativeCoreBlockEntity core&&core.bound()&&actor instanceof Player player&&core.owner().equals(player.getUUID())){
            ItemStack result=new ItemStack(this);AdministrativeCoreItemData.write(result,core.townId(),core.owner(),core.kind());return List.of(result);
        }
        if(entity instanceof AdministrativeCoreBlockEntity core&&!core.bound()
                &&actor instanceof Player player&&player.getUUID().equals(core.placer()))
            return List.of(new ItemStack(this));
        return List.of();
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer server
                &&level.getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core){
            if(core.bound())AdministrativeCoreMenu.open(server,core);
            else if(kind==AdministrativeKind.TOWN_HALL)
                ru.projectkingdom.settlement.KingdomSettlementMenu.open(server,core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
