package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import ru.projectkingdom.enterprise.EnterpriseKind;
import static org.junit.jupiter.api.Assertions.*;

class KingdomProfessionWorkRulesTest {
    @Test void craftingHasStrictIndustryPermissions() {
        assertTrue(KingdomProfessionWorkRules.crafting("smith", EnterpriseKind.SMITHY));
        assertTrue(KingdomProfessionWorkRules.crafting("baker", EnterpriseKind.BAKERY));
        assertTrue(KingdomProfessionWorkRules.crafting("potter", EnterpriseKind.POTTERY));
        assertTrue(KingdomProfessionWorkRules.crafting("carpenter", EnterpriseKind.CONSTRUCTION));
        assertTrue(KingdomProfessionWorkRules.crafting("mason", EnterpriseKind.CONSTRUCTION));
        assertFalse(KingdomProfessionWorkRules.crafting("smith", EnterpriseKind.BAKERY));
        assertFalse(KingdomProfessionWorkRules.crafting("baker", EnterpriseKind.SMITHY));
        assertFalse(KingdomProfessionWorkRules.crafting("weaver", EnterpriseKind.POTTERY));
        assertFalse(KingdomProfessionWorkRules.crafting("priest", EnterpriseKind.BAKERY));
        assertFalse(KingdomProfessionWorkRules.crafting(null, EnterpriseKind.SMITHY));
    }

    @Test void tfcQuernIsBakeryOnlyAndDoesNotMakeOtherTradesReal() {
        assertTrue(KingdomProfessionWorkRules.grill("baker", EnterpriseKind.BAKERY));
        assertTrue(KingdomProfessionWorkRules.grill("cook", EnterpriseKind.BAKERY));
        assertTrue(KingdomProfessionWorkRules.grill("butcher", EnterpriseKind.BAKERY));
        assertFalse(KingdomProfessionWorkRules.grill("smith", EnterpriseKind.BAKERY));
        assertFalse(KingdomProfessionWorkRules.grill("cook", EnterpriseKind.SMITHY));
        assertTrue(KingdomProfessionWorkRules.quern("baker", EnterpriseKind.BAKERY));
        assertFalse(KingdomProfessionWorkRules.quern("baker", EnterpriseKind.POTTERY));
        assertFalse(KingdomProfessionWorkRules.quern("smith", EnterpriseKind.BAKERY));
        assertTrue(KingdomProfessionWorkRules.executable("baker"));
        assertFalse(KingdomProfessionWorkRules.executable("weaver"));
        assertFalse(KingdomProfessionWorkRules.executable("farrier"));
    }

    @Test void allProfessionIdsAreAcceptedByOldNpcCommand() {
        for (var profession : KingdomProfessionRegistry.all())
            assertEquals(profession.id(), NpcEconomy.normalizeJob(profession.id()));
        assertThrows(IllegalArgumentException.class, () -> NpcEconomy.normalizeJob("invalid_job"));
    }
}
