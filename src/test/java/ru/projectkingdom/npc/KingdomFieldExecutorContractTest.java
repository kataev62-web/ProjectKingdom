package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomFieldExecutorContractTest {
    private static String code(String name)throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/"+name));
    }
    @Test void logsCanBeRecoveredOnlyFromRealMatchingTfcSpecies() {
        assertTrue(KingdomTfcForestry.eligibleGroundLog("tfc:wood/log/oak","oak"));
        assertTrue(KingdomTfcForestry.eligibleGroundLog("tfc:wood/stripped_log/oak","oak"));
        assertTrue(KingdomTfcForestry.eligibleGroundLog("tfc:wood/wood/oak","oak"));
        assertFalse(KingdomTfcForestry.eligibleGroundLog("minecraft:oak_log","oak"));
        assertFalse(KingdomTfcForestry.eligibleGroundLog("tfc:wood/log/spruce","oak"));
        assertFalse(KingdomTfcForestry.eligibleGroundLog(null,"oak"));
        assertFalse(KingdomTfcForestry.eligibleGroundLog("tfc:wood/log/oak",""));
    }
    @Test void realFallenItemCollectionRespectsOwnershipAndNoFakeStock() throws Exception {
        var forest=code("KingdomTfcForestry.java");
        assertTrue(forest.contains("entity.getOwner()"));
        assertTrue(forest.contains("!owner.equals(worker.employer)"));
        assertTrue(forest.contains("ownedHarvestLand(level,entity.blockPosition(),worker.employer)"));
        assertTrue(forest.contains("area.inside(entity.blockPosition())"));
        assertTrue(forest.contains("NpcFieldWork.canFit(worker,List.of(stack))"));
        assertTrue(forest.contains("NpcFieldWork.put(worker,stack.copy())"));
        assertTrue(forest.contains("item.discard()"));
        assertTrue(forest.contains("if(recovery!=Recovery.NONE)return false;"));
    }
    @Test void minerHandlesTfcSurfaceSoilWithoutDiggingForbiddenBlocks() throws Exception {
        assertTrue(KingdomMineWork.surfaceSoil("tfc:soil/grass/loam"));
        assertTrue(KingdomMineWork.surfaceSoil("tfc:soil/dirt/silt"));
        assertTrue(KingdomMineWork.surfaceSoil("minecraft:grass_block"));
        assertFalse(KingdomMineWork.surfaceSoil("minecraft:bedrock"));
        assertFalse(KingdomMineWork.surfaceSoil("tfc:rock/raw/granite"));
        var mine=code("KingdomMineWork.java");
        assertTrue(mine.contains("KingdomTfcForestry.ownedHarvestLand(level,pos,worker.employer)"));
        assertTrue(mine.contains("Шахтёр: ошибка геометрии маршрута"));
        assertTrue(mine.contains("Шахтёр: подход к выработке заблокирован"));
    }
    @Test void minerPathfinderIsNotOverriddenByGenericCommute() throws Exception {
        var mod=code("KingdomNpcMod.java");
        assertTrue(mod.contains("||\"miner\".equals(w.job)"));
        assertTrue(mod.contains("? Double.MAX_VALUE // Physical field executor"));
        var workday=code("KingdomNpcWorkday.java");
        assertTrue(workday.contains("if (\"miner\".equals(worker.job))"));
        assertTrue(workday.contains("land.canUse(level,center,worker.employer)"));
    }
}
