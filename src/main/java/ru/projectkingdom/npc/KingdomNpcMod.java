package ru.projectkingdom.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ru.projectkingdom.core.KingdomBookGuardian;
import ru.projectkingdom.core.KingdomBookRegistry;
import ru.projectkingdom.economy.KingdomEconomyRegistry;
import ru.projectkingdom.enterprise.KingdomEnterpriseRegistry;
import ru.projectkingdom.enterprise.EnterpriseAccessService;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.administration.KingdomAdministrationRegistry;
import ru.projectkingdom.administration.AdministrativeAccessService;

import java.util.Locale;
import java.util.UUID;

@Mod(KingdomNpcMod.MOD_ID)
public final class KingdomNpcMod {
    public static final String MOD_ID = "kingdomnpc";
    private static final int TICKS_PER_DAY = 24000;
    private static final int MAX_NPCS = 50; // Alpha safeguard; not a population ceiling in final design.

    public KingdomNpcMod() {
        KingdomCurrency.register();
        KingdomManagedStorage.register();
        KingdomDemandBoardBlock.register();
        KingdomBookRegistry.register(FMLJavaModLoadingContext.get().getModEventBus());
        KingdomEconomyRegistry.register(FMLJavaModLoadingContext.get().getModEventBus());
        KingdomEnterpriseRegistry.register();
        KingdomConstructionRegistry.register();
        KingdomAdministrationRegistry.register();
        KingdomNetwork.register();
        MinecraftForge.EVENT_BUS.register(new KingdomBookGuardian());
        MinecraftForge.EVENT_BUS.register(this);
    }

    // One-click binding: after choosing a button in NPC menu, right-click a real chest.
    private static final Map<UUID, BindRequest> BINDING = new ConcurrentHashMap<>();
    private record BindRequest(int npcId, String role, String dimension) {}
    public static void beginBind(ServerPlayer p, int id, boolean source) {
        beginBindRole(p,id,source?"raw":"finished");
    }
    public static void beginBindRole(ServerPlayer p, int id, String role) {
        if (!role.equals("raw") && !role.equals("finished") && !role.equals("workstation")) return;
        BINDING.put(p.getUUID(), new BindRequest(id,role,p.level().dimension().location().toString()));
        p.sendSystemMessage(Component.literal("[Королевство] Нажми ПКМ по "+(role.equals("workstation")?"рабочей станции":"нужному складу")+" на своей территории."));
    }
    /** Shift + right click with a compass opens the entire kingdom UI without chat commands. */
    @SubscribeEvent public void openKingdomMenu(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown() || !event.getItemStack().is(Items.COMPASS)) return;
        KingdomMainMenu.open(player);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    /**
     * Ancient-Warfare-style construction control:
     * drafting table/legacy lectern -> choose blueprint; crafted hammer ->
     * one fully verified and physically paid placement; blueprint -> preview.
     */
    @SubscribeEvent public void architectLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        ServerLevel level = player.serverLevel();
        BlockPos marker = event.getPos();
        if (!KingdomConstructionMarkers.isMarker(level, marker)) return;

        var data = KingdomConstructionData.get(player.getServer());
        var project = data.atMarker(level.dimension().location().toString(),
            marker.getX(), marker.getY(), marker.getZ());
        ItemStack held = player.getItemInHand(event.getHand());
        // A block held in the hand is a normal placement attempt, not a menu click.
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) return;

        if (held.is(KingdomConstructionRegistry.BUILDERS_HAMMER.get())) {
            if (project == null) {
                player.sendSystemMessage(Component.literal("[Строительство] Выбери здание на чертёжном столе и создай проект."));
            } else if (!project.owner().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("[Строительство] Помогать молотом на этой стройке пока может только владелец проекта."));
            } else {
                var step = KingdomConstructionWork.placeNext(player.getServer(), level, project);
                var progress = data.progress(project.id());
                var design = KingdomConstructionPlans.resolve(player.getServer(),project.design());
                int total = design == null ? 0 : design.blocks().size();
                player.sendSystemMessage(Component.literal("[Строительство] " + step.result().message()
                    + " — " + progress + "/" + total
                    + (step.target() == null ? "" : " (" + step.target().toShortString() + ")")));
                if (step.result() == KingdomConstructionWork.Result.PLACED && !player.isCreative()) {
                    held.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(event.getHand()));
                }
            }
        } else if (project != null
                && held.is(KingdomConstructionRegistry.BUILDING_BLUEPRINT.get())) {
            if (!project.owner().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("[Строительство] Этот чертёж принадлежит другому владельцу."));
            } else {
                var step = KingdomConstructionWork.next(player.getServer(), project);
                int count = KingdomConstructionPreview.show(player, project);
                player.sendSystemMessage(Component.literal("[Чертёж] 3D-контур: "+count
                    +" недостроенных блоков (точки из частиц, до 144 за просмотр). "
                    + (step.target() == null ? step.result().message()
                        : "Следующий: "+step.material()+", "+step.target().toShortString())));
            }
        } else if (project != null) {
            if (project.owner().equals(player.getUUID()))
                KingdomMainMenu.openSite(player, project.id());
            else
                player.sendSystemMessage(Component.literal("[Строительство] Чужая строительная площадка."));
        } else {
            KingdomMainMenu.openArchitect(player);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    @SubscribeEvent public void bankCounter(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(BindingInteractionPolicy.allowsPlacement(event.getItemStack()))return;
        if(!KingdomBankData.get(player.getServer()).branch(player.serverLevel(),event.getPos()))return;
        KingdomMainMenu.openBank(player);
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    /** Registered warehouse counter: sneak-right-click opens the order desk instead of its inventory. */
    @SubscribeEvent public void warehouseCounter(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown())return;
        if(BindingInteractionPolicy.allowsPlacement(event.getItemStack()))return;
        if(!KingdomWarehouseMarket.get(player.getServer()).isDesk(player.serverLevel(),event.getPos()))return;
        KingdomMainMenu.openWarehouse(player);
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    /** Local trade is opened only from an existing BUY notice at a registered counter.
     * No polling, automatic chunk loads, or arbitrary-item test pricing.
     * A second, already loaded container directly beside the counter receives goods.
     */
    @SubscribeEvent public void simplePhysicalTrade(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) return;
        var level = player.serverLevel();
        var position = event.getPos();
        var enterprises = KingdomEnterpriseData.get(player.getServer());
        for (var firm : enterprises.all()) {
            var counter = enterprises.counter(firm.id());
            if (counter == null || !counter.dimension().equals(level.dimension().location().toString())
                    || !counter.pos().equals(position) || !enterprises.atCounter(player, firm.id())) continue;
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            if (!(level.getBlockEntity(position) instanceof net.minecraft.world.Container cashbox)) return;
            net.minecraft.world.Container warehouse = null;
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var adjacent = position.relative(direction);
                if (!level.hasChunkAt(adjacent)) continue;
                if (level.getBlockEntity(adjacent) instanceof net.minecraft.world.Container found && found != cashbox) {
                    warehouse = found;
                    break;
                }
            }
            if (warehouse == null) {
                player.sendSystemMessage(Component.literal("[Торговля] Рядом с кассой нужен складской контейнер."));
                return;
            }
            try {
                var path = player.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("data").resolve("kingdom_trade_notices.dat");
                var board = SharedTradeNoticeBoards.get(path);
                var notices = board.offers(firm.id(), SimpleTradeNoticeBoard.Side.BUY);
                if (notices.isEmpty()) {
                    player.sendSystemMessage(Component.literal("[Торговля] У предприятия нет заявок на покупку."));
                    return;
                }
                // Select only the item held by the player. The notice determines its ID and price.
                var heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(player.getMainHandItem().getItem()).toString();
                var match = notices.stream().filter(n -> n.itemId().equals(heldId)).findFirst();
                if (match.isEmpty()) {
                    player.sendSystemMessage(Component.literal("[Торговля] В руке нет товара из заявок предприятия."));
                    return;
                }
                var boundWarehouse = warehouse;
                var boundCashbox = cashbox;
                boolean opened = KingdomSimpleTradeMenu.openForNotice(player, board, match.get().id(),
                        firm.id(), boundWarehouse, boundCashbox, KingdomCurrency.COPPER.get(),
                        () -> enterprises.atCounter(player, firm.id())
                                && level.getBlockEntity(position) == boundCashbox);
                if (!opened) player.sendSystemMessage(Component.literal("[Торговля] Заявка недоступна."));
            } catch (java.io.IOException | IllegalArgumentException e) {
                player.sendSystemMessage(Component.literal("[Торговля] Не удалось прочитать заявки."));
            }
            return;
        }
    }
    @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { BINDING.remove(e.getEntity().getUUID());EnterpriseAccessService.cancel(e.getEntity().getUUID());AdministrativeAccessService.cancel(e.getEntity().getUUID());KingdomConstructionPreview.clear(e.getEntity().getUUID()); }
    @SubscribeEvent public void bindAdministrativeTarget(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!AdministrativeAccessService.hasSession(player.getUUID()))return;
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) {
            AdministrativeAccessService.cancel(player.getUUID());
            return;
        }
        var status=AdministrativeAccessService.complete(player,player.serverLevel(),event.getPos());
        player.sendSystemMessage(Component.literal("[Project Kingdom] Привязка городской службы: "+status));
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    @SubscribeEvent public void bindEnterpriseContainer(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!EnterpriseAccessService.hasSession(player.getUUID()))return;
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) {
            EnterpriseAccessService.cancel(player.getUUID());
            return;
        }
        var status=EnterpriseAccessService.complete(player,player.serverLevel(),event.getPos());
        player.sendSystemMessage(Component.literal("[Project Kingdom] Привязка предприятия: "+status));
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    @SubscribeEvent public void bindChest(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        BindRequest request=BINDING.get(p.getUUID());
        if(request==null)return;
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) {
            BINDING.remove(p.getUUID());
            return;
        }
        if (!request.dimension().equals(p.level().dimension().location().toString())) {
            BINDING.remove(p.getUUID());return;
        }
        KingdomNpcData db=KingdomNpcData.get(p.getServer());
        KingdomNpcData.Worker w=db.workers.get(request.npcId());
        if(w==null || !(p.hasPermissions(2)||p.getUUID().equals(w.employer))) {
            BINDING.remove(p.getUUID());p.sendSystemMessage(Component.literal("[Королевство] Нет прав на привязку."));return;
        }
        ServerLevel level=p.serverLevel(); BlockPos pos=event.getPos();
        if(!request.role().equals("workstation") && !NpcLogistics.isContainer(level,pos)){
            p.sendSystemMessage(Component.literal("[Королевство] Выбери настоящий контейнер."));return;
        }
        if(request.role().equals("workstation") && level.getBlockState(pos).isAir()){
            p.sendSystemMessage(Component.literal("[Королевство] Выбери блок оборудования."));return;
        }
        KingdomTerritories territories=KingdomTerritories.get(p.getServer());
        String role=request.role();
        if(!territories.register(level,pos,p.getUUID(),role)){
            p.sendSystemMessage(Component.literal("[Королевство] Склад не привязан: сначала зарегистрируй свой участок командой /knpc claim здесь. Чужие участки запрещены."));
            return;
        }
        if(role.equals("raw")) {w.hasSource=true;w.sourceX=pos.getX();w.sourceY=pos.getY();w.sourceZ=pos.getZ();}
        else if(role.equals("finished")) {w.hasDestination=true;w.destX=pos.getX();w.destY=pos.getY();w.destZ=pos.getZ();}
        else {w.hasWorkstation=true;w.workX=pos.getX();w.workY=pos.getY();w.workZ=pos.getZ();}
        db.setDirty();BINDING.remove(p.getUUID());
        p.sendSystemMessage(Component.literal("[Королевство] "+(role.equals("raw")?"Склад сырья":role.equals("finished")?"Склад продукции":"Рабочая станция")+" назначен(а): "+pos.toShortString()));
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }

    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[Kingdom NPC] " + message), false);
    }

    private static KingdomNpcData.Worker find(CommandSourceStack source, int id) {
        return KingdomNpcData.get(source.getServer()).workers.get(id);
    }

    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static boolean canEdit(CommandSourceStack source, KingdomNpcData.Worker worker) {
        return source.hasPermission(2) || (source.getEntity() instanceof ServerPlayer p && p.getUUID().equals(worker.employer));
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        KingdomExpansionCommands.register(event);
        var root = Commands.literal("knpc");
        root.then(Commands.literal("village")
            .then(Commands.literal("audit").executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                LivingVillageAudit.report(p);
                return 1;
            }))
            .then(Commands.literal("status").executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                VillageStatus.report(p); return 1;
            }))
            .then(Commands.literal("activate").executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                VillageWorkActivation.activate(p); return 1;
            })));
        // Physical warehouse counter: orders cannot be purchased remotely.
        root.then(Commands.literal("warehouse")
            .then(Commands.literal("register").executes(ctx -> {
                reply(ctx.getSource(),"Старые логистические стойки сохранены только для миграции. Установите блок городского рынка.");return 0;
            })));
        // Physical market: sellers register a chest, buyers inspect live stock and prices.
        root.then(Commands.literal("market")
            .then(Commands.literal("register").then(Commands.argument("price",IntegerArgumentType.integer(1,100000)).executes(ctx -> {
                ServerPlayer p=player(ctx.getSource());
                if(!KingdomMainMenu.isNearAdministrative(p,ru.projectkingdom.administration.AdministrativeKind.CITY_MARKET)){
                    reply(ctx.getSource(),"Торговый склад можно выставить только у активного городского рынка.");return 0;}
                BlockPos pos=p.blockPosition().below();
                boolean ok=KingdomWarehouseMarket.get(p.getServer()).register(p.serverLevel(),pos,p.getUUID(),IntegerArgumentType.getInteger(ctx,"price"));
                reply(ctx.getSource(),ok?"Торговый сундук зарегистрирован: "+pos.toShortString():"Встань на свой зарегистрированный сундук на своей территории.");
                return ok?1:0;
            })))
            .then(Commands.literal("remove").executes(ctx -> {
                ServerPlayer p=player(ctx.getSource());
                boolean ok=KingdomWarehouseMarket.get(p.getServer()).remove(p.serverLevel(),p.blockPosition().below(),p.getUUID());
                reply(ctx.getSource(),ok?"Торговый сундук снят с продажи.":"Нет твоего торгового сундука под ногами.");return ok?1:0;
            }))
            .then(Commands.literal("find")
                .then(Commands.argument("item",StringArgumentType.word())
                    .then(Commands.argument("count",IntegerArgumentType.integer(1,2304)).executes(ctx -> {
                        ServerPlayer p=player(ctx.getSource());
                        String item=StringArgumentType.getString(ctx,"item");
                        int count=IntegerArgumentType.getInteger(ctx,"count");
                        var marketTown=KingdomMainMenu.nearbyAdministrativeTown(p,ru.projectkingdom.administration.AdministrativeKind.CITY_MARKET);
                        if(marketTown==null){reply(ctx.getSource(),"Подойди к активному городскому рынку.");return 0;}
                        var quotes=KingdomWarehouseMarket.get(p.getServer()).cityMarketSearch(p.serverLevel(),marketTown,item,count);
                        if(quotes.isEmpty()) {reply(ctx.getSource(),"Нет загруженных складов с таким количеством товара.");return 0;}
                        for(int i=0;i<Math.min(8,quotes.size());i++) {
                            var q=quotes.get(i);
                            reply(ctx.getSource(),"#"+i+" | Продавец "+q.stall().owner()+" | "+q.stall().pos().toShortString()+" | остаток "+q.available()+" | цена за заказ "+q.totalCopper()+" медных");
                        }
                        return quotes.size();
                    })))));
        // Checkout requires proximity to a registered warehouse counter; quoteIndex is 0-based
        // from /knpc market find. This does not yet dispatch an NPC or settle the seller.
        root.then(Commands.literal("supply")
            .then(Commands.literal("order")
                .then(Commands.argument("item",StringArgumentType.word())
                    .then(Commands.argument("count",IntegerArgumentType.integer(1,2304))
                        .then(Commands.argument("quoteIndex",IntegerArgumentType.integer(0,100))
                            .executes(ctx -> {
                                ServerPlayer p=player(ctx.getSource());
                                String item=StringArgumentType.getString(ctx,"item");
                                int count=IntegerArgumentType.getInteger(ctx,"count");
                                int index=IntegerArgumentType.getInteger(ctx,"quoteIndex");
                                if(!KingdomMainMenu.isNearAdministrative(p,ru.projectkingdom.administration.AdministrativeKind.CITY_MARKET)){
                                    reply(ctx.getSource(),"Подойди к активному городскому рынку.");return 0;}
                                var order=KingdomWarehouseMarket.get(p.getServer()).checkout(
                                    p,item,count,index,p.blockPosition().below(),10);
                                reply(ctx.getSource(),order==null
                                    ?"Заказ не оформлен: проверь товар, остатки, свой сундук и наличные."
                                    :"Заказ "+order.id+" оплачен: "+order.goodsPrice+" за товар + "+order.deliveryFee
                                        +" за доставку. Средства зарезервированы; курьер пока не назначен.");
                                return order==null?0:1;
                            }))))));
        // Safe escrow operations: actual player wallets and proximity to physical trade point.
        root.then(Commands.literal("supply")
            .then(Commands.literal("refund").then(Commands.argument("id",StringArgumentType.word()).executes(ctx -> {
                ServerPlayer p=player(ctx.getSource());
                try {
                    var orders=KingdomSupplyOrders.get(p.getServer());
                    boolean ok=orders.refund(java.util.UUID.fromString(StringArgumentType.getString(ctx,"id")),p);
                    if(ok)p.getInventory().setChanged();
                    reply(ctx.getSource(),ok?"Наличные возвращены в кошелёк; заказ отменён.":"Возврат невозможен: заказ уже в работе, оплачен продавцу или кошелёк недоступен.");
                    return ok?1:0;
                } catch(IllegalArgumentException ex){reply(ctx.getSource(),"Неверный номер заказа.");return 0;}
            })))
            .then(Commands.literal("seller-collect").then(Commands.argument("id",StringArgumentType.word()).executes(ctx -> {
                ServerPlayer p=player(ctx.getSource());
                try {
                    var orders=KingdomSupplyOrders.get(p.getServer());
                    var o=orders.find(java.util.UUID.fromString(StringArgumentType.getString(ctx,"id")));
                    if(o==null || !o.dimension.equals(p.serverLevel().dimension().location().toString())
                            || p.distanceToSqr(o.source.getX()+0.5,o.source.getY()+0.5,o.source.getZ()+0.5)>64) {
                        reply(ctx.getSource(),"Продавец должен находиться рядом со своим торговым сундуком.");return 0;
                    }
                    boolean ok=orders.collectGoodsCash(o.id,p);
                    if(ok)p.getInventory().setChanged();
                    reply(ctx.getSource(),ok?"Оплата за товар выдана наличными. Товар теперь можно забирать.":"Получение оплаты недоступно или уже выполнено.");
                    return ok?1:0;
                }catch(IllegalArgumentException ex){reply(ctx.getSource(),"Неверный номер заказа.");return 0;}
            })))
            .then(Commands.literal("status").then(Commands.argument("id",StringArgumentType.word()).executes(ctx -> {
                ServerPlayer p=player(ctx.getSource());
                try {
                    var o=KingdomSupplyOrders.get(p.getServer()).find(java.util.UUID.fromString(StringArgumentType.getString(ctx,"id")));
                    if(o==null || (!o.buyer.equals(p.getUUID())&&!o.seller.equals(p.getUUID())))return 0;
                    reply(ctx.getSource(),"Заказ "+o.id+": "+o.state+"; забрано "+o.pickedUp+"/"+o.count+"; доставлено "+o.delivered+"; резерв товара "+o.goodsEscrow+"; доставки "+o.deliveryEscrow);
                    return 1;
                }catch(IllegalArgumentException ex){reply(ctx.getSource(),"Неверный номер заказа.");return 0;}
            }))));
        // Operator-only recovery: never invents missing items or money.
        root.then(Commands.literal("supply-recovery").requires(source -> source.hasPermission(2))
            .then(Commands.literal("inspect").then(Commands.argument("id",StringArgumentType.word()).executes(ctx -> {
                try {
                    var o=KingdomSupplyOrders.get(ctx.getSource().getServer()).find(UUID.fromString(StringArgumentType.getString(ctx,"id")));
                    if(o==null){reply(ctx.getSource(),"Заказ не найден.");return 0;}
                    reply(ctx.getSource(),"Заказ "+o.id+" ["+o.state+"] "+o.itemId+" x"+o.count
                        +"; продавец "+o.source+"; покупатель "+o.destination+"; измерение "+o.dimension
                        +"; курьер "+o.courier+"; получено "+o.pickedUp+"; доставлено "+o.delivered
                        +"; журнал "+o.pendingKind+" x"+o.pendingQuantity
                        +"; до операции "+o.pendingPickedBefore+"/"+o.pendingDeliveredBefore
                        +". Проверьте реальные сундуки, NPC и повозку перед восстановлением.");
                    return 1;
                }catch(IllegalArgumentException ex){reply(ctx.getSource(),"Неверный UUID.");return 0;}
            })))
            .then(Commands.literal("resolve").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("kind",StringArgumentType.word())
                    .then(Commands.argument("actualDelta",IntegerArgumentType.integer(0,2304)).executes(ctx -> {
                        try {
                            var kind=KingdomSupplyOrders.TransferKind.valueOf(StringArgumentType.getString(ctx,"kind").toUpperCase(Locale.ROOT));
                            var orders=KingdomSupplyOrders.get(ctx.getSource().getServer());
                            UUID id=UUID.fromString(StringArgumentType.getString(ctx,"id"));
                            int delta=IntegerArgumentType.getInteger(ctx,"actualDelta");
                            boolean ok=orders.reconcile(id,kind,delta);
                            reply(ctx.getSource(),ok?"Журнал сверки закрыт. Физические предметы не изменены."
                                :"Отказ: неверные данные или счётчики. Ничего не изменено.");
                            return ok?1:0;
                        }catch(IllegalArgumentException ex){reply(ctx.getSource(),"Неверный UUID или тип PICKUP/DELIVERY.");return 0;}
                    }))))));
        // Admin diagnostics and operator-verified acknowledgement only. No replay, money minting,
        // inventory reconstruction, or automatic crash recovery.
        root.then(Commands.literal("economy-recovery").requires(source -> source.hasPermission(4))
            .then(Commands.literal("list").executes(ctx -> {
                var finance=KingdomEnterpriseData.get(ctx.getSource().getServer());
                int count=0;
                for(var trade:finance.trades())if(trade.state()!=KingdomEnterpriseData.TradeState.SETTLED){
                    reply(ctx.getSource(),"ТРЕБУЕТ СВЕРКИ: "+trade.id()+" покупатель "+trade.buyer()
                        +" продавец "+trade.seller()+" "+trade.count()+" шт.; цена "+trade.copper()+" мед.");
                    count++;
                }
                if(count==0)reply(ctx.getSource(),"Незакрытых сделок нет.");
                return count;
            }))
            .then(Commands.literal("inspect").then(Commands.argument("id",StringArgumentType.word()).executes(ctx -> {
                try {
                    UUID id=UUID.fromString(StringArgumentType.getString(ctx,"id"));
                    var result=KingdomEnterpriseData.get(ctx.getSource().getServer())
                            .assessTrade(ctx.getSource().getLevel(),id);
                    reply(ctx.getSource(),"Сверка "+id+": "+result
                            +". BEFORE — до сделки, AFTER — после, MIXED_OR_CHANGED — расхождение; "
                            +"LEGACY_UNVERIFIABLE — старый формат. Команда ничего не изменяет.");
                    return 1;
                }catch(IllegalArgumentException ex){reply(ctx.getSource(),"Неверный UUID.");return 0;}
            })))
            .then(Commands.literal("ack").then(Commands.argument("id",StringArgumentType.word()).executes(ctx -> {
                try {
                    UUID id=UUID.fromString(StringArgumentType.getString(ctx,"id"));
                    var finance=KingdomEnterpriseData.get(ctx.getSource().getServer());
                    var assessment=finance.assessTrade(ctx.getSource().getLevel(),id);
                    if(assessment!=TradeWriteAheadJournal.RecoveryAssessment.AFTER){
                        reply(ctx.getSource(),"Отказ: фактические деньги и товары не соответствуют состоянию "
                                +"после сделки ("+assessment+"). Нужна ручная проверка; ничего не изменено.");
                        return 0;
                    }
                    boolean ok=finance.acknowledgeVerified(ctx.getSource().getLevel(),id);
                    reply(ctx.getSource(),ok?"Сверка подтверждена администратором; предметы и деньги НЕ изменены."
                        :"Невозможно закрыть: ID неизвестен либо нет статуса NEEDS_RECONCILIATION.");
                    return ok?1:0;
                }catch(IllegalArgumentException ex){reply(ctx.getSource(),"Неверный UUID.");return 0;}
            }))));
        // Optional, read-only MineColonies discovery. Never modifies citizens or colony ownership.
        root.then(Commands.literal("colonies").executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                reply(ctx.getSource(), "Команда доступна только игроку."); return 0;
            }
            MineColoniesBridge.reportNearby(player);
            return 1;
        }));

        // A bank counter is a real lodestone registered at its physical location.
        root.then(Commands.literal("bank").then(Commands.literal("register").executes(ctx -> {
            reply(ctx.getSource(),"Регистрация старых Lodestone-банков отключена. Установите блок банка; старые записи сохраняются для миграции.");return 0;
        })));
        root.then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
            .then(Commands.argument("job", StringArgumentType.word()).suggests((c,b) -> {
                for (String job : new String[]{"free", "farmer", "smith", "miner", "lumberjack", "merchant", "guard", "builder"}) b.suggest(job);
                return b.buildFuture();
            }).executes(ctx -> {
                CommandSourceStack src = ctx.getSource();
                String job;
                try { job = NpcEconomy.normalizeJob(StringArgumentType.getString(ctx, "job")); }
                catch (IllegalArgumentException ex) { reply(src, ex.getMessage()); return 0; }
                KingdomNpcData db = KingdomNpcData.get(src.getServer());
                if (db.workers.size() >= MAX_NPCS) { reply(src, "Alpha NPC limit reached: " + MAX_NPCS); return 0; }
                ServerLevel level = src.getLevel();
                Villager npc = EntityType.VILLAGER.create(level);
                if (npc == null) return 0;
                var pos = src.getPosition();
                npc.moveTo(pos.x, pos.y, pos.z, 0, 0);
                npc.setCustomName(Component.literal("Kingdom " + job));
                npc.setCustomNameVisible(true);
                npc.setPersistenceRequired();
                npc.setVillagerData(npc.getVillagerData().setProfession(profession(job)));
                if (!level.addFreshEntity(npc)) { reply(src, "Failed to spawn villager"); return 0; }
                var created = db.add(npc.getUUID(), "Kingdom " + job, level.dimension().location().toString(), level.getDayTime() / TICKS_PER_DAY);
                reply(src, "NPC #" + created.id + " created; job=" + job);
                return 1;
            })));
        root.then(Commands.literal("integrations").executes(ctx -> {
            reply(ctx.getSource(), KingdomNpcIntegrations.report()); return 1;
        }));
        root.then(Commands.literal("identity").then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(ctx -> {
            var w=find(ctx.getSource(), IntegerArgumentType.getInteger(ctx,"id"));
            if (w==null) return 0;
            reply(ctx.getSource(), "#"+w.id+" gender="+w.gender+" age="+w.ageYears+" stage="+NpcIdentity.stage(w.ageYears)
                +" origin="+w.originMod+" workplace="+w.workplaceMod+":"+w.workplaceId);
            return 1;
        })));
        root.then(Commands.literal("gender").requires(src -> src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1))
            .then(Commands.argument("value",StringArgumentType.word()).executes(ctx -> {
