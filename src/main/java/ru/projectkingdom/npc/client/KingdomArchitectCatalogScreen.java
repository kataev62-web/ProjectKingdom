package ru.projectkingdom.npc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.projectkingdom.network.ArchitectCatalogActionPacket;
import ru.projectkingdom.network.ArchitectCatalogPacket;
import ru.projectkingdom.network.KingdomNetwork;

/**
 * Project Kingdom's native architect catalog.
 *
 * A textured miniature is rendered from the schematic's real Minecraft block
 * models; a complete, scrollable material bill is always available by name
 * and icon, while the most needed materials stay visible beside the miniature.
 */
public final class KingdomArchitectCatalogScreen extends Screen {
    private static final int DARK=0xF0262922,EDGE=0xFF9B8051,
        BEIGE=0xFFF0DFB6,GOLD=0xFFFFD78B,SOFT=0xFFB8AF98,GREEN=0xFF365A43;
    private ArchitectCatalogPacket data;
    private KingdomArchitectModelRenderer.Model model;
    private EditBox copyName;
    private int x,y,w,h,listWidth,rows,offset;
    private boolean allMaterials,expanded;
    private int materialOffset;
    private float viewAngle=42,zoom=1;

    public KingdomArchitectCatalogScreen(ArchitectCatalogPacket packet){
        super(Component.literal("Project Kingdom / Архитектор"));
        this.data=packet;
        this.model=KingdomArchitectModelRenderer.decode(packet.miniature());
    }

    /** Handles the initial open and server-authorized selection changes. */
    public static void receive(ArchitectCatalogPacket packet){
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        if(mc.screen instanceof KingdomArchitectCatalogScreen screen
                && screen.data.token().equals(packet.token())){
            screen.data=packet;
            screen.model=KingdomArchitectModelRenderer.decode(packet.miniature());
            screen.materialOffset=0;
            screen.allMaterials=false;
            screen.expanded=false;
            screen.viewAngle=42;screen.zoom=1;
            screen.revealSelected();
            return;
        }
        if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
            mc.player.closeContainer();
        mc.setScreen(new KingdomArchitectCatalogScreen(packet));
    }

    private void send(int action,int index,String copy){
        KingdomNetwork.CHANNEL.sendToServer(new ArchitectCatalogActionPacket(
            data.token(),action,index,copy==null?"":copy));
    }

    @Override protected void init(){
        super.init();
        w=Math.min(470,Math.max(244,width-10));
        h=Math.min(310,Math.max(184,height-10));
        x=(width-w)/2;y=(height-h)/2;
        listWidth=Math.min(155,Math.max(98,w*36/100));
        rows=Math.max(3,Math.min(8,(h-98)/17));
        revealSelected();

        int rightX=x+listWidth+8;
        int rightW=w-listWidth-16;
        int footer=y+h-25;
        int btnWidth=Math.max(68,(rightW-8)/2);

        addRenderableWidget(Button.builder(Component.literal("Разместить"),b->
            send(ArchitectCatalogActionPacket.PLACE,data.selected(),""))
            .bounds(rightX,footer,btnWidth,20).build());
        addRenderableWidget(Button.builder(Component.literal("Копировать"),b->{
            String val=copyName.getValue();
            if(!val.matches("[a-z0-9_-]{1,48}")){
                if(minecraft!=null&&minecraft.player!=null)
                    minecraft.player.displayClientMessage(Component.literal(
                        "Имя схемы: латиница a-z, цифры, _ или - (1–48)"),false);
                return;
            }
            send(ArchitectCatalogActionPacket.COPY,data.selected(),val);
            minecraft.setScreen(null);
        }).bounds(rightX+btnWidth+8,footer,btnWidth,20).build());

        // The copy filename belongs to the left navigation column, not in
        // the valuable preview/material area.
        copyName=new EditBox(font,x+8,y+h-48,listWidth-16,16,
            Component.literal("Имя нового чертежа"));
        copyName.setMaxLength(48);
        copyName.setValue("moy_dom");
        addRenderableWidget(copyName);
        addRenderableWidget(Button.builder(Component.literal("▲"),b->
            offset=Math.max(0,offset-rows))
            .bounds(x+6,footer,20,20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"),b->
            offset=Math.min(Math.max(0,data.entries().size()-rows),offset+rows))
            .bounds(x+listWidth-26,footer,20,20).build());

        // The full bill is an explicit button, not a hidden action on the
        // building illustration as before.
        int materialTabY=y+Math.max(107,h-91);
        var materials=Button.builder(Component.literal("Все материалы ("+data.materials().size()+")"),
            b->{allMaterials=!allMaterials;materialOffset=0;})
            .bounds(rightX,materialTabY,rightW,17).build();
        materials.setTooltip(Tooltip.create(Component.literal(
            "Показать все материалы, включая редкие блоки; колесо мыши листает список")));
        addRenderableWidget(materials);

        int miniTop=y+42;
        addRenderableWidget(Button.builder(Component.literal("↶"),b->
            viewAngle-=30).bounds(rightX+2,miniTop+2,18,17).build());
        addRenderableWidget(Button.builder(Component.literal("↷"),b->
            viewAngle+=30).bounds(rightX+rightW-20,miniTop+2,18,17).build());
    }

    private void revealSelected(){
        if(data.selected()<offset)offset=data.selected();
        if(data.selected()>=offset+rows)offset=Math.max(0,data.selected()-rows+1);
    }

    private int rightX(){return x+listWidth+8;}
    private int rightWidth(){return w-listWidth-16;}
    private int previewTop(){return y+42;}
    private int previewHeight(){return Math.max(55,h-154);}
    private boolean inPreview(double mx,double my){
        return mx>=rightX()&&mx<rightX()+rightWidth()
            &&my>=previewTop()&&my<previewTop()+previewHeight();
    }

    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(mx>=x+listWidth && mx<x+w-5 && my>=previewTop() && my<y+h-28){
            if(allMaterials){
                materialOffset=Math.max(0,Math.min(Math.max(0,data.materials().size()-1),
                    materialOffset-(int)Math.signum(delta)*2));
            }else if(inPreview(mx,my)){
                zoom=Math.max(.55f,Math.min(2.4f,zoom+(float)Math.signum(delta)*.12f));
            }else{
                materialOffset=Math.max(0,Math.min(Math.max(0,data.materials().size()-1),
                    materialOffset-(int)Math.signum(delta)));
            }
            return true;
        }
        if(mx>=x && mx<=x+listWidth){
            offset=Math.max(0,Math.min(Math.max(0,data.entries().size()-rows),
                offset-(int)Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx,my,delta);
    }

    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(button==0&&!allMaterials&&inPreview(mx,my)){
            viewAngle+=(float)dx*1.8f;
            return true;
        }
        return super.mouseDragged(mx,my,button,dx,dy);
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0 && mx>=x+5&&mx<x+listWidth-5
                &&my>=y+34 && my<y+34+rows*17){
            int index=offset+(int)((my-(y+34))/17);
            if(index>=0&&index<data.entries().size()){
                send(ArchitectCatalogActionPacket.SELECT,index,"");
                return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }

    @Override public void render(GuiGraphics gfx,int mouseX,int mouseY,float delta){
        renderBackground(gfx);
        gfx.fill(x-2,y-2,x+w+2,y+h+2,0xB4000000);
        gfx.fill(x,y,x+w,y+h,DARK);
        gfx.fill(x,y,x+w,y+26,0xFF354E3A);
        gfx.fill(x,y,x+w,y+1,EDGE);
        gfx.fill(x,y+h-1,x+w,y+h,EDGE);
        gfx.fill(x+listWidth,y+27,x+listWidth+1,y+h-26,EDGE);
        gfx.drawString(font,"PROJECT KINGDOM",x+8,y+7,GOLD,false);
        gfx.drawString(font,"АРХИТЕКТОР",x+Math.max(105,w-105),y+7,BEIGE,false);
        gfx.drawString(font,"Библиотека",x+7,y+27,GOLD,false);

        for(int row=0;row<rows;row++){
            int index=offset+row;
            if(index>=data.entries().size())break;
            var e=data.entries().get(index);
            int rx=x+6,ry=y+34+row*17;
            boolean selected=index==data.selected();
            gfx.fill(rx,ry,x+listWidth-6,ry+16,selected?GREEN:0xA034342C);
            if(selected)gfx.fill(rx,ry,rx+2,ry+16,0xFFF0CD75);
            gfx.drawString(font,font.plainSubstrByWidth(e.title(),listWidth-18),
                rx+5,ry+4,selected?0xFFFFFFCA:SOFT,false);
        }
        gfx.drawString(font,"Название копии:",x+8,y+h-59,SOFT,false);

        int rx=rightX(),rw=rightWidth();
        var current=data.entries().get(data.selected());
        gfx.drawCenteredString(font,font.plainSubstrByWidth(current.title(),rw),
            rx+rw/2,y+28,GOLD);

        int pt=previewTop(),ph=previewHeight();
        gfx.fill(rx,pt,rx+rw,pt+ph,0xFF151C18);
        gfx.enableScissor(rx+2,pt+2,rx+rw-2,pt+ph-2);
        if(allMaterials){
            gfx.drawString(font,"Полная ведомость блоков",rx+23,pt+5,GOLD,false);
            renderMaterials(gfx,rx+3,pt+20,rw-6,Math.max(30,h-113),true);
        }else{
            KingdomArchitectModelRenderer.draw(gfx,model,rx+2,pt+2,rw-4,ph-4,
                viewAngle,zoom);
        }
        gfx.disableScissor();

        int infoY=pt+ph+4;
        gfx.drawString(font,"Размер: "+current.width()+" × "+current.length()
            +" × "+current.height(),rx+2,infoY,BEIGE,false);
        gfx.drawString(font,"Всего блоков: "+current.blocks(),rx+2,infoY+12,GOLD,false);

        // At least the major materials are always legible by icon and localized
        // name, without knowing to click on a drawing.
        int materialsY=y+Math.max(107,h-91);
        if(!allMaterials){
            gfx.drawString(font,"Основные материалы:",rx+2,materialsY-11,SOFT,false);
            renderMaterials(gfx,rx+3,materialsY+18,rw-6,
                Math.max(1,(y+h-28)-(materialsY+18)),false);
        }
        super.render(gfx,mouseX,mouseY,delta);
    }

    private void renderMaterials(GuiGraphics gfx,int left,int top,int width,
                                 int space,boolean full){
        int step=18;
        int maxRows=Math.max(1,space/step);
        int limit=full?Math.max(4,maxRows):Math.min(3,maxRows);
        int start=full?materialOffset:0;
        int bottom=full?y+h-30:y+h-28;
        gfx.enableScissor(left,top,left+width,Math.min(bottom,top+space));
        for(int row=0;row<limit;row++){
            int idx=start+row;
            if(idx>=data.materials().size())break;
            var material=data.materials().get(idx);
            int rowY=top+row*step;
            if(row%2==0)gfx.fill(left,rowY,left+width,rowY+step,0x55353A2F);
            ItemStack stack=stackFor(material.blockId());
            if(!stack.isEmpty())gfx.renderFakeItem(stack,left+1,rowY+1);
            else gfx.fill(left+3,rowY+3,left+15,rowY+15,0xFF9A8B79);
            String label=stack.isEmpty()?material.blockId():stack.getHoverName().getString();
            String count="×"+material.count();
            int countWidth=font.width(count);
            gfx.drawString(font,font.plainSubstrByWidth(label,Math.max(12,width-24-countWidth)),
                left+19,rowY+5,BEIGE,false);
            gfx.drawString(font,count,left+width-countWidth-2,rowY+5,GOLD,false);
        }
        gfx.disableScissor();
    }

    private static ItemStack stackFor(String blockName){
        ResourceLocation id=ResourceLocation.tryParse(blockName);
        if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))return ItemStack.EMPTY;
        var item=BuiltInRegistries.BLOCK.get(id).asItem();
        return item==Items.AIR?ItemStack.EMPTY:new ItemStack(item);
    }

    @Override public boolean isPauseScreen(){return false;}
}
