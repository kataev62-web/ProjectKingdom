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
import ru.projectkingdom.administration.AdministrativeCoreBlockEntity;
import ru.projectkingdom.administration.AdministrativeCoreMenu;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;

/** Menu-driven management; all changes are verified on the server. */
public final class KingdomMainMenu extends ChestMenu {
    private enum Page { HOME, BUILD, PROJECTS, ARMY, TERRITORY, PEOPLE, CIVICS, WALLET, PAYMENTS, PAYROLL, ENTERPRISE, BANK, SITE, MATERIALS, WAREHOUSE, ORDERS }
    private final SimpleContainer content;
    private final ServerPlayer viewer;
    private final Page page;
    private final java.util.UUID siteId;
    /** Server-owned origin of the construction-company core that opened this menu. */
    private final BlockPos architectAnchor;
    private String marketItem="minecraft:charcoal";
    private int marketCount=20;
    private int marketQuote=0;
    private int schematicPage=0;
    private int materialsPage=0;
    // Server-bound slot identities: never let live market ordering change the clicked offer.
    private final java.util.Map<Integer,java.util.UUID> tradeSlots=new java.util.HashMap<>();
    private KingdomMainMenu(int id, Inventory inventory, ServerPlayer player, Page page, SimpleContainer content, java.util.UUID siteId, BlockPos architectAnchor) {
        super(MenuType.GENERIC_9x3, id, inventory, content, 3);
        this.content=content; this.viewer=player; this.page=page; this.siteId=siteId;
        this.architectAnchor=architectAnchor == null ? null : architectAnchor.immutable();
        draw();
    }
    private static KingdomMainMenu make(int id, Inventory inventory, ServerPlayer player, Page page,java.util.UUID siteId, BlockPos architectAnchor) {
        return new KingdomMainMenu(id, inventory, player, page, new SimpleContainer(27),siteId,architectAnchor);
    }
    public static void open(ServerPlayer player) { show(player,Page.HOME); }
    /** One native Kingdom atlas instead of a separate vanilla chest map selector. */
    public static void openMaps(ServerPlayer player) {
        ru.projectkingdom.network.KingdomNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new ru.projectkingdom.network.OpenGraphicAtlasPacket(
                    ru.projectkingdom.map.KingdomGraphicAtlasData.Layer.LAND.ordinal()));
    }
    public static void openWallet(ServerPlayer player) { show(player,Page.WALLET); }
    public static void openBank(ServerPlayer player) { openAdministrativeOrExplain(player,AdministrativeKind.BANK); }
    public static void openWarehouse(ServerPlayer player) { openAdministrativeOrExplain(player,AdministrativeKind.CITY_MARKET); }
    public static void openTownHall(ServerPlayer player) { openAdministrativeOrExplain(player,AdministrativeKind.TOWN_HALL); }
    public static void openTreasury(ServerPlayer player) { openAdministrativeOrExplain(player,AdministrativeKind.TREASURY); }
    public static void openLandOffice(ServerPlayer player) { openAdministrativeOrExplain(player,AdministrativeKind.LAND_OFFICE); }
    public static void openOrders(ServerPlayer player) { show(player,Page.ORDERS); }
    public static void openArchitect(ServerPlayer player) { show(player,Page.BUILD); }
    /** Opened from the company core: reuse that existing block; no second lectern. */
    public static void openArchitect(ServerPlayer player, BlockPos office) {
        if (office == null || !KingdomArchitectSiteAccess.mayUse(player,office)) {
            player.sendSystemMessage(Component.literal("[Архитектор] Строительная контора недоступна или нет прав."));
            return;
        }
        show(player,Page.BUILD,null,office);
    }
    public static void openProjects(ServerPlayer player) { show(player,Page.PROJECTS); }
    public static void openSite(ServerPlayer player,java.util.UUID id) { show(player,Page.SITE,id); }
    private static void openAdministrativeOrExplain(ServerPlayer player,AdministrativeKind kind){
        if(openNearbyAdministrative(player,kind))return;
        player.sendSystemMessage(Component.literal("[Project Kingdom] Старая точка доступа оставлена только для миграции. Подойдите к активному блоку: "+kind.russianName()+"."));
    }
    public static boolean isNearAdministrative(ServerPlayer player,AdministrativeKind kind){
        return nearbyAdministrativeTown(player,kind)!=null;
    }
    public static java.util.UUID nearbyAdministrativeTown(ServerPlayer player,AdministrativeKind kind){
        if(player==null||kind==null)return null;String dimension=player.level().dimension().location().toString();BlockPos origin=player.blockPosition();
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());java.util.UUID selected=null;double best=Double.MAX_VALUE;
        for(var town:KingdomCivicsData.get(player.getServer()).towns()){
            var binding=data.core(town.id(),kind);if(binding==null||!dimension.equals(binding.dimension()))continue;BlockPos pos=BlockPos.of(binding.packedPos());
            double distance=pos.distSqr(origin);if(distance>64||distance>=best||!player.serverLevel().hasChunkAt(pos))continue;
            if(player.serverLevel().getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core&&core.bound()&&town.id().equals(core.townId())&&kind==core.kind()){selected=town.id();best=distance;}
        }return selected;
    }
    public static boolean openNearbyAdministrative(ServerPlayer player,AdministrativeKind kind){
        if(!isNearAdministrative(player,kind))return false;String dimension=player.level().dimension().location().toString();BlockPos origin=player.blockPosition();
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());AdministrativeCoreBlockEntity selected=null;double best=Double.MAX_VALUE;
        for(var town:KingdomCivicsData.get(player.getServer()).towns()){
            var binding=data.core(town.id(),kind);if(binding==null||!dimension.equals(binding.dimension()))continue;BlockPos pos=BlockPos.of(binding.packedPos());double distance=pos.distSqr(origin);
            if(distance>64||distance>=best||!player.serverLevel().hasChunkAt(pos))continue;
            if(player.serverLevel().getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core&&core.bound()&&town.id().equals(core.townId())&&kind==core.kind()){selected=core;best=distance;}
        }
        if(selected==null)return false;AdministrativeCoreMenu.open(player,selected);return true;
    }
    private static void show(ServerPlayer player,Page page) {show(player,page,null,null);}
    private static void show(ServerPlayer player,Page page,java.util.UUID siteId) {
        show(player,page,siteId,null);
    }
    private static void show(ServerPlayer player,Page page,java.util.UUID siteId,BlockPos office) {
        player.openMenu(new SimpleMenuProvider((id,inv,p)->make(id,inv,player,page,siteId,office),
            Component.literal("Project Kingdom / "+switch(page){
                case HOME -> "Управление"; case BUILD -> "Архитектор";
                case PROJECTS -> "Проекты"; case ARMY -> "Армия";
                case TERRITORY -> "Территории"; case PEOPLE -> "Жители"; case CIVICS -> "Города и нации"; case WALLET -> "Кошелёк"; case PAYMENTS -> "Платежи"; case PAYROLL -> "Зарплаты"; case ENTERPRISE -> "Предприятие"; case BANK -> "Банк"; case SITE -> "Строительная площадка"; case MATERIALS -> "Материалы для стройки"; case WAREHOUSE -> "Заказ доставки"; case ORDERS -> "Мои доставки";
            })));
    }
    private void icon(int slot, Item item, String name) {
        ItemStack stack=new ItemStack(item);stack.setHoverName(Component.literal(name));content.setItem(slot,stack);
    }
    private void materialIcon(int slot, KingdomConstructionDiagnostics.Material material) {
        ItemStack stack=new ItemStack(material.item());
        stack.setHoverName(Component.empty().append(material.item().getDescription())
            .append(Component.literal(" | Требуется: "+material.required()
                +" | На складе: "+material.available()+" | Не хватает: "+material.missing())));
        content.setItem(slot,stack);
    }
    private void draw() {
        content.clearContent();
        tradeSlots.clear();
        switch(page) {
            case HOME -> {
                icon(10,Items.BRICKS,"Строительство и архитектор");
                icon(11,Items.PAPER,"Мои проекты");
                icon(12,Items.IRON_SWORD,"Армия и приказы");
                icon(13,Items.GRASS_BLOCK,"Земельное управление");
                icon(14,Items.VILLAGER_SPAWN_EGG,"Жители и профессии");
                icon(15,Items.EMERALD,"Ратуша города");
                icon(16,Items.CHEST,"Городской рынок");
                icon(20,Items.OAK_SIGN,"Города, государства и управление");
                icon(21,Items.FILLED_MAP,"Карты Kingdom: территория, политика, предприятия");
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
                var chest=market.cityMarketDeliveryChest(viewer.serverLevel(),viewer.blockPosition(),viewer.getUUID());
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
                icon(10,Items.OAK_SIGN,"Новые предприятия создаются установкой одного из 8 блоков-ядер (всего "+count+" / 16)");
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
                var schematics=KingdomSchematicLibrary.available(viewer.getServer());
                int offset=schematicPage*7;
                for(int i=0;i<7 && offset+i<schematics.size();i++)
                    icon(15+i,Items.MAP,"Предпросмотр 3D: "+schematics.get(offset+i));
                int pages=Math.max(1,(schematics.size()+6)/7);
                icon(25,Items.COMPASS,"Схемы .schem, страница "+(schematicPage+1)+"/"+pages+" (следующая)");
                icon(22,KingdomConstructionRegistry.DRAFTING_TABLE_ITEM.get(),
                    "Выбери проект: появится 3D-макет. Передвигай и поворачивай; фиксируй только после проверки участка.");
                icon(23,KingdomConstructionRegistry.BUILDERS_HAMMER.get(),
                    "Строительный молот: ПКМ по столу проекта = один реальный блок из соседнего сундука.");
                icon(24,KingdomConstructionRegistry.BUILDING_BLUEPRINT.get(),
                    "После фиксации нужен один чертёж, реальные материалы и NPC-строители. F8 — вернуть предпросмотр.");
            }
            case SITE -> {
                var sites=KingdomConstructionData.get(viewer.getServer());
                var project=sites.find(siteId);
                if(project!=null && sites.mayAssist(siteId,viewer.getUUID())) {
                    var design=KingdomConstructionPlans.resolve(viewer.getServer(),project.design());
                    if(design!=null) {
                        int progress=KingdomConstructionData.get(viewer.getServer()).progress(project.id());
                        icon(10,Items.BRICKS,KingdomConstructionPlans.title(project.design())+" — "+KingdomConstructionData.statusRu(project.status()));
                        icon(11,Items.CLOCK,"Готово: "+progress+" из "+design.blocks().size()+" блоков");
                        icon(13,Items.IRON_PICKAXE,project.owner().equals(viewer.getUUID())
                            ?(project.status()==KingdomConstructionData.Status.PLANNED?"Начать строительство NPC":"Приостановить строительство NPC")
                            :"Запускать и останавливать стройку может только владелец");
                        icon(14,Items.CHEST,"Склад материалов рядом со столом; NPC-строитель с договором работает по дневному расписанию");
                        icon(15,Items.WRITABLE_BOOK,"Материалы: открыть список со значками и количеством");
                        icon(16,Items.COMPASS,"Состояние: "+KingdomConstructionDiagnostics.nextStep(viewer.getServer(),project));
                        icon(17,Items.HOPPER,KingdomConstructionDiagnostics.missingMaterials(viewer.getServer(),project)+" — нажми для подробностей");
                        int helpers=sites.assistants(project.id()).size();
                        if(project.owner().equals(viewer.getUUID())) {
                            var nearby=nearbyPlayers().stream().min(
                                java.util.Comparator.comparingDouble(viewer::distanceToSqr));
                            if(nearby.isPresent()) {
                                var p=nearby.get();
                                icon(18,Items.PLAYER_HEAD,
                                    (sites.assistants(project.id()).contains(p.getUUID())?"Отозвать приглашение: ":"Пригласить в бригаду: ")
                                        +p.getScoreboardName()+" (ЛКМ)");
                            } else icon(18,Items.PLAYER_HEAD,"Для приглашения строитель должен стоять рядом (до 8 блоков)");
                        } else icon(18,Items.PLAYER_HEAD,"Ты приглашён в бригаду — строй молотом из общего склада");
                        icon(19,Items.PAPER,"Бригада: "+helpers+"/8. Приглашение не даёт права менять владельца или распоряжаться проектом.");
                        icon(22,KingdomConstructionRegistry.DRAFTING_TABLE_ITEM.get(),"ПКМ по столу / кафедре для управления. Молот — строить, чертёж — подсветка");
                    }
                }
            }
            case MATERIALS -> {
                var projects=KingdomConstructionData.get(viewer.getServer());
                var project=projects.find(siteId);
                if(project!=null && projects.mayAssist(siteId,viewer.getUUID())) {
                    var rows=KingdomConstructionDiagnostics.materials(viewer.getServer(),project);
                    int pages=Math.max(1,(rows.size()+17)/18);
                    if(materialsPage>=pages)materialsPage=pages-1;
                    for(int i=0;i<18 && materialsPage*18+i<rows.size();i++)
                        materialIcon(i,rows.get(materialsPage*18+i));
                    icon(20,Items.BOOK,"Материалы: "+rows.size()+" видов. Наведи курсор на значок для названия и количества.");
                    icon(21,Items.ARROW,"Предыдущая страница");
                    icon(22,Items.ARROW,"Следующая страница ("+(materialsPage+1)+"/"+pages+")");
                    icon(23,Items.HOPPER,KingdomConstructionDiagnostics.missingMaterials(viewer.getServer(),project));
                    icon(24,Items.CHEST,"Материалы берутся только из настоящего сундука или бочки рядом с чертёжным столом.");
                }
            }
            case PROJECTS -> {
                int count=0;
                for(var project:KingdomConstructionData.get(viewer.getServer()).all()) {
                    if(!KingdomConstructionData.get(viewer.getServer())
                            .mayAssist(project.id(),viewer.getUUID()))continue;
                    if(count>=18)break;
                    icon(count,Items.PAPER,KingdomConstructionPlans.title(project.design())+" | "+KingdomConstructionData.statusRu(project.status())+" | "+project.x()+","+project.y()+","+project.z());count++;
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
                icon(11,Items.FILLED_MAP,"Карты и управление территориями в разделе Карты Kingdom");
                icon(15,Items.BOOK,"Перейти в раздел Карты");
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
        if(page!=Page.HOME)icon(26,Items.ARROW,page==Page.MATERIALS?"Назад к строительству":"Назад в главное меню");
        broadcastChanges();
    }
    private void say(String message) {viewer.sendSystemMessage(Component.literal("[Project Kingdom] "+message));}
    private void place(KingdomBuildingTypes.Type type) {
        KingdomArchitectSessions.beginBuiltin(viewer,type,architectAnchor);
    }
    private java.util.List<ServerPlayer> nearbyPlayers() {
        return viewer.serverLevel().players().stream()
            .filter(p->p!=viewer && p.isAlive() && viewer.distanceToSqr(p)<=64.0)
            .sorted(java.util.Comparator.comparing(p->p.getUUID().toString()))
            .toList();
    }
    private boolean openNearbyEnterprise(){
        var origin=viewer.blockPosition();double best=Double.MAX_VALUE;ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity selected=null;
        for(int dx=-6;dx<=6;dx++)for(int dy=-4;dy<=4;dy++)for(int dz=-6;dz<=6;dz++){
            var pos=origin.offset(dx,dy,dz);if(!viewer.serverLevel().hasChunkAt(pos))continue;
            if(viewer.serverLevel().getBlockEntity(pos) instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity core&&core.bound()){
                double distance=pos.distSqr(origin);if(distance<best){best=distance;selected=core;}
            }
        }
        if(selected==null)return false;ru.projectkingdom.enterprise.EnterpriseCoreMenu.open(viewer,selected);return true;
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
        if(page!=Page.HOME && slot==26){if(page==Page.MATERIALS)show(viewer,Page.SITE,siteId);else show(viewer,Page.HOME);return;}
        switch(page){
            case HOME -> {switch(slot){
                case 10 -> show(viewer,Page.BUILD);
                case 11 -> show(viewer,Page.PROJECTS);
                case 12 -> show(viewer,Page.ARMY);
                case 13 -> openLandOffice(viewer);
                case 14 -> show(viewer,Page.PEOPLE);
                case 15 -> openTownHall(viewer);
                case 16 -> openWarehouse(viewer);
                case 20 -> show(viewer,Page.CIVICS);
                case 21 -> openMaps(viewer);
                case 22 -> show(viewer,Page.WALLET);
                case 23 -> {if(!openNearbyEnterprise()){say("Подойди к установленному блоку-ядру предприятия.");show(viewer,Page.ENTERPRISE);}}
            }}
            case WALLET -> {
                var wallet=KingdomCurrency.wallet(viewer);
                if(wallet.isEmpty()){say("Сначала получи предмет Кошелёк");break;}
                if(slot==11){long collected=KingdomCurrency.collect(viewer);say("В кошелёк добавлено: "+collected+" медных");draw();}
                if(slot==13||slot==14||slot==15){long amount=slot==13?1:slot==14?100:10000;
                    say(KingdomCurrency.withdraw(viewer,amount)?"Монета получена":"Недостаточно денег или нет места");draw();}
                if(slot==16){openTreasury(viewer);}
                if(slot==19){show(viewer,Page.PAYMENTS);}
                if(slot==20){openTreasury(viewer);}
                if(slot==21){say(KingdomTaxData.get(viewer.getServer()).pay(viewer.getServer(),viewer,100)?"Налоговый долг оплачен":"Нет долга или недостаточно денег");draw();}
                if(slot==22){openTreasury(viewer);}
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
                    var destination=market.cityMarketDeliveryChest(viewer.serverLevel(),viewer.blockPosition(),viewer.getUUID());
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
                if(slot==10){say("Создай нужный блок-ядро по рецепту и установи его на своей территории.");draw();}
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
            case BUILD -> {
                switch(slot){
                    case 10 -> place(KingdomBuildingTypes.Type.TOWN_HALL);
                    case 11 -> place(KingdomBuildingTypes.Type.HOUSE);
                    case 12 -> place(KingdomBuildingTypes.Type.WAREHOUSE);
                    case 13 -> place(KingdomBuildingTypes.Type.SMITHY);
                    case 14 -> place(KingdomBuildingTypes.Type.TAVERN);
                    case 25 -> {
                        int size=KingdomSchematicLibrary.available(viewer.getServer()).size();
                        schematicPage=(schematicPage+1)%Math.max(1,(size+6)/7);
                        draw();
                    }
                    default -> {
                        if(slot>=15&&slot<=21) {
                            var items=KingdomSchematicLibrary.available(viewer.getServer());
                            int idx=schematicPage*7+slot-15;
                            if(idx<items.size())KingdomArchitectSessions.beginSchematic(viewer,items.get(idx),architectAnchor);
                        }
                    }
                }
            }
            case SITE -> {
                var db=KingdomConstructionData.get(viewer.getServer());
                var project=db.find(siteId);
                if((slot==15 || slot==17) && project!=null && db.mayAssist(siteId,viewer.getUUID())) {
                    show(viewer,Page.MATERIALS,siteId);
                    break;
                }
                if(slot==13) {
                    boolean start=project!=null && project.status()==KingdomConstructionData.Status.PLANNED;
                    say(db.order(siteId,viewer.getUUID(),start)
                        ?(start?"Строительство поручено NPC. Поставь сундук рядом со столом и назначь строителя.":"Строительство остановлено.")
                        :"Только владелец может управлять строительством.");draw();
                } else if(slot==18 && project!=null && project.owner().equals(viewer.getUUID())) {
                    var nearby=nearbyPlayers().stream().min(
                        java.util.Comparator.comparingDouble(viewer::distanceToSqr));
                    if(nearby.isPresent()) {
                        var target=nearby.get();
                        boolean previously=db.assistants(siteId).contains(target.getUUID());
                        say(db.toggleAssistant(siteId,viewer.getUUID(),target.getUUID())
                            ?(previously?"Отозвано разрешение строить для ":"Приглашён строитель: ")+target.getScoreboardName()
                            :"Не удалось изменить бригаду (максимум 8 участников).");
                    } else say("Пусть помощник подойдёт к тебе на расстояние до 8 блоков.");
                    draw();
                }
            }
            case MATERIALS -> {
                var project=KingdomConstructionData.get(viewer.getServer()).find(siteId);
                if(project==null || !KingdomConstructionData.get(viewer.getServer()).mayAssist(siteId,viewer.getUUID())) break;
                int pages=Math.max(1,(KingdomConstructionDiagnostics.materials(viewer.getServer(),project).size()+17)/18);
                if(slot==21){materialsPage=(materialsPage+pages-1)%pages;draw();}
                if(slot==22){materialsPage=(materialsPage+1)%pages;draw();}
            }
            case PROJECTS -> {if(slot>=0&&slot<18){
                int i=0;
                for(var project:KingdomConstructionData.get(viewer.getServer()).all()){
                    if(!KingdomConstructionData.get(viewer.getServer()).mayAssist(project.id(),viewer.getUUID()))continue;
                    if(i++==slot){openSite(viewer,project.id());break;}
                }
            }}
            case CIVICS -> {
                var db=KingdomCivicsData.get(viewer.getServer());
                var pos=viewer.blockPosition();var dim=viewer.level().dimension().location().toString();
                int cx=pos.getX()>>4,cz=pos.getZ()>>4;
                if(slot==10){var t=db.foundTown(viewer.getUUID(),"Город_"+viewer.getScoreboardName());say(t==null?"Ты уже состоишь в городе":"Основан город: "+t.name());draw();}
                if(slot==11){var n=db.foundNation(viewer.getUUID(),"Королевство_"+viewer.getScoreboardName());say(n==null?"Нацию может основать мэр независимого города":"Основана нация: "+n.name());draw();}
                if(slot==12){openMaps(viewer);break;}
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
            case TERRITORY -> {
                if(slot==15 || slot==11) openMaps(viewer);
            }
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
