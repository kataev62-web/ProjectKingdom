package ru.projectkingdom.enterprise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomMainMenu;
import ru.projectkingdom.property.KingdomRentalsData;
import ru.projectkingdom.property.KingdomRentalsData.Room;

/**
 * The in-world reception desk: a fully server-side hospitality GUI.
 * No bank menu, remote lease action, fake money, or client-owned rights.
 */
public final class KingdomHospitalityMenu extends ChestMenu {
    private static final int FIRST=9,LAST=20,PAGE_SIZE=12;
    private final ServerPlayer viewer;
    private final SimpleContainer display;
    private final UUID enterprise;
    private final BlockPos corePos;
    private final EnterpriseKind kind;
    private final Map<Integer,UUID> slots=new HashMap<>();
    private int page;
    private UUID selected;

    private KingdomHospitalityMenu(int id,Inventory inventory,ServerPlayer viewer,
                                   UUID enterprise,BlockPos corePos,EnterpriseKind kind,
                                   SimpleContainer display){
        super(MenuType.GENERIC_9x3,id,inventory,display,3);
        this.viewer=viewer;this.enterprise=enterprise;
        this.corePos=corePos.immutable();this.kind=kind;this.display=display;
        render();
    }
    public static void open(ServerPlayer player,EnterpriseCoreBlockEntity core){
        if(player==null||core==null||!core.bound()
                ||(core.kind()!=EnterpriseKind.HOTEL&&core.kind()!=EnterpriseKind.TAVERN))return;
        var firms=KingdomEnterpriseData.get(player.getServer());
        var firm=firms.find(core.enterpriseId());
        var binding=firms.core(core.enterpriseId());
        if(firm==null||binding==null||!binding.active()
                ||binding.kind()!=core.kind()||!firm.owner().equals(core.owner())
                ||!binding.dimension().equals(player.serverLevel().dimension().location().toString())
                ||binding.packedPos()!=core.getBlockPos().asLong()
                ||core.getBlockPos().distSqr(player.blockPosition())>64)return;
        player.openMenu(new SimpleMenuProvider((id,inventory,ignored)->
                new KingdomHospitalityMenu(id,inventory,player,core.enterpriseId(),
                        core.getBlockPos(),core.kind(),new SimpleContainer(27)),
                Component.literal(core.kind()==EnterpriseKind.HOTEL
                        ?"Kingdom — Стойка гостиницы":"Kingdom — Стойка таверны")));
    }

    private boolean manager(){
        return KingdomEnterpriseData.get(viewer.getServer()).mayManage(viewer.getUUID(),enterprise);
    }
    private void icon(int slot,Item item,String label){
        ItemStack stack=new ItemStack(item);
        stack.setHoverName(Component.literal(label));
        display.setItem(slot,stack);
    }
    private void send(String text){
        viewer.sendSystemMessage(Component.literal("[Kingdom Аренда] "+text));
    }
    private List<Room> visibleRooms(){
        var all=KingdomRentalsData.get(viewer.getServer()).roomsAtReception(enterprise);
        if(manager())return all;
        List<Room> visible=new ArrayList<>();
        for(var room:all)
            if(room.listed()||viewer.getUUID().equals(room.tenant()))
                visible.add(room);
        return visible;
    }
    private void render(){
        slots.clear();display.clearContent();
        var rentals=KingdomRentalsData.get(viewer.getServer());
        var firms=KingdomEnterpriseData.get(viewer.getServer());
        var building=rentals.receptionBuilding(enterprise);
        boolean manager=manager();
        icon(0,kind==EnterpriseKind.HOTEL?Items.WHITE_BED:Items.BARREL,
                kind.title()+" — настоящая аренда комнат");
        icon(1,Items.BOOK,building==null?"Здание не привязано к стойке":
                "Здание: "+building.name()+" | Комнат: "+rentals.roomsAtReception(enterprise).size());
        var cashier=EnterpriseAccessService.snapshot(viewer.serverLevel(),firms,enterprise,
                EnterpriseBindingRole.CASHBOX);
        icon(2,Items.CHEST,"Касса предприятия: "+EnterpriseAccessService.display(cashier.status()));
        if(kind==EnterpriseKind.TAVERN && manager)
            icon(3,Items.BREAD,"Продукты таверны: ЛКМ — привязать сундук с настоящей едой");
        else
            icon(3,Items.COPPER_INGOT,"Цена за весь срок; оплата монетами прямо в кассу предприятия.");
        if(kind==EnterpriseKind.TAVERN){
            var arrivals=ru.projectkingdom.settlement.KingdomTavernImmigration.inspect(viewer.getServer(),enterprise);
            icon(6,Items.VILLAGER_SPAWN_EGG,"Переселенцы без профессии: "+arrivals.residents()+"/"+arrivals.places()+" | "+arrivals.message());
            icon(7,Items.RED_BED,"Кровати под крышей: "+arrivals.beds()+" | 1 место = 1 житель");
            icon(8,Items.BREAD,"Свежие порции в таверне: "+arrivals.meals()+" | минимум 2 для приглашения");
        }
        if(manager){
            icon(4,Items.OAK_SIGN,"Владелец: привязать здание, в котором установлена стойка");
            icon(5,Items.WRITABLE_BOOK,"Разметить комнаты, менять цены и объявления через меню недвижимости");
        }
        List<Room> rooms=visibleRooms();
        int maxPage=Math.max(0,(rooms.size()-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(maxPage,page));
        for(int i=0;i<PAGE_SIZE && page*PAGE_SIZE+i<rooms.size();i++){
            var room=rooms.get(page*PAGE_SIZE+i);
            int slot=FIRST+i;
            slots.put(slot,room.id());
            boolean mine=viewer.getUUID().equals(room.tenant());
            icon(slot,room.purpose()==KingdomRentalsData.Purpose.HOME?Items.RED_BED:Items.OAK_DOOR,
                    room.name()+" | "+room.priceCopper()+" меди / "+room.days()+" сут. | "+
                    (mine?"Ваш договор":room.reserved()?"Занято":room.listed()?"Свободно":"Не сдаётся"));
        }
        icon(21,Items.ARROW,"Предыдущая страница");
        icon(22,Items.SPECTRAL_ARROW,"Следующая страница");
        var chosen=selected==null?null:rentals.room(selected);
        icon(23,Items.PAPER,chosen==null?"Нажмите на номер, чтобы выбрать":
                "Выбрано: "+chosen.name()+" | "+chosen.priceCopper()+" меди за "+chosen.days()+" суток");
        icon(24,Items.GOLD_INGOT,"Снять выбранный свободный номер: оплатить из кошелька в кассу");
        icon(25,Items.CLOCK,"Продлить свой номер: повторная оплата в кассу");
        icon(26,Items.BOOK,manager?"Управление предприятием: касса, сотрудники, название":
                "Выйти в меню Kingdom");
        broadcastChanges();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player!=viewer||!stillValid(player)||type!=ClickType.PICKUP||button!=0
                ||slot<0||slot>=27)return;
        UUID tapped=slots.get(slot);
        if(tapped!=null){selected=tapped;render();return;}
        var rentals=KingdomRentalsData.get(viewer.getServer());
        if(slot==21){page=Math.max(0,page-1);selected=null;render();return;}
        if(slot==22){page++;selected=null;render();return;}
        if(slot==3&&kind==EnterpriseKind.TAVERN&&manager()){
            boolean started=EnterpriseAccessService.begin(viewer,enterprise,EnterpriseBindingRole.RAW_STORAGE);
            if(started){
                send("Сейчас нажмите ПКМ по своему сундуку с продуктами рядом с таверной. Только реальные свежие продукты привлекают жителей.");
                viewer.closeContainer();
            }else send("Не удалось начать привязку продуктов.");
            return;
        }
        if(slot==4&&manager()){
            var deeds=KingdomCivicsData.get(viewer.getServer());
            boolean ok=rentals.bindReception(deeds,viewer.getUUID(),enterprise,
                    viewer.serverLevel().dimension().location().toString(),corePos);
            send(ok?"Здание привязано к гостинице/таверне. Комнаты можно сдавать через эту стойку.":
                    "Не удалось привязать. Стойка должна стоять внутри здания, полностью принадлежащего владельцу предприятия.");
            render();return;
        }
        if(slot==5&&manager()){KingdomMainMenu.openRentals(viewer);return;}
        if((slot==24||slot==25)&&selected!=null){
            boolean ok=KingdomEnterpriseData.get(viewer.getServer()).payHospitalityRoom(
                    viewer,enterprise,selected,slot==25);
            send(ok?(slot==24?"Номер арендован. Монеты поступили в кассу гостиницы/таверны.":
                    "Проживание продлено. Оплата поступила в кассу предприятия."):
                    "Оплата не выполнена: проверьте доступность номера, кошелёк, кассовый сундук и срок договора.");
            render();return;
        }
        if(slot==26){
            if(manager() && viewer.serverLevel().getBlockEntity(corePos) instanceof EnterpriseCoreBlockEntity core)
                EnterpriseCoreMenu.open(viewer,core);
            else KingdomMainMenu.open(viewer);
        }
    }
    @Override public boolean stillValid(Player player){
        if(player!=viewer||!player.isAlive()||corePos.distSqr(player.blockPosition())>64
                ||!viewer.serverLevel().hasChunkAt(corePos))return false;
        if(!(viewer.serverLevel().getBlockEntity(corePos) instanceof EnterpriseCoreBlockEntity core)
                ||!core.bound()||!enterprise.equals(core.enterpriseId())||core.kind()!=kind)return false;
        var binding=KingdomEnterpriseData.get(viewer.getServer()).core(enterprise);
        return binding!=null&&binding.active()
                &&binding.dimension().equals(viewer.serverLevel().dimension().location().toString())
                &&binding.packedPos()==corePos.asLong()&&binding.kind()==kind;
    }
    @Override public void removed(Player player){super.removed(player);display.clearContent();}
}
