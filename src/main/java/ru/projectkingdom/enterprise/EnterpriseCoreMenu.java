package ru.projectkingdom.enterprise;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import ru.projectkingdom.npc.KingdomCurrency;
import ru.projectkingdom.npc.KingdomSimpleTradeMenu;
import ru.projectkingdom.npc.SharedTradeNoticeBoards;
import ru.projectkingdom.npc.SimpleTradeNoticeBoard;
import net.minecraftforge.network.NetworkHooks;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomEnterprisePayrollData;
import ru.projectkingdom.npc.KingdomMainMenu;
import ru.projectkingdom.npc.KingdomFarmFieldsMenu;

/** Slot-free management menu anchored to one loaded enterprise core. */
public final class EnterpriseCoreMenu extends AbstractContainerMenu {
    public static final int DEPOSIT_100=100,WITHDRAW_100=101,BIND_CASHBOX=110,BIND_RAW=111,BIND_FINISHED=112,OPEN_ARCHITECT=120,OPEN_PROJECTS=121,OPEN_FARM_FIELDS=122;
    public static final int APPLY_JOB=150,BUY_FIRST=160,BUY_LAST=163,SELL_HELD=170,ACCEPT_FIRST=180,CLOSE_BUSINESS=190,MIGRATE_CASH=191;
    private record Closing(UUID enterprise,long expiration) {}
    private static final Map<UUID,Closing> PENDING_CLOSURES=new ConcurrentHashMap<>();
    public record ShopOffer(UUID id,String itemId,long price,long stock) {}
    public record StockLine(ItemStack sample,long count) {
        public StockLine { sample=sample.copy(); }
    }
    private static final int MAX_STOCK_LINES=64;
    private final List<StockLine> rawStocks,finishedStocks;
    private static List<StockLine> readStockLines(FriendlyByteBuf buf){
        int size=buf.readVarInt();
        if(size<0||size>MAX_STOCK_LINES)throw new IllegalArgumentException("Warehouse list too large");
        List<StockLine> result=new ArrayList<>();
        for(int i=0;i<size;i++)result.add(new StockLine(buf.readItem(),buf.readVarLong()));
        return List.copyOf(result);
    }
    private static void writeStockLines(FriendlyByteBuf buf,List<StockLine> entries){
        buf.writeVarInt(entries.size());
        for(var line:entries){buf.writeItem(line.sample());buf.writeVarLong(line.count());}
    }
    private static List<StockLine> stockLines(ServerPlayer viewer,KingdomEnterpriseData data,UUID firm,EnterpriseBindingRole role){
        var access=EnterpriseAccessService.snapshot(viewer.serverLevel(),data,firm,role);
        if(access.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE||access.container()==null)return List.of();
        List<StockLine> rows=new ArrayList<>();
        var store=access.container();
        for(int slot=0;slot<store.getContainerSize();slot++){
            var stack=store.getItem(slot);
            if(stack.isEmpty())continue;
            boolean grouped=false;
            for(int index=0;index<rows.size();index++){
                var existing=rows.get(index);
                if(ItemStack.isSameItemSameTags(existing.sample(),stack)){
                    rows.set(index,new StockLine(existing.sample(),existing.count()+stack.getCount()));
                    grouped=true;break;
                }
            }
            if(!grouped && rows.size()<MAX_STOCK_LINES){
                var sample=stack.copy();sample.setCount(1);
                rows.add(new StockLine(sample,stack.getCount()));
            }
        }
        return List.copyOf(rows);
    }
    private final List<ShopOffer> publicOffers;
    private final int applications;
    private final String firstApplicant;
    private final boolean applied;
    private final Inventory inventory;private final BlockPos corePos;private final UUID enterpriseId;private final EnterpriseKind kind;
    private final String enterpriseName;private final UUID owner;private final long cash;private final int workers;private final long debt;
    private final String cashboxState,rawState,finishedState;private final boolean manager;

    public EnterpriseCoreMenu(int id,Inventory inventory,FriendlyByteBuf buffer){
        this(id,inventory,buffer.readBlockPos(),buffer.readUUID(),EnterpriseKind.byId(buffer.readUtf(24)),buffer.readUtf(48),
                buffer.readUUID(),buffer.readLong(),buffer.readInt(),buffer.readLong(),buffer.readUtf(96),buffer.readUtf(96),buffer.readUtf(96),buffer.readBoolean(),
                readOffers(buffer),buffer.readVarInt(),buffer.readUtf(32),buffer.readBoolean(),
                readStockLines(buffer),readStockLines(buffer));
    }
    private EnterpriseCoreMenu(int id,Inventory inventory,BlockPos pos,UUID enterprise,EnterpriseKind kind,String name,UUID owner,
                               long cash,int workers,long debt,String cashbox,String raw,String finished,boolean manager,
                               List<ShopOffer> offers,int applications,String firstApplicant,boolean applied,
                               List<StockLine> rawStocks,List<StockLine> finishedStocks){
        super(KingdomEnterpriseRegistry.CORE_MENU.get(),id);this.inventory=inventory;this.corePos=pos;this.enterpriseId=enterprise;this.kind=kind;
        this.enterpriseName=name;this.owner=owner;this.cash=cash;this.workers=workers;this.debt=debt;
        this.cashboxState=cashbox;this.rawState=raw;this.finishedState=finished;this.manager=manager;
        this.publicOffers=List.copyOf(offers);this.applications=applications;this.firstApplicant=firstApplicant;this.applied=applied;
        this.rawStocks=List.copyOf(rawStocks);this.finishedStocks=List.copyOf(finishedStocks);
    }
    private static List<ShopOffer> readOffers(FriendlyByteBuf buffer) {
        int size=Math.min(4,Math.max(0,buffer.readVarInt()));
        List<ShopOffer> rows=new ArrayList<>();
        for(int i=0;i<size;i++)
            rows.add(new ShopOffer(buffer.readUUID(),buffer.readUtf(160),buffer.readLong(),buffer.readLong()));
        return List.copyOf(rows);
    }
    private static List<ShopOffer> shopOffers(ServerPlayer player,KingdomEnterpriseData data,UUID enterprise) {
        List<ShopOffer> result=new ArrayList<>();
        if(data.unresolved(enterprise))return List.of();
        for(var offer:data.offers()) {
            if(!enterprise.equals(offer.seller()))continue;
            long stock=data.stock(player.serverLevel(),offer);
            if(stock>0 && offer.unitPrice()>0)result.add(new ShopOffer(offer.id(),offer.itemId(),offer.unitPrice(),stock));
            if(result.size()>=4)break;
        }
        return List.copyOf(result);
    }
    public static void open(ServerPlayer player,EnterpriseCoreBlockEntity core){
        if(core==null||!core.bound())return;
        var data=KingdomEnterpriseData.get(player.getServer());
        var firm=data.find(core.enterpriseId());if(firm==null)return;
        boolean manager=data.mayManage(player.getUUID(),firm.id());
        int workers=0;long debt=0;
        String cashbox="Касса в блоке",raw="Не подключено",finished="Не подключено";
        List<StockLine> rawStocks=List.of(),finishedStocks=List.of();
        if(manager){
            var payroll=KingdomEnterprisePayrollData.get(player.getServer());
            workers=(int)payroll.all().stream().filter(e->e.enterprise().equals(firm.id())&&e.active()).count();
            debt=payroll.all().stream().filter(e->e.enterprise().equals(firm.id())).mapToLong(e->e.owed()).reduce(0,(a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);
            var actualCash=EnterpriseAccessService.snapshot(player.serverLevel(),data,firm.id(),EnterpriseBindingRole.CASHBOX);
            cashbox=actualCash.status()==EnterpriseAccessService.Status.BOUND_AVAILABLE?"Касса внутри блока":
                    EnterpriseAccessService.display(actualCash.status());
            raw=status(player,data,firm.id(),EnterpriseBindingRole.RAW_STORAGE);
            finished=status(player,data,firm.id(),EnterpriseBindingRole.FINISHED_STORAGE);
            rawStocks=stockLines(player,data,firm.id(),EnterpriseBindingRole.RAW_STORAGE);
            finishedStocks=stockLines(player,data,firm.id(),EnterpriseBindingRole.FINISHED_STORAGE);
        }
        var applications=KingdomEnterpriseApplications.get(player.getServer());
        var pending=manager?applications.applications(firm.id()):List.<KingdomEnterpriseApplications.Application>of();
        int applicationCount=pending.size();
        String firstApplicant=pending.isEmpty()?"":pending.get(0).name();
        boolean applied=!manager && applications.pending(firm.id(),player.getUUID());
        List<ShopOffer> offers=manager?List.of():shopOffers(player,data,firm.id());
        long visibleCash=manager?firm.cash():0;
        final int workerCount=workers, count=applicationCount;
        final long visibleDebt=debt;
        final String cashState=cashbox,rawState=raw,finishedState=finished,applicant=firstApplicant;
        final List<StockLine> rawInventory=rawStocks,finishedInventory=finishedStocks;
        NetworkHooks.openScreen(player,new MenuProvider(){
            @Override public Component getDisplayName(){return Component.literal(firm.name());}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player ignored){
                return new EnterpriseCoreMenu(id,inv,core.getBlockPos(),firm.id(),core.kind(),firm.name(),
                    firm.owner(),visibleCash,workerCount,visibleDebt,cashState,rawState,finishedState,manager,
                    offers,count,applicant,applied,rawInventory,finishedInventory);
            }
        },buffer->{
            buffer.writeBlockPos(core.getBlockPos());buffer.writeUUID(firm.id());
            buffer.writeUtf(core.kind().id());buffer.writeUtf(firm.name(),48);buffer.writeUUID(firm.owner());
            buffer.writeLong(visibleCash);buffer.writeInt(workerCount);buffer.writeLong(visibleDebt);
            buffer.writeUtf(cashState,96);buffer.writeUtf(rawState,96);buffer.writeUtf(finishedState,96);buffer.writeBoolean(manager);
            buffer.writeVarInt(offers.size());
            for(var offer:offers){
                buffer.writeUUID(offer.id());buffer.writeUtf(offer.itemId(),160);
                buffer.writeLong(offer.price());buffer.writeLong(offer.stock());
            }
            buffer.writeVarInt(count);buffer.writeUtf(applicant,32);buffer.writeBoolean(applied);
            writeStockLines(buffer,rawInventory);writeStockLines(buffer,finishedInventory);
        });
    }
    private static String status(ServerPlayer player,KingdomEnterpriseData data,UUID firm,EnterpriseBindingRole role){
        return EnterpriseAccessService.display(
            EnterpriseAccessService.snapshot(player.serverLevel(),data,firm,role).status());
    }
    public UUID enterpriseId(){return enterpriseId;}public EnterpriseKind kind(){return kind;}public String enterpriseName(){return enterpriseName;}
    public UUID owner(){return owner;}public long cash(){return cash;}public int workers(){return workers;}public long debt(){return debt;}
    public String cashboxState(){return cashboxState;}public String rawState(){return rawState;}public String finishedState(){return finishedState;}public boolean manager(){return manager;}
    public List<ShopOffer> publicOffers(){return publicOffers;}
    public int applications(){return applications;}
    public String firstApplicant(){return firstApplicant;}
    public boolean applied(){return applied;}
    public List<StockLine> rawStocks(){return rawStocks;}
    public List<StockLine> finishedStocks(){return finishedStocks;}
    @Override public boolean clickMenuButton(Player player,int action){
        if(!(player instanceof ServerPlayer server)||!stillValid(player))return false;var data=KingdomEnterpriseData.get(server.getServer());
        if(action==MIGRATE_CASH){
            if(!data.mayManage(server.getUUID(),enterpriseId))return false;
            var outcome=data.migrateLegacyCash(server,enterpriseId);
            server.sendSystemMessage(Component.literal("[Касса] "+KingdomEnterpriseData.cashMigrationMessage(outcome)));
            if(outcome==KingdomEnterpriseData.CashMigration.MOVED||outcome==KingdomEnterpriseData.CashMigration.READY)
                server.closeContainer();
            return outcome==KingdomEnterpriseData.CashMigration.MOVED||outcome==KingdomEnterpriseData.CashMigration.READY;
        }
        if(action==CLOSE_BUSINESS){
            if(!data.mayManage(server.getUUID(),enterpriseId))return false;
            var old=PENDING_CLOSURES.get(server.getUUID());
            long now=server.serverLevel().getGameTime();
            if(old==null||!enterpriseId.equals(old.enterprise())||old.expiration()<now){
                PENDING_CLOSURES.put(server.getUUID(),new Closing(enterpriseId,now+600));
                server.sendSystemMessage(Component.literal("[Предприятие] Для закрытия нажмите «Подтвердить закрытие» ещё раз в течение 30 секунд. Деньги, товары и долги должны быть погашены."));
                return true;
            }
            PENDING_CLOSURES.remove(server.getUUID());
            var result=data.closeBusiness(server,enterpriseId,corePos);
            server.sendSystemMessage(Component.literal("[Предприятие] "+KingdomEnterpriseData.closeMessage(result)));
            if(result==KingdomEnterpriseData.ClosureStatus.CLOSED)server.closeContainer();
            return result==KingdomEnterpriseData.ClosureStatus.CLOSED;
        }
        if(action==APPLY_JOB && !data.mayManage(server.getUUID(),enterpriseId)) {
            boolean ok=KingdomEnterpriseApplications.get(server.getServer()).request(server.getServer(),
                    enterpriseId,server.getUUID(),server.getScoreboardName());
            server.sendSystemMessage(Component.literal(ok?"Заявка на работу отправлена владельцу.":"Заявка уже отправлена или приём заявок закрыт."));
            return ok;
        }
        if(action>=BUY_FIRST && action<=BUY_LAST && !data.mayManage(server.getUUID(),enterpriseId)) {
            int index=action-BUY_FIRST;
            if(index>=publicOffers.size())return false;
            boolean ok=data.buyAsVisitor(server,enterpriseId,publicOffers.get(index).id());
            server.sendSystemMessage(Component.literal(ok?"Куплен 1 предмет. Монеты списаны из кошелька.":"Покупка отклонена: проверь товар, деньги, место и расстояние."));
            return ok;
        }
        if(action==SELL_HELD && !data.mayManage(server.getUUID(),enterpriseId))return openSellNotice(server,data);
        if(action==ACCEPT_FIRST && data.mayManage(server.getUUID(),enterpriseId)) {
            boolean ok=KingdomEnterpriseApplications.get(server.getServer())
                .acceptFirst(server.getServer(),server.getUUID(),enterpriseId,10);
            server.sendSystemMessage(Component.literal(ok?"Заявитель принят на работу: 10 медных в игровые сутки.":"Новых заявок нет или приём невозможен."));
            return ok;
        }
        if(kind==EnterpriseKind.FARM && data.mayManage(server.getUUID(),enterpriseId)
                && action==OPEN_FARM_FIELDS) {
            KingdomFarmFieldsMenu.open(server,enterpriseId,corePos);return true;
        }
        if(kind==EnterpriseKind.CONSTRUCTION && manager && action==OPEN_ARCHITECT) {
            KingdomMainMenu.openArchitect(server,corePos);return true;
        }
        if(kind==EnterpriseKind.CONSTRUCTION && manager && action==OPEN_PROJECTS) {
            KingdomMainMenu.openProjects(server);return true;
        }
        return switch(action){
            case DEPOSIT_100->{
                boolean ok=data.depositAtCore(server,enterpriseId,100);
                server.sendSystemMessage(Component.literal(ok
                        ?"В кассу внутри блока внесено 100 медных реальными монетами."
                        :"Внесение отклонено: проверьте монеты в кошельке, место во внутренней кассе и старый баланс. При необходимости перенесите прежнюю кассу."));
                yield ok;
            }
            case WITHDRAW_100->{
                boolean ok=data.withdrawAtCore(server,enterpriseId,100);
                server.sendSystemMessage(Component.literal(ok
                        ?"Из внутренней кассы выдано 100 медных в кошелёк."
                        :"Выдача невозможна: проверьте сумму в блоке, номиналы монет и место в кошельке."));
                yield ok;
            }
            case BIND_CASHBOX->{
                server.sendSystemMessage(Component.literal("Касса уже находится внутри блока предприятия. Для старой кассы используйте «Перенести монеты» во вкладке «Касса»."));
                yield false;
            }
            case BIND_RAW->beginBinding(server,EnterpriseBindingRole.RAW_STORAGE);
            case BIND_FINISHED->beginBinding(server,EnterpriseBindingRole.FINISHED_STORAGE);
            default->false;};
    }
    private boolean beginBinding(ServerPlayer player,EnterpriseBindingRole role) {
        boolean started=EnterpriseAccessService.begin(player,enterpriseId,role);
        player.sendSystemMessage(Component.literal(started
            ?"Привязка «"+role.title()+"»: подойдите не дальше 6 блоков и нажмите ПКМ по отдельному сундуку или складу Kingdom. Держите руку пустой."
            :"Нет прав. Привязку может выполнять только владелец предприятия."));
        return started;
    }
    private boolean openSellNotice(ServerPlayer player,KingdomEnterpriseData data) {
        var counter=data.counter(enterpriseId);
        if(counter==null || !counter.dimension().equals(player.level().dimension().location().toString())
                || counter.pos().distSqr(player.blockPosition())>64
                || !data.atCounter(player,enterpriseId)){
            player.sendSystemMessage(Component.literal("Для продажи подойди к кассе предприятия с товаром в руке."));
            return false;
        }
        if(!(player.serverLevel().getBlockEntity(counter.pos()) instanceof Container cashbox))return false;
        // Never choose an arbitrary neighbouring chest for client-facing exchange.
        var bound=data.storageBinding(enterpriseId,EnterpriseBindingRole.RAW_STORAGE);
        if(bound==null || !bound.dimension().equals(counter.dimension())
                || bound.pos().distSqr(counter.pos())>4)return false;
        var access=EnterpriseAccessService.snapshot(player.serverLevel(),data,enterpriseId,
                EnterpriseBindingRole.RAW_STORAGE);
        if(access.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                || access.container()==cashbox)return false;
        try {
            var path=player.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("kingdom_trade_notices.dat");
            var board=SharedTradeNoticeBoards.get(path);
            var notices=board.offers(enterpriseId,SimpleTradeNoticeBoard.Side.BUY);
            var held=player.getMainHandItem();
            if(held.isEmpty()){player.sendSystemMessage(Component.literal("Держи продаваемый товар в руке."));return false;}
            var itemId=BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            var match=notices.stream().filter(n->n.itemId().equals(itemId)).findFirst();
            if(match.isEmpty()){
                player.sendSystemMessage(Component.literal("У предприятия нет заявки на покупку этого товара."));
                return false;
            }
            var warehouse=access.container();
            boolean opened=KingdomSimpleTradeMenu.openForNotice(player,board,match.get().id(),
                enterpriseId,warehouse,cashbox,KingdomCurrency.COPPER.get(),
                () -> data.atCounter(player,enterpriseId)
                    && player.serverLevel().getBlockEntity(counter.pos())==cashbox
                    && EnterpriseAccessService.snapshot(player.serverLevel(),data,enterpriseId,
                        EnterpriseBindingRole.RAW_STORAGE).container()==warehouse);
            if(!opened)player.sendSystemMessage(Component.literal("Заявка на закупку недоступна."));
            return opened;
        } catch(java.io.IOException | IllegalArgumentException error){
            player.sendSystemMessage(Component.literal("Ошибка чтения торговых заявок."));
            return false;
        }
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){
        if(player.level()!=inventory.player.level()||corePos.distSqr(player.blockPosition())>64||!player.level().hasChunkAt(corePos))return false;
        return player.level().getBlockEntity(corePos) instanceof EnterpriseCoreBlockEntity core&&enterpriseId.equals(core.enterpriseId())&&kind==core.kind();
    }
}
