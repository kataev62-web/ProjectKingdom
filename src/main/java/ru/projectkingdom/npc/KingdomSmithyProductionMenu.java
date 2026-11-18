package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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

/** Server-authoritative production screen; no client-provided recipe, item or quantity. */
public final class KingdomSmithyProductionMenu extends ChestMenu {
    private final ServerPlayer player;
    private final SimpleContainer items;
    private final UUID enterprise;
    private final BlockPos corePos;
    private final List<KingdomNpcData.Worker> smiths;
    private int workerIndex,page,quantity=1;
    private KingdomTfcSmithWork.RecipeOption selected;
    private List<KingdomTfcSmithWork.RecipeOption> choices=List.of();

    private KingdomSmithyProductionMenu(int id,Inventory inventory,ServerPlayer player,
            UUID enterprise,BlockPos corePos,SimpleContainer container){
        super(MenuType.GENERIC_9x3,id,inventory,container,3);
        this.player=player;this.items=container;this.enterprise=enterprise;this.corePos=corePos;
        var staff=new ArrayList<KingdomNpcData.Worker>();
        var payroll=KingdomEnterprisePayrollData.get(player.getServer());
        for(var worker:KingdomNpcData.get(player.getServer()).all()){
            if(!"smith".equals(worker.job)||!player.getUUID().equals(worker.employer)
                    ||!enterprise.toString().equals(worker.workplaceId))continue;
            var contract=payroll.contract(enterprise,worker.entityId);
            if(contract!=null&&contract.active())staff.add(worker);
        }
        staff.sort(Comparator.comparingInt(w->w.id));
        this.smiths=List.copyOf(staff);
        var order=KingdomSmithyOrdersData.get(player.getServer()).order(enterprise);
        if(order!=null)for(int i=0;i<smiths.size();i++)
            if(smiths.get(i).entityId.equals(order.worker())){workerIndex=i;break;}
        redraw();
    }
    public static void open(ServerPlayer player,UUID enterprise,BlockPos core){
        if(player==null||enterprise==null||core==null)return;
        var firms=KingdomEnterpriseData.get(player.getServer());
        if(firms.kind(enterprise)!=EnterpriseKind.SMITHY
                ||!firms.mayManage(player.getUUID(),enterprise))return;
        player.openMenu(new SimpleMenuProvider((id,inv,p)->
            new KingdomSmithyProductionMenu(id,inv,player,enterprise,core,new SimpleContainer(27)),
            Component.literal("Кузница TFC — заказы")));
    }
    private KingdomNpcData.Worker worker(){
        return workerIndex>=0&&workerIndex<smiths.size()?smiths.get(workerIndex):null;
    }
    private void icon(int slot,Item type,String name,String... details){
        ItemStack stack=new ItemStack(type);
        stack.setHoverName(Component.literal(name));
        if(details.length>0){
            var lore=new ListTag();
            for(String line:details)
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line))));
            stack.getOrCreateTagElement("display").put("Lore",lore);
        }
        items.setItem(slot,stack);
    }
    private void redraw(){
        items.clearContent();
        var w=worker();
        choices=w==null?List.of():KingdomTfcSmithWork.availableRecipes(player.serverLevel(),w);
        if(selected!=null&&!choices.contains(selected))selected=null;
        if(selected==null&&!choices.isEmpty())selected=choices.get(0);
        int pages=Math.max(1,(choices.size()+8)/9);
        page=Math.min(page,pages-1);
        for(int i=0;i<9;i++){
            int index=page*9+i;
            if(index>=choices.size())break;
            var option=choices.get(index);
            icon(i,selected!=null&&selected.equals(option)?Items.EMERALD:Items.PAPER,
                (selected!=null&&selected.equals(option)?"[ВЫБРАН] ":"")+option.recipeId(),
                "Настоящий TFC AnvilRecipe", "Сырьё: "+option.inputId(),
                "Рецепт доступен для текущей наковальни", "ЛКМ — выбрать изделие");
        }
        icon(9,Items.ARROW,"Предыдущая страница ("+(page+1)+"/"+pages+")");
        icon(10,Items.ARROW,"Следующая страница ("+(page+1)+"/"+pages+")");
        icon(11,Items.REDSTONE,"Количество −1: "+quantity);
        icon(12,Items.EMERALD,"Количество +1: "+quantity);
        icon(13,Items.REDSTONE_BLOCK,"Количество −10: "+quantity);
        icon(14,Items.EMERALD_BLOCK,"Количество +10: "+quantity);
        icon(15,Items.ANVIL,"СОХРАНИТЬ ЗАКАЗ: "+quantity+" шт.",
            "Только настоящий TFC anvil recipe", "Нужны реальное сырьё и молот на наковальне");
        var data=KingdomSmithyOrdersData.get(player.getServer());
        var order=data.order(enterprise);
        icon(16,Items.LEVER,"ПАУЗА / ПРОДОЛЖИТЬ",
            "Не удаляет предметы внутри настоящих TFC-машин");
        icon(17,Items.CLOCK,"ОБНОВИТЬ реальные рецепты и состояние");
        icon(18,Items.VILLAGER_SPAWN_EGG,"КУЗНЕЦ: "+(w==null?"нет нанятого":w.name),
            "Сотрудников кузницы: "+smiths.size(),
            "ЛКМ — следующий нанятый кузнец",
            w==null?"Найми кузнеца через предприятие":"NPC UUID: "+w.entityId);
        if(order==null){
            icon(19,Items.WRITABLE_BOOK,"Заказ не задан",
                "Выбери рецепт, количество и нажми СОХРАНИТЬ");
        }else{
            icon(19,order.finished()?Items.GOLD_INGOT:Items.BOOK,
                "ЗАКАЗ: "+order.delivered()+"/"+order.requested()
                    +" | "+order.phase().name(),
                "Рецепт: "+order.recipe(),"Сырьё: "+order.input(),
                "Уже доставлено: "+order.delivered(),
                "Осталось: "+order.remaining(),
                "Причина/состояние: "+order.message());
        }
        icon(20,Items.CHEST,"Склады: сырьё → TFC горн → TFC наковальня → продукция",
            "Всё физическое: никаких виртуальных материалов");
        icon(21,Items.IRON_INGOT,"СЛИТКИ: автоматическое литьё пока не реализовано",
            "Требуются настоящие TFC тигель, формы, жидкий металл и охлаждение.",
            "Это меню выполняет только настоящие AnvilRecipe.");
        icon(26,Items.OAK_DOOR,"ЗАКРЫТЬ");
        broadcastChanges();
    }
    @Override public boolean stillValid(Player player){
        if(player!=this.player||player.distanceToSqr(corePos.getX()+.5,
                corePos.getY()+.5,corePos.getZ()+.5)>64)return false;
        var firms=KingdomEnterpriseData.get(this.player.getServer());
        var binding=firms.core(enterprise);
        if(firms.kind(enterprise)!=EnterpriseKind.SMITHY
                ||!firms.mayManage(this.player.getUUID(),enterprise)
                ||binding==null||!binding.active()
                ||binding.packedPos()!=corePos.asLong()
                ||!binding.dimension().equals(this.player.serverLevel().dimension().location().toString())
                ||!this.player.serverLevel().hasChunkAt(corePos))return false;
        return this.player.serverLevel().getBlockEntity(corePos)
                instanceof EnterpriseCoreBlockEntity be
                &&be.bound()&&enterprise.equals(be.enterpriseId())
                &&this.player.getUUID().equals(be.owner())&&be.kind()==EnterpriseKind.SMITHY;
    }
    @Override public void clicked(int slot,int button,ClickType click,Player actor){
        if(!stillValid(actor)||click!=ClickType.PICKUP||button!=0||slot<0||slot>=27)return;
        var w=worker();
        var orders=KingdomSmithyOrdersData.get(player.getServer());
        switch(slot){
            case 9 -> page=Math.max(0,page-1);
            case 10 -> page++;
            case 11 -> quantity=Math.max(1,quantity-1);
            case 12 -> quantity=Math.min(KingdomSmithyOrdersData.MAX_QUANTITY,quantity+1);
            case 13 -> quantity=Math.max(1,quantity-10);
            case 14 -> quantity=Math.min(KingdomSmithyOrdersData.MAX_QUANTITY,quantity+10);
            case 18 -> {if(!smiths.isEmpty()){workerIndex=(workerIndex+1)%smiths.size();selected=null;page=0;}}
            case 15 -> {
                // Recompute server-side: the raw stock, recipe, tier and employment
                // may have changed since the GUI was drawn.
                if(w!=null&&selected!=null
                    &&KingdomSmithyOrdersData.get(player.getServer()).order(enterprise)!=null
                    &&!KingdomSmithyOrdersData.get(player.getServer()).order(enterprise).finished()){
                    player.sendSystemMessage(Component.literal("[Кузница] Сначала завершите действующий заказ."));
                }else if(w==null||selected==null){
                    player.sendSystemMessage(Component.literal("[Кузница] Нет действительного TFC-рецепта или нанятого кузнеца."));
                }else if(!KingdomTfcSmithWork.availableRecipes(player.serverLevel(),w).contains(selected)
                        ||!KingdomTfcSmithWork.idleAnvil(player.serverLevel(),w)){
                    player.sendSystemMessage(Component.literal("[Кузница] Проверьте настоящее сырьё, наковальню и свободное место для ковки."));
                }else if(orders.create(enterprise,w.entityId,selected.recipeId(),selected.inputId(),quantity)){
                    w.smithRecipeId=selected.recipeId();
                    w.order="work";
                    KingdomNpcData.get(player.getServer()).setDirty();
                    player.sendSystemMessage(Component.literal("[Кузница] Создан заказ "+quantity+" шт.: "+selected.recipeId()));
                }
            }
            case 16 -> {
                if(!orders.togglePause(enterprise))
                    player.sendSystemMessage(Component.literal("[Кузница] Изменить паузу сейчас нельзя: нет активного заказа или изделие ожидает доставки."));
            }
            case 26 -> {player.closeContainer();return;}
            default -> {
                if(slot>=0&&slot<9&&page*9+slot<choices.size())
                    selected=choices.get(page*9+slot);
            }
        }
        if(player.containerMenu==this)redraw();
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
