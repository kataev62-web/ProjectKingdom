package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSmithyProductionContractTest {
    private static String source(String path)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }
    public static final class Recipe {
        private final String id;
        public Recipe(String id){this.id=id;}
        public String getId(){return id;}
    }

    @Test void playerCanSelectOnlyNativeAnvilRecipeNotFabricatedIngredient() throws Exception{
        var smith=source("npc/KingdomTfcSmithWork.java");
        assertTrue(smith.contains("AnvilRecipe"));
        assertTrue(smith.contains("getMethod(\"getAll\""));
        assertTrue(smith.contains("getMethod(\"getTier\")"));
        assertTrue(smith.contains("!recipeId.startsWith(\"tfc:\")"));
        assertTrue(smith.contains("forgeCapability(stack)==null"));
        assertTrue(smith.contains("raw.canTakeItem(raw,i,stack)"));
        assertTrue(smith.contains("selectUniqueAnvilRecipePreflight(level,anvil,stack,w)"));
        assertTrue(smith.contains("order.input().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())"));
        Method select=KingdomTfcSmithWork.class.getDeclaredMethod("selectedRecipe",List.class,String.class);
        select.setAccessible(true);
        var real=new Recipe("tfc:anvil/steel_sword_blade");
        assertSame(real,select.invoke(null,List.of(real),real.getId()));
        assertNull(select.invoke(null,List.of(real),"tfc:anvil/iron_pickaxe_head"));
    }

    @Test void realForgeAndAnvilMoveAndDeliveryAreBoundedByOnePhysicalOrder()throws Exception{
        String smith=source("npc/KingdomTfcSmithWork.java");
        assertTrue(smith.contains("KingdomSmithyOrdersData.get(server)"));
        assertTrue(smith.contains("!order.worker().equals(w.entityId)"));
        assertTrue(smith.contains("order.phase()==KingdomSmithyOrdersData.Phase.READY"));
        assertTrue(smith.contains("actual.equals(order.pendingItem())"));
        assertTrue(smith.contains("inAnvil.getCount()!=order.pendingCount()"));
        assertTrue(smith.contains("if(!intoChest(finished,inAnvil))"));
        assertTrue(smith.contains("anvilInv.setStackInSlot(0,ItemStack.EMPTY)"));
        assertTrue(smith.contains("orders.delivered(company,w.entityId,actual,inAnvil.getCount())"));
        assertTrue(smith.contains("forgeInv.setStackInSlot(slot,ItemStack.EMPTY)"));
        assertTrue(smith.contains("if(!hotEnough(inAnvil))"));
        assertTrue(smith.contains("heatingSlots.setStackInSlot(slot,inAnvil.copy())"));
        assertTrue(smith.contains("anvilInv.setStackInSlot(0,ItemStack.EMPTY)"));
        assertTrue(smith.contains("if(!alreadyHeating)"),
                "One-item orders must not draw multiple metal inputs while heating");
        assertTrue(smith.contains("stock.shrink(1);chest.setChanged();machine.setChanged()"));
        assertTrue(smith.contains("getMethod(\"work\""));
        assertFalse(smith.contains("new ItemStack(Items.IRON_INGOT)"));
    }

    @Test void screenRechecksOwnerCoreAndCurrentRecipeBeforeOrderCreation()throws Exception{
        String menu=source("npc/KingdomSmithyProductionMenu.java");
        assertTrue(menu.contains("firms.mayManage(this.player.getUUID(),enterprise)"));
        assertTrue(menu.contains("binding.packedPos()!=corePos.asLong()"));
        assertTrue(menu.contains("be.bound()&&enterprise.equals(be.enterpriseId())"));
        assertTrue(menu.contains("KingdomTfcSmithWork.availableRecipes(player.serverLevel(),w).contains(selected)"));
        assertTrue(menu.contains("KingdomTfcSmithWork.idleAnvil(player.serverLevel(),w)"));
        assertTrue(menu.contains("orders.create(enterprise,w.entityId,selected.recipeId(),selected.inputId(),quantity)"));
        String core=source("enterprise/EnterpriseCoreMenu.java");
        assertTrue(core.contains("OPEN_SMITHY_PRODUCTION=128"));
        assertTrue(core.contains("kind==EnterpriseKind.SMITHY && data.mayManage(server.getUUID(),enterpriseId)"));
        String screen=source("enterprise/EnterpriseCoreScreen.java");
        assertTrue(screen.contains("Заказы кузницы TFC"));
    }

    @Test void castingCannotBeMistakenForNativeAnvilOutput()throws Exception{
        String smith=source("npc/KingdomTfcSmithWork.java");
        String tick=smith.substring(smith.indexOf("public static boolean tick("));
        assertFalse(tick.contains("cooledFilledMold(mold)"),
            "Casting vessels must not count toward a selected anvil recipe");
        assertFalse(tick.contains("fromChest(raw,forge,forgeInv,slot,\n                stack->!forgeFuel(stack))"));
        assertTrue(source("npc/KingdomSmithyProductionMenu.java")
                .contains("автоматическое литьё пока не реализовано"));
    }
}
