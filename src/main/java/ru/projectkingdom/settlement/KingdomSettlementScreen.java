package ru.projectkingdom.settlement;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.projectkingdom.network.KingdomNetwork;

/** A real, keyboard-accessible Minecraft settlement founding screen. */
public final class KingdomSettlementScreen extends AbstractContainerScreen<KingdomSettlementMenu> {
    private EditBox name;
    public KingdomSettlementScreen(KingdomSettlementMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        this.imageWidth=338;this.imageHeight=210;
        this.inventoryLabelY=999;
    }
    @Override protected void init() {
        super.init();
        int x=leftPos+25,y=topPos+89;
        if(!menu.founded()){
            name=new EditBox(font,x,y,imageWidth-50,20,Component.literal("Название поселения"));
            name.setMaxLength(32);
            name.setHint(Component.literal("Например: Новая Дубрава"));
            addRenderableWidget(name);
            setInitialFocus(name);
            addRenderableWidget(Button.builder(Component.literal("Создать поселение"),
                b->create()).bounds(x,topPos+122,imageWidth-50,25).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Закрыть"),
                b->onClose()).bounds(leftPos+25,topPos+166,imageWidth-50,20).build());
    }
    private void create(){
        if(name==null)return;
        String text=name.getValue().strip();
        if(KingdomSettlementFounding.cleanName(text)==null)return;
        KingdomNetwork.CHANNEL.sendToServer(new KingdomSettlementFoundPacket(menu.pos(),text));
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==257||key==335){
            if(!menu.founded()){create();return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos-3,topPos-3,leftPos+imageWidth+3,topPos+imageHeight+3,0xFF382719);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFFDECC9E);
        g.fill(leftPos+7,topPos+7,leftPos+imageWidth-7,topPos+imageHeight-7,0xFFF3E5C1);
        g.fill(leftPos+12,topPos+12,leftPos+imageWidth-12,topPos+13,0xFF9A6B37);
        g.fill(leftPos+12,topPos+60,leftPos+imageWidth-12,topPos+61,0xFFBA996A);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawCenteredString(font,"ОСНОВАНИЕ ПОСЕЛЕНИЯ",imageWidth/2,21,0xFF49311D);
        g.drawCenteredString(font,menu.title(),imageWidth/2,43,0xFF745332);
        if(menu.founded()) {
            g.drawCenteredString(font,"Здесь уже основано поселение.",imageWidth/2,101,0xFF355E38);
            g.drawCenteredString(font,"Рост: хутор → деревня → посёлок → город",imageWidth/2,124,0xFF745332);
        }else{
            g.drawString(font,"Введите название нового поселения:",25,72,0xFF49311D,false);
            g.drawCenteredString(font,"Одно поселение на основателя • стартовый чанк 16×16",
                    imageWidth/2,155,0xFF745332);
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);
        super.render(g,mx,my,partial);
        renderTooltip(g,mx,my);
    }
}
