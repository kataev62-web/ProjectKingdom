package ru.projectkingdom.npc;

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
import net.minecraft.core.BlockPos;

/** Server-authoritative interactive 7x4 chunk map. No client-only map dependency. */
public final class KingdomLandMapMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final SimpleContainer grid;
    private int centerX, centerZ;
    private int selectedX, selectedZ;
    private static final long[] PRICE_CHOICES = {25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};
    private int salePriceIndex = 4;
    private int leasePriceIndex = 0;
    private static final int START = 1;
    private KingdomLandMapMenu(int id, Inventory inv, ServerPlayer player, int x, int z, SimpleContainer container) {
        super(MenuType.GENERIC_9x6,id,inv,container,6);
        this.grid=container;
        this.viewer=player;
        this.centerX=x;this.centerZ=z;this.selectedX=x;this.selectedZ=z;
        redraw();
    }
    public static void open(ServerPlayer player) {
        int x=player.blockPosition().getX()>>4,z=player.blockPosition().getZ()>>4;
        openAt(player,x,z);
    }
    private static void openAt(ServerPlayer player,int x,int z) {
        player.openMenu(new SimpleMenuProvider((id,inv,p)->new KingdomLandMapMenu(id,inv,player,x,z,new SimpleContainer(54)),
                Component.literal("Kingdom | Земельная карта")));
    }
    private void item(int slot, Item type,String label) {
        ItemStack stack=new ItemStack(type);
        stack.setHoverName(Component.literal(label));
        grid.setItem(slot,stack);
    }
    private String dimension() {return viewer.serverLevel().dimension().location().toString();}
    private KingdomCivicsData.Plot plot(int x,int z) {
        return KingdomCivicsData.get(viewer.getServer()).plot(viewer.serverLevel(),new BlockPos(x<<4,64,z<<4));
    }
    private void redraw() {
        grid.clearContent();
        var db=KingdomCivicsData.get(viewer.getServer());
        var own=db.townOf(viewer.getUUID());
        for(int dz=-1;dz<=2;dz++)for(int dx=-3;dx<=3;dx++){
            int x=centerX+dx,z=centerZ+dz,slot=(dz+1)*9+dx+4;
            var land=plot(x,z);
            var town=land==null?null:db.townById(land.town());
            boolean ours=viewer.getUUID().equals(db.plotOwner(dimension(),x,z));
            boolean leased=land!=null&&db.activeTenant(viewer.getUUID(),dimension(),x,z,System.currentTimeMillis());
            Item symbol=land==null?Items.LIGHT_GRAY_STAINED_GLASS_PANE:
                    leased?Items.BLUE_STAINED_GLASS_PANE:
                    ours?Items.LIME_STAINED_GLASS_PANE:Items.RED_STAINED_GLASS_PANE;
            String name=land==null?"Свободен":(town==null?"Занят":town.name()+" / "+land.zone());
            item(slot,symbol,"Чанк "+x+", "+z+" | "+name+(x==selectedX&&z==selectedZ?" | ВЫБРАН":""));
        }
        item(45,Items.ARROW,"← Сдвинуть карту на запад");
        item(46,Items.COMPASS,"↑ Сдвинуть карту на север");
        item(47,Items.COMPASS,"↓ Сдвинуть карту на юг");
        item(48,Items.ARROW,"→ Сдвинуть карту на восток");
        item(49,Items.MAP,"Выбран чанк: "+selectedX+", "+selectedZ+" | ЛКМ по квадрату для выбора");
        item(50,Items.GOLD_NUGGET,"Оформить покупку в ратуше — 100 медных (только мэр)");
        db.clearExpiredLease(dimension(),selectedX,selectedZ,System.currentTimeMillis());
        var debt=db.leaseDebt(dimension(),selectedX,selectedZ);
        item(17,Items.WRITABLE_BOOK,debt==null?"Арендной задолженности нет":
                "Долг: "+debt.copper()+" медных | должник: "+debt.tenant());
        item(8,Items.GOLD_INGOT,"Погасить задолженность наличными через банк (до 100 медных)");
        item(9,Items.EMERALD,"Погасить задолженность лично арендодателю (до 100 медных)");
        var offer=db.leaseOffer(dimension(),selectedX,selectedZ);
        var contract=db.leaseContract(dimension(),selectedX,selectedZ);
        item(51,Items.PAPER,contract!=null?"Аренда до "+new java.util.Date(contract.expiresAt()):
             offer!=null?"Оформить аренду в ратуше: "+offer.priceCopper()+" медных (владелец рядом)":
             "Выставить участок в аренду: "+PRICE_CHOICES[leasePriceIndex]+" медных / 7 суток");
        item(52,Items.COMPASS,"Вернуться к текущей позиции");
        item(44,Items.EMERALD,"Продлить свою аренду на 7 суток — наличными в ратуше");
        item(43,Items.BARRIER,"Расторгнуть свою аренду (без автоматического возврата платы)");
        item(41,Items.GOLD_INGOT,"Оплатить аренду наличными через кассу банка (без присутствия владельца)");
        item(42,Items.GOLD_INGOT,"Продлить аренду наличными через кассу банка");
        var sale=db.saleOffer(dimension(),selectedX,selectedZ);
        item(36,Items.GOLD_NUGGET,"Цена продажи: "+PRICE_CHOICES[salePriceIndex]+" медных | нажми для изменения");
        item(26,Items.EMERALD,"Купить у владельца лично в ратуше (наличные)");
        item(35,Items.PAPER,"Цена аренды: "+PRICE_CHOICES[leasePriceIndex]+" медных / 7 суток | изменить");
        item(38,Items.EMERALD,"Выставить свой участок на продажу: "+PRICE_CHOICES[salePriceIndex]+" медных");
        item(39,Items.BARRIER,"Снять объявление о продаже");
        item(40,Items.GOLD_BLOCK,sale==null?"Нет объявления о продаже":
                "Купить у владельца: "+sale.priceCopper()+" медных (наличными в банке)");
        item(27,Items.IRON_PICKAXE,"Работник рядом: выдать/отозвать право строить и ломать");
        item(37,Items.OAK_DOOR,"Работник рядом: выдать/отозвать право взаимодействовать");
        item(53,Items.BARRIER,"Закрыть карту");
        broadcastChanges();
    }
    private void message(String s) {viewer.sendSystemMessage(Component.literal("[Kingdom Земля] "+s));}
    /** A land map is a catalogue, not a remote checkout. Transactions require a real town hall. */
    private boolean atTownHall() {
        String dim=dimension();
        for (var project:KingdomConstructionData.get(viewer.getServer()).all()) {
            if(project.status()!=KingdomConstructionData.Status.COMPLETED
                    || !project.dimension().equals(dim) || !project.design().equals("TOWN_HALL"))continue;
            double dx=viewer.getX()-(project.x()+6.5);
            double dy=viewer.getY()-(project.y()+1);
            double dz=viewer.getZ()-(project.z()+7.5);
            if(dx*dx+dy*dy+dz*dz<=16*16)return true;
        }
        message("Для оформления сделки приди в построенную ратушу (не дальше 16 блоков).");
        return false;
    }
    private void purchase() {
        if(!atTownHall())return;
        var db=KingdomCivicsData.get(viewer.getServer());
        var town=db.townOf(viewer.getUUID());
        if(town==null||!town.mayor().equals(viewer.getUUID())){message("Покупать городскую землю может только мэр");return;}
        if(plot(selectedX,selectedZ)!=null){message("Участок уже зарегистрирован");return;}
        BlockPos at=new BlockPos(selectedX<<4,64,selectedZ<<4);
        var old=KingdomTerritories.get(viewer.getServer());
        var oldOwner=old.owner(viewer.serverLevel(),at);
        if(oldOwner!=null&&!oldOwner.equals(viewer.getUUID())){message("Чужой участок в старом реестре");return;}
        var wallet=KingdomCurrency.wallet(viewer);
        if(wallet.isEmpty()||KingdomCurrency.balance(wallet)<100){message("Для покупки нужно 100 медных в кошельке");return;}
        // Debit first; compensate on any failed registration. A claimed plot cannot be bought twice.
        if(!KingdomCurrency.debit(wallet,100)){message("Оплата не прошла");return;}
        if(!old.claim(viewer.serverLevel(),at,viewer.getUUID()) || !db.claim(viewer.getUUID(),dimension(),selectedX,selectedZ)) {
            KingdomCurrency.credit(wallet,100);
            message("Не удалось оформить участок; деньги возвращены");return;
        }
        viewer.getInventory().setChanged();
        message("Куплен чанк "+selectedX+", "+selectedZ+" за 100 медных. Монеты списаны из кошелька.");
    }
    private void leaseAction() {
        if(!atTownHall())return;
        var db=KingdomCivicsData.get(viewer.getServer());
        db.clearExpiredLease(dimension(),selectedX,selectedZ,System.currentTimeMillis());
        var p=plot(selectedX,selectedZ);
        if(p==null){message("Сначала участок должен принадлежать городу");return;}
        var town=db.townById(p.town());
        if(town==null)return;
        if(viewer.getUUID().equals(db.plotOwner(dimension(),selectedX,selectedZ))) {
            if(db.offerLease(viewer.getUUID(),dimension(),selectedX,selectedZ,PRICE_CHOICES[leasePriceIndex],7L*24*60*60*1000))message("Предложение аренды опубликовано: "+PRICE_CHOICES[leasePriceIndex]+" медных за 7 реальных суток");
            else message("Участок уже арендован или недоступен");
            return;
        }
        var offer=db.leaseOffer(dimension(),selectedX,selectedZ);
        if(offer==null){message("Этот участок не выставлен в аренду");return;}
        ServerPlayer mayor=viewer.getServer().getPlayerList().getPlayer(offer.landlord());
        if(mayor==null||mayor.level()!=viewer.level()||mayor.distanceToSqr(viewer)>64){message("Для передачи наличных мэр должен находиться в пределах 8 блоков");return;}
        if(!KingdomCurrency.transfer(viewer,mayor,offer.priceCopper())){message("Не хватает монет или кошелька у одной из сторон");return;}
        if(!db.acceptLease(viewer.getUUID(),dimension(),selectedX,selectedZ,System.currentTimeMillis())){
            if(!KingdomCurrency.transfer(mayor,viewer,offer.priceCopper()))message("ОШИБКА: не удалось вернуть платёж, требуется вмешательство администратора");
            else message("Оформление не удалось, деньги возвращены");
            return;
        }
        message("Договор заключён, аренда оплачена наличными на 7 суток");
        mayor.sendSystemMessage(Component.literal("[Kingdom Земля] Получено "+offer.priceCopper()+" медных за аренду участка "+selectedX+", "+selectedZ));
    }
    private void renew() {
        if(!atTownHall())return;
        var db=KingdomCivicsData.get(viewer.getServer());
        long now=System.currentTimeMillis();
        db.clearExpiredLease(dimension(),selectedX,selectedZ,now);
        var contract=db.leaseContract(dimension(),selectedX,selectedZ);
        if(contract==null||!contract.tenant().equals(viewer.getUUID())){message("Нет действующего договора аренды");return;}
        ServerPlayer landlord=viewer.getServer().getPlayerList().getPlayer(contract.landlord());
        if(landlord==null||landlord.level()!=viewer.level()||landlord.distanceToSqr(viewer)>64){
            message("Для оплаты наличными арендодатель должен быть рядом (8 блоков)");return;
        }
        long price=contract.paidCopper();
        if(!KingdomCurrency.transfer(viewer,landlord,price)){message("Не хватает наличных или кошелька у одной из сторон");return;}
        if(!db.renewLease(viewer.getUUID(),dimension(),selectedX,selectedZ,now,price,7L*24*60*60*1000)) {
            if(!KingdomCurrency.transfer(landlord,viewer,price))message("ОШИБКА возврата оплаты: обратитесь к администратору");
            else message("Продление не удалось, деньги возвращены");
            return;
        }
        message("Аренда продлена на 7 суток; уплачено "+price+" медных");
        landlord.sendSystemMessage(Component.literal("[Kingdom Земля] Получена аренда: "+price+" медных"));
    }
    /** Debt is never charged automatically. Partial settlement must be initiated by tenant. */
    private void settleDebt(boolean viaBank) {
        var civics=KingdomCivicsData.get(viewer.getServer());
        civics.clearExpiredLease(dimension(),selectedX,selectedZ,System.currentTimeMillis());
        var debt=civics.leaseDebt(dimension(),selectedX,selectedZ);
        if(debt==null||!debt.tenant().equals(viewer.getUUID())) {
            message("На этом участке нет твоей задолженности");return;
        }
        long amount=Math.min(100,debt.copper());
        if(viaBank) {
            if(KingdomBankData.get(viewer.getServer()).payLeaseDebtCash(viewer,dimension(),selectedX,selectedZ,amount))
                message("В банк внесено "+amount+" медных в погашение аренды");
            else message("Платёж не принят: нужен кошелёк с монетами и банковская стойка рядом");
            return;
        }
        if(!atTownHall())return;
        ServerPlayer landlord=viewer.getServer().getPlayerList().getPlayer(debt.landlord());
        if(landlord==null||landlord.level()!=viewer.level()||landlord.distanceToSqr(viewer)>64) {
            message("Арендодатель должен находиться рядом (8 блоков)");return;
        }
        if(!KingdomCurrency.transfer(viewer,landlord,amount)) {
            message("Недостаточно монет или отсутствует кошелёк");return;
        }
        if(!civics.recordLeaseDebtPayment(viewer.getUUID(),dimension(),selectedX,selectedZ,amount)) {
            if(!KingdomCurrency.transfer(landlord,viewer,amount))
                message("КРИТИЧЕСКАЯ ОШИБКА: возврат наличных не удался");
            else message("Платёж отменён, монеты возвращены");
            return;
        }
        message("Погашено "+amount+" медных наличными");
        landlord.sendSystemMessage(Component.literal("[Kingdom Земля] Получено "+amount+" медных в погашение аренды"));
    }
    private void bankLease(boolean renewal) {
        var bank=KingdomBankData.get(viewer.getServer());
        if(!bank.atBranch(viewer)) {message("Подойди к зарегистрированной банковской стойке (5 блоков).");return;}
        if(bank.payLeaseCash(viewer,dimension(),selectedX,selectedZ,renewal))
            message("Наличные приняты банком. Договор оформлен; деньги зачислены на хранение арендодателю.");
        else message("Оплата отклонена: проверь предложение, договор, срок и монеты в кошельке.");
    }
    private void saleAction(int action) {
        var db=KingdomCivicsData.get(viewer.getServer());
        if(action==0) {
            if(!atTownHall())return;
            message(db.offerSale(viewer.getUUID(),dimension(),selectedX,selectedZ,PRICE_CHOICES[salePriceIndex])
                    ?"Участок выставлен на продажу за "+PRICE_CHOICES[salePriceIndex]+" медных":"Нельзя выставить участок: проверь собственность и аренду");
        } else if(action==1) {
            message(db.cancelSale(viewer.getUUID(),dimension(),selectedX,selectedZ)
                    ?"Объявление о продаже снято":"Нет твоего объявления");
        } else {
            message(KingdomBankData.get(viewer.getServer()).payLandSaleCash(viewer,dimension(),selectedX,selectedZ)
                    ?"Покупка зарегистрирована; наличные переданы в банк продавца"
                    :"Покупка отклонена: приди к банковской стойке с кошельком и проверь объявление");
        }
    }
    /** Face-to-face property sale: seller receives physical coins, bank balances remain untouched. */
    private void directSale() {
        if(!atTownHall())return;
        var db=KingdomCivicsData.get(viewer.getServer());
        if(!db.canBuySale(viewer.getUUID(),dimension(),selectedX,selectedZ)) {
            message("Участок не продаётся или сделка недоступна");return;
        }
        var offer=db.saleOffer(dimension(),selectedX,selectedZ);
        ServerPlayer seller=viewer.getServer().getPlayerList().getPlayer(offer.seller());
        if(seller==null||seller.level()!=viewer.level()||seller.distanceToSqr(viewer)>64) {
            message("Продавец должен находиться рядом (8 блоков)");return;
        }
        if(!KingdomCurrency.transfer(viewer,seller,offer.priceCopper())) {
            message("Нужны кошельки и достаточное количество монет");return;
        }
        if(!db.completeSale(viewer.getUUID(),dimension(),selectedX,selectedZ)) {
            if(!KingdomCurrency.transfer(seller,viewer,offer.priceCopper()))
                message("КРИТИЧЕСКАЯ ОШИБКА: возврат наличных не выполнен, нужен администратор");
            else message("Сделка отменена, наличные возвращены");
            return;
        }
        message("Куплен участок "+selectedX+", "+selectedZ+" за "+offer.priceCopper()+" медных лично у владельца");
        seller.sendSystemMessage(Component.literal("[Kingdom Земля] Продан участок "+selectedX+", "+selectedZ+" за "+offer.priceCopper()+" медных"));
    }
    /** Scoped plot permission for the nearest consenting co-located worker candidate. */
    private void workerAccess(boolean construction) {
        var db=KingdomCivicsData.get(viewer.getServer());
        String dim=dimension();long now=System.currentTimeMillis();
        if(!viewer.getUUID().equals(db.plotOwner(dim,selectedX,selectedZ))
                && !db.activeTenant(viewer.getUUID(),dim,selectedX,selectedZ,now)) {
            message("Только собственник или действующий арендатор может назначать работников");return;
        }
        ServerPlayer candidate=null;
        for(ServerPlayer other:viewer.getServer().getPlayerList().getPlayers()) {
            if(other==viewer||other.level()!=viewer.level()||other.distanceToSqr(viewer)>64)continue;
            if(candidate!=null){message("Рядом несколько игроков. Для назначения должен остаться один (8 блоков).");return;}
            candidate=other;
        }
        if(candidate==null){message("Попроси работника подойти на расстояние до 8 блоков");return;}
        var first=construction?KingdomCivicsData.Action.BUILD:KingdomCivicsData.Action.INTERACT;
        var second=construction?KingdomCivicsData.Action.BREAK:KingdomCivicsData.Action.USE;
        boolean enable=!db.workerHasGrant(candidate.getUUID(),dim,selectedX,selectedZ,first);
        if(!db.setWorkerAccess(viewer.getUUID(),candidate.getUUID(),dim,selectedX,selectedZ,first,enable,now)
                ||!db.setWorkerAccess(viewer.getUUID(),candidate.getUUID(),dim,selectedX,selectedZ,second,enable,now)) {
            message("Не удалось изменить права работника");return;
        }
        message((enable?"Права выданы: ":"Права отозваны: ")+candidate.getGameProfile().getName());
        candidate.sendSystemMessage(Component.literal("[Kingdom Земля] "+viewer.getGameProfile().getName()
                +(enable?" назначил вас работником":" отозвал права")+" на участке "+selectedX+", "+selectedZ));
    }
    private void surrender() {
        var db=KingdomCivicsData.get(viewer.getServer());
        if(db.surrenderLease(viewer.getUUID(),dimension(),selectedX,selectedZ))message("Аренда прекращена. Предоплата не возвращается автоматически.");
        else message("Нет договора аренды на твоё имя");
    }
    @Override public void clicked(int slot,int button,ClickType click,Player player) {
        if(player!=viewer||viewer.level().isClientSide||click!=ClickType.PICKUP||button!=0||slot<0||slot>=54)return;
        int col=slot%9,row=slot/9;
        if(row<4&&col>=1&&col<=7){selectedX=centerX+col-4;selectedZ=centerZ+row-1;redraw();return;}
        switch(slot){
            case 8 -> settleDebt(true);
            case 9 -> settleDebt(false);
            case 27 -> workerAccess(true);
            case 37 -> workerAccess(false);
            case 35 -> leasePriceIndex=(leasePriceIndex+1)%PRICE_CHOICES.length;
            case 36 -> salePriceIndex=(salePriceIndex+1)%PRICE_CHOICES.length;
            case 26 -> directSale();
            case 38 -> saleAction(0);
            case 39 -> saleAction(1);
            case 40 -> saleAction(2);
            case 41 -> bankLease(false);
            case 42 -> bankLease(true);
            case 43 -> surrender();
            case 44 -> renew();
            case 45 -> centerX-=7;
            case 46 -> centerZ-=5;
            case 47 -> centerZ+=5;
            case 48 -> centerX+=7;
            case 50 -> purchase();
            case 51 -> leaseAction();
            case 52 -> {centerX=viewer.blockPosition().getX()>>4;centerZ=viewer.blockPosition().getZ()>>4;}
            case 53 -> {viewer.closeContainer();return;}
            default -> {return;}
        }
        redraw();
    }
    @Override public boolean stillValid(Player player){return player==viewer&&player.isAlive();}
    @Override public void removed(Player player){super.removed(player);grid.clearContent();}
}
