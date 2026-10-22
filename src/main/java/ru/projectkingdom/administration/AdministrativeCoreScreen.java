package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.projectkingdom.core.KingdomFonts;

public final class AdministrativeCoreScreen extends AbstractContainerScreen<AdministrativeCoreMenu> {
    private static final int INK=0xFF312116,MUTED=0xFF72563A,PARCHMENT=0xFFF1D69C,EDGE=0xFF9E713B,ACCENT=0xFF6E2830;
    private AdministrativeMenuLayout.Tab selected=AdministrativeMenuLayout.Tab.INFO;
    private final List<Button> tabButtons=new ArrayList<>();
    public AdministrativeCoreScreen(AdministrativeCoreMenu menu,Inventory inventory,Component title){
        super(menu,inventory,KingdomFonts.readable(title));imageWidth=AdministrativeMenuLayout.WIDTH;imageHeight=AdministrativeMenuLayout.HEIGHT;
    }
    @Override protected void init(){
        super.init();var tabs=AdministrativeMenuLayout.tabs(menu.kind());var rects=AdministrativeMenuLayout.tabRects(menu.kind());
        for(int i=0;i<tabs.size();i++){
            var tab=tabs.get(i);var rect=rects.get(i);
            tabButtons.add(addRenderableWidget(Button.builder(KingdomFonts.readable(shortLabel(tab)),button->selected=tab)
                    .bounds(leftPos+rect.x(),topPos+rect.y(),rect.width(),rect.height()).build()));
        }
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
            case CITIZENS->placeholder(g,"Граждане города",90);
            case OFFICES->placeholder(g,menu.manager()?"Назначение должностных лиц":"Только просмотр должностей",90);
            case FINANCE->placeholder(g,"Физические резервы и обязательства",90);
            case STORAGE->placeholder(g,"Привязанные реальные контейнеры",90);
            case TRADE->placeholder(g,"Торговые места и заказы",90);
            case LAND->placeholder(g,"Участки, продажа и аренда",90);
            case CASES->placeholder(g,menu.kind()==AdministrativeKind.GUARD_ADMINISTRATION?"Ордера и розыск":"Судебные дела",90);
            case CELLS->placeholder(g,"Камеры и записи о заключённых",90);
            case GUARDS->placeholder(g,"Стражники и районы патруля",90);
            case LOG->placeholder(g,"Журнал административных действий",90);
            case SETTINGS->placeholder(g,menu.manager()?"Настройки службы":"Нет прав управления",90);
        }
        line(g,menu.manager()?"Права управления подтверждены сервером.":"Доступен разрешённый просмотр.",58,188,MUTED);
    }
    private void placeholder(GuiGraphics g,String text,int y){line(g,text,58,y,MUTED);line(g,"Подробные действия подключаются по этапам.",58,y+18,MUTED);}
    private void line(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,KingdomFonts.readable(text),x,y,color,false);}
    private static String shortLabel(AdministrativeMenuLayout.Tab tab){return switch(tab){
        case INFO->"И";case CITIZENS->"Ж";case OFFICES->"Д";case FINANCE->"Ф";case STORAGE->"С";case TRADE->"Т";
        case LAND->"З";case CASES->"ДЛ";case CELLS->"К";case GUARDS->"СЖ";case LOG->"Л";case SETTINGS->"Н";};}
    private static String title(AdministrativeMenuLayout.Tab tab){return switch(tab){
        case INFO->"Сведения";case CITIZENS->"Граждане";case OFFICES->"Должности";case FINANCE->"Финансы";case STORAGE->"Склад";
        case TRADE->"Рынок";case LAND->"Земля";case CASES->"Дела";case CELLS->"Камеры";case GUARDS->"Стража";case LOG->"Журнал";case SETTINGS->"Настройки";};}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
