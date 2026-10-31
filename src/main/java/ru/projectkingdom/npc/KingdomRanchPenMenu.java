package ru.projectkingdom.npc;

import java.util.UUID;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;
import ru.projectkingdom.enterprise.EnterpriseKind;

/** Server-authoritative livestock pen editor. No animal ownership by menu clicks. */
public final class KingdomRanchPenMenu extends ChestMenu {
    private final ServerPlayer player;
    private final SimpleContainer items;
    private final UUID farm;
    private final BlockPos core;
    private int selected;

    private KingdomRanchPenMenu(int id,Inventory inventory,ServerPlayer player,
                                 UUID farm,BlockPos core,SimpleContainer items){
        super(MenuType.GENERIC_9x3,id,inventory,items,3);
        this.player=player;this.farm=farm;this.core=core;this.items=items;
        redraw();
    }
    public static void open(ServerPlayer p,UUID farm,BlockPos core){
        if(!KingdomFarmFieldData.allowed(p,farm))return;
        p.openMenu(new SimpleMenuProvider((id,inv,owner)->
            new KingdomRanchPenMenu(id,inv,p,farm,core,new SimpleContainer(27)),
            Component.literal("Ферма — загоны TFC")));
    }
    private void icon(int slot,Item item,String label){
        var stack=new ItemStack(item);
        stack.setHoverName(Component.literal(label));items.setItem(slot,stack);
    }
    /** Split long statuses across normal multiline vanilla tooltips instead of truncating. */
    private void detail(int slot,Item item,String title,List<String> details){
        var stack=new ItemStack(item);
        stack.setHoverName(Component.literal(title));
        var lore=new ListTag();
        for(String line:details)lore.add(StringTag.valueOf(
                Component.Serializer.toJson(Component.literal(line))));
        stack.getOrCreateTagElement("display").put("Lore",lore);
        items.setItem(slot,stack);
    }
    private void redraw(){
        items.clearContent();
        var pens=KingdomRanchPenData.get(player.getServer()).of(farm);
        if(selected>=pens.size())selected=Math.max(0,pens.size()-1);
        for(int i=0;i<pens.size()&&i<4;i++){
            var pen=pens.get(i);
            var status=KingdomLivestockWork.inspect(player.serverLevel(),pen);
            String title=(i==selected?"[ВЫБРАН] ":"")+"Загон "+(i+1)
                +" ("+(pen.maxX()-pen.minX()+1)+"×"+(pen.maxZ()-pen.minZ()+1)+")";
            if(!status.areaLoaded()){
                detail(i,Items.OAK_FENCE,title,List.of(
                    "Животные: НЕИЗВЕСТНО",
                    "Не все чанки загона загружены.",
                    "Подойдите к загону, затем откройте меню.",
                    pen.enabled()?"Загон включён":"Загон остановлен"));
            }else if(!status.tfcAvailable()){
                detail(i,Items.OAK_FENCE,title,List.of(
                    "Всего животных: "+status.animals(),
                    "TFC-обработчик недоступен.",
                    "Проверьте журнал сервера.",
                    pen.enabled()?"Загон включён":"Загон остановлен"));
            }else{
                detail(i,Items.OAK_FENCE,title,List.of(
                    "Всего животных: "+status.animals(),
                    "Животных TFC: "+status.tfcAnimals(),
                    "Другие животные: "+status.otherAnimals(),
                    "Голодных TFC: "+status.hungry(),
                    "Шерсть: "+status.woolReady()+" | Молоко: "+status.milkReady(),
                    "Яйца в гнёздах: "+status.edibleEggs(),
                    pen.enabled()?"Загон включён":"Загон остановлен"));
            }
        }
        icon(9,Items.BOOK,"Загоны "+pens.size()+"/4. Наведи на загон для подсчёта животных."); 
        var chosen=selected<pens.size()?pens.get(selected):null;
        boolean autoFeed=chosen!=null&&KingdomRanchPolicyData.get(player.getServer())
                .policy(chosen.id()).autoFeed();
        detail(10,Items.WHEAT,"АВТОКОРМЛЕНИЕ: "+(autoFeed?"ВКЛ":"ВЫКЛ"),List.of(
            "ЛКМ — изменить режим выбранного загона.",
            "По умолчанию выключено: кормление TFC",
            "возможно лишь один раз в игровой день.",
            "При включении NPC может накормить раньше вас.",
            "Вручную: Shift + ПКМ с подходящим кормом."));
        icon(18,Items.OAK_FENCE,"ДОБАВИТЬ ЗАГОН: два угла Shift+ПКМ пустой рукой");
        icon(19,Items.LEVER,"ВКЛЮЧИТЬ / ВЫКЛЮЧИТЬ выбранный загон");
        icon(20,Items.BARRIER,"УДАЛИТЬ загон из плана. Животные и блоки остаются.");
        boolean enabled=chosen!=null
            &&KingdomRanchPolicyData.get(player.getServer()).policy(chosen.id()).cullOld();
        icon(21,Items.IRON_SWORD,"ПЛАНОВЫЙ ЗАБОЙ старых животных: "
            +(enabled?"ВКЛ":"ВЫКЛ")
            +" | только старые, не беременные; стадо минимум 6, не более одного в сутки; ЛКМ изменить");
        boolean eggCollection=chosen!=null
            &&KingdomRanchPolicyData.get(player.getServer()).policy(chosen.id()).collectEggs();
        icon(23,Items.EGG,"СБОР ЯИЦ: "+(eggCollection?"ВКЛ":"ВЫКЛ")
            +" — ЛКМ изменить. Только неоплодотворённые яйца из настоящих гнёзд TFC.");
        icon(24,Items.MILK_BUCKET,"Молоко: реальная жидкость из TFC в настоящую пустую тару со склада.");
        icon(22,Items.SHEARS,"Уход: корм из сырьевого склада, шерсть в готовый склад. Требуются настоящие ножницы.");
        icon(26,Items.OAK_DOOR,"ЗАКРЫТЬ");
        broadcastChanges();
    }
    @Override public boolean stillValid(Player p){
        return p==player&&KingdomFarmFieldData.allowed(player,farm)
            &&player.distanceToSqr(core.getX()+.5,core.getY()+.5,core.getZ()+.5)<=64
            &&player.serverLevel().hasChunkAt(core)
            &&player.serverLevel().getBlockEntity(core) instanceof EnterpriseCoreBlockEntity be
            &&be.bound()&&farm.equals(be.enterpriseId())&&be.kind()==EnterpriseKind.FARM;
    }
    @Override public void clicked(int slot,int button,ClickType click,Player p){
        if(!stillValid(p)||click!=ClickType.PICKUP||button!=0||slot<0||slot>=27)return;
        var data=KingdomRanchPenData.get(player.getServer());
        var pens=data.of(farm);
        if(slot<4){
            if(slot<pens.size())selected=slot;
        }else switch(slot){
            case 18 -> {
                if(KingdomFarmSelection.beginPen(player,farm))player.closeContainer();
                return;
            }
            case 10 -> {
                if(selected<pens.size()){
                    var policy=KingdomRanchPolicyData.get(player.getServer());
                    if(policy.toggleAutoFeed(player,farm,pens.get(selected).id()))
                        player.sendSystemMessage(Component.literal("[Ферма] Автокормление "+
                            (policy.policy(pens.get(selected).id()).autoFeed()?"включено":"выключено")+
                            ". Ручное кормление TFC: Shift+ПКМ с подходящим кормом, раз в игровые сутки."));
                }
            }
            case 19 -> {
                if(selected<pens.size())data.toggle(player,farm,pens.get(selected).id());
            }
            case 20 -> {
                if(selected<pens.size())data.remove(player,farm,pens.get(selected).id());
            }
            case 21 -> {
                if(selected<pens.size()){
                    var policy=KingdomRanchPolicyData.get(player.getServer());
                    if(policy.toggleCull(player,farm,pens.get(selected).id())){
                        boolean enabled=policy.policy(pens.get(selected).id()).cullOld();
                        player.sendSystemMessage(Component.literal("[Ферма] Плановый забой старых животных "
                            +(enabled?"ВКЛЮЧЁН":"ОТКЛЮЧЁН")
                            +". Сохраняем как минимум 6 животных, не трогаем беременных."));
                    }
                }
            }
            case 23 -> {
                if(selected<pens.size()){
                    var policy=KingdomRanchPolicyData.get(player.getServer());
                    if(policy.toggleEggCollection(player,farm,pens.get(selected).id())){
                        boolean enabled=policy.policy(pens.get(selected).id()).collectEggs();
                        player.sendSystemMessage(Component.literal(
                            "[Ферма] Сбор яиц из гнёзд "+(enabled?"включён":"отключён")
                            +". Оплодотворённые яйца остаются в гнёздах."));
                    }
                }
            }
            case 26 -> {player.closeContainer();return;}
            default -> {}
        }
        if(player.containerMenu==this)redraw();
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
