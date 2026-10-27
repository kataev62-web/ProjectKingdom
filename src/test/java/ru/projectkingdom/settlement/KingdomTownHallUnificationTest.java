package ru.projectkingdom.settlement;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class KingdomTownHallUnificationTest {
    private static String src(String path)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }
    @Test void rankAdvancesOnlyOneStepAndSurvivesSave() {
        UUID id=UUID.randomUUID();
        var ranks=new KingdomSettlementRanks();
        ranks.initializeHamlet(id);
        var hamlet=KingdomSettlementGrowth.Rank.HAMLET;
        var village=KingdomSettlementGrowth.Rank.VILLAGE;
        var city=KingdomSettlementGrowth.Rank.CITY;
        assertFalse(ranks.promote(id,hamlet,city));
        assertTrue(ranks.promote(id,hamlet,village));
        assertFalse(ranks.promote(id,hamlet,village));
        assertEquals(village,KingdomSettlementRanks.fromTag(ranks.save(new CompoundTag())).current(id,hamlet));
    }
    @Test void unifiedTownHallMaintainsLegacyBlockData()throws Exception{
        assertTrue(src("administration/AdministrativeCoreBlock.java").contains("core.markFounding(player.getUUID())"));
        assertTrue(src("settlement/KingdomSettlementFounding.java").contains("AdministrativeCoreBinding"));
        assertTrue(src("settlement/KingdomSettlementFounding.java").contains("KingdomSettlementRanks.get(server).initializeHamlet"));
        assertTrue(src("administration/AdministrativeActionPacket.java").contains("PROMOTE_TOWN"));
        assertTrue(src("administration/AdministrativeCoreScreen.java").contains("Повысить до:"));
        assertTrue(src("npc/KingdomCreativeTabs.java").contains("\"settlement_charter\".equals(id.getPath())"));
        assertNull(getClass().getResourceAsStream("/data/kingdomnpc/recipes/settlement_charter.json"));
    }
    @Test void requirementsAreSequential() {
        assertEquals(KingdomSettlementGrowth.Rank.HAMLET,
                new KingdomSettlementGrowth.Progress(4,1,3,false).eligible());
        assertEquals(KingdomSettlementGrowth.Rank.VILLAGE,
                new KingdomSettlementGrowth.Progress(4,1,3,true).eligible());
    }
}
