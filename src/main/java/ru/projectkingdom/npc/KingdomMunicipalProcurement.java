package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import ru.projectkingdom.administration.*;

/**
 * City food procurement, only on explicit treasury-manager confirmation at
 * their loaded bank. Real physical bank coins back existing supply-order escrow.
 * No virtual mint, remote merchant settlement, or autonomous debit.
 */
public final class KingdomMunicipalProcurement {
    private KingdomMunicipalProcurement() {}
    private static final int MAX_LOT=16;

    public record Proposal(KingdomWarehouseMarket.Stall seller,BlockPos destination,
                           ItemStack lot,int count,long price) {
        public Proposal { lot=lot.copy(); }
        @Override public ItemStack lot(){return lot.copy();}
    }
    public enum Result {
        SUCCESS, NO_PERMISSION, NOT_AT_BANK, UNKNOWN_RESERVE, NO_SHORTAGE,
        ALREADY_ORDERED, NO_MARKET_FOOD, NO_DESTINATION, NO_MONEY,
        PAYMENT_REQUIRES_REVIEW
    }

    private static boolean memberCanBuy(ServerPlayer actor,UUID town) {
        var civics=KingdomCivicsData.get(actor.getServer());
        var city=civics.townById(town);
        if(city==null || !civics.isMember(town,actor.getUUID()))return false;
        var admin=KingdomAdministrationData.get(actor.getServer());
        return AdministrativePermissionService.mayManage(actor.getUUID(),
                admin.founder(town),city.mayor(),
                admin.roles(town,actor.getUUID()),AdministrativeKind.TREASURY);
    }

    /** Requires the buyer's *actual* town, not a forged client-supplied UUID. */
    public static Result purchase(ServerPlayer actor,String itemId,int amount) {
        if(actor==null||itemId==null||itemId.isBlank()||amount<1||amount>MAX_LOT)
            return Result.NO_MARKET_FOOD;
        var server=actor.getServer();
        var city=KingdomCivicsData.get(server).townOf(actor.getUUID());
        if(city==null || !memberCanBuy(actor,city.id()))return Result.NO_PERMISSION;
        UUID town=city.id();
        var admin=KingdomAdministrationData.get(server);
        var bank=admin.core(town,AdministrativeKind.BANK);
        var level=actor.serverLevel();
        if(bank==null||!bank.dimension().equals(level.dimension().location().toString())
                ||actor.distanceToSqr(BlockPos.of(bank.packedPos()).getX()+.5,
                        BlockPos.of(bank.packedPos()).getY()+.5,
                        BlockPos.of(bank.packedPos()).getZ()+.5)>64
                ||!KingdomWarehouseMarket.get(server).activeMunicipalCore(level,town,AdministrativeKind.BANK))
            return Result.NOT_AT_BANK;
        var forecast=KingdomMunicipalFoodReserves.get(server).evaluate(server,town);
        if(forecast==null||!forecast.complete())return Result.UNKNOWN_RESERVE;
        if(forecast.missing()<amount)return Result.NO_SHORTAGE;
        var orders=KingdomSupplyOrders.get(server);
        var journal=KingdomMunicipalProcurementData.get(server);
        if(journal.active(town,itemId,orders))return Result.ALREADY_ORDERED;
        Container vault=KingdomPhysicalCash.townVault(actor,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return Result.NO_MONEY;
        Proposal quote=select(level,town,itemId,amount,actor.getUUID());
        if(quote==null)return Result.NO_MARKET_FOOD;
        long price=quote.price();
        long physicallyHeld=KingdomPhysicalCash.value(vault);
        long cityBacking=admin.treasuryReserve(town);
        long bankBacking=admin.bankReserve(town);
        // The same bank room also backs resident deposits. Preserve their
        // full claims before and after a municipal purchase.
        if(price<1||price>1_000_000 || city.treasury()<price
                ||!fullyBacked(physicallyHeld,cityBacking,bankBacking,price))
            return Result.NO_MONEY;
        // Everything revalidated on the main server thread before cash withdrawal.
        var receipt=journal.prepare(town,actor.getUUID(),itemId,amount,price,orders);
        if(receipt==null)return Result.ALREADY_ORDERED;
        if(!KingdomPhysicalCash.takeForNpc(vault,price)) {
            journal.cancelUnused(receipt.id());
            return Result.NO_MONEY;
        }
        // Write-ahead status is intentionally not replayable across SavedData files.
        if(!journal.advance(receipt.id(),KingdomMunicipalProcurementData.Phase.PREPARED,
                KingdomMunicipalProcurementData.Phase.CASH_REMOVED,null))
            return Result.PAYMENT_REQUIRES_REVIEW;
        if(!KingdomCivicsData.get(server).withdrawTownAuthorized(town,actor.getUUID(),price))
            return Result.PAYMENT_REQUIRES_REVIEW;
        if(!journal.advance(receipt.id(),KingdomMunicipalProcurementData.Phase.CASH_REMOVED,
                KingdomMunicipalProcurementData.Phase.TREASURY_DEBITED,null))
            return Result.PAYMENT_REQUIRES_REVIEW;
        var order=orders.createMunicipal(town,quote.seller().owner(),
                level.dimension().location().toString(),itemId,quote.seller().pos(),
                quote.destination(),amount,price,quote.lot());
        if(order==null || !orders.escrow(order.id,price,0))
            return Result.PAYMENT_REQUIRES_REVIEW;
        if(!journal.advance(receipt.id(),KingdomMunicipalProcurementData.Phase.TREASURY_DEBITED,
                KingdomMunicipalProcurementData.Phase.ORDERED,order.id))
            return Result.PAYMENT_REQUIRES_REVIEW;
        return Result.SUCCESS;
    }

    /** Full payment never implies stock has arrived: the existing courier must travel. */
    public static boolean authorized(ServerLevel level,KingdomSupplyOrders.Order order) {
        if(order==null||order.mode!=KingdomSupplyOrders.Mode.MUNICIPAL
                ||order.buyer.equals(order.seller)
                ||!order.dimension.equals(level.dimension().location().toString())
                ||order.source.equals(order.destination)
                ||!level.hasChunkAt(order.destination))
            return false;
        var server=level.getServer();
        if(!KingdomMunicipalProcurementData.get(server).authorizedDelivery(order))
            return false;
        var admin=KingdomAdministrationData.get(server);
        var civics=KingdomCivicsData.get(server);
        if(civics.townById(order.buyer)==null
                || !KingdomWarehouseMarket.get(server).activeMunicipalCore(
                    level,order.buyer,AdministrativeKind.CENTRAL_WAREHOUSE))
            return false;
        if(!KingdomMunicipalFoodStorage.destination(
                server,order.buyer,order.dimension,order.destination))return false;
        var city=civics.townAt(level,order.destination);
        if(city==null||!city.id().equals(order.buyer)
                ||!(level.getBlockEntity(order.destination) instanceof Container))return false;
        // Once physical cargo has been picked up, the seller's chunk may
        // unload. Delivery to the public warehouse must still proceed.
        // For another pickup, the seller must be loaded and listed again.
        if(order.pickedUp>order.delivered)return true;
        if(!level.hasChunkAt(order.source))return false;
        boolean registered=false;
        for(var stall:KingdomWarehouseMarket.get(server).all())
            if(stall.owner().equals(order.seller)
                    && stall.dimension().equals(order.dimension)
                    && stall.pos().equals(order.source)){
                registered=true;break;
            }
        return registered && level.getBlockEntity(order.source) instanceof Container;
    }

    public static Proposal select(ServerLevel level,UUID town,String itemId,
                                  int amount,UUID actor) {
        if(amount<1||amount>MAX_LOT)return null;
        var market=KingdomWarehouseMarket.get(level.getServer());
        var orders=KingdomSupplyOrders.get(level.getServer());
        var dim=level.dimension().location().toString();
        var storage=KingdomMunicipalFoodStorage.inspect(level.getServer(),town);
        if(!storage.complete())return null;
        Proposal cheapest=null;
        for(var bound:storage.containers()) {
            if(bound.level()!=level)continue;
            BlockPos destination=bound.pos();
            Container recipient=bound.container();
            var city=KingdomCivicsData.get(level.getServer()).townAt(level,destination);
            if(city==null || !town.equals(city.id()))continue;
            for(var stall:market.all()) {
                if(!dim.equals(stall.dimension())||stall.owner().equals(actor)
                        ||stall.owner().equals(town)||!level.hasChunkAt(stall.pos())
                        ||stall.pos().equals(destination))continue;
                if(!(level.getBlockEntity(stall.pos()) instanceof Container source))continue;
                if(market.nearbyCityMarketTown(level,stall.pos(),8)==null
                        || !KingdomTerritories.get(level.getServer()).canUse(
                            level,stall.pos(),stall.owner())
                        || !KingdomTerritories.get(level.getServer()).canUse(
                            level,stall.pos().above(),stall.owner()))continue;
                if(!level.hasChunkAt(stall.pos().above())
                        ||!(level.getBlockEntity(stall.pos().above()) instanceof Container sellerCashbox))
                    continue;
                long cost;
                try{cost=Math.multiplyExact(stall.copperPerItem(),(long)amount);}
                catch(ArithmeticException ex){continue;}
                if(cost<1||cost>1_000_000
                        ||!KingdomCourierCashbox.canDeposit(sellerCashbox,cost))
                    continue;
                int reserved=orders.reserved(dim,stall.pos(),itemId);
                for(int slot=0;slot<source.getContainerSize();slot++){
                    ItemStack stack=source.getItem(slot);
                    if(stack.isEmpty()||!itemId.equals(
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                            ||!source.canTakeItem(source,slot,stack)
                            ||KingdomNpcNutrition.inspect(stack)==null)continue;
                    // Conservative reservation: never sell a stack if the
                    // merchant has other unfulfilled orders for the item ID.
                    int free=stack.getCount()-reserved;
                    if(free<amount || !fits(recipient,stack,amount))continue;
                    if(cheapest==null||cost<cheapest.price())
                        cheapest=new Proposal(stall,destination,stack,amount,cost);
                }
            }
        }
        return cheapest;
    }

    /** Food IDs actually offered for physical delivery to this town, no virtual catalog. */
    public static List<String> availableFoods(ServerLevel level,UUID town,int limit) {
        if(level==null||town==null||limit<1)return List.of();
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        var market=KingdomWarehouseMarket.get(level.getServer());
        String dimension=level.dimension().location().toString();
        for(var stall:market.all()) {
            if(ids.size()>=64)break;
            if(!dimension.equals(stall.dimension())||!level.hasChunkAt(stall.pos())
                    ||!(level.getBlockEntity(stall.pos()) instanceof Container c))continue;
            for(int i=0;i<c.getContainerSize()&&ids.size()<64;i++){
                ItemStack stack=c.getItem(i);
                if(stack.isEmpty()||KingdomNpcNutrition.inspect(stack)==null)continue;
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        List<String> results=new ArrayList<>();
        for(String id:ids) {
            if(select(level,town,id,1,town)!=null)results.add(id);
            if(results.size()>=Math.min(8,limit))break;
        }
        return List.copyOf(results);
    }

    /** Preserve both municipal cash and all separate citizen bank liabilities. */
    static boolean fullyBacked(long vaultCopper,long townLiability,
                               long bankLiability,long debitCopper) {
        return vaultCopper>=0 && townLiability>=debitCopper && debitCopper>0
                && bankLiability>=0 && vaultCopper>=townLiability
                && vaultCopper-townLiability>=bankLiability;
    }

    static boolean fits(Container chest,ItemStack item,int count){
        if(chest==null||item==null||item.isEmpty()||count<1)return false;
        int remainder=count;
        for(int i=0;i<chest.getContainerSize() && remainder>0;i++){
            if(!chest.canPlaceItem(i,item))continue;
            var existing=chest.getItem(i);
            int free=existing.isEmpty()
                    ?Math.min(chest.getMaxStackSize(),item.getMaxStackSize())
                    :ItemStack.isSameItemSameTags(existing,item)
                    ?Math.max(0,Math.min(chest.getMaxStackSize(),item.getMaxStackSize())-existing.getCount())
                    :0;
            remainder-=Math.min(free,remainder);
        }
        return remainder==0;
    }
}
