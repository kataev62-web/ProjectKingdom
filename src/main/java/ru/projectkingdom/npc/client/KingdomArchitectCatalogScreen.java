package ru.projectkingdom.npc.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.network.ArchitectCatalogActionPacket;
import ru.projectkingdom.network.ArchitectCatalogPacket;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.npc.KingdomBlockStateCodec;

/**
 * Native MineColonies-inspired catalog, using Kingdom colors and original
 * graphics. The building drawing is calculated from the selected REAL
 * schematic palette; not a generic icon or a misleading stock image.
 */
public final class KingdomArchitectCatalogScreen extends Screen {
    private static final int DARK=0xF0262922,EDGE=0xFF9B8051,BEIGE=0xFFF0DFB6,
        GOLD=0xFFFFD78B,SOFT=0xFFB8AF98,GREEN=0xFF365A43;
    private ArchitectCatalogPacket data;
    private EditBox name;
    private int x,y,w,h,listWidth,rows,offset;
    private List<RenderBlock> drawing=List.of();
    private boolean showMaterials;
    private int materialOffset;

    private record RenderBlock(int isoX,int isoY,int color,int depth){}

    public KingdomArchitectCatalogScreen(ArchitectCatalogPacket packet){
        super(Component.literal("Project Kingdom / Архитектор"));
        this.data=packet;
        remakeDrawing();
    }

    /** Handles both first open and selecting another blueprint. */
    public static void receive(ArchitectCatalogPacket packet){
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        if(mc.screen instanceof KingdomArchitectCatalogScreen screen
                && screen.data.token().equals(packet.token())){
            screen.data=packet;
            screen.remakeDrawing();
            screen.showMaterials=false;
            screen.materialOffset=0;
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
        w=Math.min(440,Math.max(240,width-12));
        h=Math.min(266,Math.max(190,height-12));
        x=(width-w)/2;y=(height-h)/2;
        listWidth=Math.min(152,Math.max(102,w*38/100));
        rows=Math.max(3,Math.min(8,(h-99)/17));
        revealSelected();
        final int footer=y+h-25;
        var place=Button.builder(Component.literal("Разместить"),btn->{
            send(ArchitectCatalogActionPacket.PLACE,data.selected(),"");
        }).bounds(x+listWidth+10,footer,Math.max(74,(w-listWidth-22)/2),20).build();
        addRenderableWidget(place);
        addRenderableWidget(Button.builder(Component.literal("Копировать"),btn->{
            if(name==null)return;
            String val=name.getValue();
            if(!val.matches("[a-z0-9_-]{1,48}")){
                if(minecraft!=null&&minecraft.player!=null)
                    minecraft.player.displayClientMessage(
                        Component.literal("Имя: 1–48 символов латиницей, 0-9, _ или -"),false);
                return;
            }
            send(ArchitectCatalogActionPacket.COPY,data.selected(),val);
            minecraft.setScreen(null); // World right-clicks now mark the structure.
        }).bounds(x+listWidth+14+Math.max(74,(w-listWidth-22)/2),
            footer,Math.max(69,(w-listWidth-24)/2),20).build());
        name=new EditBox(font,x+listWidth+10,y+h-48,w-listWidth-20,17,
            Component.literal("Имя копии"));
        name.setMaxLength(48);
        name.setValue("moy_dom");
        name.setHint(Component.literal("name_for_copy"));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("▲"),btn->{
            offset=Math.max(0,offset-rows);
        }).bounds(x+6,footer,19,20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"),btn->{
            offset=Math.min(Math.max(0,data.entries().size()-rows),offset+rows);
        }).bounds(x+listWidth-26,footer,19,20).build());
    }

    private void revealSelected(){
        if(data.selected()<offset)offset=data.selected();
        if(data.selected()>=offset+rows)offset=Math.max(0,data.selected()-rows+1);
    }

    @Override public boolean mouseScrolled(double mouseX,double mouseY,double delta){
        if(showMaterials && mouseX>x+listWidth && mouseX<x+w-7
                &&mouseY>=y+43 &&mouseY<y+h-80){
            materialOffset=Math.max(0,Math.min(Math.max(0,data.materials().size()-1),
                materialOffset-(int)Math.signum(delta)));
            return true;
        }
        if(mouseX>=x&&mouseX<=x+listWidth){
            offset=Math.max(0,Math.min(Math.max(0,data.entries().size()-rows),
                offset-(int)Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,delta);
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0 && mx>=x+listWidth+9 && mx<x+w-9
                && my>=y+43 && my<y+Math.max(105,h-104)){
            showMaterials=!showMaterials;
            materialOffset=0;
            return true;
        }
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
        gfx.drawString(font,"Библиотека зданий",x+7,y+27,GOLD,false);

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

        int rightX=x+listWidth+9;
        int rightWidth=w-listWidth-18;
        var current=data.entries().get(data.selected());
        gfx.drawCenteredString(font,
            font.plainSubstrByWidth(current.title(),rightWidth),
            rightX+rightWidth/2,y+29,GOLD);
        int drawingTop=y+43;
        int drawingHeight=Math.max(62,h-148);
        gfx.fill(rightX,drawingTop,rightX+rightWidth,drawingTop+drawingHeight,0xBB1A241F);
        gfx.enableScissor(rightX+2,drawingTop+2,rightX+rightWidth-2,drawingTop+drawingHeight-2);
        if(showMaterials){
            int visible=Math.max(1,(drawingHeight-16)/11);
            gfx.drawString(font,"Материалы — колёсико",rightX+4,drawingTop+4,GOLD,false);
            for(int i=0;i<visible;i++){
                int index=materialOffset+i;
                if(index>=data.materials().size())break;
                var material=data.materials().get(index);
                String block=material.blockId();
                if(block.contains(":"))block=block.substring(block.indexOf(':')+1);
                gfx.drawString(font,font.plainSubstrByWidth(
                    block+" × "+material.count(),rightWidth-10),
                    rightX+4,drawingTop+16+i*11,BEIGE,false);
            }
        }else{
            drawBlueprint(gfx,rightX,rightWidth,drawingTop,drawingHeight);
            gfx.drawString(font,"Клик: состав блоков",rightX+4,
                drawingTop+drawingHeight-11,0xFFEAD39D,false);
        }
        gfx.disableScissor();

        int infoY=drawingTop+drawingHeight+3;
        gfx.drawString(font,"Размер: "+current.width()+" × "+current.length()
            +" × "+current.height(),rightX+2,infoY,BEIGE,false);
        gfx.drawString(font,"Всего блоков: "+current.blocks(),rightX+2,infoY+12,GOLD,false);
        gfx.drawString(font,"Клик по рисунку: материалы",rightX+2,infoY+24,SOFT,false);
        gfx.drawString(font,"Имя копии:",rightX+2,y+h-59,SOFT,false);
        super.render(gfx,mouseX,mouseY,delta);
    }

    private void remakeDrawing(){
        var selected=data.miniature();
        var decoded=new ArrayList<RenderBlock>();
        List<Integer> colors=new ArrayList<>();
        var mc=Minecraft.getInstance();
        for(var state:selected.palette()){
            int color=0xFFB5B5AB;
            try {
                BlockState block=KingdomBlockStateCodec.parse(state);
                if(mc.level!=null)color=0xFF000000|block.getMapColor(mc.level,BlockPos.ZERO).col;
            }catch(IllegalArgumentException ignored){}
            colors.add(color);
        }
        for(var cell:selected.cells()){
            // Actual isometric projection: X and Z form a diamond, Y raises
            // the blocks. Every colored pixel corresponds to a real palette cell.
            decoded.add(new RenderBlock(
                cell.x()-cell.z(),
                cell.x()+cell.z()-2*cell.y(),
                colors.get(cell.state()),
                cell.x()+cell.z()+cell.y()));
        }
        decoded.sort(Comparator.comparingInt(RenderBlock::depth));
        drawing=List.copyOf(decoded);
    }

    private void drawBlueprint(GuiGraphics gfx,int rightX,int rw,int top,int rh){
        if(drawing.isEmpty())return;
        var model=data.miniature();
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        for(var cell:drawing){
            minX=Math.min(minX,cell.isoX());maxX=Math.max(maxX,cell.isoX());
            minY=Math.min(minY,cell.isoY());maxY=Math.max(maxY,cell.isoY());
        }
        // Quantized pixel art from the schematic, not a stock item thumbnail.
        double scale=Math.min((rw-12.0)/Math.max(1,maxX-minX+3),
            (rh-12.0)/Math.max(1,maxY-minY+3));
        scale=Math.max(.6,Math.min(6,scale));
        int originX=rightX+rw/2-(int)Math.round((minX+maxX)*scale/2.0);
        int originY=top+rh/2-(int)Math.round((minY+maxY)*scale/2.0);
        int thickness=Math.max(1,(int)Math.ceil(scale*1.3));
        for(var block:drawing){
            int bx=originX+(int)Math.round(block.isoX()*scale);
            int by=originY+(int)Math.round(block.isoY()*scale);
            gfx.fill(bx,by,bx+thickness,by+Math.max(1,thickness/2+1),block.color());
        }
    }

    @Override public boolean isPauseScreen(){return false;}
}
