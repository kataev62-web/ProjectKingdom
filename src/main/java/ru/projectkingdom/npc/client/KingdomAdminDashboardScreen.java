package ru.projectkingdom.npc.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectkingdom.network.KingdomAdminActionPacket;
import ru.projectkingdom.network.KingdomAdminDashboardPacket;
import ru.projectkingdom.network.KingdomNetwork;

/**
 * Kingdom-native stone/green/gold administration panel.
 * Cities and NPCs are selected by click, not by typing UUIDs or chat commands.
 * This is a thin view of server-authorized state; no game data is mutated here.
 */
public final class KingdomAdminDashboardScreen extends Screen {
    private static final int BG=0xF0252923,HEADER=0xFF2F4938,GOLD=0xFFE6C67B,
        TEXT=0xFFF1E3BF,MUTED=0xFFB3AF9E,SELECTED=0xFF40634F,EDGE=0xFF95784C;
    private static final UUID EMPTY=new UUID(0L,0L);

    private KingdomAdminDashboardPacket state;
    private EditBox name;
    private boolean nationsTab;
    private UUID selectedTown;
    private UUID selectedNation;
    private int selectedNpc=-1;
    private int left,top,widthUi,heightUi,listWidth,rows;
    private Button toggle,create,mayor,founder,claim,delete,confirm,cancel,cityTab,nationTab;

    public KingdomAdminDashboardScreen(KingdomAdminDashboardPacket initial){
        super(Component.literal("Project Kingdom · Администрирование"));
        state=initial;
    }
    public static void receive(KingdomAdminDashboardPacket next){
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        if(mc.screen instanceof KingdomAdminDashboardScreen active
                &&active.state.session().equals(next.session())){
            active.state=next;
            active.ensureSelection();
            active.updateButtons();
            return;
        }
        if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
            mc.player.closeContainer();
        mc.setScreen(new KingdomAdminDashboardScreen(next));
    }
    private void submit(int action,UUID id,int npc,String text,int page){
        KingdomNetwork.CHANNEL.sendToServer(new KingdomAdminActionPacket(
            state.session(),action,id==null?EMPTY:id,npc,text==null?"":text,page));
    }
    private void submit(int action){submit(action,selected(),selectedNpc,name==null?"":name.getValue(),0);}
    private UUID selected(){return nationsTab?selectedNation:selectedTown;}
    private void ensureSelection(){
        if(selectedTown==null ||
            state.cities().stream().noneMatch(x->x.id().equals(selectedTown))){
            selectedTown=state.cities().isEmpty()?null:state.cities().get(0).id();
        }
        if(selectedNation==null ||
            state.nations().stream().noneMatch(x->x.id().equals(selectedNation))){
            selectedNation=state.nations().isEmpty()?null:state.nations().get(0).id();
        }
        if(state.npcs().stream().noneMatch(n->n.id()==selectedNpc)){
            selectedNpc=state.npcs().isEmpty()?-1:state.npcs().get(0).id();
        }
    }
    private Button add(int x,int y,int w,int h,String label,Runnable onClick){
        return addRenderableWidget(Button.builder(Component.literal(label),btn->onClick.run())
            .bounds(x,y,Math.max(20,w),Math.max(12,h)).build());
    }
    @Override protected void init(){
        super.init();
        widthUi=Math.min(520,Math.max(284,width-10));
        heightUi=Math.min(320,Math.max(202,height-10));
        left=(width-widthUi)/2;top=(height-heightUi)/2;
        listWidth=Math.max(136,(widthUi-22)*55/100);
        rows=Math.max(3,Math.min(7,(heightUi-155)/17));
        ensureSelection();

        int leftX=left+8, rightX=left+listWidth+12, rightW=widthUi-listWidth-20;
        cityTab=add(leftX,top+26,(listWidth-12)/2,18,"Города",()->{
            nationsTab=false;updateButtons();
        });
        nationTab=add(leftX+(listWidth-12)/2+4,top+26,(listWidth-12)/2,18,"Королевства",()->{
            nationsTab=true;updateButtons();
        });
        toggle=add(left+widthUi-134,top+3,126,19,"Роль: ВЫКЛ",
            ()->submit(KingdomAdminActionPacket.TOGGLE));
        int pageY=top+51+rows*17;
        add(leftX,pageY,20,16,"◀",()->submit(nationsTab?
            KingdomAdminActionPacket.NATION_PAGE:KingdomAdminActionPacket.CITY_PAGE,
            EMPTY,-1,"",Math.max(0,(nationsTab?state.nationPage():state.cityPage())-1)));
        add(left+listWidth-29,pageY,20,16,"▶",()->submit(nationsTab?
            KingdomAdminActionPacket.NATION_PAGE:KingdomAdminActionPacket.CITY_PAGE,
            EMPTY,-1,"",(nationsTab?state.nationPage():state.cityPage())+1));
        add(rightX,pageY,20,16,"◀",()->submit(KingdomAdminActionPacket.NPC_PAGE,
            EMPTY,-1,"",Math.max(0,state.npcPage()-1)));
        add(left+widthUi-30,pageY,20,16,"▶",()->submit(KingdomAdminActionPacket.NPC_PAGE,
            EMPTY,-1,"",state.npcPage()+1));

        name=new EditBox(font,leftX,top+heightUi-105,widthUi-16,18,
            Component.literal("Название города или королевства"));
        name.setMaxLength(48);name.setHint(Component.literal("Название нового поселения"));
        addRenderableWidget(name);
        int gap=4, half=(widthUi-20-gap)/2;
        create=add(leftX,top+heightUi-81,half,20,"Создать город",()->
            submit(nationsTab?KingdomAdminActionPacket.CREATE_NATION:
                KingdomAdminActionPacket.CREATE_CITY));
        mayor=add(leftX+half+gap,top+heightUi-81,half,20,"Назначить мэром",()->
            submit(nationsTab?KingdomAdminActionPacket.APPOINT_RULER:
                KingdomAdminActionPacket.APPOINT_MAYOR));
        founder=add(leftX,top+heightUi-56,half,20,"Назначить основателем",()->
            submit(KingdomAdminActionPacket.APPOINT_FOUNDER));
        claim=add(leftX+half+gap,top+heightUi-56,half,20,"Закрепить мой чанк",()->
            submit(KingdomAdminActionPacket.CLAIM_CURRENT_CHUNK));
        delete=add(leftX,top+heightUi-30,half,20,"Удалить выбранное",()->
            submit(nationsTab?KingdomAdminActionPacket.PREPARE_NATION_DELETE:
                KingdomAdminActionPacket.PREPARE_CITY_DELETE));
        confirm=add(leftX,top+heightUi-30,half,20,"ДА, УДАЛИТЬ",()->
            submit(KingdomAdminActionPacket.CONFIRM_DELETE,state.confirmingTarget(),-1,"",0));
        cancel=add(leftX+half+gap,top+heightUi-30,half,20,"ОТМЕНА",()->
            submit(KingdomAdminActionPacket.CANCEL_DELETE));
        add(leftX+half+gap,top+heightUi-30,half,20,"Обновить список",()->
            submit(KingdomAdminActionPacket.REFRESH));
        updateButtons();
    }
    private void updateButtons(){
        if(toggle==null)return;
        toggle.setMessage(Component.literal(state.enabled()?"Роль: ВКЛ":"Роль: ВЫКЛ"));
        boolean on=state.enabled(),danger=on&&state.confirming();
        if(name!=null)name.setEditable(on);
        cityTab.active=on;nationTab.active=on;
        create.active=on&&!danger&&selectedNpc>=0;
        mayor.active=on&&!danger&&selected()!=null&&selectedNpc>=0;
        founder.active=on&&!danger&&!nationsTab&&selected()!=null&&selectedNpc>=0;
        claim.active=on&&!danger&&!nationsTab&&selectedTown!=null;
        delete.active=on&&!danger&&selected()!=null;
        confirm.visible=danger;confirm.active=danger;
        cancel.visible=danger;cancel.active=danger;
        delete.visible=!danger;
        founder.visible=!nationsTab;
        claim.visible=!nationsTab;
        create.setMessage(Component.literal(nationsTab?"Создать королевство":"Создать город"));
        mayor.setMessage(Component.literal(nationsTab?"Назначить правителем":"Назначить мэром"));
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&state.enabled()&&!state.confirming()){
            int rowY=top+50;
            if(my>=rowY&&my<rowY+rows*17){
                int index=(int)((my-rowY)/17);
                if(mx>=left+8&&mx<left+listWidth-4){
                    if(nationsTab){
                        if(index<state.nations().size())selectedNation=state.nations().get(index).id();
                    }else if(index<state.cities().size())selectedTown=state.cities().get(index).id();
                    updateButtons();return true;
                }
                if(mx>=left+listWidth+12&&mx<left+widthUi-8){
                    if(index<state.npcs().size())selectedNpc=state.npcs().get(index).id();
                    updateButtons();return true;
                }
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(!state.enabled())return super.mouseScrolled(mx,my,delta);
        int change=delta>0?-1:1;
        if(mx>=left+8&&mx<left+listWidth-4){
            int page=nationsTab?state.nationPage():state.cityPage();
            submit(nationsTab?KingdomAdminActionPacket.NATION_PAGE:
                KingdomAdminActionPacket.CITY_PAGE,EMPTY,-1,"",Math.max(0,page+change));
            return true;
        }
        if(mx>=left+listWidth+12&&mx<left+widthUi-8){
            submit(KingdomAdminActionPacket.NPC_PAGE,EMPTY,-1,"",
                Math.max(0,state.npcPage()+change));return true;
        }
        return super.mouseScrolled(mx,my,delta);
    }
    @Override public void render(GuiGraphics gfx,int mouseX,int mouseY,float delta){
        renderBackground(gfx);
        gfx.fill(left-3,top-3,left+widthUi+3,top+heightUi+3,0xDD090C09);
        gfx.fill(left,top,left+widthUi,top+heightUi,BG);
        gfx.fill(left,top,left+widthUi,top+25,HEADER);
        gfx.fill(left,top,left+widthUi,top+1,EDGE);
        gfx.fill(left,top+heightUi-1,left+widthUi,top+heightUi,EDGE);
        gfx.drawString(font,"PROJECT KINGDOM / АДМИНИСТРИРОВАНИЕ",
            left+9,top+9,GOLD,false);
        gfx.drawString(font,"Города / государства",left+10,top+45,MUTED,false);
        gfx.drawString(font,"Жители NPC",left+listWidth+14,top+45,GOLD,false);
        for(int row=0;row<rows;row++){
            int y=top+50+row*17;
            if(nationsTab){
                if(row<state.nations().size()){
                    var record=state.nations().get(row);
                    drawRow(gfx,left+8,y,listWidth-12,record.id().equals(selectedNation),
                        record.name()+"  ("+record.cities()+")");
                }
            }else if(row<state.cities().size()){
                var record=state.cities().get(row);
                drawRow(gfx,left+8,y,listWidth-12,record.id().equals(selectedTown),
                    record.name()+"  ["+record.plots()+" участков]");
            }
            if(row<state.npcs().size()){
                var npc=state.npcs().get(row);
                drawRow(gfx,left+listWidth+12,y,widthUi-listWidth-21,
                    npc.id()==selectedNpc,"#"+npc.id()+" "+npc.name()+" ("+npc.age()+")");
            }
        }
        int pagination=top+53+rows*17;
        gfx.drawCenteredString(font,
            "Стр. "+((nationsTab?state.nationPage():state.cityPage())+1)+"/"+
                Math.max(1,((nationsTab?state.nationTotal():state.cityTotal())+15)/16),
            left+listWidth/2,pagination,TEXT);
        gfx.drawCenteredString(font,"Стр. "+(state.npcPage()+1)+"/"+
            Math.max(1,(state.npcTotal()+15)/16),left+listWidth+
                (widthUi-listWidth)/2,pagination,TEXT);

        int detailsY=top+heightUi-149;
        if(detailsY>pagination+12){
            if(nationsTab){
                var nation=state.nations().stream().filter(n->n.id().equals(selectedNation))
                    .findFirst().orElse(null);
                if(nation!=null){
                    gfx.drawString(font,font.plainSubstrByWidth(
                        "Правитель: "+nation.ruler(),widthUi-18),
                        left+10,detailsY,TEXT,false);
                }
            }else{
                var town=state.cities().stream().filter(n->n.id().equals(selectedTown))
                    .findFirst().orElse(null);
                if(town!=null){
                    gfx.drawString(font,font.plainSubstrByWidth(
                        "Мэр: "+town.mayor()+"  |  основатель: "+town.founder(),widthUi-18),
                        left+10,detailsY,TEXT,false);
                }
            }
        }
        int statusY=top+heightUi-118;
        gfx.drawString(font,font.plainSubstrByWidth(state.message(),widthUi-18),
            left+9,statusY,state.confirming()?0xFFFF9999:MUTED,false);
        if(!state.enabled())
            gfx.drawString(font,"Включи режим справа вверху. Требуются права OP 4.",
                left+9,statusY-12,GOLD,false);
        super.render(gfx,mouseX,mouseY,delta);
    }
    private void drawRow(GuiGraphics g,int x,int y,int w,boolean selected,String label){
        g.fill(x,y,x+w,y+16,selected?SELECTED:0x833A3A30);
        if(selected)g.fill(x,y,x+3,y+16,GOLD);
        g.drawString(font,font.plainSubstrByWidth(label,w-9),x+6,y+4,
            selected?TEXT:MUTED,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
