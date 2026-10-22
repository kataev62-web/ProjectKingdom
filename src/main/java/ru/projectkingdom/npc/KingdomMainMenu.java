package ru.projectkingdom.npc;

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

/** Menu-driven management; all changes are verified on the server. */
public final class KingdomMainMenu extends ChestMenu {
    private enum Page { HOME, BUILD, PROJECTS, ARMY, TERRITORY, PEOPLE, CIVICS, WALLET, PAYMENTS, PAYROLL, ENTERPRISE, BANK, SITE, WAREHOUSE, ORDERS }
    private final SimpleContainer content;
    private final ServerPlayer viewer;
    private final Page page;
    private final java.util.UUID siteId;
    private String marketItem="minecraft:charcoal";
    private int marketCount=20;
    private int marketQuote=0;
    // Server-bound slot identities: never let live market ordering change the clicked offer.
    private final java.util.Map<Integer,java.util.UUID> tradeSlots=new java.util.HashMap<>();
    private KingdomMainMenu(int id, Inventory inventory, ServerPlayer player, Page page, SimpleContainer content, java.util.UUID siteId) {
        super(MenuType.GENERIC_9x3, id, inventory, content, 3);
        this.content=content; this.viewer=player; this.page=page; this.siteId=siteId; draw();
    }
    private static KingdomMainMenu make(int id, Inventory inventory, ServerPlayer player, Page page,java.util.UUID siteId) {
        return new KingdomMainMenu(id, inventory, player, page, new SimpleContainer(27),siteId);
    }
    public static void open(ServerPlayer player) { show(player,Page.HOME); }
    public static void openWallet(ServerPlayer player) { show(player,Page.WALLET); }
    public static void openBank(ServerPlayer player) { show(player,Page.BANK); }
    public static void openWarehouse(ServerPlayer player) { show(player,Page.WAREHOUSE); }
    public static void openOrders(ServerPlayer player) { show(player,Page.ORDERS); }
    public static void openArchitect(ServerPlayer player) { show(player,Page.BUILD); }
    public static void openSite(ServerPlayer player,java.util.UUID id) { show(player,Page.SITE,id); }
    private static void show(ServerPlayer player,Page page) {show(player,page,null);}
    private static void show(ServerPlayer player,Page page,java.util.UUID siteId) {
        player.openMenu(new SimpleMenuProvider((id,inv,p)->make(id,inv,player,page,siteId),
            Component.literal("Project Kingdom / "+switch(page){
                case HOME -> "Управление"; case BUILD -> "Архитектор";
                case PROJECTS -> "Проекты"; case ARMY -> "Армия";
                case TERRITORY -> "Территории"; case PEOPLE -> "Жители"; case CIVICS -> "Города и нации"; case WALLET -> "Кошелёк"; case PAYMENTS -> "Платежи"; case PAYROLL -> "Зарплаты"; case ENTERPRISE -> "Предприятие"; case BANK -> "Банк"; case SITE -> "Строительная площадка"; case WAREHOUSE -> "Заказ доставки"; case ORDERS -> "Мои доставки";
            })));
    }
    private void icon(int slot, Item item, String name) {
        ItemStack stack=new ItemStack(item);stack.setHoverName(Component.literal(name));content.setItem(slot,stack);
    }
    private void draw() {
        content.clearContent();
        tradeSlots.clear();
        switch(page) {
            case HOME -> {
                icon(10,Items.BRICKS,"Строительство и архитектор");
                icon(11,Items.PAPER,"Мои проекты");
                icon(12,Items.IRON_SWORD,"Армия и приказы");
                icon(13,Items.GRASS_BLOCK,"Территории");
                icon(14,Items.VILLAGER_SPAWN_EGG,"Жители и профессии");
                icon(15,Items.EMERALD,"Города, нации и участки");
                icon(16,Items.CHEST,"Склады: назначаются через меню NPC");
                icon(22,KingdomCurrency.WALLET.get(),"Монеты и кошелёк");
                icon(23,Items.EMERALD,"Моё предприятие и касса");
            }
            case WALLET -> {
                var wallet=KingdomCurrency.wallet(viewer);
                icon(10,KingdomCurrency.WALLET.get(),wallet.isEmpty()?"Денежный мешок отсутствует":"Во всех мешках: "+KingdomCurrency.balance(viewer)+" медных");
                icon(11,Items.HOPPER,"Собрать все монеты из инвентаря в кошелёк");
                icon(13,KingdomCurrency.COPPER.get(),"Получить 1 медную монету");
                icon(14,KingdomCurrency.SILVER.get(),"Получить 1 серебряную (100 медных)");
                icon(15,KingdomCurrency.GOLD.get(),"Получить 1 золотую (10000 медных)");
                icon(16,Items.CHEST,"Перевести 100 медных из кошелька в казну города");
                icon(19,Items.PLAYER_HEAD,"Переводы игрокам поблизости");
                icon(20,Items.GOLD_INGOT,"Получить 100 медных из казны (только мэр)");
                icon(21,Items.PAPER,"Оплатить налоговый долг (до 100 медных); долг: "+KingdomTaxData.get(viewer.getServer()).owed(viewer.getServer(),viewer.getUUID()));
                icon(22,Items.WRITABLE_BOOK,"Зарплаты работников города");
            }
            case WAREHOUSE -> {
                icon(10,Items.CHARCOAL,"Древесный уголь");
                icon(11,Items.COAL,"Каменный уголь");
                icon(12,Items.IRON_INGOT,"Железо");
                icon(13,Items.OAK_LOG,"Брёвна");
                icon(14,Items.WHEAT,"Пшеница");
                icon(15,Items.PAPER,"Количество: "+marketCount+" (нажми для изменения)");
                var market=KingdomWarehouseMarket.get(viewer.getServer());
                var quotes=market.search(viewer.serverLevel(),marketItem,marketCount);
                for(int i=0;i<Math.min(4,quotes.size());i++){
                    var q=quotes.get(i);
                    icon(18+i,i==marketQuote?Items.LIME_DYE:Items.GRAY_DYE,
                        "#"+i+" | "+q.totalCopper()+" мед. | запас "+q.available()+" | "+q.stall().pos().toShortString());
                }
                var chest=market.deliveryChest(viewer.serverLevel(),viewer.blockPosition(),viewer.getUUID());
                icon(22,Items.EMERALD,"Оплатить заказ + доставку (10 мед.)");
                icon(23,Items.CHEST,chest==null?"Нет сундука приёмки рядом со стойкой":"Сундук приёмки: "+chest.toShortString());
                icon(24,Items.PAPER,"Выбрано предложение #"+marketQuote+". Деньги списываются из кошелька.");
                icon(26,Items.WRITABLE_BOOK,"Мои заказы: статусы и ход доставки");
            }
            case ORDERS -> {
                int n=0;
                for(var order:KingdomSupplyOrders.get(viewer.getServer()).all()) {
                    if(!order.buyer.equals(viewer.getUUID()) || n>=18)continue;
                    icon(n++,order.state==KingdomSupplyOrders.State.DELIVERED?Items.LIME_DYE:Items.PAPER,
                        order.itemId+" x"+order.count+" | "+order.state
                        +" | доставлено "+order.delivered+"/"+order.count
                        +" | курьер "+(order.courier==null?"ожидается":"назначен")
                        +" | ID "+order.id);
                }
                if(n==0)icon(13,Items.BARRIER,"Пока нет заказов");
                icon(26,Items.CHEST,"Вернуться к оформлению заказа");
            }
            case BANK -> {
                var bank=KingdomBankData.get(viewer.getServer());
                icon(10,Items.GOLD_INGOT,"Счёт хранения: "+bank.balance(viewer.getUUID())+" медных");
                icon(11,Items.HOPPER,"Положить 1 медную из кошелька");
                icon(12,Items.HOPPER,"Положить 100 медных из кошелька");
                icon(13,Items.HOPPER,"Положить 10000 медных из кошелька");
                icon(15,Items.CHEST,"Снять 1 медную в кошелёк");
                icon(16,Items.CHEST,"Снять 100 медных в кошелёк");
                icon(17,Items.CHEST,"Снять 10000 медных в кошелёк");
                icon(22,Items.PAPER,"Банк только хранит монеты. Покупки и зарплаты оплачиваются наличными.");
            }
            case ENTERPRISE -> {
                var data=KingdomEnterpriseData.get(viewer.getServer());var firm=data.owned(viewer.getUUID());
                int count=data.ownedAll(viewer.getUUID()).size();
                icon(10,Items.OAK_SIGN,"Создать предприятие (всего "+count+" / 16)");
                icon(11,Items.PAPER,firm==null?"Нет предприятия":"Активно: "+firm.name()+" (нажми, чтобы переключить)");
                icon(12,Items.HOPPER,"Внести 100 медных в активную кассу");
                icon(13,Items.CHEST,"Снять 100 медных из активной кассы");
                icon(14,Items.BARREL,firm==null?"Сначала открой предприятие":
                    "Привязать сундук/бочку под ногами; касса "+firm.cash()+" мед., доступ "+data.atCounter(viewer,firm.id()));
                icon(15,Items.EMERALD,"Выставить предмет из руки: 10 медных/шт. (из привязанного сундука)");
                icon(16,Items.GOLD_INGOT,"Выставить предмет из руки: 100 медных/шт.");
                icon(17,Items.GOLD_BLOCK,"Выставить предмет из руки: 1000 медных/шт.");
                int n=0;
                for(var offer:data.offers()) {
                    if(n>=4)break;
                    var vendor=data.find(offer.seller());
                    if(firm==null || vendor==null || vendor.id().equals(firm.id())
                        || !data.atCounter(viewer,vendor.id()) || !data.atCounter(viewer,firm.id()))continue;
                    tradeSlots.put(18+n,offer.id());
                    icon(18+n,Items.PAPER,"Купить "+offer.itemId()+" (1 шт. ЛКМ; 16 шт. ПКМ) по "
                        +offer.unitPrice()+" мед./шт. Запас: "+data.stock(viewer.serverLevel(),offer)
                        +" | "+vendor.name());n++;
                }
                icon(22,Items.BOOK,"Товары реально переходят между сундуками двух предприятий; оплата только из кассы.");
                var nearby=nearbyPlayers();
                if(firm!=null && !nearby.isEmpty()) {
                    var target=nearby.get(0);
                    var employment=KingdomEnterprisePayrollData.get(viewer.getServer()).contract(firm.id(),target.getUUID());
                    icon(24,Items.PLAYER_HEAD,"Сотрудник "+target.getScoreboardName()+": "
                        +(employment==null?"не нанят":employment.dailyCopper()+" мед./сутки, долг "+employment.owed())
                        +" | ЛКМ: нанять/повысить, ПКМ: уволить");
                }
                icon(25,Items.GOLD_INGOT,"Получить зарплату предприятия у кассы; долг: "
                    +KingdomEnterprisePayrollData.get(viewer.getServer()).totalDue(viewer.getServer(),viewer.getUUID()));
            }
            case PAYMENTS -> {
                int index=0;
                for(ServerPlayer target:nearbyPlayers()) {
                    if(index>=18)break;
                    icon(index,Items.PLAYER_HEAD,"Перевести 100 медных: "+target.getScoreboardName());index++;
                }
                icon(22,Items.BOOK,"Платёж 100 медных. Оба игрока должны иметь кошельки и находиться рядом");
            }
            case PAYROLL -> {
                var db=KingdomCivicsData.get(viewer.getServer());
                var town=db.townOf(viewer.getUUID());
                icon(10,Items.GOLD_INGOT,"Казна: "+(town==null?0:town.treasury())+" медных");
                icon(11,Items.CLOCK,"Забрать начисленную зарплату из казны (долг: "+KingdomPayrollData.get(viewer.getServer()).accrue(viewer.getServer(),viewer.getUUID())+")");
                int idx=0;
                for(ServerPlayer target:nearbyPlayers()) {
                    if(idx>=9)break;
                    var workerTown=db.townOf(target.getUUID());
                    if(town!=null&&workerTown!=null&&town.id().equals(workerTown.id())) {
                        var contract=KingdomPayrollData.get(viewer.getServer()).contract(target.getUUID());
                        icon(12+idx,Items.PAPER,target.getScoreboardName()+" | ставка "+(contract==null?0:contract.dailyCopper())+" | ЛКМ: назначить/повысить, ПКМ: уволить");idx++;
                    }
                }
                icon(22,Items.BOOK,"Ставки: 0 -> 10 -> 100 -> 1000 медных за игровые сутки");
            }
            case BUILD -> {
                icon(10,Items.BELL,"Ратуша: 13x15, 2 этажа");
                icon(11,Items.OAK_DOOR,"Жилой дом: 9x11, 1 этаж");
                icon(12,Items.BARREL,"Склад: 13x15, 1 этаж");
                icon(13,Items.ANVIL,"Кузница: 11x13, 1 этаж");
                icon(14,Items.BREWING_STAND,"Таверна: 13x15, 2 этажа");
                icon(22,Items.LECTERN,"Поставь кафедру на своей земле, встань рядом и выбери здание. Затем ПКМ по кафедре.");
            }
            case SITE -> {
                var project=KingdomConstructionData.get(viewer.getServer()).find(siteId);
                if(project!=null && project.owner().equals(viewer.getUUID())) {
                    var type=KingdomBuildingTypes.type(project.design());
                    if(type!=null) {
                        var design=KingdomBuildingTypes.design(type);
                        int progress=KingdomConstructionData.get(viewer.getServer()).progress(project.id());
                        icon(10,Items.BRICKS,type.title+" — "+project.status());
                        icon(11,Items.CLOCK,"Готово: "+progress+" из "+design.blocks().size()+" блоков");
                        icon(13,Items.IRON_PICKAXE,project.status()==KingdomConstructionData.Status.PLANNED?"Начать строительство NPC":"Приостановить строительство NPC");
                        icon(14,Items.CHEST,"Материалы: сундук или бочка вплотную к кафедре; нужен NPC builder с приказом Работать");
                        icon(15,Items.WRITABLE_BOOK,"Смета: "+design.bill());
                        icon(16,Items.COMPASS,"Состояние: "+KingdomConstructionDiagnostics.nextStep(viewer.getServer(),project));
                        icon(17,Items.HOPPER,KingdomConstructionDiagnostics.missingMaterials(viewer.getServer(),project));
                        icon(22,Items.LECTERN,"ПКМ по строительной кафедре для управления");
                    }
                }
            }
            case PROJECTS -> {
                int count=0;
                for(var project:KingdomConstructionData.get(viewer.getServer()).all()) {
                    if(!project.owner().equals(viewer.getUUID()))continue;
                    if(count>=18)break;
                    icon(count,Items.PAPER,project.design()+" | "+project.status()+" | "+project.x()+","+project.y()+","+project.z());count++;
                }
                icon(22,Items.BOOK,"Показано проектов: "+count+" (макс. 18). ЛКМ по проекту — открыть стройку.");
            }
            case ARMY -> {
                icon(10,Items.SHIELD,"Создать отряд (реестр; без появления солдат)");
                int n=0;
                for(var unit:KingdomArmyData.get(viewer.getServer()).all()) {
                    if(!unit.owner().equals(viewer.getUUID()))continue;
                    if(n>=8)break;
                    icon(18+n,Items.IRON_SWORD,unit.name()+" | "+unit.order()+" | ЛКМ: следующий приказ");n++;
                }
                icon(16,Items.BOOK,"Приказы сохраняются; военный ИИ ещё не подключён");
            }
            case TERRITORY -> {
                icon(11,Items.GRASS_BLOCK,"Закрепить текущий чанк 16x16");
                icon(15,Items.MAP,"Открыть земельную карту: выбор и покупка чанков");
            }
            case CIVICS -> {
                var db=KingdomCivicsData.get(viewer.getServer());
                var town=db.townOf(viewer.getUUID());var nation=db.nationOf(viewer.getUUID());
                icon(10,Items.OAK_SIGN,town==null?"Основать город":"Город: "+town.name());
                icon(11,Items.GOLD_INGOT,nation==null?"Основать нацию":"Нация: "+nation.name());
                var plot=db.plot(viewer.serverLevel(),viewer.blockPosition());
                icon(12,Items.MAP,plot==null?"Открыть земельную карту":"Земельная карта | зона: "+plot.zone());
                icon(13,Items.BRICKS,"Сменить назначение текущего участка");
                icon(14,Items.OAK_DOOR,"Переключить общественный доступ (только использование)");
                icon(15,KingdomCurrency.WALLET.get(),"Внести 100 медных из кошелька в казну города");
                icon(16,Items.GOLD_INGOT,"Перевести 1 единицу казны города в казну нации");
                var taxes=KingdomTaxData.get(viewer.getServer());
                long day=viewer.getServer().overworld().getDayTime()/24000L;
                icon(17,Items.PAPER,"Налог за сутки: "+(town==null?0:taxes.rate(town.id(),day))
                    +" медных | Мэр: нажми, чтобы изменить на следующий день");
                var candidates=nearbyPlayers();
                if(town!=null && !candidates.isEmpty()){
                    var target=candidates.get(0);
                    icon(18,Items.PLAYER_HEAD,"Льгота для "+target.getScoreboardName()+": "
                        +taxes.isExempt(town.id(),target.getUUID())+" | переключить (только мэр)");
                }
                icon(22,Items.BOOK,"Казна города: "+(town==null?0:town.treasury())+" | нации: "+(nation==null?0:nation.treasury()));
            }
            case PEOPLE -> {
                icon(11,Items.VILLAGER_SPAWN_EGG,"Открой меню жителя нажатием ПКМ по NPC");
                icon(15,Items.BOOK,"Работа, склады, зарплата и профессии — в меню жителя");
            }
        }
        if(page!=Page.HOME)icon(26,Items.ARROW,"Назад в главное меню");
        broadcastChanges();
    }
    private void say(String message) {viewer.sendSystemMessage(Component.literal("[Project Kingdom] "+message));}
    private void place(KingdomBuildingTypes.Type type) {
        say(KingdomSiteBuilder.place(viewer,type));
    }
    private java.util.List<ServerPlayer> nearbyPlayers() {
        return viewer.serverLevel().players().stream()
            .filter(p->p!=viewer && p.isAlive() && viewer.distanceToSqr(p)<=64.0)
            .sorted(java.util.Comparator.comparing(p->p.getUUID().toString()))
            .toList();
    }
    private void withdrawTownToWallet(long amount) {
        if(KingdomCurrency.wallet(viewer).isEmpty()){say("Нужен денежный мешок со свободным местом");return;}
        var db=KingdomCivicsData.get(viewer.getServer());
        if(!db.withdrawTown(viewer.getUUID(),amount)){say("Недостаточно средств или нет прав мэра");return;}
        if(!KingdomCurrency.credit(viewer,amount)) {
            // Unreachable under server-thread preconditions, but never silently destroy funds.
            db.depositTown(viewer.getUUID(),amount);
            say("Ошибка перевода; средства возвращены в казну");return;
        }
        viewer.getInventory().setChanged();say("Из казны получено "+amount+" медных");
    }
    private void depositWallet(long amount) {
        var db=KingdomCivicsData.get(viewer.getServer());
        if(KingdomCurrency.balance(viewer)<amount){say("Недостаточно монет в денежных мешках");return;}
        if(db.townOf(viewer.getUUID())==null){say("Ты не состоишь в городе");return;}
        if(db.depositTown(viewer.getUUID(),amount)) {
            if(!KingdomCurrency.debit(viewer,amount))throw new IllegalStateException("Purse debit after treasury credit failed");
            say("В казну внесено "+amount+" медных монет");
        } else say("Казна не принимает перевод");
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(player!=viewer || viewer.level().isClientSide || type!=ClickType.PICKUP || (button!=0 && !(page==Page.PAYROLL && button==1) && !(page==Page.ENTERPRISE && button==1)) || slot<0 || slot>=27)return;
        if(page!=Page.HOME && slot==26){show(viewer,Page.HOME);return;}
        switch(page){
            case HOME -> {switch(slot){
                case 10 -> show(viewer,Page.BUILD);
                case 11 -> show(viewer,Page.PROJECTS);
                case 12 -> show(viewer,Page.ARMY);
                case 13 -> show(viewer,Page.TERRITORY);
                case 14,16 -> show(viewer,Page.PEOPLE);
                case 15 -> show(viewer,Page.CIVICS);
                case 22 -> show(viewer,Page.WALLET);
                case 23 -> show(viewer,Page.ENTERPRISE);
            }}
            case WALLET -> {
                var wallet=KingdomCurrency.wallet(viewer);
                if(wallet.isEmpty()){say("Сначала получи предмет Кошелёк");break;}
                if(slot==11){long collected=KingdomCurrency.collect(viewer);say("В кошелёк добавлено: "+collected+" медных");draw();}
                if(slot==13||slot==14||slot==15){long amount=slot==13?1:slot==14?100:10000;
                    say(KingdomCurrency.withdraw(viewer,amount)?"Монета получена":"Недостаточно денег или нет места");draw();}
                if(slot==16){depositWallet(100);draw();}
                if(slot==19){show(viewer,Page.PAYMENTS);}
                if(slot==20){withdrawTownToWallet(100);draw();}
                if(slot==21){say(KingdomTaxData.get(viewer.getServer()).pay(viewer.getServer(),viewer,100)?"Налоговый долг оплачен":"Нет долга или недостаточно денег");draw();}
                if(slot==22){show(viewer,Page.PAYROLL);}
            }
            case ORDERS -> { if(slot==26)show(viewer,Page.WAREHOUSE); }
            case WAREHOUSE -> {
                if(slot==26){show(viewer,Page.ORDERS);break;}
                String chosen=switch(slot){case 10 -> "minecraft:charcoal";case 11 -> "minecraft:coal";
                    case 12 -> "minecraft:iron_ingot";case 13 -> "minecraft:oak_log";
                    case 14 -> "minecraft:wheat";default -> null;};
                if(chosen!=null){marketItem=chosen;marketQuote=0;draw();break;}
                if(slot==15){marketCount=marketCount==1?8:marketCount==8?20:marketCount==20?64:1;marketQuote=0;draw();break;}
                if(slot>=18&&slot<=21){marketQuote=slot-18;draw();break;}
                if(slot==22){
                    var market=KingdomWarehouseMarket.get(viewer.getServer());
                    var destination=market.deliveryChest(viewer.serverLevel(),viewer.blockPosition(),viewer.getUUID());
                    if(destination==null){say("Нужен твой сундук приёмки вплотную к зарегистрированной стойке.");break;}
                    var order=market.checkout(viewer,marketItem,marketCount,marketQuote,destination,10);
                    say(order==null?"Заказ не оформлен: проверь запас, цену и кошелёк.":
                        "Заказ оплачен: "+order.id+". Ожидай курьера.");draw();
                }
            }
            case BANK -> {
                var bank=KingdomBankData.get(viewer.getServer());
                long amount=switch(slot){case 11,15 -> 1;case 12,16 -> 100;case 13,17 -> 10000;default -> 0;};
                if(amount>0){boolean ok=slot<=13?bank.deposit(viewer,amount):bank.withdraw(viewer,amount);
                    say(ok?(slot<=13?"В банк внесено ":"Из банка снято ")+amount+" медных":"Операция отклонена: проверь кошелёк и остаток");draw();}
            }
            case ENTERPRISE -> {
                var db=KingdomEnterpriseData.get(viewer.getServer());var firm=db.owned(viewer.getUUID());
                if(slot==10){var e=db.create(viewer.getUUID(),"Мастерская_"+viewer.getScoreboardName()+"_"+(db.ownedAll(viewer.getUUID()).size()+1));
                    say(e==null?"Достигнут лимит предприятий или неверное имя":"Создано: "+e.name());draw();}
                else if(slot==11){say(db.selectNext(viewer.getUUID())?"Переключено предприятие":"Других предприятий пока нет");draw();}
                else if(slot==12){say(db.deposit(viewer,100)?"В кассу внесено 100":"Встань у своей кассы, проверь кошелёк");draw();}
                else if(slot==13){say(db.withdraw(viewer,100)?"Из кассы получено 100":"Встань у кассы, проверь остаток");draw();}
                else if(slot==14){say(db.bindCounter(viewer,viewer.blockPosition().below())?"Сундук или бочка зарегистрированы как касса":"Встань на принадлежащий тебе сундук/бочку");draw();}
                else if(slot>=15 && slot<=17){
                    var held=viewer.getMainHandItem();
                    if(held.isEmpty())say("Возьми в руку товар из кассового сундука");
                    else {long price=slot==15?10:slot==16?100:1000;
                        String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                        say(db.listItem(viewer,item,price)==null?"Нет товара в привязанном сундуке или нет прав":"Товар выставлен: "+item+" по "+price+" мед.");
                    }draw();
                }
                else if(slot==24 && firm!=null) {
                    var candidates=nearbyPlayers();
                    if(!candidates.isEmpty()){
                        var target=candidates.get(0);
                        var jobs=KingdomEnterprisePayrollData.get(viewer.getServer());
                        var contract=jobs.contract(firm.id(),target.getUUID());
                        if(button==1)say(jobs.dismiss(viewer.getServer(),viewer.getUUID(),firm.id(),target.getUUID())
                            ?"Сотрудник уволен, задолженность сохранена":"Нет действующего договора");
                        else {
                            long old=contract==null||!contract.active()?0:contract.dailyCopper();
                            long rate=old==0?10:old==10?100:old==100?1000:10;
                            say(jobs.hire(viewer.getServer(),viewer.getUUID(),firm.id(),target.getUUID(),rate)
                                ?"Сотрудник нанят; ставка "+rate:"Нет прав");
                        }
                    }draw();
                }
                else if(slot==25){say(KingdomEnterprisePayrollData.get(viewer.getServer()).claimAny(viewer.getServer(),viewer)
                    ?"Начисленная зарплата получена":"Нет долга или доступной кассы с деньгами");draw();}
                else if(slot>=18 && slot<22){
                    java.util.UUID selected=tradeSlots.get(slot);
                    if(selected!=null)say(db.buy(viewer,selected,button==1?16:1)
                        ?"Товар приобретён, деньги перечислены продавцу":"Покупка отклонена: проверь деньги, товары и свободные слоты");
                    draw();
                }
            }
            case PAYMENTS -> {
                var targets=nearbyPlayers();
                if(slot>=0 && slot<18 && slot<targets.size()) {
                    var target=targets.get(slot);
                    boolean ok=KingdomCurrency.transfer(viewer,target,100);
                    say(ok?"Переведено 100 медных игроку "+target.getScoreboardName():"Перевод отклонён: проверь кошельки и баланс");
                    if(ok)target.sendSystemMessage(Component.literal("[Project Kingdom] Получено 100 медных от "+viewer.getScoreboardName()));
                    draw();
                }
            }
            case PAYROLL -> {
                var civics=KingdomCivicsData.get(viewer.getServer());var town=civics.townOf(viewer.getUUID());
                if(slot==11){say(KingdomPayrollData.get(viewer.getServer()).claim(viewer.getServer(),viewer)?"Зарплата получена из казны":"Нет начислений, кошелька или средств в казне");draw();break;}
                if(town==null||!town.mayor().equals(viewer.getUUID())){say("Только мэр управляет зарплатами");break;}
                if(slot>=12&&slot<21) {
                    var eligible=nearbyPlayers().stream().filter(p->{var t=civics.townOf(p.getUUID());return t!=null&&t.id().equals(town.id());}).limit(9).toList();
                    int idx=slot-12;if(idx>=eligible.size())break;
                    var target=eligible.get(idx);var payroll=KingdomPayrollData.get(viewer.getServer());
                    var contract=payroll.contract(target.getUUID());
                    if(button==1){say(payroll.dismiss(viewer.getServer(),viewer.getUUID(),target.getUUID())?"Договор прекращён":"Нет договора");}
                    else if(button==0){long old=contract==null?0:contract.dailyCopper();long rate=old==0?10:old==10?100:old==100?1000:10;
                        say(payroll.hire(viewer.getServer(),viewer.getUUID(),target.getUUID(),rate)?"Зарплата назначена: "+rate+" медных/сутки":"Не удалось назначить зарплату");}
                    draw();
                }
            }
            case BUILD -> {switch(slot){
                case 10 -> place(KingdomBuildingTypes.Type.TOWN_HALL);
                case 11 -> place(KingdomBuildingTypes.Type.HOUSE);
                case 12 -> place(KingdomBuildingTypes.Type.WAREHOUSE);
                case 13 -> place(KingdomBuildingTypes.Type.SMITHY);
                case 14 -> place(KingdomBuildingTypes.Type.TAVERN);
            }}
            case SITE -> {if(slot==13){
                var db=KingdomConstructionData.get(viewer.getServer());
                var project=db.find(siteId);
                boolean start=project!=null && project.status()==KingdomConstructionData.Status.PLANNED;
                say(db.order(siteId,viewer.getUUID(),start)?(start?"Строительство поручено NPC. Поставь сундук рядом с кафедрой и назначь строителя.":"Строительство остановлено."):"Нельзя изменить заказ");draw();
            }}
            case PROJECTS -> {if(slot>=0&&slot<18){
                int i=0;
                for(var project:KingdomConstructionData.get(viewer.getServer()).all()){
                    if(!project.owner().equals(viewer.getUUID()))continue;
                    if(i++==slot){openSite(viewer,project.id());break;}
                }
            }}
            case CIVICS -> {
                var db=KingdomCivicsData.get(viewer.getServer());
                var pos=viewer.blockPosition();var dim=viewer.level().dimension().location().toString();
                int cx=pos.getX()>>4,cz=pos.getZ()>>4;
                if(slot==10){var t=db.foundTown(viewer.getUUID(),"Город_"+viewer.getScoreboardName());say(t==null?"Ты уже состоишь в городе":"Основан город: "+t.name());draw();}
                if(slot==11){var n=db.foundNation(viewer.getUUID(),"Королевство_"+viewer.getScoreboardName());say(n==null?"Нацию может основать мэр независимого города":"Основана нация: "+n.name());draw();}
                if(slot==12){KingdomLandMapMenu.open(viewer);break;}
                if(slot==13){var plot=db.plot(viewer.serverLevel(),pos);if(plot==null){say("Сначала закрепи участок в разделе Территории");break;}
                    var zones=KingdomCivicsData.Zone.values();var next=zones[(plot.zone().ordinal()+1)%zones.length];
                    say(db.zone(viewer.getUUID(),dim,cx,cz,next)?"Зона: "+next:"Нет прав мэра");draw();}
                if(slot==15){depositWallet(100);draw();break;}
                if(slot==16){say(db.townPaysNation(viewer.getUUID(),1)?"1 единица переведена в казну нации":"Нужны права мэра, нация и средства в казне");draw();break;}
                if(slot==17){
                    var town=db.townOf(viewer.getUUID());
                    if(town==null){say("Нет города");break;}
                    var taxes=KingdomTaxData.get(viewer.getServer());
                    long current=taxes.rate(town.id(),viewer.getServer().overworld().getDayTime()/24000L);
                    long next=current==10?20:current==20?50:current==50?0:current==0?5:10;
                    say(taxes.setRate(viewer.getServer(),viewer.getUUID(),next)
                        ?"Новая ставка "+next+" мед. вступит в силу завтра":"Ставку может менять только мэр");draw();break;
                }
                if(slot==18){
                    var town=db.townOf(viewer.getUUID());var candidates=nearbyPlayers();
                    if(town==null||candidates.isEmpty()){say("Нет подходящего жителя");break;}
                    var taxes=KingdomTaxData.get(viewer.getServer());var target=candidates.get(0);
                    boolean value=!taxes.isExempt(town.id(),target.getUUID());
                    say(taxes.exempt(viewer.getServer(),viewer.getUUID(),target.getUUID(),value)
                        ?"Льгота изменена. Ранее начисленные долги остаются":"Нужны права мэра и гражданство жителя");draw();break;
                }
                if(slot==14){var plot=db.plot(viewer.serverLevel(),pos);if(plot==null){say("Участок не закреплён");break;}
                    say(db.setPublicUse(viewer.getUUID(),dim,cx,cz,!plot.publicUse())?"Общественный доступ: "+!plot.publicUse():"Нет прав мэра");draw();}
            }
            case TERRITORY -> {if(slot==15){KingdomLandMapMenu.open(viewer);break;} if(slot==11){
                var db=KingdomCivicsData.get(viewer.getServer());
                var town=db.townOf(viewer.getUUID());
                if(town==null||!town.mayor().equals(viewer.getUUID())){say("Сначала основай город через меню Города и нации");break;}
                var old=KingdomTerritories.get(viewer.getServer());
                var owner=old.owner(viewer.serverLevel(),viewer.blockPosition());
                if(owner!=null&&!owner.equals(viewer.getUUID())){say("Чужая территория");break;}
                var current=db.plot(viewer.serverLevel(),viewer.blockPosition());
                if(current!=null && !current.town().equals(town.id())){say("Участок закреплён за другим городом");break;}
                if(!old.claim(viewer.serverLevel(),viewer.blockPosition(),viewer.getUUID())){say("Чужая территория");break;}
                var claimPos=viewer.blockPosition();
                String claimDim=viewer.level().dimension().location().toString();
                boolean ok=db.claim(viewer.getUUID(),claimDim,claimPos.getX()>>4,claimPos.getZ()>>4);
                say(ok?(current==null?"Чанк закреплён за городом: "+town.name():"Это уже участок твоего города: "+town.name()):"Не удалось зарегистрировать участок города");
                draw();
            }}
            case ARMY -> {
                if(slot==10){var u=KingdomArmyData.get(viewer.getServer()).create(viewer.getUUID(),"Отряд_"+(KingdomArmyData.get(viewer.getServer()).all().size()+1));say("Создан "+u.name());draw();}
                if(slot>=18 && slot<26){int index=slot-18,n=0;
                    for(var unit:KingdomArmyData.get(viewer.getServer()).all()){
                        if(!unit.owner().equals(viewer.getUUID()))continue;
                        if(n++!=index)continue;
                        var orders=KingdomArmyData.Order.values();
                        var next=orders[(unit.order().ordinal()+1)%orders.length];
                        if(KingdomArmyData.get(viewer.getServer()).order(unit.id(),viewer.getUUID(),next)){say("Приказ: "+next);draw();}break;
                    }
                }
            }
            default -> { }
        }
    }
    @Override public boolean stillValid(Player player){return player==viewer && player.isAlive();}
    @Override public void removed(Player player){super.removed(player);content.clearContent();}
}
