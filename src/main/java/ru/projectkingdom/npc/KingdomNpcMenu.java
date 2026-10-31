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
    private enum Page { DIALOGUE, MANAGEMENT, PROFESSIONS }
    private final SimpleContainer display;
    private final int npcId;
    private final ServerPlayer viewer;
    private Page page = Page.DIALOGUE;

    public KingdomNpcMenu(int containerId, Inventory inventory, ServerPlayer viewer, int npcId) {
        this(containerId, inventory, viewer, npcId, false);
    }

    public KingdomNpcMenu(int containerId, Inventory inventory, ServerPlayer viewer, int npcId, boolean management) {
        this(containerId, inventory, viewer, npcId, new SimpleContainer(27),
                management ? Page.MANAGEMENT : Page.DIALOGUE);
    }

    private KingdomNpcMenu(int containerId, Inventory inventory, ServerPlayer viewer, int npcId,
                           SimpleContainer contents, Page initialPage) {
        super(MenuType.GENERIC_9x3, containerId, inventory, contents, 3);
        this.display = contents;
        this.npcId = npcId;
        this.viewer = viewer;
        this.page = initialPage;
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
        if(page==Page.DIALOGUE)redrawDialogue(w);
        else if(page==Page.PROFESSIONS)redrawProfessions(w);
        else redrawManagement(w);
        broadcastChanges();
    }

    private void redrawDialogue(KingdomNpcData.Worker w) {
        NpcSocialMemory memory=w.social(viewer.getUUID());
        icon(4, Items.PLAYER_HEAD, w.name + " | " + profession(w.job) + " | " + gender(w.gender));
        icon(0, Items.SUNFLOWER, "Поприветствовать");
        icon(1, Items.NAME_TAG, "Спросить: кто ты?");
        icon(2, Items.BREAD, "Спросить о нуждах");
        icon(3, Items.IRON_PICKAXE, "Спросить о работе");
        icon(5, Items.WRITABLE_BOOK, "Спросить о задании");
        icon(6, Items.EMERALD, "Поговорить о торговле");
        icon(7, Items.HEART_OF_THE_SEA, "Отношения: " + memory.label()
                + " | репутация " + memory.reputation());
        icon(8, Items.PAPER, "Можно написать рядом в чат: «"+w.name+", как дела?»");
        icon(18, Items.BREAD, "Предложить помощь: передать 1 настоящий хлеб");
        icon(22, Items.COMMAND_BLOCK, "Управление NPC: договор, работа, склады и приказы");
        icon(26, Items.BARRIER, "Закончить разговор");
    }

    private void redrawManagement(KingdomNpcData.Worker w) {
        boolean owned = controls(w);
        icon(4, Items.PLAYER_HEAD, "NPC №" + w.id + " | " + w.name);
        icon(0, Items.RED_BED, "НАЗНАЧИТЬ ДОМ: здесь, где стоишь");
        icon(1, Items.CRAFTING_TABLE, "ВЫБРАТЬ ПРОФЕССИЮ: один клик по нужной работе");
        icon(2, Items.OAK_DOOR, "Назад к разговору");
        icon(3, Items.LIME_DYE, "НАСТРОИТЬ ПРОФЕССИЮ И ЗАПУСТИТЬ: автоматически выбрать своё предприятие, два склада и рабочий станок");
        icon(6, Items.HOPPER, "РУЧНАЯ НАСТРОЙКА: указать склад сырья (обычно находится автоматически)");
        icon(7, Items.CHEST, "РУЧНАЯ НАСТРОЙКА: указать склад продукции (обычно находится автоматически)");
        icon(8, Items.MINECART, "ПЕРЕВОЗИТЬ ПРЕДМЕТЫ МЕЖДУ СКЛАДАМИ");
        icon(5, Items.ANVIL, "РУЧНАЯ НАСТРОЙКА: выбрать станок (только если автоматический поиск не подошёл)");
        var workshop=KingdomNpcWorkshopData.get(viewer.getServer());
        var plan=workshop.plan(w.entityId);
        boolean tfcQuern = viewer.level().dimension().location().toString().equals(w.dimension)
            && KingdomTfcQuernWork.isQuern(viewer.serverLevel(), w);
        boolean tfcGrill = viewer.level().dimension().location().toString().equals(w.dimension)
            && KingdomTfcGrillWork.isGrill(viewer.serverLevel(), w);
        boolean tfcPot = viewer.level().dimension().location().toString().equals(w.dimension)
            && KingdomTfcPotWork.isPot(viewer.serverLevel(),w);
        boolean tfcLoom = viewer.level().dimension().location().toString().equals(w.dimension)
            && KingdomTfcLoomWork.isLoom(viewer.serverLevel(),w);
        boolean tfcTannery = viewer.level().dimension().location().toString().equals(w.dimension)
            && KingdomTfcTanneryWork.isBarrel(viewer.serverLevel(),w);
        icon(9, tfcTannery ? Items.BARREL : tfcLoom ? Items.LOOM : tfcQuern ? Items.STONE : tfcGrill ? Items.CAMPFIRE :
            tfcPot ? Items.CAULDRON : Items.CRAFTING_TABLE, tfcTannery
            ? "TFC КОЖЕВЕННАЯ БОЧКА: известковая вода, промывка и таннин, настоящие выдержки по времени. Заполни жидкостью заранее"
            : tfcLoom
            ? "TFC ТКАЦКИЙ СТАНОК: настоящие нити и шерсть со склада, пошаговое ткачество TFC, готовая ткань на складе"
            : tfcQuern
            ? "TFC ЖЕРНОВ: реальный помол по рецептам TFC с настоящим ручным камнем"
            : tfcGrill ? "TFC ГРИЛЬ: жарка еды по настоящим рецептам TFC с расходом топлива"
            : tfcPot ? "TFC КОТЁЛ: обычная варка, супы и варенье. В сырьевом складе нужны еда, топливо, вода в настоящей ёмкости и миски или банки"
            : plan==null
                ? "РЕЦЕПТ (ЛКМ): выложи ингредиенты в первых 9 ячейках сырьевого склада"
                : "РЕЦЕПТ "+plan.output()+" | ЛКМ: записать заново | ПКМ: цена "+plan.salePrice()+" мед./шт.");
        icon(10, Items.BOOK, "Профессия: " + profession(w.job));
        icon(11, Items.CLOCK, "Возраст: " + w.ageYears + " лет | Пол: " + gender(w.gender)
            + (viewer.hasPermissions(2) ? " | ЛКМ: изменить пол" : ""));
        icon(12, Items.BREAD, "Сытость: " + w.hunger + "/100 | "
            + KingdomNpcNutrition.summary(w));
        long wagesOwed=KingdomEnterprisePayrollData.get(viewer.getServer()).totalDue(viewer.getServer(),w.entityId);
        long purse=w.wagePurse.stream().mapToLong(stack->KingdomCurrency.value(stack)*stack.getCount()).sum();
        icon(13, KingdomCurrency.COPPER.get(), "Зарплата: " + w.dailyWage + " мед./сутки | долг: " + wagesOwed + " | у NPC: " + purse);
        icon(14, Items.PAPER, "Приказ: " + order(w.order));
        icon(15, Items.CHEST, "План: " + NpcAutonomyPlanner.description(
            safeIntent(w.autonomousIntent)) + (plan==null?"":" | ПКМ: остатки "+plan.low()+"–"+plan.high()));
        icon(17, Items.BARREL, "Добыто: " + w.fieldHarvested + " | Сумка: " + w.fieldInventory.stream().mapToInt(ItemStack::getCount).sum() + " | Доставлено: " + w.deliveredItems + " | Испечено: " + w.bakedBread + " | Груз: " + (w.cargo.isEmpty() ? "пусто" : w.cargo.getHoverName().getString()+" x"+w.cargo.getCount()));
        icon(16, Items.GOLD_INGOT, "Старый фонд зарплат: " + KingdomNpcData.get(viewer.getServer()).balance(viewer.getUUID())
            + " (возврат /knpc refund_legacy <сумма> у банковской стойки)");
        if (w.employer == null && w.ageYears >= 18) icon(19, Items.LIME_DYE, "НАНЯТЬ И НАСТРОИТЬ ПРОФЕССИЮ: один клик");
        if (owned && w.employer != null) icon(19, Items.RED_DYE, "УВОЛИТЬ жителя");
        if (owned) {
            icon(20, Items.LEAD, "СЛЕДОВАТЬ за мной");
            icon(21, Items.BARRIER, "СТОЯТЬ на месте");
            icon(22, Items.IRON_PICKAXE, "РАБОТАТЬ: автоматически настроить и начать; при ошибке покажет причину");
            icon(23, Items.OAK_DOOR, "ВЕРНУТЬСЯ ДОМОЙ");
            icon(24, Items.SUNFLOWER, "СВОБОДНАЯ ЖИЗНЬ");
            icon(25, Items.GOLD_NUGGET, "+1 к дневной зарплате");
        }
        icon(18, Items.BREAD, "ЛКМ: дать свежую еду TFC из руки | ПКМ работодателя: "
            + (w.rationsEnabled?"отключить":"включить")+" питание со склада");
        icon(26, KingdomCurrency.COPPER.get(), "ВНЕСТИ 1 медную из кошелька на зарплатный счёт");
    }

    /** All 26 trades on one page; the last slot returns to management. */
    private void redrawProfessions(KingdomNpcData.Worker w) {
        var trades=KingdomProfessionRegistry.all();
        for(int i=0;i<trades.size() && i<26;i++){
            var job=trades.get(i);
            boolean real=KingdomNpcDuty.hasPhysicalExecutor(job.id());
            Item item=switch(KingdomNpcDuty.kind(job.id())){
                case FIELD -> Items.IRON_AXE;
                case WORKSHOP -> Items.CRAFTING_TABLE;
                case CONSTRUCTION -> Items.BRICKS;
                case DELIVERY -> Items.MINECART;
                case SERVICE -> Items.EMERALD;
                default -> Items.PAPER;
            };
            icon(i,item,(w.job.equals(job.id())?"[ВЫБРАНА] ":"")
                +job.title()+(real?" | физическая работа":" | производство пока не подключено"));
        }
        icon(26,Items.OAK_DOOR,"НАЗАД к управлению NPC");
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
                || (button != 0 && !(button==1 && (slotId==9||slotId==15||slotId==18)))) return;
        KingdomNpcData db = KingdomNpcData.get(viewer.getServer());
        KingdomNpcData.Worker w = worker();
        if (!nearby(w)) { viewer.closeContainer(); return; }
        if(page==Page.PROFESSIONS){
            if(slotId==26){page=Page.MANAGEMENT;}
            else if(slotId<KingdomProfessionRegistry.all().size()){
                if(w.employer!=null&&!controls(w)){
                    info("Менять профессию нанятого жителя может только работодатель.");
                }else if(w.ageYears<18){
                    info("Профессию можно назначить взрослому NPC.");
                }else{
                    String selected=KingdomProfessionRegistry.all().get(slotId).id();
                    w.job=selected;
                    db.setDirty();
                    page=Page.MANAGEMENT;
                    if(w.employer!=null && controls(w)
                        &&KingdomNpcDuty.hasPhysicalExecutor(selected))
                        info(KingdomNpcQuickSetup.configure(viewer,w).message());
                    else if(!KingdomNpcDuty.hasPhysicalExecutor(selected))
                        info("Профессия выбрана, но её физическая работа ещё не подключена.");
                    else info("Профессия выбрана: "+profession(selected)
                        +". Нажми «Нанять и настроить профессию».");
                }
            }
            if(viewer.containerMenu==this)redraw();
            return;
        }
        if(page==Page.DIALOGUE){
            switch(slotId){
                case 0 -> NpcDialogueService.respond(viewer,w,"Привет");
                case 1 -> NpcDialogueService.respond(viewer,w,"Кто ты?");
                case 2 -> NpcDialogueService.respond(viewer,w,"Как дела?");
                case 3 -> NpcDialogueService.respond(viewer,w,"Как работа?");
                case 5 -> NpcDialogueService.respond(viewer,w,"Чем тебе помочь?");
                case 6 -> NpcDialogueService.respond(viewer,w,"Покажи товары");
                case 7 -> NpcDialogueService.respond(viewer,w,"Какие у нас отношения?");
                case 18 -> NpcDialogueService.respond(viewer,w,"Вот хлеб");
                case 22 -> page=Page.MANAGEMENT;
                case 26 -> {viewer.closeContainer();return;}
                default -> { }
            }
            if(viewer.containerMenu==this)redraw();
            return;
        }
        boolean owner = controls(w);
        boolean changed = false;
        switch (slotId) {
            case 11 -> {
                if (!viewer.hasPermissions(2)) {
                    info("Изменение пола доступно только администратору.");
                    break;
                }
                w.gender = NpcIdentity.nextGender(w.gender);
                info("Пол NPC №" + w.id + ": " + gender(w.gender) + ". Данные сохранены.");
                changed = true;
                db.setDirty();
                KingdomFemaleOutfitSync.notifyChanged(viewer.getServer(), w);
            }
            case 2 -> page=Page.DIALOGUE;
            case 9 -> {
                if(!owner){info("Нужны права работодателя.");break;}
                if("smith".equals(w.job)){
                    info("Кузнец TFC: сначала зарегистрируй кузницу, поставь настоящий горн TFC "
                         +"и наковальню внутри, затем настрой профессию. Молот нельзя требовать в сундуке.");
                    break;
                }
                if(viewer.level().dimension().location().toString().equals(w.dimension)
                    &&KingdomTfcTanneryWork.isBarrel(viewer.serverLevel(),w)){
                    info("Кожевник TFC: установи настоящую бочку TFC на своей земле, заранее налей нужную жидкость (известковая вода / чистая вода / таннин), назначь сырьевой и готовый склады и договор на кожевенную мастерскую. NPC запечатает бочку, дождётся реального рецепта и заберёт предмет. Очистку размоченных шкур ножом пока выполняй вручную.");
                    break;
                }
                if(viewer.level().dimension().location().toString().equals(w.dimension)
                        && KingdomTfcLoomWork.isLoom(viewer.serverLevel(),w)){
                    info("Ткач TFC: поставь настоящий ткацкий станок на территории ткацкой мастерской, назначь его рабочим блоком NPC; нужен сырьевой склад с настоящей пряжей TFC, склад готовой ткани, действующий договор и приказ работать. NPC выполнит шаги штатного рецепта TFC.");
                    break;
                }
                if(viewer.level().dimension().location().toString().equals(w.dimension)
                        && KingdomTfcQuernWork.isQuern(viewer.serverLevel(),w)){
                    info("Жернов TFC работает без ручного рецепта. Нужны ручной камень, сырьевой и готовый склады, приказ работать.");
                    break;
                }
                if(viewer.level().dimension().location().toString().equals(w.dimension)
                        && KingdomTfcGrillWork.isGrill(viewer.serverLevel(),w)){
                    info("Гриль TFC готовит реальные продукты без ручного рецепта. Положи в сырьевой склад мясо или другую еду и топливо; нужны оба склада, приказ работать и действующая пекарня.");
                    break;
                }
                if(viewer.level().dimension().location().toString().equals(w.dimension)
                        && KingdomTfcPotWork.isPot(viewer.serverLevel(),w)){
                    info("Котёл TFC: назначь рабочий блок, сырьевой и готовый склады. Для воды нужен настоящий сосуд с жидкостью, для супа — миски TFC, для варенья — банки с крышками. NPC тратит продукты, топливо и посуду, варит по рецептам TFC и переносит порции в готовый склад.");
                    break;
                }
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
                var result=KingdomNpcQuickSetup.configure(viewer,w);
                info(result.message());
                changed=true;
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
                if(w.employer!=null&&!owner){
                    info("Сменить профессию может работодатель.");break;
                }
                page=Page.PROFESSIONS;
            }
            case 19 -> {
                if (w.employer == null && w.ageYears >= 18) {
                    var result=KingdomNpcQuickSetup.configure(viewer,w);
                    info(result.message());
                    changed=true;
                } else if (owner && w.employer != null) {
                    var jobs=KingdomEnterprisePayrollData.get(viewer.getServer());
                    for(var contract:new java.util.ArrayList<>(jobs.all()))
                        if(contract.worker().equals(w.entityId)&&contract.active())
                            jobs.dismiss(viewer.getServer(),viewer.getUUID(),contract.enterprise(),w.entityId);
                    w.employer = null;
                    w.rationsEnabled=false;
                    w.order = "wander";
                    w.contractPaused = false;
                    w.unpaidDays = 0;
                    info("Трудовой договор расторгнут.");
                    changed = true;
                }
            }
            case 18 -> {
                if(button==1){
                    if(!owner||w.employer==null){
                        info("Питание со склада включает только работодатель.");
                    }else{
                        w.rationsEnabled=!w.rationsEnabled;
                        info(w.rationsEnabled?"Выдача свежего питания из назначенного склада включена."
                            :"Автоматическая выдача питания отключена.");
                        changed=true;
                    }
                    break;
                }
                ItemStack held=viewer.getMainHandItem();
                var meal=KingdomNpcNutrition.inspect(held);
                if(meal!=null){
                    held.shrink(1);
                    viewer.getInventory().setChanged();
                    KingdomNpcNutrition.eat(w,meal);
                    info("Житель съел настоящую еду. "+KingdomNpcNutrition.summary(w));
                    changed=true;
                }else if(!net.minecraftforge.fml.ModList.get().isLoaded("tfc")
                        && consume(Items.BREAD)){
                    w.hunger=NpcLife.feed(w.hunger,1);changed=true;
                    info("Житель накормлен хлебом.");
                }else info("Держи в основной руке свежую съедобную еду TFC.");
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
                if(next.equals("work")){
                    var result=KingdomNpcQuickSetup.configure(viewer,w);
                    info(result.message());
                    changed=true;
                    break;
                }
                if (next.equals("home") && !w.hasHome) {
                    info("Дом ещё не назначен (для администратора: /knpc sethome " + npcId + ").");break;
                }
                w.order = next;
                info("Новый приказ: " + order(next));
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
