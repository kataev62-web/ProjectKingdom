package ru.projectkingdom.enterprise;

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
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomEnterprisePayrollData;

/** Slot-free management menu anchored to one loaded enterprise core. */
public final class EnterpriseCoreMenu extends AbstractContainerMenu {
    public static final int DEPOSIT_100=100,WITHDRAW_100=101,BIND_CASHBOX=110,BIND_RAW=111,BIND_FINISHED=112;
    private final Inventory inventory;private final BlockPos corePos;private final UUID enterpriseId;private final EnterpriseKind kind;
    private final String enterpriseName;private final UUID owner;private final long cash;private final int workers;private final long debt;
    private final String cashboxState,rawState,finishedState;private final boolean manager;

    public EnterpriseCoreMenu(int id,Inventory inventory,FriendlyByteBuf buffer){
        this(id,inventory,buffer.readBlockPos(),buffer.readUUID(),EnterpriseKind.byId(buffer.readUtf(24)),buffer.readUtf(48),
                buffer.readUUID(),buffer.readLong(),buffer.readInt(),buffer.readLong(),buffer.readUtf(32),buffer.readUtf(32),buffer.readUtf(32),buffer.readBoolean());
    }
    private EnterpriseCoreMenu(int id,Inventory inventory,BlockPos pos,UUID enterprise,EnterpriseKind kind,String name,UUID owner,
                               long cash,int workers,long debt,String cashbox,String raw,String finished,boolean manager){
        super(KingdomEnterpriseRegistry.CORE_MENU.get(),id);this.inventory=inventory;this.corePos=pos;this.enterpriseId=enterprise;this.kind=kind;
        this.enterpriseName=name;this.owner=owner;this.cash=cash;this.workers=workers;this.debt=debt;
        this.cashboxState=cashbox;this.rawState=raw;this.finishedState=finished;this.manager=manager;
    }
    public static void open(ServerPlayer player,EnterpriseCoreBlockEntity core){
        if(core==null||!core.bound())return;var data=KingdomEnterpriseData.get(player.getServer());var firm=data.find(core.enterpriseId());if(firm==null)return;
        var payroll=KingdomEnterprisePayrollData.get(player.getServer());int workers=(int)payroll.all().stream().filter(e->e.enterprise().equals(firm.id())&&e.active()).count();
        long debt=payroll.all().stream().filter(e->e.enterprise().equals(firm.id())).mapToLong(e->e.owed()).reduce(0,(a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);
        String cashbox=status(player,data,firm.id(),EnterpriseBindingRole.CASHBOX),raw=status(player,data,firm.id(),EnterpriseBindingRole.RAW_STORAGE),finished=status(player,data,firm.id(),EnterpriseBindingRole.FINISHED_STORAGE);
        boolean manager=data.mayManage(player.getUUID(),firm.id());
        NetworkHooks.openScreen(player,new MenuProvider(){
            @Override public Component getDisplayName(){return Component.literal(firm.name());}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player ignored){return new EnterpriseCoreMenu(id,inv,core.getBlockPos(),firm.id(),core.kind(),firm.name(),firm.owner(),firm.cash(),workers,debt,cashbox,raw,finished,manager);}
        },buffer->{buffer.writeBlockPos(core.getBlockPos());buffer.writeUUID(firm.id());buffer.writeUtf(core.kind().id());buffer.writeUtf(firm.name(),48);buffer.writeUUID(firm.owner());buffer.writeLong(firm.cash());buffer.writeInt(workers);buffer.writeLong(debt);buffer.writeUtf(cashbox);buffer.writeUtf(raw);buffer.writeUtf(finished);buffer.writeBoolean(manager);});
    }
    private static String status(ServerPlayer player,KingdomEnterpriseData data,UUID firm,EnterpriseBindingRole role){return EnterpriseAccessService.snapshot(player.serverLevel(),data,firm,role).status().name();}
    public UUID enterpriseId(){return enterpriseId;}public EnterpriseKind kind(){return kind;}public String enterpriseName(){return enterpriseName;}
    public UUID owner(){return owner;}public long cash(){return cash;}public int workers(){return workers;}public long debt(){return debt;}
    public String cashboxState(){return cashboxState;}public String rawState(){return rawState;}public String finishedState(){return finishedState;}public boolean manager(){return manager;}
    @Override public boolean clickMenuButton(Player player,int action){
        if(!(player instanceof ServerPlayer server)||!stillValid(player))return false;var data=KingdomEnterpriseData.get(server.getServer());
        return switch(action){
            case DEPOSIT_100->data.depositAtCore(server,enterpriseId,100);
            case WITHDRAW_100->data.withdrawAtCore(server,enterpriseId,100);
            case BIND_CASHBOX->EnterpriseAccessService.begin(server,enterpriseId,EnterpriseBindingRole.CASHBOX);
            case BIND_RAW->EnterpriseAccessService.begin(server,enterpriseId,EnterpriseBindingRole.RAW_STORAGE);
            case BIND_FINISHED->EnterpriseAccessService.begin(server,enterpriseId,EnterpriseBindingRole.FINISHED_STORAGE);
            default->false;};
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){
        if(player.level()!=inventory.player.level()||corePos.distSqr(player.blockPosition())>64||!player.level().hasChunkAt(corePos))return false;
        return player.level().getBlockEntity(corePos) instanceof EnterpriseCoreBlockEntity core&&enterpriseId.equals(core.enterpriseId())&&kind==core.kind();
    }
}
