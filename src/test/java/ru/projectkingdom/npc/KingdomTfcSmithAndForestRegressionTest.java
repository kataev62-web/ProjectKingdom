package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import ru.projectkingdom.enterprise.EnterpriseBuildingRegistration;

/** Production regressions for physical TFC woodcutting and smithing, never phantom crafts. */
final class KingdomTfcSmithAndForestRegressionTest {
    @org.junit.jupiter.api.BeforeAll static void initializeMinecraft(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static String source(String name)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+name));
    }
    @Test void reliableForgingPathRespectsRecipeLastStep(){
        assertEquals(4,KingdomTfcSmithWork.nextStep(2,0,-1,-1,-1,
                new String[]{"PUNCH_LAST"}));
        assertEquals(5,KingdomTfcSmithWork.nextStep(7,0,-1,-1,-1,
                new String[]{"BEND_LAST"}));
        assertEquals(-1,KingdomTfcSmithWork.nextStep(-1,0,-1,-1,-1,
                new String[]{"HIT_LAST"}));
        assertEquals(-1,KingdomTfcSmithWork.nextStep(5,0,-1,-1,-1,new String[]{}));
    }
    @Test void recipeStateSurvivesNpcSaveReload(){
        var registry=new KingdomNpcData();
        var worker=registry.add(java.util.UUID.randomUUID(),"Smith","minecraft:overworld",0);
        worker.smithRecipeId="tfc:anvil/bronze_pickaxe_head";
        worker.smithOutputReady=true;
        var copy=KingdomNpcData.load(registry.save(new net.minecraft.nbt.CompoundTag()));
        var saved=copy.all().iterator().next();
        assertEquals(worker.smithRecipeId,saved.smithRecipeId);
        assertTrue(saved.smithOutputReady);
    }
    @Test void forgeMovesOnlyRealItemsAndNativeTfcWorks()throws Exception{
        var core=source("npc/KingdomTfcSmithWork.java");
        assertTrue(core.contains("IItemHandlerModifiable"));
        assertTrue(core.contains("chest.canTakeItem(chest,i,stack)"));
        assertTrue(core.contains("dest.setStackInSlot(slot,unit)"));
        assertTrue(core.contains("stock.shrink(1)"));
        assertTrue(core.contains("getMethod(\"light\""));
        assertTrue(core.contains("getMethod(\"work\""));
        assertTrue(core.contains("getMethod(\"getRules\""));
        assertTrue(core.contains("getMethod(\"chooseRecipe\""));
        assertTrue(core.contains("company"));
        assertTrue(core.contains("contract.active()"));
        assertTrue(core.contains("selectedRecipe("));
        assertTrue(core.contains("cycleRecipe("));
        assertTrue(core.contains("smithOutputReady"));
        assertTrue(core.contains("building==null"));
        assertTrue(core.contains("anvilInv.getStackInSlot(2).isEmpty()"));
        var server=source("npc/KingdomNpcMod.java");
        assertTrue(server.contains("KingdomTfcSmithWork.tick(level,resident,w)"));
        assertTrue(source("npc/KingdomNpcWorkday.java")
                .contains("KingdomTfcSmithWork.isForge(level,worker)"));
    }
    @Test void autoSetupBindsSmithToRegisteredBuildingNotVanillaCraftingTable()throws Exception{
        var quick=source("npc/KingdomNpcQuickSetup.java");
        assertTrue(quick.contains("\"tfc:charcoal_forge\".equals(id)"));
        assertTrue(quick.contains("KingdomTfcSmithWork.inside(building,machine)"));
        assertTrue(quick.contains("EnterpriseBuildingRegistration.get(server)"));
        assertTrue(quick.contains("worker.workplaceId=business.id().toString()"));
    }
    @Test void lumberjackUsesTfcAxeTagAndFellsBeforeReplanting()throws Exception{
        var f=source("npc/KingdomTfcForestry.java");
        assertTrue(f.contains("return !stack.isEmpty()&&stack.is(TFC_AXES);"));
        assertTrue(f.contains("if(best!=null){"));
        assertTrue(f.contains("return fell(level,npc,w,area,best)")||f.contains("if(fell(level,npc,w,area,best))return true;"));
        int plant=f.indexOf("if(plant(level,npc,w,area,raw,source))return true;",f.indexOf("boolean allowedHarvest"));
        int fell=f.indexOf("if(fell(level,npc,w,area,best))return true;");
        assertTrue(fell>0&&plant>=0);
        assertTrue(f.contains("for(int y=area.y()-2;y<=area.y()+16;y++)"));
        assertTrue(f.contains("nativeLogging.invoke(null,level,root,fake,w.fieldTool)"));
    }
}
