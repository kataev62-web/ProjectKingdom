package ru.projectkingdom.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.function.BooleanSupplier;

/**
 * Kingdom's native medieval GUI art. Textures live in the JAR and are tiled
 * rather than stretched beyond their source size, so the panel remains crisp
 * at all supported Minecraft GUI scales without external resource packs.
 *
 * Layout uses vanilla GUI coordinates; clicks, keyboard focus, narration and
 * tooltips remain genuine Minecraft widgets.
 */
public final class KingdomAtlasSkin {
    private static final String DOMAIN = "kingdomnpc";
    private static final ResourceLocation WOOD =
            new ResourceLocation(DOMAIN,"textures/gui/atlas_wood.png");
    private static final ResourceLocation PARCHMENT =
            new ResourceLocation(DOMAIN,"textures/gui/atlas_parchment.png");
    private static final ResourceLocation PANEL =
            new ResourceLocation(DOMAIN,"textures/gui/atlas_panel.png");
    private static final ResourceLocation BUTTON =
            new ResourceLocation(DOMAIN,"textures/gui/atlas_button.png");
    public static final int INK = 0xFF392819;
    public static final int GOLD = 0xFFD5AF65;
    public static final int CREAM = 0xFFF8E8C5;
    private static final int WALNUT = 0xFF291D16;

    private KingdomAtlasSkin() {}

    private static void tile(GuiGraphics gui,ResourceLocation texture,
                             int x,int y,int w,int h,int tw,int th) {
        if(w<=0||h<=0)return;
        for(int dy=0;dy<h;dy+=th) {
            for(int dx=0;dx<w;dx+=tw) {
                int cw=Math.min(tw,w-dx),ch=Math.min(th,h-dy);
                gui.blit(texture,x+dx,y+dy,0,0,cw,ch,tw,th);
            }
        }
    }

    public static void frame(GuiGraphics gui,int left,int top,int width,int height,
                             int navWidth,int infoX,int infoWidth) {
        gui.fill(left-3,top-3,left+width+3,top+height+4,0xC2000000);
        gui.fill(left-2,top-2,left+width+2,top+height+2,WALNUT);
        tile(gui,PARCHMENT,left+2,top+2,width-4,height-4,64,64);
        tile(gui,WOOD,left+3,top+3,navWidth-2,height-6,64,64);
        gui.fill(left+navWidth,top+3,left+navWidth+2,top+height-3,INK);
        gui.fill(left+navWidth+2,top+3,left+navWidth+3,top+height-3,GOLD);
        if(infoWidth>0) {
            tile(gui,PANEL,infoX,top+25,infoWidth,height-51,64,64);
            gui.fill(infoX-3,top+24,infoX-1,top+height-24,INK);
            gui.fill(infoX-2,top+25,infoX-1,top+height-25,GOLD);
        }
        gui.fill(left+1,top+1,left+width-1,top+2,GOLD);
        gui.fill(left+1,top+height-2,left+width-1,top+height-1,GOLD);
        gui.fill(left+1,top+1,left+2,top+height-1,GOLD);
        gui.fill(left+width-2,top+1,left+width-1,top+height-1,GOLD);
        // Four brass fasteners on a walnut construction frame.
        stud(gui,left+4,top+4);
        stud(gui,left+width-7,top+4);
        stud(gui,left+4,top+height-7);
        stud(gui,left+width-7,top+height-7);
    }

    private static void stud(GuiGraphics gui,int x,int y) {
        gui.fill(x,y,x+4,y+4,INK);
        gui.fill(x+1,y+1,x+3,y+3,GOLD);
    }

    public static void mapBezel(GuiGraphics gui,int x,int y,int w,int h) {
        gui.fill(x-4,y-4,x+w+4,y+h+4,WALNUT);
        gui.fill(x-3,y-3,x+w+3,y+h+3,GOLD);
        gui.fill(x-2,y-2,x+w+2,y+h+2,INK);
        tile(gui,PARCHMENT,x,y,w,h,64,64);
        // Guide ticks, positioned on the physical map frame, not map pixels.
        for(int px=0;px<w;px+=24) {
            gui.fill(x+px,y-3,x+px+1,y-1,CREAM);
            gui.fill(x+px,y+h+1,x+px+1,y+h+3,CREAM);
        }
    }

    private static void buttonSkin(GuiGraphics gui,int x,int y,int w,int h,
                                   boolean selected,boolean hovered) {
        if(w<5 || h<6)return;
        gui.fill(x,y+1,x+w,y+h+1,0x99000000);
        tile(gui,BUTTON,x,y,w,h,64,20);
        if(selected) {
            gui.fill(x+1,y+1,x+w-1,y+3,0xFFECC675);
            gui.fill(x+1,y+h-3,x+w-1,y+h-1,0xFFAD8548);
        }
        if(hovered) {
            gui.fill(x+2,y+3,x+w-2,y+h-3,0x45FFE9AA);
        }
        gui.fill(x,y,x+w,y+1,selected?CREAM:GOLD);
        gui.fill(x,y+h-1,x+w,y+h,WALNUT);
        gui.fill(x,y,x+1,y+h,INK);
        gui.fill(x+w-1,y,x+w,y+h,INK);
        if(selected)gui.fill(x+1,y+3,x+3,y+h-2,0xFFE9C26D);
    }

    /** Focusable, narratable, icon-bearing button with the native Kingdom skin. */
    public static final class AtlasButton extends AbstractButton {
        private final ItemStack icon;
        private final Runnable action;
        private final BooleanSupplier selected;

        public AtlasButton(int x,int y,int width,int height,String text,
                           Item icon,Runnable action,BooleanSupplier selected) {
            super(x,y,width,height,Component.literal(text));
            this.icon=icon==null?ItemStack.EMPTY:new ItemStack(icon);
            this.action=action;
            this.selected=selected==null?()->false:selected;
            setTooltip(Tooltip.create(Component.literal(text)));
        }

        @Override public void onPress() {
            if(active)action.run();
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
        @Override protected void renderWidget(GuiGraphics gui,int mouseX,int mouseY,float partial) {
            boolean chosen=selected.getAsBoolean();
            buttonSkin(gui,getX(),getY(),width,height,chosen,isHoveredOrFocused());
            var font=Minecraft.getInstance().font;
            int textX=getX()+5;
            boolean showIcon=!icon.isEmpty() && width>=82 && height>=18;
            if(showIcon){
                gui.renderItem(icon,getX()+3,getY()+(height-16)/2);
                textX=getX()+22;
            }
            int maxWidth=Math.max(4,width-(textX-getX())-4);
            String label=font.plainSubstrByWidth(getMessage().getString(),maxWidth);
            int textY=getY()+(height-8)/2;
            int color=active?(chosen?0xFFFFE8AE:0xFFF6E4C6):0xFFB8A58D;
            if(showIcon)gui.drawString(font,label,textX,textY,color,true);
            else gui.drawCenteredString(font,label,getX()+width/2,textY,color);
        }
    }
}
