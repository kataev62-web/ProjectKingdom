package ru.projectkingdom.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-authoritative UI backed by the vanilla three-row chest menu. No client-side menu registration required. */
public final class KingdomNpcMenu extends ChestMenu {
    private final SimpleContainer display;
    private final int npcId;
    private final ServerPlayer viewer;

    public KingdomNpcMenu(int containerId, Inventory inventory, ServerPlayer viewer, int npcId) {
        this(containerId, inventory, viewer, npcId, new SimpleContainer(27));
    }

    private KingdomNpcMenu(int containerId, Inventory inventory, ServerPlayer viewer, int npcId, SimpleContainer contents) {
        super(MenuType.GENERIC_9x3, containerId, inventory, contents, 3);
        this.display = contents;
        this.npcId = npcId;
        this.viewer = viewer;
        redraw();
    }

    private static String profession(String job) {
        return KingdomProfessionRegistry.title(job);
    }

    private static String order(String command) {
        return switch (command) {
            case "follow" -> "Следовать";
            case "stay" -> "Стоять";
            case "work" -> "Работать";
            case "home" -> "Домой";
            case "haul" -> "Перевозить грузы";
            case "bake" -> "Старая пекарня отключена";
            default -> "Свободно гулять";
        };
    }

    private void icon(int slot, Item item, String title) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(title));
        display.setItem(slot, stack);
    }

    private KingdomNpcData.Worker worker() {
        return KingdomNpcData.get(viewer.getServer()).workers.get(npcId);
    }

    public boolean isForNpc(int id) { return id == npcId; }

    private boolean nearby(KingdomNpcData.Worker w) {
        if (w == null || !viewer.level().dimension().location().toString().equals(w.dimension)) return false;
        if (!(viewer.level() instanceof ServerLevel level)) return false;
        var entity = level.getEntity(w.entityId);
        return entity != null && entity.isAlive() && viewer.distanceToSqr(entity) <= 64;
    }

    private boolean controls(KingdomNpcData.Worker w) {
        return viewer.hasPermissions(2) || viewer.getUUID().equals(w.employer);
    }

    private void info(String message) {
        viewer.sendSystemMessage(Component.literal("[Королевство] " + message));
    }

    private void redraw() {
        KingdomNpcData.Worker w = worker();
        display.clearContent();
        if (w == null) return;
        boolean owned = controls(w);
        icon(4, Items.PLAYER_HEAD, "NPC №" + w.id + " | " + w.name);
        icon(0, Items.RED_BED, "НАЗНАЧИТЬ ДОМ: здесь, где стоишь");
        icon(1, Items.CRAFTING_TABLE, "СМЕНИТЬ ПРОФЕССИЮ");
        icon(2, Items.CLOCK, "Режим общения: NPC стоит на месте");
        icon(3, Items.GRASS_BLOCK, "ЗАКРЕПИТЬ ТЕКУЩИЙ УЧАСТОК (чанк 16×16)");
        icon(6, Items.HOPPER, "СКЛАД СЫРЬЯ (участок должен принадлежать работодателю)");
        icon(7, Items.CHEST, "СКЛАД ГОТОВОЙ ПРОДУКЦИИ (участок работодателя)");
        icon(8, Items.MINECART, "ПЕРЕВОЗИТЬ ПРЕДМЕТЫ МЕЖДУ СКЛАДАМИ");
        icon(5, Items.ANVIL, "НАЗНАЧИТЬ РАБОЧУЮ СТАНЦИЮ (ПКМ по блоку)");
        var workshop=KingdomNpcWorkshopData.get(viewer.getServer());
        var plan=workshop.plan(w.entityId);
        icon(9, Items.CRAFTING_TABLE, plan==null
            ? "РЕЦЕПТ (ЛКМ): выложи ингредиенты в первых 9 ячейках сырьевого склада"
            : "РЕЦЕПТ "+plan.output()+" | ЛКМ: записать заново | ПКМ: цена "+plan.salePrice()+" мед./шт.");
        icon(10, Items.BOOK, "Профессия: " + profession(w.job));
        icon(11, Items.CLOCK, "Возраст: " + w.ageYears + " лет | Пол: " + gender(w.gender));
        icon(12, Items.BREAD, "Сытость: " + w.hunger + "/100");
        long wagesOwed=KingdomEnterprisePayrollData.get(viewer.getServer()).totalDue(viewer.getServer(),w.entityId);
        long purse=w.wagePurse.stream().mapToLong(stack->KingdomCurrency.value(stack)*stack.getCount()).sum();
        icon(13, KingdomCurrency.COPPER.get(), "Зарплата: " + w.dailyWage + " мед./сутки | долг: " + wagesOwed + " | у NPC: " + purse);
        icon(14, Items.PAPER, "Приказ: " + order(w.order));
        icon(15, Items.CHEST, "План: " + NpcAutonomyPlanner.description(
            safeIntent(w.autonomousIntent)) + (plan==null?"":" | ПКМ: остатки "+plan.low()+"–"+plan.high()));
        icon(17, Items.BARREL, "Добыто: " + w.fieldHarvested + " | Сумка: " + w.fieldInventory.stream().mapToInt(ItemStack::getCount).sum() + " | Доставлено: " + w.deliveredItems + " | Испечено: " + w.bakedBread + " | Груз: " + (w.cargo.isEmpty() ? "пусто" : w.cargo.getHoverName().getString()+" x"+w.cargo.getCount()));
        icon(16, Items.GOLD_INGOT, "Старый фонд зарплат: " + KingdomNpcData.get(viewer.getServer()).balance(viewer.getUUID())
            + " (возврат /knpc refund_legacy <сумма> у банковской стойки)");
        if (w.employer == null && w.ageYears >= 18) icon(19, Items.LIME_DYE, "НАНЯТЬ жителя");
        if (owned && w.employer != null) icon(19, Items.RED_DYE, "УВОЛИТЬ жителя");
        if (owned) {
            icon(20, Items.LEAD, "СЛЕДОВАТЬ за мной");
            icon(21, Items.BARRIER, "СТОЯТЬ на месте");
            icon(22, Items.IRON_PICKAXE, "РАБОТАТЬ: реальные культуры, древесина, руда");
            icon(23, Items.OAK_DOOR, "ВЕРНУТЬСЯ ДОМОЙ");
            icon(24, Items.SUNFLOWER, "СВОБОДНАЯ ЖИЗНЬ");
            icon(25, Items.GOLD_NUGGET, "+1 к дневной зарплате");
        }
        icon(18, Items.BREAD, "ПОКОРМИТЬ: потратить 1 хлеб");
        icon(26, KingdomCurrency.COPPER.get(), "ВНЕСТИ 1 медную из кошелька на зарплатный счёт");
        broadcastChanges();
    }

    private static NpcAutonomyPlanner.Intention safeIntent(String value) {
        try {return NpcAutonomyPlanner.Intention.valueOf(value);}
        catch(IllegalArgumentException e) {return NpcAutonomyPlanner.Intention.IDLE;}
    }
    private static String gender(String gender) {
        return switch (gender) {
            case "male" -> "мужской";
            case "female" -> "женский";
            default -> "не указан";
        };
    }

    private boolean consume(Item item) {
        if (viewer.getInventory().countItem(item) < 1) return false;
        for (int i = 0; i < viewer.getInventory().getContainerSize(); i++) {
            ItemStack stack = viewer.getInventory().getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                viewer.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // Never let players take the icons; all mutations are validated server-side.
        if (viewer.level().isClientSide || player != viewer) return;
        if (slotId < 0 || slotId >= 27 || type != ClickType.PICKUP
                || (button != 0 && !(button==1 && (slotId==9||slotId==15)))) return;
        KingdomNpcData db = KingdomNpcData.get(viewer.getServer());
        KingdomNpcData.Worker w = worker();
        if (!nearby(w)) { viewer.closeContainer(); return; }
        boolean owner = controls(w);
        boolean changed = false;
        switch (slotId) {
            case 9 -> {
                if(!owner){info("Нужны права работодателя.");break;}
                if(button==1){
                    var registry=KingdomNpcWorkshopData.get(viewer.getServer());
                    var plan=registry.plan(w.entityId);
                    if(plan!=null && viewer.getUUID().equals(w.employer)){
                        registry.rotatePrice(w.entityId);
                        var current=registry.plan(w.entityId);
                        KingdomEnterpriseData.get(viewer.getServer()).publishWorkshop(
                            viewer.getServer(),w.entityId,current.enterprise(),current.salePrice());
                        info("Цена продажи: "+current.salePrice()+" мед./шт.");
                    }else info("Сначала запиши рецепт.");
                }else info(KingdomNpcWorkshop.configure(viewer,w));
            }
            case 15 -> {
                if(button==1 && owner && viewer.getUUID().equals(w.employer)){
                    var registry=KingdomNpcWorkshopData.get(viewer.getServer());
                    if(registry.rotateStock(w.entityId)){
                        var plan=registry.plan(w.entityId);
                        info("Новые пороги запаса: "+plan.low()+"–"+plan.high());
                    }else info("Сначала запиши рецепт в слот 9.");
                }
            }
            case 5 -> {
                if (!owner || w.employer == null) {info("Назначить станцию может только работодатель.");break;}
                KingdomNpcMod.beginBindRole(viewer,npcId,"workstation");
                viewer.closeContainer();return;
            }
            case 3 -> {
                var land=KingdomTerritories.get(viewer.getServer());
                if(land.claim(viewer.serverLevel(),viewer.blockPosition(),viewer.getUUID())) {
                    info("Участок закреплён за тобой. Его теперь можно использовать для складов и станций.");
                } else info("У этого участка уже есть другой владелец.");
            }
            case 6, 7 -> {
                if (!owner || w.employer == null) {info("Сначала найми жителя.");break;}
                KingdomNpcMod.beginBind(viewer, npcId,slotId==6);
                viewer.closeContainer();
                return;
            }
            case 8 -> {
                if (!owner || w.employer == null) {info("Сначала найми жителя.");break;}
                if (!w.hasSource || !w.hasDestination) {info("Сначала назначь склад сырья и склад продукции на своей территории.");break;}
                if (w.contractPaused) {info("Трудовой договор приостановлен — пополни счёт.");break;}
                w.order="haul";info("Начата настоящая перевозка предметов. Отслеживай прогресс в меню.");changed=true;
            }
            case 0 -> {
                if (!owner) { info("Назначить дом может работодатель."); break; }
                var pos = viewer.blockPosition();
                w.homeX = pos.getX(); w.homeY = pos.getY(); w.homeZ = pos.getZ();
                w.hasHome = true;
                info("Дом назначен: " + pos.toShortString());
                changed = true;
            }
            case 1 -> {
                if (!owner) { info("Сменить профессию может работодатель."); break; }
                w.job = KingdomProfessionRegistry.next(w.job);
                info("Новая профессия: " + profession(w.job));
                changed = true;
            }
            case 19 -> {
                if (w.employer == null && w.ageYears >= 18) {
                    if(KingdomEnterpriseData.get(viewer.getServer()).owned(viewer.getUUID())==null){
                        info("Сначала зарегистрируй предприятие: зарплата NPC выплачивается из его кассы.");break;
                    }
                    var firm=KingdomEnterpriseData.get(viewer.getServer()).owned(viewer.getUUID());
                    if(firm==null || !KingdomEnterprisePayrollData.get(viewer.getServer())
                            .hire(viewer.getServer(),viewer.getUUID(),firm.id(),w.entityId,w.dailyWage)){
                        info("Не получилось оформить договор с предприятием.");break;
                    }
                    w.employer = viewer.getUUID();
                    w.workplaceId=firm.id().toString();
                    w.lastPaidDay = viewer.getServer().overworld().getDayTime() / 24000;
                    w.unpaidDays = 0;
                    w.contractPaused = false;
                    info("Житель нанят на предприятие. Зарплата начисляется как долг и выплачивается у кассы.");
                    changed = true;
                } else if (owner && w.employer != null) {
                    var jobs=KingdomEnterprisePayrollData.get(viewer.getServer());
                    for(var contract:new java.util.ArrayList<>(jobs.all()))
                        if(contract.worker().equals(w.entityId)&&contract.active())
                            jobs.dismiss(viewer.getServer(),viewer.getUUID(),contract.enterprise(),w.entityId);
                    w.employer = null;
                    w.order = "wander";
                    w.contractPaused = false;
                    w.unpaidDays = 0;
                    info("Трудовой договор расторгнут.");
                    changed = true;
                }
            }
            case 18 -> {
                if (consume(Items.BREAD)) {
                    w.hunger = NpcLife.feed(w.hunger, 1);
                    info("Житель накормлен.");
                    changed = true;
                } else info("Для кормления нужен хлеб в инвентаре.");
            }
            case 26 -> {
                if (db.balance(viewer.getUUID())<Long.MAX_VALUE && KingdomCurrency.debit(viewer,1)) {
                    db.deposit(viewer.getUUID(),1);
                    viewer.getInventory().setChanged();
                    info("В резерв зарплат внесена 1 медная монета из кошелька.");
                } else info("В кошельке недостаточно медных монет.");
            }
            case 20,21,22,23,24 -> {
                if (!owner) {info("Сначала найми жителя.");break;}
                String next = switch (slotId) {
                    case 20 -> "follow";
                    case 21 -> "stay";
                    case 22 -> "work";
                    case 23 -> "home";
                    default -> "wander";
                };
                if (next.equals("work") && (w.employer == null || w.contractPaused)) {
                    info("Для работы нужен действующий трудовой договор и оплаченная зарплата.");break;
                }
                if (next.equals("home") && !w.hasHome) {
                    info("Дом ещё не назначен (для администратора: /knpc sethome " + npcId + ").");break;
                }
                w.order = next;
                info("Новый приказ: " + order(next) + (next.equals("work") ? ". Для производства открой кнопку рецепта и назначь реальные склады." : ""));
                changed = true;
            }
            case 25 -> {
                if (owner && w.dailyWage < 64) {
                    w.dailyWage++;
                    info("Зарплата: " + w.dailyWage + " мед./день");
                    changed = true;
                }
            }
            default -> { }
        }
        if (changed) db.setDirty();
        redraw();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player) {return player == viewer && nearby(worker());}
}
