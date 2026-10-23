package ru.projectkingdom.npc.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectkingdom.npc.KingdomArchitectGeometry;

/**
 * Small non-pausing placement palette over the live 3D world. The textured
 * world hologram remains visible behind it; F8 brings controls back after
 * walking around for first-person inspection.
 */
public final class KingdomArchitectScreen extends Screen {
    private String error="";
    private int left,top;
    public KingdomArchitectScreen(){super(Component.literal("Архитектор Project Kingdom"));}
    private void button(int x,int y,int w,String title,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(title),ignored->action.run())
            .bounds(left+x,top+y,w,19).build());
    }
    @Override protected void init(){
        super.init();
        left=Math.max(4,(width-278)/2);
        top=Math.max(4,(height-232)/2);
        button(9,49,61,"X −1",()->move(-1,0,0));
        button(76,49,61,"X +1",()->move(1,0,0));
        button(143,49,61,"Z −1",()->move(0,0,-1));
        button(209,49,61,"Z +1",()->move(0,0,1));
        button(9,73,61,"X −10",()->move(-10,0,0));
        button(76,73,61,"X +10",()->move(10,0,0));
        button(143,73,61,"Z −10",()->move(0,0,-10));
        button(209,73,61,"Z +10",()->move(0,0,10));
        button(9,97,81,"Высота −1",()->move(0,-1,0));
        button(98,97,81,"Высота +1",()->move(0,1,0));
        button(187,97,83,"Повернуть ↻",()->{
            var view=KingdomArchitectClient.current();
            if(view!=null)view.rotate();
        });
        button(9,142,125,"Осмотреть (F8)",()->minecraft.setScreen(null));
        button(143,142,127,"Подтвердить",()->KingdomArchitectClient.decide(false));
        button(9,167,261,"Отменить проект",()->KingdomArchitectClient.decide(true));
    }
    private void move(int x,int y,int z){
        var view=KingdomArchitectClient.current();
        if(view!=null)view.move(x,y,z);
    }
    public void error(String error){this.error=error;}
    @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partialTick){
        // Do not call renderBackground(): the world and live ghost stay visible.
        gui.fill(left,top,left+278,top+214,0xD523251F);
        gui.fill(left+3,top+3,left+275,top+42,0xE0453528);
        var view=KingdomArchitectClient.current();
        gui.drawCenteredString(font,"АРХИТЕКТОР — РАЗМЕЩЕНИЕ",left+139,top+9,0xFFF5E6BD);
        if(view!=null){
            gui.drawCenteredString(font,font.plainSubstrByWidth(view.packet.title(),257),
                left+139,top+25,0xFFE7D3A5);
            var size=view.footprint();
            gui.drawString(font,"X "+view.origin.getX()+"  Y "+view.origin.getY()
                +"  Z "+view.origin.getZ()+"   /   "+view.rotation*90+"°",
                left+9,top+121,0xFFF4E2C5,false);
            gui.drawString(font,"Габариты: "+size.width()+" × "+view.packet.height()
                +" × "+size.length()+" | реальная стройка после фиксации",
                left+9,top+132,0xFFD3C29D,false);
            if(view.pending)gui.drawCenteredString(font,"Проверка участка сервером...",
                left+139,top+195,0xFFFFD890);
        }
        if(!error.isEmpty())gui.drawCenteredString(font,
            font.plainSubstrByWidth(error,256),left+139,top+195,0xFFFF9999);
        super.render(gui,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){
        // ESC hides only the controls, leaving the ghost to inspect on foot.
        minecraft.setScreen(null);
    }
}
