package ru.projectkingdom.administration;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import ru.projectkingdom.npc.KingdomCivicsData;

public final class AdministrativeCoreMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final BlockPos corePos;
    private final UUID townId,founder,mayor;
    private final AdministrativeKind kind;
    private final String townName;
    private final boolean manager,publicAccess;

    public AdministrativeCoreMenu(int id,Inventory inventory,FriendlyByteBuf buffer){
        this(id,inventory,buffer.readBlockPos(),buffer.readUUID(),AdministrativeKind.byId(buffer.readUtf(32)).orElseThrow(),
                buffer.readUtf(48),buffer.readUUID(),buffer.readUUID(),buffer.readBoolean(),buffer.readBoolean());
    }
    private AdministrativeCoreMenu(int id,Inventory inventory,BlockPos corePos,UUID townId,AdministrativeKind kind,
                                   String townName,UUID founder,UUID mayor,boolean manager,boolean publicAccess){
        super(KingdomAdministrationRegistry.CORE_MENU.get(),id);this.inventory=inventory;this.corePos=corePos;this.townId=townId;
        this.kind=kind;this.townName=townName;this.founder=founder;this.mayor=mayor;this.manager=manager;this.publicAccess=publicAccess;
    }
    public static void open(ServerPlayer player,AdministrativeCoreBlockEntity core){
        if(core==null||!core.bound())return;
        KingdomCivicsData civics=KingdomCivicsData.get(player.getServer());var town=civics.townById(core.townId());if(town==null)return;
        boolean publicAccess=AdministrativePermissionService.mayUsePublic(core.kind());
        if(!publicAccess&&!civics.isMember(town.id(),player.getUUID())){
            player.sendSystemMessage(Component.literal("[Project Kingdom] Эта городская служба недоступна посторонним."));return;
        }
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());UUID founder=data.founder(town.id());if(founder==null)return;
        boolean manager=AdministrativePermissionService.mayManage(player.getUUID(),founder,town.mayor(),data.roles(town.id(),player.getUUID()),core.kind());
        NetworkHooks.openScreen(player,new MenuProvider(){
            @Override public Component getDisplayName(){return Component.literal(core.kind().russianName());}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player ignored){
                return new AdministrativeCoreMenu(id,inv,core.getBlockPos(),town.id(),core.kind(),town.name(),founder,town.mayor(),manager,publicAccess);
            }
        },buffer->{buffer.writeBlockPos(core.getBlockPos());buffer.writeUUID(town.id());buffer.writeUtf(core.kind().id(),32);
            buffer.writeUtf(town.name(),48);buffer.writeUUID(founder);buffer.writeUUID(town.mayor());buffer.writeBoolean(manager);buffer.writeBoolean(publicAccess);});
    }
    public BlockPos corePos(){return corePos;}public UUID townId(){return townId;}public AdministrativeKind kind(){return kind;}public String townName(){return townName;}
    public UUID founder(){return founder;}public UUID mayor(){return mayor;}public boolean manager(){return manager;}public boolean publicAccess(){return publicAccess;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){
        if(player.level()!=inventory.player.level()||corePos.distSqr(player.blockPosition())>64||!player.level().hasChunkAt(corePos))return false;
        if(!(player.level().getBlockEntity(corePos) instanceof AdministrativeCoreBlockEntity core)
                ||!townId.equals(core.townId())||kind!=core.kind())return false;
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());
        AdministrativeCoreBinding active=data.core(townId,kind);
        return active!=null&&active.dimension().equals(player.level().dimension().location().toString())&&active.packedPos()==corePos.asLong();
    }
}
