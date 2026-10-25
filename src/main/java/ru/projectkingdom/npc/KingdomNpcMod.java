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
import net.minecraftforge.event.ServerChatEvent;
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
        new KingdomFemaleOutfitSync();
        MinecraftForge.EVENT_BUS.register(new KingdomBookGuardian());
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Register one of several central-warehouse rooms using two server-verified corners. */
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void selectWarehouseCorners(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var warehouse=ru.projectkingdom.administration.KingdomCentralWarehouseRooms.get(player.getServer());
        if(!warehouse.hasSession(player.getUUID()))return;
        if(!event.getItemStack().isEmpty()){
            ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
            player.sendSystemMessage(Component.literal("[Центральный склад] Освободите руку и начните разметку заново."));
        }else{
            var outcome=warehouse.select(player,event.getPos());
            player.sendSystemMessage(Component.literal("[Центральный склад] "+ru.projectkingdom.administration.KingdomCentralWarehouseRooms.message(outcome)));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }

    /** Protected municipal bank vault: mark opposite corners with an empty-hand click. */
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void selectBankVaultCorners(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var survey=ru.projectkingdom.administration.KingdomBankVaultRooms.get(player.getServer());
        if(!survey.hasSession(player.getUUID()))return;
        if(!event.getItemStack().isEmpty()){
            ru.projectkingdom.administration.KingdomBankVaultRooms.cancel(player.getUUID());
            player.sendSystemMessage(Component.literal("[Банк] Для разметки освободите руку, затем нажмите кнопку разметки заново."));
        }else{
            var result=survey.select(player,event.getPos());
            player.sendSystemMessage(Component.literal("[Банк] "+ru.projectkingdom.administration.KingdomBankVaultRooms.message(result)));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    /** Room/building surveying is a private two-click server session. */
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void selectRentalCorners(PlayerInteractEvent.RightClickBlock event) {
        ru.projectkingdom.property.KingdomRentalSelection.handle(event);
    }

    /** Blueprint copying: two right-clicked corners on an owned, loaded building. */
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void chooseBlueprintCopyCorners(PlayerInteractEvent.RightClickBlock event) {
        KingdomStructureCopy.handle(event);
    }

    // Farmer field selection is bound to a specific owned farm and right-clicked soil.
    /** Capture only drops from Kingdom's explicitly permitted, synchronous TFC culling. */
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onOwnedLivestockDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event){
        KingdomTfcButcheryWork.onRealDrops(event);
    }

    @SubscribeEvent public void chooseFarmCorner(PlayerInteractEvent.RightClickBlock event) {
        KingdomFarmSelection.select(event);
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
        // The construction enterprise core remains an enterprise first:
        // ordinary right-click opens its existing management UI. The menu's
        // Architect button launches the draft workflow using this core.
        if (level.getBlockState(marker).is(
                ru.projectkingdom.enterprise.KingdomEnterpriseRegistry.CONSTRUCTION.get())
                && !held.is(KingdomConstructionRegistry.BUILDERS_HAMMER.get())
                && !held.is(KingdomConstructionRegistry.BUILDING_BLUEPRINT.get()))
            return;
        // A block held in the hand is a normal placement attempt, not a menu click.
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) return;

        if (held.is(KingdomConstructionRegistry.BUILDERS_HAMMER.get())) {
            if (project == null) {
                player.sendSystemMessage(Component.literal("[Строительство] Выбери здание на чертёжном столе и создай проект."));
            } else if (!data.mayAssist(project.id(), player.getUUID())) {
                player.sendSystemMessage(Component.literal("[Строительство] Нет разрешения: попроси владельца пригласить тебя в строительную бригаду."));
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
            if (!data.mayAssist(project.id(), player.getUUID())) {
                player.sendSystemMessage(Component.literal("[Строительство] Чужой чертёж: приглашение строителя отсутствует."));
            } else {
                var step = KingdomConstructionWork.next(player.getServer(), project);
                int count = KingdomConstructionPreview.show(player, project);
                player.sendSystemMessage(Component.literal("[Чертёж] 3D-контур: "+count
                    +" недостроенных блоков (точки из частиц, до 144 за просмотр). "
                    + (step.target() == null ? step.result().message()
                        : "Следующий: "+step.material()+", "+step.target().toShortString())));
            }
        } else if (project != null) {
            if (data.mayAssist(project.id(), player.getUUID()))
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
    @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { BINDING.remove(e.getEntity().getUUID());KingdomFarmSelection.cancel(e.getEntity().getUUID());ru.projectkingdom.property.KingdomRentalSelection.cancel(e.getEntity().getUUID());EnterpriseAccessService.cancel(e.getEntity().getUUID());AdministrativeAccessService.cancel(e.getEntity().getUUID());ru.projectkingdom.administration.KingdomBankVaultRooms.cancel(e.getEntity().getUUID());ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(e.getEntity().getUUID());KingdomConstructionPreview.clear(e.getEntity().getUUID());KingdomArchitectSessions.cancel(e.getEntity().getUUID());KingdomArchitectCatalog.close(e.getEntity().getUUID());KingdomStructureCopy.cancel(e.getEntity().getUUID()); }
    @SubscribeEvent public void bindAdministrativeTarget(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!AdministrativeAccessService.hasSession(player.getUUID()))return;
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) {
            AdministrativeAccessService.cancel(player.getUUID());
            player.sendSystemMessage(Component.literal("[Город] Привязка отменена: вы держите строительный блок. Освободите руку и начните заново."));
            return;
        }
        String role=AdministrativeAccessService.pendingRole(player.getUUID());
        var status=AdministrativeAccessService.complete(player,player.serverLevel(),event.getPos());
        player.sendSystemMessage(Component.literal("[Город] "+role+": "
                +AdministrativeAccessService.explanation(status)));
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    @SubscribeEvent public void bindEnterpriseContainer(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!EnterpriseAccessService.hasSession(player.getUUID()))return;
        if (BindingInteractionPolicy.allowsPlacement(event.getItemStack())) {
            EnterpriseAccessService.cancel(player.getUUID());
            player.sendSystemMessage(Component.literal("[Предприятие] Привязка отменена: у вас в руке блок. Уберите его и начните привязку заново."));
            return;
        }
        String role=EnterpriseAccessService.pendingRole(player.getUUID());
        var status=EnterpriseAccessService.complete(player,player.serverLevel(),event.getPos());
        player.sendSystemMessage(Component.literal("[Предприятие] "+role+": "
                +EnterpriseAccessService.explanation(status)));
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
        event.getDispatcher().register(Commands.literal("kingdomadmin")
                .requires(source->source.hasPermission(2))
                .executes(ctx->{
                    var operator=ctx.getSource().getPlayerOrException();
                    ru.projectkingdom.administration.KingdomAdminEnterpriseMenu.open(operator);
                    return 1;
                }));
        var root = Commands.literal("knpc");
        root.then(Commands.literal("talk")
            .then(Commands.argument("id", IntegerArgumentType.integer(1))
            .then(Commands.argument("phrase", StringArgumentType.greedyString()).executes(ctx -> {
                ServerPlayer speaker=player(ctx.getSource());
                var worker=find(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"id"));
                boolean ok=NpcDialogueService.respond(speaker,worker,StringArgumentType.getString(ctx,"phrase"));
                if(!ok)reply(ctx.getSource(),"NPC не найден, слишком далеко или реплика пуста.");
                return ok?1:0;
            }))));
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
                var src=ctx.getSource(); var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
                if(w==null)return 0;
                try{w.gender=NpcIdentity.gender(StringArgumentType.getString(ctx,"value"));}
                catch(IllegalArgumentException ex){reply(src,ex.getMessage());return 0;}
                KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Identity updated");return 1;
            }))));
        root.then(Commands.literal("age").requires(src -> src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1))
            .then(Commands.argument("years",IntegerArgumentType.integer(0,120)).executes(ctx -> {
                var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));if(w==null)return 0;
                if(w.employer!=null && IntegerArgumentType.getInteger(ctx,"years")<18){reply(src,"End employment before changing to minor");return 0;}
                w.ageYears=IntegerArgumentType.getInteger(ctx,"years");w.ageDaysAccumulated=0;
                w.ageAnchorDay=src.getServer().overworld().getDayTime()/TICKS_PER_DAY;
                KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Age updated");return 1;
            }))));
        root.then(Commands.literal("workplace").requires(src -> src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1))
            .then(Commands.argument("mod",StringArgumentType.word())
            .then(Commands.argument("location",StringArgumentType.word()).executes(ctx -> {
                var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));if(w==null)return 0;
                String mod=StringArgumentType.getString(ctx,"mod");
                if(!mod.equals("kingdomnpc") && !KingdomNpcIntegrations.installed(mod)){
                    reply(src,"Integration mod not installed: "+mod);return 0;
                }
                w.workplaceMod=mod; w.workplaceId=StringArgumentType.getString(ctx,"location");
                KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Workplace reference stored (no external tasks executed)");return 1;
            })))));
        root.then(Commands.literal("order").then(Commands.argument("id", IntegerArgumentType.integer(1))
            .then(Commands.argument("task", StringArgumentType.word()).suggests((c,b) -> {
                for (String task: new String[]{"wander","stay","follow","work","home","haul"}) b.suggest(task);
                return b.buildFuture();
            }).executes(ctx -> {
                var src = ctx.getSource(); var w = find(src,IntegerArgumentType.getInteger(ctx,"id"));
                if (w==null || !canEdit(src,w)) { reply(src,"No permission or NPC missing"); return 0; }
                String order;
                try { order=NpcSimulation.normalizeOrder(StringArgumentType.getString(ctx,"task")); }
                catch (IllegalArgumentException e) { reply(src,e.getMessage()); return 0; }
                if (order.equals("work") && (w.employer==null || w.contractPaused)) {reply(src,"An active contract is required");return 0;}
                if (order.equals("haul") && (!w.hasSource || !w.hasDestination)) {reply(src,"Link source and destination chests first");return 0;}
                if (order.equals("home") && !w.hasHome) {reply(src,"Home not assigned");return 0;}
                w.order=order; KingdomNpcData.get(src.getServer()).setDirty();
                reply(src,"Order for #"+w.id+": "+order); return 1;
            }))));
        root.then(Commands.literal("sethome").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
            var src=ctx.getSource(); var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
            if (w==null || !canEdit(src,w)) {reply(src,"No permission");return 0;}
            if (!w.dimension.equals(src.getLevel().dimension().location().toString())) {reply(src,"Wrong dimension");return 0;}
            BlockPos pos=BlockPos.containing(src.getPosition());
            w.homeX=pos.getX();w.homeY=pos.getY();w.homeZ=pos.getZ();w.hasHome=true;
            KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Home saved for #"+w.id);return 1;
        })));
        root.then(Commands.literal("stock").then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(ctx -> {
            var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
            if (w==null){reply(src,"NPC not found");return 0;}
            reply(src,"#"+w.id+" virtual stock="+w.produced+", workdays="+w.workUnits+", XP="+w.skillXp);
            return 1;
        })));
        root.then(Commands.literal("needs").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
            var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
            if(w==null){reply(src,"NPC not found");return 0;}
            reply(src,"#"+w.id+" hunger="+w.hunger+" energy="+w.energy+" morale="+w.morale+" safety="+w.safety
                +" housing="+w.hasHome+" settlement="+w.settlement+" family="+w.familyName);
            return 1;
        })));
        root.then(Commands.literal("settlement").then(Commands.argument("id", IntegerArgumentType.integer(1))
            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
                if(w==null || !canEdit(src,w)){reply(src,"No permission");return 0;}
                w.settlement=StringArgumentType.getString(ctx,"name");
                KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Settlement assigned: "+w.settlement);return 1;
            }))));
        root.then(Commands.literal("family").requires(src -> src.hasPermission(2))
            .then(Commands.argument("id", IntegerArgumentType.integer(1))
            .then(Commands.argument("surname", StringArgumentType.word()).executes(ctx -> {
                var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
                if(w==null){reply(src,"NPC missing");return 0;}
                w.familyName=StringArgumentType.getString(ctx,"surname");
                KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Family set: "+w.familyName);return 1;
            }))));
        root.then(Commands.literal("feed").then(Commands.argument("id",IntegerArgumentType.integer(1))
            .then(Commands.argument("amount",IntegerArgumentType.integer(1,5)).executes(ctx -> {
                var src=ctx.getSource();var p=player(src);var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
                if(w==null){reply(src,"NPC missing");return 0;}
                int amount=IntegerArgumentType.getInteger(ctx,"amount");
                if(p.getInventory().countItem(Items.BREAD)<amount){reply(src,"Not enough bread");return 0;}
                int remaining=amount;
                for(int slot=0;slot<p.getInventory().getContainerSize()&&remaining>0;slot++){
                    ItemStack stack=p.getInventory().getItem(slot);
                    if(stack.is(Items.BREAD)){int take=Math.min(remaining,stack.getCount());stack.shrink(take);remaining-=take;}
                }
                w.hunger=NpcLife.feed(w.hunger,amount);
                KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Fed NPC #"+w.id+" hunger="+w.hunger);return 1;
            }))));
        root.then(Commands.literal("list").executes(ctx -> {
            var src = ctx.getSource();
            var db = KingdomNpcData.get(src.getServer());
            reply(src, "NPC count=" + db.workers.size());
            for (var w : db.all()) reply(src, "#" + w.id + " " + w.name + " job=" + w.job + " employer=" + (w.employer == null ? "none" : w.employer) + " paused=" + w.contractPaused + " order=" + w.order);
            return 1;
        }));
        root.then(Commands.literal("info").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
            var src = ctx.getSource(); var w = find(src, IntegerArgumentType.getInteger(ctx,"id"));
            if (w == null) {reply(src,"NPC not found");return 0;}
            reply(src, "#"+w.id+" gender="+w.gender+" age="+w.ageYears+" stage="+NpcIdentity.stage(w.ageYears)+" job="+w.job+" wage="+w.dailyWage+" copper/day; earned="+w.earned+"; missed="+w.unpaidDays+"; paused="+w.contractPaused+" order="+w.order);
            return 1;
        })));
        root.then(Commands.literal("hire").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
            var src=ctx.getSource(); var p=player(src); var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
            if(w==null || w.employer!=null || !NpcIdentity.canHire(w.ageYears)) {reply(src,"NPC unavailable for hire (must be adult)");return 0;}
            if(KingdomEnterpriseData.get(src.getServer()).owned(p.getUUID())==null){
                reply(src,"Сначала зарегистрируй предприятие и кассу для зарплаты NPC.");return 0;
            }
            w.employer=p.getUUID(); w.lastPaidDay=src.getServer().overworld().getDayTime()/TICKS_PER_DAY;
            w.unpaidDays=0; w.contractPaused=false;
            KingdomNpcData.get(src.getServer()).setDirty(); reply(src,"NPC #"+w.id+" нанят. Зарплата начисляется как долг из бюджета предприятия.");return 1;
        })));
        root.then(Commands.literal("fire").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
            var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
            if(w==null||!canEdit(src,w)) {reply(src,"No permission or unknown NPC");return 0;}
            w.employer=null;w.job="free";w.order="wander";w.contractPaused=false;w.unpaidDays=0;
            KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Contract ended for #"+w.id);return 1;
        })));
        root.then(Commands.literal("job").then(Commands.argument("id",IntegerArgumentType.integer(1))
            .then(Commands.argument("profession",StringArgumentType.word()).executes(ctx -> {
                var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
                if(w==null||!canEdit(src,w)){reply(src,"No permission or unknown NPC");return 0;}
                String job;
                try {job=NpcEconomy.normalizeJob(StringArgumentType.getString(ctx,"profession"));}
                catch(IllegalArgumentException ex){reply(src,ex.getMessage());return 0;}
                w.job=job;KingdomNpcData.get(src.getServer()).setDirty();
                for (ServerLevel level : src.getServer().getAllLevels()) {
                    var e = level.getEntity(w.entityId);
                    if (e instanceof Villager v) v.setVillagerData(v.getVillagerData().setProfession(profession(job)));
                }
                reply(src,"#"+w.id+" profession set to "+job);return 1;
            }))));
        root.then(Commands.literal("wage").then(Commands.argument("id",IntegerArgumentType.integer(1))
            .then(Commands.argument("copper",IntegerArgumentType.integer(1,10000)).executes(ctx -> {
                var src=ctx.getSource();var w=find(src,IntegerArgumentType.getInteger(ctx,"id"));
                if(w==null||!canEdit(src,w)){reply(src,"No permission or unknown NPC");return 0;}
                w.dailyWage=IntegerArgumentType.getInteger(ctx,"copper");
                KingdomNpcData.get(src.getServer()).setDirty();reply(src,"Wage updated");return 1;
            }))));
        // Old NPC payroll reserve is deprecated. The automatic payroll debit was disabled;
        // no new wallet funds should be trapped inside its legacy account.
        root.then(Commands.literal("deposit").then(Commands.argument("copper",IntegerArgumentType.integer(1,2304)).executes(ctx -> {
            reply(ctx.getSource(),"Старый фонд зарплат закрыт для взносов. Используй кассу предприятия в меню.");
            return 0;
        })));
        // Recover only already backed legacy deposits, at a physical bank branch.
        root.then(Commands.literal("refund_legacy").then(Commands.argument("copper",IntegerArgumentType.integer(1,2304)).executes(ctx -> {
            var src=ctx.getSource();var p=player(src);int amount=IntegerArgumentType.getInteger(ctx,"copper");
            if(!KingdomBankData.get(src.getServer()).atBranch(p)){
                reply(src,"Для возврата подойди к банковской стойке.");return 0;
            }
            KingdomNpcData legacy=KingdomNpcData.get(src.getServer());
            long balance=legacy.balance(p.getUUID());
            if(balance<amount){
                reply(src,"Нет такого остатка старого фонда.");return 0;
            }
            if(!KingdomCurrency.credit(p,amount))return 0;
            legacy.accounts.put(p.getUUID(),balance-amount);
            legacy.setDirty();p.getInventory().setChanged();
            reply(src,"Возвращено "+amount+" медных из старого фонда; остаток "+(balance-amount));return 1;
        })));
        root.then(Commands.literal("balance").executes(ctx -> {
            var src=ctx.getSource();reply(src,"NPC payroll reserve="+KingdomNpcData.get(src.getServer()).balance(player(src).getUUID())+" copper");return 1;
        }));
        root.then(Commands.literal("claim").executes(ctx -> {
            var p=player(ctx.getSource());
            boolean ok=KingdomTerritories.get(p.getServer()).claim(p.serverLevel(),p.blockPosition(),p.getUUID());
            reply(ctx.getSource(),ok?"Участок (текущий чанк 16x16) закреплён за вами.":"Этот участок принадлежит другому владельцу.");
            return ok?1:0;
        }));
        root.then(Commands.literal("land").executes(ctx -> {
            var p=player(ctx.getSource());
            var owner=KingdomTerritories.get(p.getServer()).owner(p.serverLevel(),p.blockPosition());
            var civics=KingdomCivicsData.get(p.getServer());
            var plot=civics.plot(p.serverLevel(),p.blockPosition());
            var town=civics.townOf(p.getUUID());
            String legacy=owner==null?"нет":owner.equals(p.getUUID())?"твоя":"чужая";
            String city=plot==null?"не зарегистрирован":(town!=null && plot.town().equals(town.id())?town.name():"другой город")+" / зона "+(plot==null?"нет":plot.zone());
            reply(ctx.getSource(),"Чанк "+(p.blockPosition().getX()>>4)+","+(p.blockPosition().getZ()>>4)+"; старое владение: "+legacy+"; город: "+city);
            return 1;
        }));
        event.getDispatcher().register(root);
    }

    /** Text fallback for voice: only explicit "Имя, фраза" / "#id фраза" is intercepted. */
    @SubscribeEvent
    public void addressedChat(ServerChatEvent event) {
        var server=event.getPlayer().getServer();
        boolean handled=server.isSameThread()
                ? NpcDialogueService.respondAddressed(event.getPlayer(),event.getRawText())
                : server.submit(() -> NpcDialogueService.respondAddressed(
                        event.getPlayer(),event.getRawText())).join();
        if (handled)
            event.setCanceled(true);
    }

    /** Vanilla villager remains interactive; avoid opening its trade UI for tracked workers. */
    @SubscribeEvent
    public void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager npc) || !(event.getEntity() instanceof ServerPlayer p)) return;
        var db=KingdomNpcData.get(p.getServer());
        KingdomNpcData.Worker found=null;
        for (var w:db.all()) if(w.entityId.equals(npc.getUUID())) {found=w;break;}
        // Sneak + right-click an ordinary villager to enrol it in Kingdom NPC.
        // Never overwrite existing contracts and never touch non-vanilla villagers.
        if (found == null && p.isShiftKeyDown()) {
            if (db.workers.size() >= MAX_NPCS) {
                p.sendSystemMessage(Component.literal("[Королевство] Достигнут тестовый лимит NPC: " + MAX_NPCS));
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            String name = npc.hasCustomName() ? npc.getCustomName().getString() : "Житель Королевства";
            found = db.add(npc.getUUID(), name, p.level().dimension().location().toString(),
                    p.serverLevel().getDayTime() / TICKS_PER_DAY);
            npc.setPersistenceRequired();
            npc.setCustomName(Component.literal(name + " #" + found.id));
            npc.setCustomNameVisible(true);
            p.sendSystemMessage(Component.literal("[Королевство] Житель принят в систему, №" + found.id));
        }
        if(found==null)return;
        // Freeze immediately, not after the next simulation update.
        npc.getNavigation().stop();
        npc.setNoAi(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        openNpcMenu(p,found.id);

    }

    public static void openNpcMenu(ServerPlayer player, int npcId) {
        openNpcMenu(player,npcId,false);
    }

    public static void openNpcManagement(ServerPlayer player, int npcId) {
        openNpcMenu(player,npcId,true);
    }

    private static void openNpcMenu(ServerPlayer player, int npcId, boolean management) {
        player.openMenu(new SimpleMenuProvider(
            (windowId, inventory, user) -> new KingdomNpcMenu(
                    windowId, inventory, player, npcId, management),
            Component.literal("Житель Королевства №" + npcId)));
    }

    private static VillagerProfession profession(String job) {
        return switch (job) {
            case "farmer" -> VillagerProfession.FARMER;
            case "smith" -> VillagerProfession.TOOLSMITH;
            case "miner" -> VillagerProfession.MASON;
            case "lumberjack" -> VillagerProfession.FLETCHER;
            case "merchant" -> VillagerProfession.CARTOGRAPHER;
            case "guard" -> VillagerProfession.ARMORER;
            case "builder" -> VillagerProfession.MASON;
            case "warehouse" -> VillagerProfession.CLERIC;
            case "courier" -> VillagerProfession.LEATHERWORKER;
            default -> VillagerProfession.NONE;
        };
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // Payroll debt is accrued and collected by the worker at the treasury, never auto-paid.
        // Physical AI decisions are refreshed each tick, independently of the economy.
        // Vanilla villager goals otherwise re-start navigation immediately after STOP.
        controlLoadedResidents(server);
        if (server.getTickCount() % 10 == 0) KingdomNpcBuilder.tick(server);
        // Revoke expired land access even when nobody opens the map or visits a bank.
        if (server.getTickCount() % 1200 == 0)
            KingdomCivicsData.get(server).expireDueLeases(System.currentTimeMillis());
        if (server.getTickCount() % 100 == 0) KingdomCourierDispatch.tick(server);
        // Two municipal warehouses per minute at most; scans only already-loaded
        // physical containers and records informational seasonal shortages.
        if (server.getTickCount() % 1200 == 0)
            KingdomMunicipalFoodReserves.get(server).tick(server);
        if (server.getTickCount() % 20 == 0) {
            KingdomNpcData current=KingdomNpcData.get(server);
            for (var w : current.all()) if ("bake".equals(w.order)) {w.order="stay";current.setDirty();}
            boolean moved=false;
            for (var w : current.all()) {
                if(!"haul".equals(w.order)) continue;
                for(ServerLevel level:server.getAllLevels()) {
                    if(!level.dimension().location().toString().equals(w.dimension))continue;
                    var ent=level.getEntity(w.entityId);
                    if(ent instanceof Villager npc && npc.isAlive()) {
                        ItemStack before=w.cargo.copy();long delivered=w.deliveredItems;long baked=w.bakedBread;
                        NpcLogistics.tick(level,npc,w);
                        if(!ItemStack.matches(before,w.cargo)||delivered!=w.deliveredItems||baked!=w.bakedBread)moved=true;
                    }
                }
            }
            for (var w : current.all()) {
                if(!"courier".equals(w.order))continue;
                for(ServerLevel level:server.getAllLevels()) {
                    if(!level.dimension().location().toString().equals(w.dimension))continue;
                    var ent=level.getEntity(w.entityId);
                    if(ent instanceof Villager npc && npc.isAlive())
                        moved |= KingdomCourierService.tick(level,npc,w);
                }
            }
            // Workers travel to the warehouse and request live quotes, without remote payments.
            for (var w:current.all()) {
                if(!"smith".equals(w.job) || KingdomNpcWorkshopData.get(server).plan(w.entityId)!=null)continue;
                for(ServerLevel level:server.getAllLevels()) {
                    if(!w.dimension.equals(level.dimension().location().toString()))continue;
                    var entity=level.getEntity(w.entityId);
                    if(entity instanceof Villager npc && npc.isAlive())
                        moved |= KingdomNpcSupply.tick(level,npc,w);
                }
            }
            if(moved)current.setDirty();
        }
        // Bounded physical work; each resident checks the nearby loaded area once per 40 ticks.
        if(server.getTickCount() % 40 == 0) {
            KingdomNpcData jobs=KingdomNpcData.get(server);
            boolean executed=false;
            for(var w:jobs.all()) for(ServerLevel level:server.getAllLevels()) {
                if(!w.dimension.equals(level.dimension().location().toString()))continue;
                var entity=level.getEntity(w.entityId);
                if(entity instanceof Villager resident && resident.isAlive()) {
                    // Food is a REAL owned item: a hungry self-employed owner
                    // can collect it even when its daily goal is EAT, not WORK.
                    executed |= KingdomNpcFoodRoutine.tick(level,resident,w);
                    // The physical adapters are shared by the workday scheduler.
                    // Existing independent gatherers remain independent.
                    if (KingdomNpcNutrition.permittedWorkTick(w,server.getTickCount())
                            && ((w.employer == null && KingdomNpcFarmOwner.ownedFarm(server,w)==null)
                                || KingdomNpcDuty.mayExecute(w.job,w.order,
                                    w.autonomousIntent,KingdomNpcWorkday.employed(server,w)))) {
                        executed |= NpcFieldWork.tick(level,resident,w)
                            | KingdomNpcWorkshop.tick(level,resident,w)
                            | KingdomTfcQuernWork.tick(level,resident,w)
                            | KingdomTfcPotWork.tick(level,resident,w)
                            | KingdomTfcGrillWork.tick(level,resident,w)
                            | KingdomLivestockWork.tick(level,resident,w);
                    }
                }
            }
            if(executed)jobs.setDirty();
        }
        if (server.getTickCount() % 200 != 0) return;
        KingdomNpcData db = KingdomNpcData.get(server);
        long day = server.overworld().getDayTime() / TICKS_PER_DAY;
        boolean changed = false;
        // Economy v2: preserve existing wages as debts, never pay from legacy virtual accounts.
        // Contract sync consumes no cash and reuses the NPC registry as the identity source.
        KingdomEnterprisePayrollData.get(server).syncNpc(server,db);
        // Also assess known residents: tax accrual must not depend only on opening a menu.
        var towns=KingdomCivicsData.get(server);
        var taxes=KingdomTaxData.get(server);
        for(UUID resident:towns.residents()){
            var citizenTown=towns.townOf(resident);
            if(citizenTown!=null)taxes.assess(server,resident,citizenTown.id());
        }
        for (var w : db.all()) {
            // One workday scheduler for every profession, never a smith-only route.
            // Assignment and contract are validated against actual registered state.
            boolean activeContract = KingdomNpcWorkday.employed(server, w);
            boolean assignment = false;
            for (ServerLevel workLevel : server.getAllLevels()) {
                if (workLevel.dimension().location().toString().equals(w.dimension)) {
                    assignment = KingdomNpcWorkday.hasAssignment(server, workLevel, w);
                    break;
                }
            }
            long timeOfDay = server.overworld().getDayTime() % TICKS_PER_DAY;
            boolean daytime = timeOfDay >= 0 && timeOfDay < 12000;
            var goal = KingdomNpcGoalSelector.choose(new KingdomNpcGoalSelector.Context(
                w.employer != null || KingdomNpcFarmOwner.ownedFarm(server,w)!=null,
                activeContract, assignment,
                daytime, w.hasHome, w.hunger, w.energy));
            var intent = switch (goal) {
                case EAT -> NpcAutonomyPlanner.Intention.EAT;
                case REST -> NpcAutonomyPlanner.Intention.REST;
                case RETURN_HOME -> NpcAutonomyPlanner.Intention.SEEK_SHELTER;
                case WORK -> KingdomNpcDuty.hasPhysicalExecutor(w.job)
                    ? NpcAutonomyPlanner.Intention.CONTINUE_ASSIGNED_JOB
                    : NpcAutonomyPlanner.Intention.ON_DUTY;
                case IDLE -> w.employer == null
                    ? NpcAutonomyPlanner.Intention.FIND_WORK
                    : (!activeContract || !assignment)
                        ? NpcAutonomyPlanner.Intention.REPORT_BLOCKED
                        : NpcAutonomyPlanner.Intention.IDLE;
            };
            if (w.safety < 25) intent = NpcAutonomyPlanner.Intention.SEEK_SHELTER;
            if (!w.autonomousIntent.equals(intent.name())) {
                w.autonomousIntent = intent.name();
                changed = true;
            }
            if (NpcIdentity.advance(w,day)) changed=true;
            long lifeDays = NpcSimulation.elapsed(day,w.lastLifeDay);
            if(lifeDays>0){
                w.hunger=NpcLife.stepHunger(w.hunger,lifeDays);
                boolean working="work".equals(w.order)
                    &&(w.employer!=null||KingdomNpcFarmOwner.ownedFarm(server,w)!=null)
                    &&!w.contractPaused;
                w.energy=NpcLife.stepEnergy(w.energy,lifeDays,working,w.hasHome);
                w.morale=NpcLife.stepMorale(w.morale,w.hunger,w.energy,w.safety,!w.contractPaused,lifeDays);
                KingdomNpcNutrition.age(w,lifeDays);
                w.lastLifeDay=day;changed=true;
            }
            long days = NpcSimulation.elapsed(day, w.lastWorkedDay);
            if (days > 0) {
                // Background work only with paid contract; explicitly virtual stock in alpha.
                if ("work".equals(w.order) && w.employer!=null && NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)) {
                    // Real production only: no fake goods, XP or material yield without a valid station recipe.
                    // The TFC/Create production adapters must explicitly confirm completion before crediting output.
                }
                w.lastWorkedDay=day; changed=true;
            }
        }
        if(changed)db.setDirty();
    }
    /** Server-authoritative movement controller; never modifies non-Kingdom villagers. */
    private static void controlLoadedResidents(net.minecraft.server.MinecraftServer server) {
        KingdomNpcData db = KingdomNpcData.get(server);
        boolean pathUpdate = server.getTickCount() % 10 == 0;
        for (var w : db.all()) {
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.dimension().location().toString().equals(w.dimension)) continue;
                var entity = level.getEntity(w.entityId);
                if (!(entity instanceof Villager npc) || !npc.isAlive()) continue;
                boolean talking = false;
                for (ServerPlayer other : level.players()) {
                    if (other.containerMenu instanceof KingdomNpcMenu menu
                        && menu.isForNpc(w.id) && other.distanceToSqr(npc) <= 64) {
                        talking = true;
                        break;
                    }
                }
                boolean stay = "stay".equals(w.order);
                if (stay || talking) {
                    if (!npc.isNoAi()) npc.setNoAi(true);
                    npc.getNavigation().stop();
                    var velocity = npc.getDeltaMovement();
                    npc.setDeltaMovement(0, velocity.y, 0);
                    npc.hurtMarked = true;
                } else {
                    // Restore vanilla goals, trading, wandering etc. after dialogue/STOP.
                    if (npc.isNoAi()) npc.setNoAi(false);
                    if (!pathUpdate) continue;
                    if ("haul".equals(w.order)) {
                        // Hauling path is managed by NpcLogistics (every second).
                    } else if ("home".equals(w.order) && w.hasHome) {
                        if (npc.distanceToSqr(w.homeX + 0.5, w.homeY, w.homeZ + 0.5) > 4)
                            npc.getNavigation().moveTo(w.homeX + 0.5, w.homeY, w.homeZ + 0.5, 0.8);
                        else npc.getNavigation().stop();
                    } else if (KingdomNpcDuty.autonomousOrder(w.order)) {
                        // Shared Millenaire-style route for every job.
                        // Real profession handlers take over close to the work site.
                        boolean needsHome = "SEEK_SHELTER".equals(w.autonomousIntent)
                            || "REST".equals(w.autonomousIntent)
                            || ("EAT".equals(w.autonomousIntent)
                                && KingdomNpcFarmOwner.ownedFarm(server,w)==null);
                        if (needsHome && w.hasHome) {
                            BlockPos home = new BlockPos(w.homeX, w.homeY, w.homeZ);
                            if (level.hasChunkAt(home)) {
                                if (npc.distanceToSqr(w.homeX + 0.5, w.homeY, w.homeZ + 0.5) > 4)
                                    npc.getNavigation().moveTo(w.homeX + 0.5, w.homeY, w.homeZ + 0.5, 0.8);
                                else npc.getNavigation().stop();
                            }
                        } else if ((w.employer != null
                                || KingdomNpcFarmOwner.ownedFarm(server,w)!=null) && !w.contractPaused
                                && ("CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
                                    || "ON_DUTY".equals(w.autonomousIntent))) {
                            BlockPos site = KingdomNpcWorkday.site(server, level, w);
                            if (site != null) {
                                var kind = KingdomNpcDuty.kind(w.job);
                                // Builders, gatherers and workshops already have local movement.
                                double handoff = switch (kind) {
                                    case CONSTRUCTION -> 48.0 * 48.0;
                                    // Workshop can visit its payroll counter up to 32 blocks
                                    // from the station; do not fight its own pathfinding.
                                    case WORKSHOP -> 40.0 * 40.0;
                                    // Farmer decides which of multiple assigned plots needs attention.
                                    // Generic commute must not override crop/warehouse navigation.
                                    case FIELD -> ("farmer".equals(w.job) || "breeder".equals(w.job))
                                        ? 40.0 * 40.0 : 10.0 * 10.0;
                                    case DELIVERY -> 10.0 * 10.0;
                                    case SERVICE -> 4.0;
                                    default -> 4.0;
                                };
                                if (npc.distanceToSqr(site.getX() + 0.5, site.getY(),
                                        site.getZ() + 0.5) > handoff)
                                    npc.getNavigation().moveTo(site.getX() + 0.5,
                                        site.getY(), site.getZ() + 0.5, 0.8);
                                else if (kind == KingdomNpcDuty.Kind.SERVICE)
                                    npc.getNavigation().stop();
                            }
                        }
                    } else if ("follow".equals(w.order) && w.employer != null) {
                        ServerPlayer owner = server.getPlayerList().getPlayer(w.employer);
                        if (owner != null && owner.level() == level && npc.distanceToSqr(owner) > 9)
                            npc.getNavigation().moveTo(owner, 0.9);
                        else npc.getNavigation().stop();
                    }
                }
            }
        }
    }

}
