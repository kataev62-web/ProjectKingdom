package ru.projectkingdom.enterprise;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
    private final List<Button> storageButtons=new ArrayList<>(),cashButtons=new ArrayList<>();
    private EditBox rename;private Button renameButton;
    public EnterpriseCoreScreen(EnterpriseCoreMenu menu,Inventory inventory,Component title){super(menu,inventory,KingdomFonts.readable(title));imageWidth=EnterpriseMenuLayout.WIDTH;imageHeight=EnterpriseMenuLayout.HEIGHT;}
    @Override protected void init(){
        super.init();var tabs=EnterpriseMenuLayout.tabs();int index=0;
        for(var tab:EnterpriseMenuLayout.Tab.values()){var r=tabs.get(index++);addRenderableWidget(new ParchmentIconButton(leftPos+r.x(),topPos+r.y(),r.width(),r.height(),tabIcon(tab),title(tab),()->{selected=tab;updateVisibility();},()->selected==tab));}
        storageButtons.add(addRenderableWidget(action("Касса",60,132,70,EnterpriseCoreMenu.BIND_CASHBOX)));
        storageButtons.add(addRenderableWidget(action("Сырьё",136,132,70,EnterpriseCoreMenu.BIND_RAW)));
        storageButtons.add(addRenderableWidget(action("Продукция",212,132,78,EnterpriseCoreMenu.BIND_FINISHED)));
        cashButtons.add(addRenderableWidget(action("Внести 100",78,132,90,EnterpriseCoreMenu.DEPOSIT_100)));
        cashButtons.add(addRenderableWidget(action("Снять 100",178,132,90,EnterpriseCoreMenu.WITHDRAW_100)));
        rename=new EditBox(font,leftPos+68,topPos+94,190,20,KingdomFonts.readable("Название"));rename.setMaxLength(48);rename.setValue(menu.enterpriseName());addRenderableWidget(rename);
        renameButton=addRenderableWidget(Button.builder(KingdomFonts.readable("Сохранить"),b->KingdomNetwork.CHANNEL.sendToServer(new RenameEnterprisePacket(menu.enterpriseId(),rename.getValue()))).bounds(leftPos+264,topPos+94,44,20).build());
        updateVisibility();
    }
    private Button action(String text,int x,int y,int width,int id){return Button.builder(KingdomFonts.readable(text),b->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}).bounds(leftPos+x,topPos+y,width,20).build();}
    private void updateVisibility(){for(Button b:storageButtons)b.visible=selected==EnterpriseMenuLayout.Tab.STORAGE&&menu.manager();for(Button b:cashButtons)b.visible=selected==EnterpriseMenuLayout.Tab.CASH&&menu.manager();boolean settings=selected==EnterpriseMenuLayout.Tab.SETTINGS&&menu.manager();if(rename!=null)rename.setVisible(settings);if(renameButton!=null)renameButton.visible=settings;}
    private static Item tabIcon(EnterpriseMenuLayout.Tab tab){return switch(tab){case INFO->Items.BOOK;case WORKERS->Items.VILLAGER_SPAWN_EGG;case PRODUCTION->Items.IRON_PICKAXE;case STORAGE->Items.CHEST;case CASH->Items.GOLD_INGOT;case ORDERS->Items.PAPER;case SETTINGS->Items.COMPARATOR;};}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){g.fill(leftPos+34,topPos+4,leftPos+imageWidth-4,topPos+imageHeight-4,EDGE);g.fill(leftPos+39,topPos+9,leftPos+imageWidth-9,topPos+imageHeight-9,PARCHMENT);g.fill(leftPos+51,topPos+42,leftPos+imageWidth-20,topPos+43,EDGE);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawCenteredString(font,KingdomFonts.readable(menu.kind().title()),174,18,ACCENT);
        g.drawString(font,KingdomFonts.readable(menu.enterpriseName()),54,48,INK,false);g.drawString(font,KingdomFonts.readable("Вкладка: "+title(selected)),54,64,MUTED,false);
        switch(selected){
            case INFO->{line(g,"Владелец: "+menu.owner(),54,84);line(g,"Состояние: активно",54,100);line(g,"Ядро: "+menu.enterpriseId(),54,116);}
            case WORKERS->{line(g,"Работников: "+menu.workers(),54,88);line(g,"Долг по зарплате: "+menu.debt()+" мед.",54,106);line(g,"Показаны только сохранённые договоры.",54,124);}
            case PRODUCTION->{line(g,menu.kind()==EnterpriseKind.SMITHY?"Кузнечное производство подключено.":"Не настроено — сценарий профессии будет позже.",54,90);line(g,"Фантомные товары и прогресс не создаются.",54,108);}
            case STORAGE->{line(g,"Касса: "+menu.cashboxState(),54,84);line(g,"Сырьё: "+menu.rawState(),54,101);line(g,"Продукция: "+menu.finishedState(),54,118);}
            case CASH->{line(g,"Физически обеспеченная касса: "+menu.cash()+" мед.",54,88);line(g,"Операции доступны владельцу у загруженного ядра.",54,108);}
            case ORDERS->{line(g,"Только реальные сохранённые заявки и поставки.",54,90);line(g,"Удалённое создание товаров отключено.",54,108);}
            case SETTINGS->{line(g,menu.manager()?"Новое название (до 48 символов):":"Управление доступно только владельцу.",54,80);}
        }
    }
    private void line(GuiGraphics g,String text,int x,int y){g.drawString(font,KingdomFonts.readable(text),x,y,MUTED,false);}
    private static String title(EnterpriseMenuLayout.Tab tab){return switch(tab){case INFO->"Сведения";case WORKERS->"Работники";case PRODUCTION->"Производство";case STORAGE->"Склады";case CASH->"Касса";case ORDERS->"Заказы";case SETTINGS->"Настройки";};}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
