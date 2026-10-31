package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;
import ru.projectkingdom.enterprise.EnterpriseKind;

/** Forest owners plan owned plots, tree species and physical stock thresholds. */
public final class KingdomForestMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final UUID business;
    private final BlockPos core;
    private final SimpleContainer icons;
    private int selection;
    private KingdomForestMenu(int id,Inventory inventory,ServerPlayer owner,UUID business,
                              BlockPos core,SimpleContainer icons){
        super(MenuType.GENERIC_9x3,id,inventory,icons,3);
        this.viewer=owner;this.business=business;this.core=core.immutable();
        this.icons=icons;redraw();
    }
    public static void open(ServerPlayer player,UUID business,BlockPos core){
        if(!KingdomForestData.allowed(player,business))return;
        player.openMenu(new SimpleMenuProvider((id,inventory,owner)->
            new KingdomForestMenu(id,inventory,player,business,core,new SimpleContainer(27)),
            Component.literal("Лесозаготовка — участки и посадки")));
    }
    private void icon(int slot,Item item,String text){
        ItemStack stack=new ItemStack(item);stack.setHoverName(Component.literal(text));
        icons.setItem(slot,stack);
    }
    private List<KingdomForestData.Area> plots(){
        return KingdomForestData.get(viewer.getServer()).of(business);
    }
    private void info(String message){viewer.sendSystemMessage(Component.literal("[Лес] "+message));}
    private void redraw(){
        icons.clearContent();
        var plots=plots();
        if(selection>=plots.size())selection=Math.max(0,plots.size()-1);
        for(int i=0;i<plots.size()&&i<8;i++){
            var a=plots.get(i);
            Item item=Items.OAK_SAPLING;
            var key=ResourceLocation.tryParse(a.sapling());
            if(key!=null&&BuiltInRegistries.ITEM.containsKey(key))
                item=BuiltInRegistries.ITEM.get(key);
            icon(i,item,(i==selection?"[ВЫБРАН] ":"")+"Участок "+(i+1)+
                " | "+(a.maxX()-a.minX()+1)+"x"+(a.maxZ()-a.minZ()+1)+
                " | "+(a.sapling().isBlank()?"ПОРОДА НЕ НАЗНАЧЕНА":a.sapling())+
                " | "+(a.enabled()?"работает":"пауза"));
        }
        icon(9,Items.MAP,"Участков "+plots.size()+"/8. Работа исключительно на своей территории.");
        icon(17,Items.COMPASS,"Проверка: норма древесины, вырубка, восстановление — TFC, не бесконечные блоки.");
        icon(18,Items.OAK_SAPLING,"ДОБАВИТЬ: Shift + ПКМ по двум углам своего леса");
        icon(19,Items.OAK_LOG,"ПОРОДА ИЗ РУКИ: держи настоящий саженец TFC");
        icon(20,Items.LEVER,"ПАУЗА / РАБОТА выбранного участка");
        icon(21,Items.BARRIER,"УДАЛИТЬ выбранный план (деревья останутся)");
        icon(22,Items.CHEST,"НОРМЫ ДРЕВЕСИНЫ: изменить min/max 32/128 → 64/256 → 128/512");
        icon(23,Items.BONE_MEAL,"ВОССТАНОВЛЕНИЕ ЛЕСА: включить/выключить посадки из склада");
        icon(24,Items.SPRUCE_SAPLING,"СЛЕДУЮЩАЯ ПОРОДА: перебрать реальные виды TFC без саженца в руке");
        icon(26,Items.OAK_DOOR,"ЗАКРЫТЬ");
        broadcastChanges();
    }
    @Override public boolean stillValid(Player player){
        return player==viewer&&KingdomForestData.allowed(viewer,business)
            &&viewer.distanceToSqr(core.getX()+.5,core.getY()+.5,core.getZ()+.5)<=64
            &&viewer.serverLevel().hasChunkAt(core)
            &&viewer.serverLevel().getBlockEntity(core) instanceof EnterpriseCoreBlockEntity block
            &&block.bound()&&business.equals(block.enterpriseId())
            &&block.kind()==EnterpriseKind.LUMBER;
    }
    @Override public void clicked(int slot,int button,ClickType click,Player player){
        if(!stillValid(player)||click!=ClickType.PICKUP||button!=0
            ||slot<0||slot>=27)return;
        var data=KingdomForestData.get(viewer.getServer());
        var plots=plots();
        if(slot<8){
            if(slot<plots.size())selection=slot;
        }else switch(slot){
            case 18 -> {
                if(KingdomForestSelection.begin(viewer,business))viewer.closeContainer();
                return;
            }
            case 19 -> {
                if(selection>=plots.size()){info("Сначала создай лесной участок.");break;}
                String id=BuiltInRegistries.ITEM.getKey(viewer.getMainHandItem().getItem()).toString();
                if(data.setSapling(viewer,business,plots.get(selection).id(),id))
                    info("Порода: "+id+". NPC потребуются настоящие саженцы на складе.");
                else info("Держи настоящий саженец TerraFirmaCraft в основной руке.");
            }
            case 20 -> {if(selection<plots.size())
                data.toggle(viewer,business,plots.get(selection).id());}
            case 21 -> {if(selection<plots.size())
                data.delete(viewer,business,plots.get(selection).id());}
            case 22 -> {if(selection<plots.size())
                data.cycleStock(viewer,business,plots.get(selection).id());}
            case 23 -> {if(selection<plots.size())
                data.toggleReplant(viewer,business,plots.get(selection).id());}
            case 24 -> {
                if(selection>=plots.size()){info("Сначала создай участок.");break;}
                var available=BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString)
                    .filter(KingdomTfcForestry::validSapling).sorted().toList();
                if(available.isEmpty()){info("Совместимые саженцы TFC не зарегистрированы.");break;}
                int at=available.indexOf(plots.get(selection).sapling());
                String selected=available.get((at+1)%available.size());
                if(data.setSapling(viewer,business,plots.get(selection).id(),selected))
                    info("Выбран "+selected+". На сырьевом складе нужны реальные саженцы.");
            }
            case 26 -> {viewer.closeContainer();return;}
            default -> {}
        }
        if(viewer.containerMenu==this)redraw();
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
