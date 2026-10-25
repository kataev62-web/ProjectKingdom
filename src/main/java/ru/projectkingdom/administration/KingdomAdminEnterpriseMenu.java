package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/** Remote read-only catalog; all state-changing actions use operator-only server packet. */
public final class KingdomAdminEnterpriseMenu extends AbstractContainerMenu {
    private static final int MAX_ROWS=512;
    private final List<KingdomAdminEnterpriseService.Listing> entries;
    private UUID armed;
    private long armedAt;
    private final Inventory inventory;

    public KingdomAdminEnterpriseMenu(int id,Inventory inventory,FriendlyByteBuf buf){
        this(id,inventory,read(buf));
    }
    private KingdomAdminEnterpriseMenu(int id,Inventory inventory,List<KingdomAdminEnterpriseService.Listing> rows){
        super(KingdomAdministrationRegistry.ADMIN_ENTERPRISE_MENU.get(),id);
        this.inventory=inventory;this.entries=List.copyOf(rows);
    }
    public List<KingdomAdminEnterpriseService.Listing> entries(){return entries;}
    public boolean contains(UUID id){return entries.stream().anyMatch(x->x.enterprise().equals(id));}
    public void arm(UUID id,long now){
        armed=id;armedAt=now;
    }
    public boolean consumeArm(UUID id,long now){
        boolean ok=id!=null&&id.equals(armed)&&now>=armedAt&&now-armedAt<=20_000;
        armed=null;armedAt=0;return ok;
    }
    public static void open(ServerPlayer operator){
        if(operator==null||!operator.hasPermissions(2))return;
        var entries=KingdomAdminEnterpriseService.list(operator);
        NetworkHooks.openScreen(operator,new MenuProvider(){
            @Override public Component getDisplayName(){return Component.literal("Администратор Kingdom — предприятия");}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){
                return new KingdomAdminEnterpriseMenu(id,inv,entries);
            }
        },buf->write(buf,entries));
    }
    private static void write(FriendlyByteBuf buf,List<KingdomAdminEnterpriseService.Listing> rows){
        buf.writeVarInt(rows.size());
        for(var e:rows){
            buf.writeUUID(e.enterprise());buf.writeUUID(e.owner());buf.writeUtf(e.name(),48);
            buf.writeUUID(e.townId());buf.writeUtf(e.townName(),48);
            buf.writeBoolean(e.nationId()!=null);if(e.nationId()!=null)buf.writeUUID(e.nationId());
            buf.writeUtf(e.nationName(),48);buf.writeBoolean(e.loaded());
        }
    }
    private static List<KingdomAdminEnterpriseService.Listing> read(FriendlyByteBuf buf){
        int count=buf.readVarInt();if(count<0||count>MAX_ROWS)throw new IllegalArgumentException("Enterprise admin list overflow");
        List<KingdomAdminEnterpriseService.Listing> entries=new ArrayList<>();
        for(int i=0;i<count;i++){
            UUID enterprise=buf.readUUID(),owner=buf.readUUID();String name=buf.readUtf(48);
            UUID town=buf.readUUID();String cityName=buf.readUtf(48);
            UUID nation=buf.readBoolean()?buf.readUUID():null;
            entries.add(new KingdomAdminEnterpriseService.Listing(enterprise,owner,name,town,cityName,
                    nation,buf.readUtf(48),buf.readBoolean()));
        }
        return entries;
    }
    @Override public boolean stillValid(Player player){
        return player.level().isClientSide ||
                player instanceof ServerPlayer sp && sp.hasPermissions(2);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
