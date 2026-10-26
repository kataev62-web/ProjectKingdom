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

    public static Stock inspect(ServerLevel level,UUID enterprise){
        var firms=KingdomEnterpriseData.get(level.getServer());
        if(firms.kind(enterprise)!=EnterpriseKind.TAVERN)return new Stock(0,false,"Не таверна");
        var view=EnterpriseAccessService.snapshot(level,firms,enterprise,EnterpriseBindingRole.RAW_STORAGE);
        if(view.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE || view.container()==null)
            return new Stock(0,false,"Привяжите к таверне сундук с продуктами");
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
        if(view.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE||view.container()==null)return null;
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
        return KingdomNpcNutrition.inspect(stack)!=null || stack.isEdible();
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
