package ru.projectkingdom.npc;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;

/** Physical field manager opened from the FARM enterprise production tab. */
public final class KingdomFarmFieldsMenu extends ChestMenu {
    private static final int VIEW=9, ASSIGN_SEED=18, NEW=19, TOGGLE=20, DELETE=21, DIAGNOSE=22;
    private final ServerPlayer viewer;
    private final SimpleContainer icons;
    private final UUID farm;
    private final BlockPos core;
    private int selected=0;

    private KingdomFarmFieldsMenu(int window,Inventory inventory,ServerPlayer player,
                                  UUID farm,BlockPos core,SimpleContainer icons) {
        super(MenuType.GENERIC_9x3,window,inventory,icons,3);
        this.viewer=player;this.farm=farm;this.core=core.immutable();this.icons=icons;
        redraw();
    }
    public static void open(ServerPlayer player,UUID enterprise,BlockPos position) {
        if(!KingdomFarmFieldData.allowed(player,enterprise))return;
        player.openMenu(new SimpleMenuProvider((id,inv,p)->
            new KingdomFarmFieldsMenu(id,inv,player,enterprise,position,new SimpleContainer(27)),
            Component.literal("Ферма — поля и семена")));
    }
    private void icon(int at,Item item,String label) {
        ItemStack stack=new ItemStack(item);
        stack.setHoverName(Component.literal(label));
        icons.setItem(at,stack);
    }
    private List<KingdomFarmFieldData.Field> fields() {
        return KingdomFarmFieldData.get(viewer.getServer()).of(farm);
    }
    private KingdomFarmFieldData.Field selected() {
        var list=fields();
        return selected>=0&&selected<list.size()?list.get(selected):null;
    }
    private void redraw() {
        icons.clearContent();
        var list=fields();
        if(selected>=list.size())selected=Math.max(0,list.size()-1);
        for(int i=0;i<list.size()&&i<8;i++){
            var f=list.get(i);
            Item item=Items.WHEAT_SEEDS;
            var key=ResourceLocation.tryParse(f.seedId());
            if(key!=null&&BuiltInRegistries.ITEM.containsKey(key))item=BuiltInRegistries.ITEM.get(key);
            icon(i,item,(selected==i?"[ВЫБРАНО] ":"")+"Поле "+(i+1)+" | "
                +(f.maxX()-f.minX()+1)+"x"+(f.maxZ()-f.minZ()+1)
                +" | посев: "+(f.seedId().isBlank()?"НЕ НАЗНАЧЕН":f.seedId())
                +" | "+(f.enabled()?"в работе":"пауза"));
        }
        icon(VIEW,Items.BOOK,"Поля "+list.size()+"/8. Выбери поле в верхнем ряду, затем действие.");
        icon(17,Items.OAK_FENCE,"ЖИВОТНОВОДСТВО: создание и управление загонами для овец и других животных TFC");
        icon(ASSIGN_SEED,Items.WHEAT_SEEDS,"НАЗНАЧИТЬ СЕМЕНА из основной руки на выбранное поле (не расходуются при настройке)");
        icon(NEW,Items.GRASS_BLOCK,"ДОБАВИТЬ ПОЛЕ: затем Shift + ПКМ пустой рукой по двум углам");
        icon(TOGGLE,Items.LEVER,"ВКЛЮЧИТЬ / ОСТАНОВИТЬ посевы на выбранном поле");
        icon(DELETE,Items.BARRIER,"УДАЛИТЬ поле из плана (блоки мира не трогаются)");
        icon(DIAGNOSE,Items.COMPASS,"ПРОВЕРИТЬ климат TFC: температура, влажность, свет, питание почвы");
        icon(23,Items.BEETROOT_SEEDS,"СЛЕДУЮЩАЯ КУЛЬТУРА: выбрать поддерживаемые семена TFC без предмета в руке");
        icon(26,Items.OAK_DOOR,"Закрыть настройки");
        broadcastChanges();
    }
    @Override public boolean stillValid(Player player) {
        if(player!=viewer||!KingdomFarmFieldData.allowed(viewer,farm)
            ||viewer.distanceToSqr(core.getX()+.5,core.getY()+.5,core.getZ()+.5)>64
            ||!viewer.serverLevel().hasChunkAt(core))return false;
        return viewer.serverLevel().getBlockEntity(core) instanceof EnterpriseCoreBlockEntity be
            && be.bound()&&farm.equals(be.enterpriseId())&&be.kind()==EnterpriseKind.FARM;
    }
    private void say(String msg){viewer.sendSystemMessage(Component.literal("[Ферма] "+msg));}
    @Override public void clicked(int slot,int button,ClickType click,Player player) {
        if(player!=viewer||player.level().isClientSide||click!=ClickType.PICKUP
            ||button!=0||slot<0||slot>=27||!stillValid(player))return;
        var data=KingdomFarmFieldData.get(viewer.getServer());
        var field=selected();
        if(slot<8) {
            if(slot<fields().size())selected=slot;
        } else switch(slot) {
            case 17 -> {KingdomRanchPenMenu.open(viewer,farm,core);return;}
            case NEW -> {
                if(KingdomFarmSelection.begin(viewer,farm))viewer.closeContainer();
                else say("Нет права на создание поля.");
                return;
            }
            case ASSIGN_SEED -> {
                if(field==null){say("Сначала добавь и выбери поле.");break;}
                ItemStack held=viewer.getMainHandItem();
                if(held.isEmpty()){say("Держи пакет семян TFC в основной руке до открытия меню.");break;}
                String seed=BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                if(data.setSeed(viewer,farm,field.id(),seed))
                    say("Назначен посев "+seed+". Семена для работы NPC берутся из сырьевого склада.");
                else say("Поддерживаются обычные одноблочные культуры TFC. Рис, длинные культуры и перец требуют отдельного адаптера.");
            }
            case 23 -> {
                if(field==null){say("Сначала добавь поле.");break;}
                var choices=BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString)
                    .filter(KingdomFarmFieldData::validSeed).sorted().toList();
                if(choices.isEmpty()){say("Нет поддерживаемых семян TFC.");break;}
                int next=(choices.indexOf(field.seedId())+1)%choices.size();
                if(data.setSeed(viewer,farm,field.id(),choices.get(next)))
                    say("Поле "+(selected+1)+": "+choices.get(next)
                        +". Для посадки фермеру понадобятся реальные семена на складе.");
            }
            case TOGGLE -> {
                if(field!=null)data.toggle(viewer,farm,field.id());
            }
            case DELETE -> {
                if(field!=null){data.remove(viewer,farm,field.id());say("Поле исключено из производственного плана.");}
            }
            case DIAGNOSE -> {
                if(field==null || field.seedId().isBlank()){say("Сначала назначь семена.");break;}
                var crop=ResourceLocation.tryParse(KingdomFarmFieldData.cropId(field.seedId()));
                if(crop==null||!BuiltInRegistries.BLOCK.containsKey(crop)){say("Культура отсутствует в текущем TFC.");break;}
                var pos=field.centerCrop();
                if(!viewer.serverLevel().hasChunkAt(pos)){say("Чанк поля не загружен.");break;}
                var plant=BuiltInRegistries.BLOCK.get(crop).defaultBlockState();
                if(!plant.canSurvive(viewer.serverLevel(),pos)){
                    say("В центре поля отсутствует подходящая пашня TFC. Нужна обработанная почва.");break;
                }
                var reading=KingdomFarmClimate.read(viewer.serverLevel(),pos,plant);
                say(reading.explanation());
            }
            case 26 -> {viewer.closeContainer();return;}
            default -> {}
        }
        if(viewer.containerMenu==this)redraw();
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) {return ItemStack.EMPTY;}
}
