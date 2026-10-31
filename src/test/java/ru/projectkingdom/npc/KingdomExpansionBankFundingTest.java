package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** No minting, no silent personal withdrawal, and two-step explicit funding. */
final class KingdomExpansionBankFundingTest {
    private static String src(String file)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+file));
    }
    @Test void surplusIsOnlyPhysicalMoneyNotAlreadyClaimed() throws Exception {
        assertEquals(250,KingdomBankData.freePhysicalCopper(1000,600,150,0));
        assertEquals(0,KingdomBankData.freePhysicalCopper(750,600,150,0));
        assertEquals(-1,KingdomBankData.freePhysicalCopper(700,600,150,0));
        assertEquals(-1,KingdomBankData.freePhysicalCopper(1000,Long.MAX_VALUE,1,0));
        assertEquals(-1,KingdomBankData.freePhysicalCopper(-1,0,0,0));
        assertEquals(-1,KingdomBankData.freePhysicalCopper(100,-1,0,0));
        assertEquals(50,KingdomBankData.freePhysicalCopper(1000,600,150,200));
        assertEquals(-1,KingdomBankData.freePhysicalCopper(750,600,150,200));
        assertTrue(src("administration/KingdomAdministrationData.java").contains("heldExpansionProceeds"));
    }
    @Test void movingPersonalBankReceiptChangesOwnershipWithoutCreatingCash() {
        var source=new TownBankFunds.Balances(700,700,0,0,700);
        var next=TownBankFunds.plan(source,TownBankFunds.Operation.DONATE,400);
        assertNotNull(next);
        assertEquals(300,next.personal());
        assertEquals(300,next.personalReserve());
        assertEquals(400,next.municipal());
        assertEquals(400,next.municipalReserve());
        assertEquals(700,next.physical());
    }
    @Test void mapRequiresExplicitSeparateConfirmationAndShowsBalances()throws Exception{
        String map=src("npc/KingdomCityExpansionMapMenu.java");
        assertTrue(map.contains("confirmedFunding=Set.copyOf(planned)"));
        assertTrue(map.contains("fundExpansionFromPersonal(viewer,cityId,shortage)"));
        assertTrue(map.contains("case 42 -> { fundSelectedPlan()"));
        assertTrue(map.contains("municipalBalance(town)"));
        assertTrue(map.contains("Теперь нажмите «Купить земли»"));
        String bank=src("npc/KingdomBankData.java");
        assertTrue(bank.contains("KingdomPhysicalCash.townVault(player,town"));
        assertTrue(bank.contains("TownBankFunds.Operation.DONATE"));
        assertTrue(bank.contains("creditTownBankFunds(town,amount)"));
        assertTrue(bank.contains("withdrawBankReserve(town,amount)"));
        String gui=src("administration/AdministrativeCoreScreen.java");
        assertTrue(gui.contains("bank_vault_to_town"));
        String packet=src("administration/AdministrativeTextPacket.java");
        assertTrue(packet.contains("assignFreeVaultCoinsToTown"));
    }
}
