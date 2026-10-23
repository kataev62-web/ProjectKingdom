package ru.projectkingdom.enterprise;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import ru.projectkingdom.core.ParchmentIconButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.projectkingdom.core.KingdomFonts;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.RenameEnterprisePacket;

/** MineColonies-inspired single parchment page with vertical icon-like tabs. */
public final class EnterpriseCoreScreen extends AbstractContainerScreen<EnterpriseCoreMenu> {
    private static final int INK=0xFF312116,MUTED=0xFF72563A,PARCHMENT=0xFFF1D69C,EDGE=0xFF9E713B,ACCENT=0xFF6E2830;
    private EnterpriseMenuLayout.Tab selected=EnterpriseMenuLayout.Tab.INFO;
    private enum GuestPage { BUY, SELL, JOB }
    private GuestPage guest=GuestPage.BUY;
    private final List<Button> buyButtons=new ArrayList<>();
    private Button sellButton,applyButton,acceptButton;
    private final List<Button> storageButtons=new ArrayList<>(),cashButtons=new ArrayList<>(),constructionButtons=new ArrayList<>();
    private EditBox rename;private Button renameButton;
    public EnterpriseCoreScreen(EnterpriseCoreMenu menu,Inventory inventory,Component title){super(menu,inventory,KingdomFonts.readable(title));imageWidth=EnterpriseMenuLayout.WIDTH;imageHeight=EnterpriseMenuLayout.HEIGHT;}
    @Override protected void init(){
        super.init();
        if(!menu.manager()){initVisitor();return;}
        var tabs=EnterpriseMenuLayout.tabs();int index=0;
        for(var tab:EnterpriseMenuLayout.Tab.values()){var r=tabs.get(index++);addRenderableWidget(new ParchmentIconButton(leftPos+r.x(),topPos+r.y(),r.width(),r.height(),tabIcon(tab),title(tab),()->{selected=tab;updateVisibility();},()->selected==tab));}
        storageButtons.add(addRenderableWidget(action("Касса",60,132,70,EnterpriseCoreMenu.BIND_CASHBOX)));
        storageButtons.add(addRenderableWidget(action("Сырьё",136,132,70,EnterpriseCoreMenu.BIND_RAW)));
        storageButtons.add(addRenderableWidget(action("Продукция",212,132,78,EnterpriseCoreMenu.BIND_FINISHED)));
        acceptButton=addRenderableWidget(action("Принять первого заявителя (10 мед./сут.)",58,132,250,EnterpriseCoreMenu.ACCEPT_FIRST));
        cashButtons.add(addRenderableWidget(action("Внести 100",78,132,90,EnterpriseCoreMenu.DEPOSIT_100)));
        cashButtons.add(addRenderableWidget(action("Снять 100",178,132,90,EnterpriseCoreMenu.WITHDRAW_100)));
        constructionButtons.add(addRenderableWidget(action("Архитектор",62,132,110,EnterpriseCoreMenu.OPEN_ARCHITECT)));
        constructionButtons.add(addRenderableWidget(action("Мои проекты",182,132,110,EnterpriseCoreMenu.OPEN_PROJECTS)));
        rename=new EditBox(font,leftPos+68,topPos+94,190,20,KingdomFonts.readable("Название"));rename.setMaxLength(48);rename.setValue(menu.enterpriseName());addRenderableWidget(rename);
        renameButton=addRenderableWidget(Button.builder(KingdomFonts.readable("Сохранить"),b->KingdomNetwork.CHANNEL.sendToServer(new RenameEnterprisePacket(menu.enterpriseId(),rename.getValue()))).bounds(leftPos+264,topPos+94,44,20).build());
        updateVisibility();
    }
    private void initVisitor(){
        GuestPage[] pages=GuestPage.values();
        Item[] icons={Items.EMERALD,Items.CHEST,Items.VILLAGER_SPAWN_EGG};
        String[] titles={"Покупка","Продажа","Работа"};
        for(int i=0;i<pages.length;i++){
            final GuestPage page=pages[i];
            final int slot=i;
            addRenderableWidget(new ParchmentIconButton(leftPos+4,topPos+14+i*27,25,23,
                icons[i],titles[i],()->{guest=page;updateVisibility();},()->guest==page));
        }
        for(int i=0;i<4;i++)
            buyButtons.add(addRenderableWidget(action("Купить",240,73+i*25,63,EnterpriseCoreMenu.BUY_FIRST+i)));
        sellButton=addRenderableWidget(action("Продать товар из руки",72,139,210,EnterpriseCoreMenu.SELL_HELD));
        applyButton=addRenderableWidget(action(menu.applied()?"Заявка уже отправлена":"Подать заявку на работу",
            72,139,210,EnterpriseCoreMenu.APPLY_JOB));
        updateVisibility();
    }
    private Button action(String text,int x,int y,int width,int id){return Button.builder(KingdomFonts.readable(text),b->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+y,width,20).build();}
    private void updateVisibility(){
        if(!menu.manager()){
            for(int i=0;i<buyButtons.size();i++)
                buyButtons.get(i).visible=guest==GuestPage.BUY&&i<menu.publicOffers().size();
            if(sellButton!=null)sellButton.visible=guest==GuestPage.SELL;
            if(applyButton!=null){applyButton.visible=guest==GuestPage.JOB;applyButton.active=!menu.applied();}
            return;
        }
        if(acceptButton!=null)acceptButton.visible=selected==EnterpriseMenuLayout.Tab.WORKERS&&menu.applications()>0;
        for(Button b:storageButtons)b.visible=selected==EnterpriseMenuLayout.Tab.STORAGE&&menu.manager();for(Button b:cashButtons)b.visible=selected==EnterpriseMenuLayout.Tab.CASH&&menu.manager();
        for(Button b:constructionButtons)b.visible=selected==EnterpriseMenuLayout.Tab.PRODUCTION
            &&menu.kind()==EnterpriseKind.CONSTRUCTION&&menu.manager();
        boolean settings=selected==EnterpriseMenuLayout.Tab.SETTINGS&&menu.manager();if(rename!=null)rename.setVisible(settings);if(renameButton!=null)renameButton.visible=settings;}
    private static Item tabIcon(EnterpriseMenuLayout.Tab tab){return switch(tab){case INFO->Items.BOOK;case WORKERS->Items.VILLAGER_SPAWN_EGG;case PRODUCTION->Items.IRON_PICKAXE;case STORAGE->Items.CHEST;case CASH->Items.GOLD_INGOT;case ORDERS->Items.PAPER;case SETTINGS->Items.COMPARATOR;};}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){g.fill(leftPos+34,topPos+4,leftPos+imageWidth-4,topPos+imageHeight-4,EDGE);g.fill(leftPos+39,topPos+9,leftPos+imageWidth-9,topPos+imageHeight-9,PARCHMENT);g.fill(leftPos+51,topPos+42,leftPos+imageWidth-20,topPos+43,EDGE);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        if(!menu.manager()){renderVisitor(g);return;}
        g.drawCenteredString(font,KingdomFonts.readable(menu.kind().title()),174,18,ACCENT);
        g.drawString(font,KingdomFonts.readable(menu.enterpriseName()),54,48,INK,false);g.drawString(font,KingdomFonts.readable("Вкладка: "+title(selected)),54,64,MUTED,false);
        switch(selected){
            case INFO->{line(g,"Владелец: "+menu.owner(),54,84);line(g,"Состояние: активно",54,100);line(g,"Ядро: "+menu.enterpriseId(),54,116);}
            case WORKERS->{
                line(g,"Работников: "+menu.workers(),54,82);
                line(g,"Долг по зарплате: "+menu.debt()+" мед.",54,98);
                line(g,"Заявки на работу: "+menu.applications(),54,114);
                if(menu.applications()>0)line(g,"Первый заявитель: "+menu.firstApplicant(),54,124);
            }
            case PRODUCTION->{
                if(menu.kind()==EnterpriseKind.CONSTRUCTION) {
                    line(g,"Заказы, проекты, NPC-строители и молот",54,90);
                    line(g,"открываются в едином меню Kingdom.",54,108);
                } else {
                    line(g,menu.kind()==EnterpriseKind.SMITHY?"Кузнечное производство подключено.":"Не настроено — сценарий профессии будет позже.",54,90);
                    line(g,"Фантомные товары и прогресс не создаются.",54,108);
                }
            }
            case STORAGE->{line(g,"Касса: "+menu.cashboxState(),54,84);line(g,"Сырьё: "+menu.rawState(),54,101);line(g,"Продукция: "+menu.finishedState(),54,118);}
            case CASH->{line(g,"Физически обеспеченная касса: "+menu.cash()+" мед.",54,88);line(g,"Операции доступны владельцу у загруженного ядра.",54,108);}
            case ORDERS->{line(g,"Только реальные сохранённые заявки и поставки.",54,90);line(g,"Удалённое создание товаров отключено.",54,108);}
            case SETTINGS->{line(g,menu.manager()?"Новое название (до 48 символов):":"Управление доступно только владельцу.",54,80);}
        }
    }
    private void renderVisitor(GuiGraphics g){
        g.drawCenteredString(font,KingdomFonts.readable(menu.kind().title()),174,18,ACCENT);
        line(g,menu.enterpriseName(),54,48);
        String section=switch(guest){case BUY->"Магазин";case SELL->"Продажа товара";case JOB->"Трудоустройство";};
        line(g,section,54,64);
        switch(guest){
            case BUY->{
                if(menu.publicOffers().isEmpty()){
                    line(g,"В продаже пока нет доступных товаров.",54,88);
                    line(g,"Приходи позже или проверь соседний рынок.",54,106);
                }else{
                    for(int i=0;i<menu.publicOffers().size();i++){
                        var offer=menu.publicOffers().get(i);
                        var id=ResourceLocation.tryParse(offer.itemId());
                        if(id==null || !BuiltInRegistries.ITEM.containsKey(id))continue;
                        ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(id));
                        g.renderItem(stack,54,73+i*25);
                        String name=stack.getHoverName().getString();
                        if(font.width(name)>116)name=font.plainSubstrByWidth(name,112)+"…";
                        line(g,name,76,75+i*25);
                        line(g,offer.price()+" мед. | "+offer.stock()+" шт.",76,86+i*25);
                    }
                }
            }
            case SELL->{
                line(g,"Предприятие покупает товары по заявкам.",54,85);
                line(g,"Возьми нужный товар в основную руку.",54,102);
                line(g,"Должны быть доступны касса и склад.",54,119);
            }
            case JOB->{
                line(g,"Отправь владельцу заявку на работу.",54,85);
                line(g,"Владелец сам назначит тебя и зарплату.",54,105);
                line(g,menu.applied()?"Твоя заявка уже зарегистрирована.":"После принятия появится трудовой договор.",54,124);
            }
        }
    }
    private void line(GuiGraphics g,String text,int x,int y){g.drawString(font,KingdomFonts.readable(text),x,y,MUTED,false);}
    private static String title(EnterpriseMenuLayout.Tab tab){return switch(tab){case INFO->"Сведения";case WORKERS->"Работники";case PRODUCTION->"Производство";case STORAGE->"Склады";case CASH->"Касса";case ORDERS->"Заказы";case SETTINGS->"Настройки";};}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
