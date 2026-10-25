package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.projectkingdom.core.KingdomFonts;
import ru.projectkingdom.network.KingdomNetwork;

/**
 * Server OP console: switch between cities and states, choose a registered company,
 * then arm and confirm deletion. Client grouping never grants authority.
 */
public final class KingdomAdminEnterpriseScreen extends AbstractContainerScreen<KingdomAdminEnterpriseMenu> {
    private static final int INK=0xFF382719,FADED=0xFF715941,DANGER=0xFF962C30,BG=0xFFE9D4A2;
    private final List<Button> rows=new ArrayList<>();
    private Button scopeButton,previous,next,refresh,delete,back,forward;
    private boolean nationMode,confirm;
    private int scopeIndex,page;
    private UUID selected;
    private record Scope(UUID id,String name) {}
    public KingdomAdminEnterpriseScreen(KingdomAdminEnterpriseMenu menu,Inventory inv,Component title){
        super(menu,inv,title);imageWidth=390;imageHeight=256;
    }
    private List<Scope> scopes(){
        LinkedHashMap<UUID,String> names=new LinkedHashMap<>();
        for(var r:menu.entries()){
            if(nationMode){if(r.nationId()!=null)names.putIfAbsent(r.nationId(),r.nationName());}
            else names.putIfAbsent(r.townId(),r.townName());
        }
        return names.entrySet().stream().map(e->new Scope(e.getKey(),e.getValue())).toList();
    }
    private List<KingdomAdminEnterpriseService.Listing> active(){
        var scopes=scopes();
        if(scopes.isEmpty())return List.of();
        int safe=Math.min(scopeIndex,scopes.size()-1);
        UUID id=scopes.get(safe).id();
        return menu.entries().stream().filter(r->nationMode?id.equals(r.nationId()):id.equals(r.townId())).toList();
    }
    private void moveScope(int step){
        var list=scopes();if(list.isEmpty())return;
        scopeIndex=Math.floorMod(scopeIndex+step,list.size());
        page=0;selected=null;confirm=false;updateButtons();
    }
    private void send(int action,UUID firm){
        KingdomNetwork.CHANNEL.sendToServer(new KingdomAdminEnterprisePacket(action,firm));
    }
    @Override protected void init(){
        super.init();rows.clear();
        scopeButton=addRenderableWidget(Button.builder(Component.literal("Город / Государство"), b->{
            nationMode=!nationMode;scopeIndex=0;page=0;selected=null;confirm=false;updateButtons();
        }).bounds(leftPos+13,topPos+44,174,20).build());
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->moveScope(-1))
                .bounds(leftPos+202,topPos+44,24,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->moveScope(1))
                .bounds(leftPos+349,topPos+44,24,20).build());
        for(int i=0;i<6;i++){
            final int index=i;
            rows.add(addRenderableWidget(Button.builder(Component.literal(""),b->{
                int which=page*6+index;
                var active=active();
                if(which<active.size()){selected=active.get(which).enterprise();confirm=false;updateButtons();}
            }).bounds(leftPos+18,topPos+89+i*19,354,18).build()));
        }
        back=addRenderableWidget(Button.builder(Component.literal("<"),b->{
            page=Math.max(0,page-1);selected=null;confirm=false;updateButtons();
        }).bounds(leftPos+18,topPos+208,28,19).build());
        forward=addRenderableWidget(Button.builder(Component.literal(">"),b->{
            page++;selected=null;confirm=false;updateButtons();
        }).bounds(leftPos+61,topPos+208,28,19).build());
        delete=addRenderableWidget(Button.builder(Component.literal("Удалить"),b->{
            if(selected==null)return;
            if(!confirm){confirm=true;send(KingdomAdminEnterprisePacket.ARM,selected);updateButtons();}
            else {confirm=false;send(KingdomAdminEnterprisePacket.CONFIRM,selected);updateButtons();}
        }).bounds(leftPos+195,topPos+208,177,20).build());
        refresh=addRenderableWidget(Button.builder(Component.literal("Обновить"),b->send(
                KingdomAdminEnterprisePacket.REFRESH,null)).bounds(leftPos+18,topPos+232,110,17).build());
        updateButtons();
    }
    private void updateButtons(){
        var scopes=scopes();
        previous.active=!scopes.isEmpty();next.active=!scopes.isEmpty();
        var found=active();
        back.active=page>0;forward.active=(page+1)*6<found.size();
        for(int i=0;i<rows.size();i++){
            Button b=rows.get(i);int at=page*6+i;
            b.visible=at<found.size();
            if(b.visible){
                var r=found.get(at);
                String title=(r.enterprise().equals(selected)?"● ":"")+r.name()
                        +" | "+r.owner().toString().substring(0,8)+(r.loaded()?"":" | ЧАНК НЕ ЗАГРУЖЕН");
                b.setMessage(Component.literal(font.plainSubstrByWidth(title,336)));
            }
        }
        delete.active=selected!=null;
        delete.setMessage(Component.literal(confirm?"ПОДТВЕРДИТЬ ЗАКРЫТИЕ":"Закрыть выбранное предприятие"));
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int mouseX,int mouseY){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF6C4D28);
        g.fill(leftPos+5,topPos+5,leftPos+imageWidth-5,topPos+imageHeight-5,BG);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawCenteredString(font,KingdomFonts.readable("АДМИНИСТРАТОР — ПРЕДПРИЯТИЯ"),imageWidth/2,12,INK);
        g.drawString(font,KingdomFonts.readable("Только оператор сервера. Частные предприятия тоже видны."),13,28,FADED,false);
        var list=scopes();
        String name=list.isEmpty()?"Нет зарегистрированных предприятий":list.get(Math.min(scopeIndex,list.size()-1)).name();
        g.drawString(font,KingdomFonts.readable((nationMode?"Государство: ":"Город: ")+
                font.plainSubstrByWidth(name,117)),231,50,INK,false);
        g.drawString(font,KingdomFonts.readable("Предприятие | UUID владельца | доступность"),18,74,FADED,false);
        g.drawString(font,KingdomFonts.readable("Стр. "+(page+1)+" / "+Math.max(1,(active().size()+5)/6)),103,212,INK,false);
        g.drawString(font,KingdomFonts.readable(confirm?"ВНИМАНИЕ: второй клик закрывает предприятие!":
                "Деньги, долги и непустые склады блокируют удаление."),134,234,confirm?DANGER:FADED,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float tick){
        renderBackground(g);super.render(g,mouseX,mouseY,tick);renderTooltip(g,mouseX,mouseY);
    }
}
