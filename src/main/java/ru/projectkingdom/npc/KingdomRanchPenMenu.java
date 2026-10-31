package ru.projectkingdom.npc;

import java.util.UUID;
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
    private void redraw(){
        items.clearContent();
        var pens=KingdomRanchPenData.get(player.getServer()).of(farm);
        if(selected>=pens.size())selected=Math.max(0,pens.size()-1);
        for(int i=0;i<pens.size()&&i<4;i++){
            var pen=pens.get(i);
            var status=KingdomLivestockWork.inspect(player.serverLevel(),pen);
            String herd=status.loaded()
                ?" | животных "+status.animals()+" (TFC "+status.tfcAnimals()
                    +"), голодных TFC "+status.hungry()
                :" | поголовье не проверено: чанки загона не загружены";
            icon(i,Items.OAK_FENCE,(i==selected?"[ВЫБРАН] ":"")+"Загон "+(i+1)
                +" | "+(pen.maxX()-pen.minX()+1)+"x"+(pen.maxZ()-pen.minZ()+1)
                +herd+", шерсть готова "+status.woolReady()+", молоко "+status.milkReady()
                +", свежие яйца "+status.edibleEggs()
                +" | "+(pen.enabled()?"работает":"остановлен"));
        }
        icon(9,Items.BOOK,"Загоны "+pens.size()+"/4; NPC работает только внутри выбранной территории");
        icon(18,Items.OAK_FENCE,"ДОБАВИТЬ ЗАГОН: два угла Shift+ПКМ пустой рукой");
        icon(19,Items.LEVER,"ВКЛЮЧИТЬ / ВЫКЛЮЧИТЬ выбранный загон");
        icon(20,Items.BARRIER,"УДАЛИТЬ загон из плана. Животные и блоки остаются.");
        var chosen=selected<pens.size()?pens.get(selected):null;
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
        icon(22,Items.SHEARS,"Уход NPC: корм из сырьевого склада, шерсть в готовый склад. Требуются настоящие ножницы.");
        icon(25,Items.WHEAT,"КОРМЛЕНИЕ ИГРОКОМ: держи корм, подходящий виду TFC, и ПКМ по животному. TFC разрешает кормить один раз за игровой день; если голодных 0 — уже накормлены сегодня.");
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
