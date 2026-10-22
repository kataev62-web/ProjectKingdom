package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.projectkingdom.core.KingdomFonts;
import ru.projectkingdom.network.KingdomNetwork;

public final class AdministrativeCoreScreen extends AbstractContainerScreen<AdministrativeCoreMenu> {
    private static final int INK=0xFF312116,MUTED=0xFF72563A,PARCHMENT=0xFFF1D69C,EDGE=0xFF9E713B,ACCENT=0xFF6E2830;
    private AdministrativeMenuLayout.Tab selected=AdministrativeMenuLayout.Tab.INFO;
    private final List<Button> tabButtons=new ArrayList<>();
    private final List<ActionButton> actionButtons=new ArrayList<>();
    private final List<AbstractWidget> roleControls=new ArrayList<>();
    private EditBox roleTarget;
    public AdministrativeCoreScreen(AdministrativeCoreMenu menu,Inventory inventory,Component title){
        super(menu,inventory,KingdomFonts.readable(title));imageWidth=AdministrativeMenuLayout.WIDTH;imageHeight=AdministrativeMenuLayout.HEIGHT;
    }
    @Override protected void init(){
        super.init();var tabs=AdministrativeMenuLayout.tabs(menu.kind());var rects=AdministrativeMenuLayout.tabRects(menu.kind());
        for(int i=0;i<tabs.size();i++){
            var tab=tabs.get(i);var rect=rects.get(i);
            tabButtons.add(addRenderableWidget(Button.builder(KingdomFonts.readable(shortLabel(tab)),button->{selected=tab;updateVisibility();})
                    .bounds(leftPos+rect.x(),topPos+rect.y(),rect.width(),rect.height()).build()));
        }
        if(menu.kind()==AdministrativeKind.TOWN_HALL){
            roleTarget=addRenderableWidget(new EditBox(font,leftPos+58,topPos+90,242,18,KingdomFonts.readable(Component.literal("UUID гражданина"))));
            roleTarget.setMaxLength(36);roleControls.add(roleTarget);
            addRolePair(MunicipalRole.TREASURER,"Казна",58,114);addRolePair(MunicipalRole.LAND_MANAGER,"Земля",184,114);
            addRolePair(MunicipalRole.JUDGE,"Суд",58,136);addRolePair(MunicipalRole.GUARD_CHIEF,"Стража",184,136);
            addRolePair(MunicipalRole.WAREHOUSE_MANAGER,"Склад",58,158);
            Button mayor=addRenderableWidget(Button.builder(KingdomFonts.readable("Назначить мэром"),ignored->sendText("appoint_mayor",roleTarget.getValue()))
                    .bounds(leftPos+184,topPos+158,116,18).build());roleControls.add(mayor);
        }
        switch(menu.kind()){
            case TREASURY->{
                addAction("Внести 100",AdministrativeMenuLayout.Tab.FINANCE,AdministrativeActionPacket.TREASURY_DEPOSIT_100,58,132,96,false);
                addAction("Снять 100",AdministrativeMenuLayout.Tab.FINANCE,AdministrativeActionPacket.TREASURY_WITHDRAW_100,162,132,96,true);
                addAction("Привязать хранилище",AdministrativeMenuLayout.Tab.FINANCE,AdministrativeActionPacket.BIND_TREASURY,78,158,190,true);
            }
            case BANK->{
                addAction("Внести 100",AdministrativeMenuLayout.Tab.FINANCE,AdministrativeActionPacket.BANK_DEPOSIT_100,58,132,96,false);
                addAction("Снять 100",AdministrativeMenuLayout.Tab.FINANCE,AdministrativeActionPacket.BANK_WITHDRAW_100,162,132,96,false);
                addAction("Привязать резерв",AdministrativeMenuLayout.Tab.FINANCE,AdministrativeActionPacket.BIND_BANK,78,158,190,true);
            }
            case CENTRAL_WAREHOUSE->addAction("Добавить контейнер",AdministrativeMenuLayout.Tab.STORAGE,AdministrativeActionPacket.BIND_WAREHOUSE,78,146,190,true);
            case CITY_MARKET->addAction("Добавить склад рынка",AdministrativeMenuLayout.Tab.TRADE,AdministrativeActionPacket.BIND_MARKET,78,146,190,true);
            case PRISON->addAction("Добавить камеру",AdministrativeMenuLayout.Tab.CELLS,AdministrativeActionPacket.BIND_PRISON_CELL,78,146,190,true);
            default->{}
        }
        updateVisibility();
    }
    private void addRolePair(MunicipalRole role,String label,int x,int y){
        Button add=addRenderableWidget(Button.builder(KingdomFonts.readable("+ "+label),ignored->sendText("appoint_role",roleTarget.getValue()+"|"+role.name()))
                .bounds(leftPos+x,topPos+y,96,18).build());
        Button remove=addRenderableWidget(Button.builder(Component.literal("−"),ignored->sendText("revoke_role",roleTarget.getValue()+"|"+role.name()))
                .bounds(leftPos+x+100,topPos+y,20,18).build());
        roleControls.add(add);roleControls.add(remove);
    }
    private void sendText(String field,String value){KingdomNetwork.CHANNEL.sendToServer(new AdministrativeTextPacket(menu.townId(),menu.kind(),field,value));}
    private void addAction(String label,AdministrativeMenuLayout.Tab tab,int action,int x,int y,int width,boolean managerOnly){
        Button button=addRenderableWidget(Button.builder(KingdomFonts.readable(label),ignored->KingdomNetwork.CHANNEL.sendToServer(
                new AdministrativeActionPacket(menu.townId(),menu.kind(),action))).bounds(leftPos+x,topPos+y,width,20).build());
        actionButtons.add(new ActionButton(tab,button,managerOnly));
    }
    private void updateVisibility(){
        for(ActionButton entry:actionButtons)entry.button.visible=entry.tab==selected&&(!entry.managerOnly||menu.manager());
        for(AbstractWidget widget:roleControls)widget.visible=selected==AdministrativeMenuLayout.Tab.OFFICES&&menu.manager();
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos+36,topPos+4,leftPos+imageWidth-4,topPos+imageHeight-4,EDGE);
        g.fill(leftPos+41,topPos+9,leftPos+imageWidth-9,topPos+imageHeight-9,PARCHMENT);
        g.fill(leftPos+53,topPos+43,leftPos+imageWidth-20,topPos+44,EDGE);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawCenteredString(font,KingdomFonts.readable(menu.kind().russianName().toUpperCase(java.util.Locale.ROOT)),190,18,ACCENT);
        line(g,"Город: "+menu.townName(),58,51,INK);line(g,"Раздел: "+title(selected),58,68,MUTED);
        switch(selected){
            case INFO->{line(g,"Основатель: "+menu.founder(),58,90,MUTED);line(g,"Мэр: "+menu.mayor(),58,108,MUTED);line(g,"Состояние: активно",58,126,MUTED);}
            case CITIZENS->renderRows(g,"Граждане города",menu.pageRows());
            case OFFICES->{if(menu.manager())line(g,"Укажите UUID гражданина:",58,78,MUTED);else placeholder(g,"Только просмотр должностей",90);}
            case FINANCE->{
                if(menu.kind()==AdministrativeKind.BANK){
                    line(g,"Ваш счёт: "+menu.bankAccount()+" монет",58,90,MUTED);
                    line(g,"Физический резерв банка: "+menu.bankReserve(),58,108,MUTED);
                }else if(menu.kind()==AdministrativeKind.TREASURY){
                    line(g,"Баланс казны: "+menu.treasuryBalance()+" монет",58,82,MUTED);
                    line(g,"Физический резерв: "+menu.treasuryReserve(),58,100,MUTED);
                    line(g,"Историческое обязательство: "+menu.historicalTreasury(),58,118,MUTED);
                }else placeholder(g,"Физические резервы и обязательства",90);
            }
            case STORAGE->placeholder(g,"Привязанные реальные контейнеры",90);
            case TRADE->placeholder(g,"Торговые места и заказы",90);
            case LAND->renderRows(g,"Участки, продажа и аренда",menu.pageRows());
            case CASES->renderRows(g,menu.kind()==AdministrativeKind.GUARD_ADMINISTRATION?"Ордера и розыск (автоматика выкл.)":"Судебные дела",menu.pageRows());
            case CELLS->renderRows(g,"Записи камер (содержание выкл.)",menu.pageRows());
            case GUARDS->renderRows(g,"Стража (патрули выкл.)",menu.pageRows());
            case LOG->placeholder(g,"Журнал административных действий",90);
            case SETTINGS->placeholder(g,menu.manager()?"Настройки службы":"Нет прав управления",90);
        }
        line(g,menu.manager()?"Права управления подтверждены сервером.":"Доступен разрешённый просмотр.",58,204,MUTED);
    }
    private void placeholder(GuiGraphics g,String text,int y){line(g,text,58,y,MUTED);line(g,"Подробные действия подключаются по этапам.",58,y+18,MUTED);}
    private void renderRows(GuiGraphics g,String heading,List<String> rows){line(g,heading,58,82,MUTED);int y=100;for(String row:rows){line(g,row,58,y,MUTED);y+=15;}if(rows.isEmpty())line(g,"Нет записей.",58,y,MUTED);}
    private void line(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,KingdomFonts.readable(text),x,y,color,false);}
    private static String shortLabel(AdministrativeMenuLayout.Tab tab){return switch(tab){
        case INFO->"И";case CITIZENS->"Ж";case OFFICES->"Д";case FINANCE->"Ф";case STORAGE->"С";case TRADE->"Т";
        case LAND->"З";case CASES->"ДЛ";case CELLS->"К";case GUARDS->"СЖ";case LOG->"Л";case SETTINGS->"Н";};}
    private static String title(AdministrativeMenuLayout.Tab tab){return switch(tab){
        case INFO->"Сведения";case CITIZENS->"Граждане";case OFFICES->"Должности";case FINANCE->"Финансы";case STORAGE->"Склад";
        case TRADE->"Рынок";case LAND->"Земля";case CASES->"Дела";case CELLS->"Камеры";case GUARDS->"Стража";case LOG->"Журнал";case SETTINGS->"Настройки";};}
    private record ActionButton(AdministrativeMenuLayout.Tab tab,Button button,boolean managerOnly){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
