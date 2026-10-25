package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

public final class AdministrativeCoreScreen extends AbstractContainerScreen<AdministrativeCoreMenu> {
    private static final int INK=0xFF312116,MUTED=0xFF72563A,PARCHMENT=0xFFF1D69C,EDGE=0xFF9E713B,ACCENT=0xFF6E2830;
    private AdministrativeMenuLayout.Tab selected=AdministrativeMenuLayout.Tab.INFO;
    private final List<ParchmentIconButton> tabButtons=new ArrayList<>();
    private final List<ActionButton> actionButtons=new ArrayList<>();
    private final List<AbstractWidget> roleControls=new ArrayList<>();
    private EditBox roleTarget,taxAmount,bankAmount;
    private Button joinButton,landPayButton,warehouseBack,warehouseForward;
    private int warehousePage;
    private int foodPage;
    private final List<Button> foodTabs=new ArrayList<>();
    private final List<Button> foodOffers=new ArrayList<>();
    private EditBox foodResidents,foodQuantity;
    private Button foodSave,foodBuy;
    private String selectedFood;
    public AdministrativeCoreScreen(AdministrativeCoreMenu menu,Inventory inventory,Component title){
        super(menu,inventory,KingdomFonts.readable(title));imageWidth=AdministrativeMenuLayout.WIDTH;imageHeight=AdministrativeMenuLayout.HEIGHT;
        if(!menu.foodView().notice().isBlank())
            selected=AdministrativeMenuLayout.Tab.FOOD;
        else if(menu.kind()==AdministrativeKind.TREASURY || menu.kind()==AdministrativeKind.BANK)
            selected=AdministrativeMenuLayout.Tab.FINANCE;
    }
    @Override protected void init(){
        super.init();
        var tabs=menu.kind()==AdministrativeKind.TOWN_HALL&&!menu.manager()
                ?List.of(AdministrativeMenuLayout.Tab.INFO,AdministrativeMenuLayout.Tab.CITIZENS,
                        AdministrativeMenuLayout.Tab.FOOD)
                :AdministrativeMenuLayout.tabs(menu.kind());
        var rects=AdministrativeMenuLayout.tabRects(menu.kind());
        for(int i=0;i<tabs.size();i++){
            var tab=tabs.get(i);var rect=rects.get(i);
            tabButtons.add(addRenderableWidget(new ParchmentIconButton(
                    leftPos+rect.x(),topPos+rect.y(),rect.width(),rect.height(),
                    tabIcon(tab),title(tab),()->{selected=tab;updateVisibility();},()->selected==tab)));
        }
        if(menu.kind()==AdministrativeKind.TOWN_HALL&&!menu.manager()){
            landPayButton=addRenderableWidget(Button.builder(KingdomFonts.readable("Оплатить бронь земли через банк"),
                    ignored->KingdomNetwork.CHANNEL.sendToServer(new AdministrativeActionPacket(
                        menu.townId(),AdministrativeKind.TOWN_HALL,AdministrativeActionPacket.PAY_RESERVED_LAND)))
                    .bounds(leftPos+60,topPos+170,245,20).build());
        }
        if(menu.kind()==AdministrativeKind.TOWN_HALL&&!menu.manager()&&menu.canJoin()){
            joinButton=addRenderableWidget(Button.builder(KingdomFonts.readable("Вступить в город"),
                    ignored->KingdomNetwork.CHANNEL.sendToServer(new AdministrativeActionPacket(
                            menu.townId(),AdministrativeKind.TOWN_HALL,AdministrativeActionPacket.JOIN_TOWN)))
                    .bounds(leftPos+74,topPos+132,204,22).build());
        }
        if(menu.kind()==AdministrativeKind.TOWN_HALL&&menu.manager()){
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
                taxAmount=new EditBox(font,leftPos+136,topPos+122,145,18,
                        KingdomFonts.readable("Сумма в медных"));
                taxAmount.setMaxLength(7);
                taxAmount.setFilter(value->value.matches("[0-9]*"));
                taxAmount.setValue(Long.toString(Math.min(menu.taxDebt(),100)));
                addRenderableWidget(taxAmount);
                Button full=addRenderableWidget(Button.builder(KingdomFonts.readable("Оплатить весь долг"),
                        ignored->sendText("pay_taxes","all"))
                        .bounds(leftPos+56,topPos+156,123,20).build());
                actionButtons.add(new ActionButton(AdministrativeMenuLayout.Tab.FINANCE,full,false));
                Button part=addRenderableWidget(Button.builder(KingdomFonts.readable("Оплатить часть"),
                        ignored->sendText("pay_taxes",taxAmount.getValue()))
                        .bounds(leftPos+185,topPos+156,119,20).build());
                actionButtons.add(new ActionButton(AdministrativeMenuLayout.Tab.FINANCE,part,false));
            }
            case BANK->{
                bankAmount=new EditBox(font,leftPos+150,topPos+118,132,18,KingdomFonts.readable("Сумма"));
                bankAmount.setMaxLength(7);
                bankAmount.setFilter(value->value.matches("[0-9]*"));
                bankAmount.setValue("100");
                addRenderableWidget(bankAmount);
                Button deposit=addRenderableWidget(Button.builder(KingdomFonts.readable("Внести"),
                    ignored->sendText("bank_deposit",bankAmount.getValue()))
                    .bounds(leftPos+58,topPos+151,108,20).build());
                Button withdraw=addRenderableWidget(Button.builder(KingdomFonts.readable("Снять"),
                    ignored->sendText("bank_withdraw",bankAmount.getValue()))
                    .bounds(leftPos+180,topPos+151,108,20).build());
                actionButtons.add(new ActionButton(AdministrativeMenuLayout.Tab.FINANCE,deposit,false));
                actionButtons.add(new ActionButton(AdministrativeMenuLayout.Tab.FINANCE,withdraw,false));
                addAction("Отметить комнату хранилища",AdministrativeMenuLayout.Tab.FINANCE,
                    AdministrativeActionPacket.BIND_BANK,80,179,224,true);
                addAction("Перенести монеты старой казны",AdministrativeMenuLayout.Tab.INFO,
                    AdministrativeActionPacket.CONSOLIDATE_BANK,58,158,246,true);
                addAction("Сверить старые остатки (пустой сейф)",AdministrativeMenuLayout.Tab.INFO,
                    AdministrativeActionPacket.RECONCILE_BANK,58,184,246,true);
            }
            case CENTRAL_WAREHOUSE->{
                warehouseBack=addRenderableWidget(Button.builder(KingdomFonts.readable("◀"),ignored->{
                    warehousePage=Math.max(0,warehousePage-1);updateVisibility();
                }).bounds(leftPos+251,topPos+85,24,17).build());
                warehouseForward=addRenderableWidget(Button.builder(KingdomFonts.readable("▶"),ignored->{
                    warehousePage++;updateVisibility();
                }).bounds(leftPos+279,topPos+85,24,17).build());
                addAction("Новая обычная комната",AdministrativeMenuLayout.Tab.STORAGE,
                    AdministrativeActionPacket.WAREHOUSE_ROOM_GENERAL,54,126,250,true);
                addAction("Новая продовольственная",AdministrativeMenuLayout.Tab.STORAGE,
                    AdministrativeActionPacket.WAREHOUSE_ROOM_FOOD,54,151,250,true);
                addAction("Новая холодильная камера",AdministrativeMenuLayout.Tab.STORAGE,
                    AdministrativeActionPacket.WAREHOUSE_ROOM_COLD,54,176,250,true);
            }
            case CITY_MARKET->addAction("Добавить склад рынка",AdministrativeMenuLayout.Tab.TRADE,AdministrativeActionPacket.BIND_MARKET,78,146,190,true);
            case PRISON->addAction("Добавить камеру",AdministrativeMenuLayout.Tab.CELLS,AdministrativeActionPacket.BIND_PRISON_CELL,78,146,190,true);
            default->{}
        }
        if(menu.kind()==AdministrativeKind.TOWN_HALL || menu.kind()==AdministrativeKind.BANK
                || menu.kind()==AdministrativeKind.CENTRAL_WAREHOUSE)
            initFoodControls();
        updateVisibility();
    }
    private void initFoodControls() {
        String[] labels={"Запасы","Предложения","Доставки"};
        for(int i=0;i<labels.length;i++) {
            final int page=i;
            Button button=addRenderableWidget(Button.builder(KingdomFonts.readable(labels[i]),
                    ignored->{foodPage=page;updateVisibility();})
                    .bounds(leftPos+57+i*83,topPos+81,78,20).build());
            foodTabs.add(button);
        }
        foodResidents=addRenderableWidget(new EditBox(font,leftPos+156,topPos+173,
                74,18,KingdomFonts.readable("Жители")));
        foodResidents.setMaxLength(6);
        foodResidents.setFilter(value->value.matches("[0-9]*"));
        foodResidents.setValue(Integer.toString(
                menu.foodView().configured()?menu.foodView().residents():50));
        foodSave=addRenderableWidget(Button.builder(KingdomFonts.readable("Сохранить"),
                ignored->sendText("food_config",foodResidents.getValue()))
                .bounds(leftPos+235,topPos+172,75,19).build());
        foodQuantity=addRenderableWidget(new EditBox(font,leftPos+135,topPos+178,
                49,18,KingdomFonts.readable("Количество")));
        foodQuantity.setMaxLength(2);
        foodQuantity.setFilter(value->value.matches("[0-9]*"));
        foodQuantity.setValue("1");
        foodBuy=addRenderableWidget(Button.builder(KingdomFonts.readable("Купить в казну"),
                ignored->{
                    if(selectedFood!=null)
                        sendText("food_buy",selectedFood+"|"+foodQuantity.getValue());
                }).bounds(leftPos+192,topPos+177,117,20).build());
        for(int i=0;i<menu.foodView().offers().size();i++) {
            final int index=i;
            Button button=addRenderableWidget(Button.builder(KingdomFonts.readable("Выбрать"),
                    ignored->{selectedFood=menu.foodView().offers().get(index).itemId();
                        updateVisibility();})
                    .bounds(leftPos+257,topPos+108+i*15,53,14).build());
            foodOffers.add(button);
        }
        if(!menu.foodView().offers().isEmpty())
            selectedFood=menu.foodView().offers().get(0).itemId();
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
        boolean food=selected==AdministrativeMenuLayout.Tab.FOOD&&menu.member();
        for(Button b:foodTabs)b.visible=food;
        for(Button b:foodOffers)b.visible=food&&foodPage==1;
        if(foodResidents!=null)foodResidents.setVisible(food&&foodPage==0&&menu.foodView().canConfigure());
        if(foodSave!=null)foodSave.visible=food&&foodPage==0&&menu.foodView().canConfigure();
        if(foodQuantity!=null)foodQuantity.setVisible(food&&foodPage==1&&menu.kind()==AdministrativeKind.BANK&&menu.manager());
        if(foodBuy!=null){foodBuy.visible=food&&foodPage==1&&menu.kind()==AdministrativeKind.BANK&&menu.manager();
            foodBuy.active=selectedFood!=null&&menu.foodView().complete();}

        for(AbstractWidget widget:roleControls)widget.visible=selected==AdministrativeMenuLayout.Tab.OFFICES&&menu.manager();
        if(taxAmount!=null)taxAmount.setVisible(selected==AdministrativeMenuLayout.Tab.FINANCE);
        if(bankAmount!=null)bankAmount.setVisible(selected==AdministrativeMenuLayout.Tab.FINANCE);
        if(joinButton!=null)joinButton.visible=selected==AdministrativeMenuLayout.Tab.CITIZENS&&menu.canJoin();
        if(landPayButton!=null)landPayButton.visible=selected==AdministrativeMenuLayout.Tab.CITIZENS;
        int pages=Math.max(1,(menu.warehouseRooms().size()+1)/2);
        warehousePage=Math.max(0,Math.min(warehousePage,pages-1));
        if(warehouseBack!=null){warehouseBack.visible=selected==AdministrativeMenuLayout.Tab.STORAGE;
            warehouseBack.active=warehousePage>0;}
        if(warehouseForward!=null){warehouseForward.visible=selected==AdministrativeMenuLayout.Tab.STORAGE;
            warehouseForward.active=warehousePage+1<pages;}
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos+36,topPos+4,leftPos+imageWidth-4,topPos+imageHeight-4,EDGE);
        g.fill(leftPos+41,topPos+9,leftPos+imageWidth-9,topPos+imageHeight-9,PARCHMENT);
        g.fill(leftPos+53,topPos+43,leftPos+imageWidth-20,topPos+44,EDGE);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawCenteredString(font,KingdomFonts.readable(menu.kind().russianName()),190,18,ACCENT);
        line(g,"Город: "+menu.townName(),58,51,INK);line(g,"Раздел: "+title(selected),58,68,MUTED);
        switch(selected){
            case INFO->{
                if(menu.kind()==AdministrativeKind.BANK){
                    line(g,"Архив старого личного счёта: "+menu.legacyPersonal()+" мед.",58,80,MUTED);
                    if(menu.manager()){
                        line(g,"Архив банка: "+menu.legacyBank()+" мед.",58,98,MUTED);
                        line(g,"Архив казны: "+menu.legacyTown()+" мед.",58,115,MUTED);
                        line(g,menu.vaultCopper()<0?"Комната недоступна — загрузи чанки.":
                                "Настоящие монеты в комнате: "+menu.vaultCopper(),58,132,MUTED);
                        line(g,"Архив нельзя снимать как наличные!",58,145,ACCENT);
                    }else line(g,"Архив — на сверке. Снять его нельзя.",58,105,ACCENT);
                }else{
                    line(g,"Основатель: "+menu.founder(),58,90,MUTED);
                    line(g,"Мэр: "+menu.mayor(),58,108,MUTED);
                    line(g,"Состояние: активно",58,126,MUTED);
                }
                if(menu.kind()==AdministrativeKind.TOWN_HALL&&!menu.manager())
                    line(g,"Вкладка «Граждане» — вступление в город",58,148,MUTED);
            }
            case CITIZENS->{
                if(menu.kind()==AdministrativeKind.TOWN_HALL&&!menu.manager()){
                    line(g,"Гражданство",58,91,INK);
                    if(menu.member())line(g,"Вы уже состоите в этом городе.",58,112,MUTED);
                    else if(menu.canJoin())line(g,"Чтобы стать жителем, нажмите кнопку ниже.",58,112,MUTED);
                    else line(g,"Вы уже состоите в другом городе.",58,112,MUTED);
                } else renderRows(g,"Граждане города",menu.pageRows());
            }
            case OFFICES->{if(menu.manager())line(g,"Укажите UUID гражданина:",58,78,MUTED);else placeholder(g,"Только просмотр должностей",90);}
            case FINANCE->{
                if(menu.kind()==AdministrativeKind.BANK){
                    line(g,"Ваш счёт: "+menu.bankAccount()+" медных",58,83,MUTED);
                    line(g,"Ваш кошелёк: "+menu.walletCoins()+" медных",58,101,MUTED);
                    line(g,"Сумма:",58,122,MUTED);
                    if(menu.manager())
                        line(g,menu.vaultRoomInfo(),58,139,MUTED);
                }else if(menu.kind()==AdministrativeKind.TREASURY){
                    line(g,"Ваш долг городу: "+menu.taxDebt()+" медных",58,82,INK);
                    line(g,"Монет в кошельке: "+menu.walletCoins()+" медных",58,102,MUTED);
                    line(g,"Оплатить часть:",58,127,MUTED);
                    line(g,"Все платежи поступают в городской банк.",58,188,MUTED);
                }else placeholder(g,"Физические резервы и обязательства",90);
            }
            case STORAGE->{
                if(menu.kind()==AdministrativeKind.CENTRAL_WAREHOUSE){
                    line(g,"Комнат: "+menu.warehouseRooms().size()+" | стр. "+(warehousePage+1),58,84,MUTED);
                    for(int i=0;i<2;i++){
                        int index=warehousePage*2+i;
                        if(index<menu.warehouseRooms().size()){
                            String label=menu.warehouseRooms().get(index);
                            if(font.width(label)>258)
                                label=font.plainSubstrByWidth(label,249)+"…";
                            line(g,label,58,105+i*14,MUTED);
                        }
                    }
                    if(menu.warehouseRooms().isEmpty())line(g,"Построй комнату и отметь два угла.",58,105,MUTED);
                }else placeholder(g,"Привязанные реальные контейнеры",90);
            }
            case FOOD->renderFood(g);
            case TRADE->placeholder(g,"Торговые места и заказы",90);
            case LAND->renderRows(g,"Участки, продажа и аренда",menu.pageRows());
            case CASES->renderRows(g,menu.kind()==AdministrativeKind.GUARD_ADMINISTRATION?"Ордера и розыск (автоматика выкл.)":"Судебные дела",menu.pageRows());
            case CELLS->renderRows(g,"Записи камер (содержание выкл.)",menu.pageRows());
            case GUARDS->renderRows(g,"Стража (патрули выкл.)",menu.pageRows());
            case LOG->placeholder(g,"Журнал административных действий",90);
            case SETTINGS->placeholder(g,menu.manager()?"Настройки службы":"Нет прав управления",90);
        }
        if(selected!=AdministrativeMenuLayout.Tab.FOOD)
            line(g,menu.manager()?"Права управления подтверждены сервером.":"Доступен разрешённый просмотр.",58,204,MUTED);
    }
    private void renderFood(GuiGraphics g){
        if(!menu.member()){line(g,"Доступно только гражданам этого города.",58,110,ACCENT);return;}
        var v=menu.foodView();
        if(foodPage==0){
            if(!v.configured()){
                line(g,"План продовольствия ещё не настроен.",58,113,ACCENT);
            }else{
                line(g,"Жителей: "+v.residents()+" | день TFC: "+v.day()+" | запас на "+v.coverage()+" д.",58,109,INK);
                if(!v.complete())line(g,"СКЛАД НЕДОСТУПЕН: закупка заблокирована",58,125,ACCENT);
                else{
                    line(g,"Годных порций: "+v.edible()+" / цель: "+v.target(),58,125,INK);
                    line(g,"Испорчено: "+v.rotten()+" | не хватает: "+v.missing(),58,141,v.missing()>0?ACCENT:MUTED);
                    line(g,"Питание: зерно "+v.groups().get(0)+" / фрукты "+v.groups().get(1),58,157,MUTED);
                    line(g,"Овощи "+v.groups().get(2)+" / белки "+v.groups().get(3)+" / молоко "+v.groups().get(4),58,169,MUTED);
                }
            }
            if(v.canConfigure())line(g,"Население:",58,177,MUTED);
        }else if(foodPage==1){
            line(g,"Реальные товары рынка — цена за 1 шт.",58,103,MUTED);
            for(int i=0;i<v.offers().size();i++){
                var offer=v.offers().get(i);
                String prefix=offer.itemId().equals(selectedFood)?"● ":"  ";
                String name=prefix+offer.itemId();
                if(font.width(name)>174)name=font.plainSubstrByWidth(name,169)+"…";
                line(g,name,58,111+i*15,INK);
                line(g,offer.unitPrice()+" м.",230,111+i*15,MUTED);
            }
            if(v.offers().isEmpty())line(g,"Нет доступных предложений с реальной едой.",58,131,ACCENT);
            if(menu.kind()==AdministrativeKind.BANK&&menu.manager())
                line(g,"Количество (1–16):",58,183,MUTED);
            else line(g,"Оплата: в меню банка, уполномоченным казначеем.",58,184,MUTED);
        }else{
            line(g,"Последние заказы (обновятся при открытии):",58,108,MUTED);
            int i=0;
            for(String entry:v.shipments()){
                String text=font.plainSubstrByWidth(entry,242);
                line(g,text,58,125+i*16,MUTED);i++;
            }
            if(i==0)line(g,"Оплаченных заявок пока нет.",58,130,MUTED);
        }
        if(!v.notice().isBlank()){
            String notice=font.plainSubstrByWidth(v.notice(),245);
            line(g,notice,58,204,ACCENT);
        }else line(g,"Проверки и оплата выполняются только сервером.",58,204,MUTED);
    }
    private void placeholder(GuiGraphics g,String text,int y){line(g,text,58,y,MUTED);line(g,"Подробные действия подключаются по этапам.",58,y+18,MUTED);}
    private void renderRows(GuiGraphics g,String heading,List<String> rows){line(g,heading,58,82,MUTED);int y=100;for(String row:rows){line(g,row,58,y,MUTED);y+=15;}if(rows.isEmpty())line(g,"Нет записей.",58,y,MUTED);}
    private void line(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,KingdomFonts.readable(text),x,y,color,false);}
    private static Item tabIcon(AdministrativeMenuLayout.Tab tab){return switch(tab){
        case INFO -> Items.BOOK;
        case CITIZENS -> Items.VILLAGER_SPAWN_EGG;
        case OFFICES -> Items.WRITABLE_BOOK;
        case FINANCE -> Items.GOLD_INGOT;
        case STORAGE -> Items.CHEST;
        case FOOD -> Items.BREAD;
        case TRADE -> Items.EMERALD;
        case LAND -> Items.GRASS_BLOCK;
        case CASES -> Items.PAPER;
        case CELLS -> Items.IRON_BARS;
        case GUARDS -> Items.SHIELD;
        case LOG -> Items.WRITABLE_BOOK;
        case SETTINGS -> Items.COMPARATOR;
    };}
    private static String title(AdministrativeMenuLayout.Tab tab){return switch(tab){
        case INFO->"Сведения";case CITIZENS->"Граждане";case OFFICES->"Должности";case FINANCE->"Финансы";case STORAGE->"Склад";case FOOD->"Продовольствие";
        case TRADE->"Рынок";case LAND->"Земля";case CASES->"Дела";case CELLS->"Камеры";case GUARDS->"Стража";case LOG->"Журнал";case SETTINGS->"Настройки";};}
    private record ActionButton(AdministrativeMenuLayout.Tab tab,Button button,boolean managerOnly){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
