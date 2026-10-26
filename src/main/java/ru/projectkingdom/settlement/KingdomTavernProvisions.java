package ru.projectkingdom.settlement;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import ru.projectkingdom.enterprise.EnterpriseAccessService;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomFoodFreshness;
import ru.projectkingdom.npc.KingdomNpcNutrition;

/**
 * Genuine per-enterprise TFC/vanilla-edible supplies in a separately bound
 * RAW_STORAGE chest. Never estimates imaginary supplies or counts rotten food.
 * Cashboxes are coin-only and are not used for meals.
 */
public final class KingdomTavernProvisions {
    public static final int MIN_MEALS_TO_ATTRACT=2;
    public record Stock(int meals, boolean ready, String issue) {}
    public record Portion(Container container,int slot,ItemStack item,
                          KingdomNpcNutrition.Meal nutrition) {}
    private KingdomTavernProvisions() {}

    /** Supplies must actually be stored inside the tavern, not at a remote
     * warehouse or a rented guest room. Shared double chests are checked too. */
    private static boolean pantryInsideTavern(ServerLevel level,UUID enterprise,
                                              net.minecraft.core.BlockPos storage){
        var companies=KingdomEnterpriseData.get(level.getServer());
        var registered=companies.core(enterprise);
        if(registered==null||!registered.active()||storage==null
                ||!registered.dimension().equals(level.dimension().location().toString()))
            return false;
        var desk=net.minecraft.core.BlockPos.of(registered.packedPos());
        if(desk.distSqr(storage)>144)return false;
        var properties=ru.projectkingdom.property.KingdomRentalsData.get(level.getServer());
        var inn=properties.atBuilding(registered.dimension(),desk);
        if(inn!=null&&!inn.bounds().contains(storage))return false;
        if(properties.atRoom(registered.dimension(),storage)!=null)return false;
        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var other=storage.relative(direction);
            if(ru.projectkingdom.npc.KingdomContainerAccess.samePhysicalStorage(level,storage,other)
                    &&properties.atRoom(registered.dimension(),other)!=null)
                return false;
        }
        return true;
    }

    public static Stock inspect(ServerLevel level,UUID enterprise){
        var firms=KingdomEnterpriseData.get(level.getServer());
        if(firms.kind(enterprise)!=EnterpriseKind.TAVERN)return new Stock(0,false,"Не таверна");
        var view=EnterpriseAccessService.snapshot(level,firms,enterprise,EnterpriseBindingRole.RAW_STORAGE);
        if(view.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE || view.container()==null)
            return new Stock(0,false,"Привяжите к таверне сундук с продуктами");
        if(!pantryInsideTavern(level,enterprise,view.position()))
            return new Stock(0,false,"Продуктовый сундук должен находиться внутри таверны, до 12 блоков от стойки");
        Container storage=view.container();
        int quantity=0;
        for(int slot=0;slot<storage.getContainerSize();slot++){
            var stack=storage.getItem(slot);
            if(!edible(stack)||!storage.canTakeItem(storage,slot,stack))continue;
            quantity=Math.min(32767,quantity+stack.getCount());
        }
        if(quantity<MIN_MEALS_TO_ATTRACT)
            return new Stock(quantity,false,"Нужно хотя бы 2 свежие порции еды в сундуке");
        return new Stock(quantity,true,"Еда готова для прибывающих жителей");
    }

    public static Portion choose(ServerLevel level,UUID enterprise){
        var firms=KingdomEnterpriseData.get(level.getServer());
        if(firms.kind(enterprise)!=EnterpriseKind.TAVERN)return null;
        var view=EnterpriseAccessService.snapshot(level,firms,enterprise,EnterpriseBindingRole.RAW_STORAGE);
        if(view.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE||view.container()==null
                ||!pantryInsideTavern(level,enterprise,view.position()))return null;
        Container box=view.container();
        int slot=-1;
        long earliest=Long.MAX_VALUE;
        for(int i=0;i<box.getContainerSize();i++){
            ItemStack stack=box.getItem(i);
            if(!edible(stack)||!box.canTakeItem(box,i,stack))continue;
            long expiry=KingdomFoodFreshness.expiry(stack);
            if(slot<0||expiry<earliest){slot=i;earliest=expiry;}
        }
        if(slot<0)return null;
        ItemStack item=box.getItem(slot).copyWithCount(1);
        return new Portion(box,slot,item,KingdomNpcNutrition.inspect(item));
    }

    public static boolean edible(ItemStack stack) {
        if(stack==null||stack.isEmpty()||KingdomNpcNutrition.spoiled(stack))return false;
        if(KingdomNpcNutrition.inspect(stack)!=null)return true;
        // When TFC reflection is unavailable, do not accidentally treat its
        // modded spoiled/unknown stacks as safe vanilla food.
        return stack.isEdible() && "minecraft".equals(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    /**
     * Remove exactly one original food stack. If another system changed the
     * slot since choose(), refuse rather than consuming a different resource.
     * Caller must hold the server tick and should spawn NPC only on success.
     */
    public static boolean consume(Portion portion){
        if(portion==null||portion.slot()<0)return false;
        Container box=portion.container();
        ItemStack previous=box.getItem(portion.slot());
        if(previous.isEmpty()||!ItemStack.isSameItemSameTags(previous,portion.item())
                ||!box.canTakeItem(box,portion.slot(),previous))return false;
        ItemStack removed=box.removeItem(portion.slot(),1);
        if(removed.isEmpty())return false;
        if(!ItemStack.isSameItemSameTags(removed,portion.item()) || removed.getCount()!=1)
            throw new IllegalStateException("Tavern food storage changed during reservation");
        box.setChanged();return true;
    }
}
