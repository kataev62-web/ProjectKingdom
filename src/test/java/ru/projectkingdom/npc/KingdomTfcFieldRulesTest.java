package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomTfcFieldRulesTest {
    @Test void simpleCropsOnlyNotPickableOrWild() {
        assertTrue(KingdomTfcFieldRules.simpleCrop("tfc:crop/barley",
            "net.dries007.tfc.common.blocks.crop.DefaultCropBlock$1"));
        assertFalse(KingdomTfcFieldRules.simpleCrop("tfc:crop/tomato",
            "net.dries007.tfc.common.blocks.crop.PickableCropBlock$1"));
        assertFalse(KingdomTfcFieldRules.simpleCrop("tfc:wild_crop/barley",
            "net.dries007.tfc.common.blocks.crop.DefaultCropBlock$1"));
        assertEquals("tfc:seeds/barley", KingdomTfcFieldRules.seeds("tfc:crop/barley"));
    }

    @Test void onlyRealTfcOreGradeBlocksAreGathered() {
        assertTrue(KingdomTfcFieldRules.ore("tfc:ore/normal_bismuthinite"));
        assertTrue(KingdomTfcFieldRules.ore("tfc:ore/poor_native_copper"));
        assertTrue(KingdomTfcFieldRules.ore("tfc:ore/rich_native_gold"));
        assertFalse(KingdomTfcFieldRules.ore("tfc:rock/raw/basalt"));
        assertFalse(KingdomTfcFieldRules.ore("tfc:ore/sample"));
        assertFalse(KingdomTfcFieldRules.ore("minecraft:iron_ore"));
    }
}
