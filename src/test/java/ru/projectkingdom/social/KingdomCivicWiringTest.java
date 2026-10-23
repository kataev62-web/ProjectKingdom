package ru.projectkingdom.social;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomCivicWiringTest {
    private static String src(String path)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }
    @Test void honorsRequireActiveTownHallAndActualLegalAuthority()throws Exception{
        String packet=src("administration/AdministrativeTextPacket.java");
        String service=src("social/KingdomCivicService.java");
        assertTrue(packet.contains("menu.stillValid(sender)"));
        assertTrue(packet.contains("honor_grant"));
        assertTrue(packet.contains("KingdomCivicService.award(sender"));
        assertTrue(service.contains("mayCouncil(actor,townId)"));
        assertTrue(service.contains("civics.isMember(townId,recipient)"));
        assertTrue(service.contains("KingdomSocialData.get(server).rank(recipient)"));
        assertTrue(service.contains("KingdomReputationData.get(server).score(townId,recipient)"));
    }
    @Test void petitionsCannotReplayAgainstNewItemsWithoutFreshServerSnapshot()throws Exception{
        String menu=src("administration/AdministrativeCoreMenu.java");
        String packet=src("administration/AdministrativeActionPacket.java");
        assertTrue(menu.contains("KingdomCivicService.preview(player,town.id())"));
        assertTrue(menu.contains("civic.write(buffer)"));
        assertTrue(packet.contains("menu.civic().pendingId()"));
        assertTrue(packet.contains("KingdomCivicService.review("));
    }
    @Test void sovereignRequiresActualNationRulerAndSameNationSubject()throws Exception{
        String service=src("social/KingdomCivicService.java");
        String handler=src("administration/AdministrativeTextPacket.java");
        assertTrue(handler.contains("realm_award"));
        assertTrue(handler.contains("KingdomCivicService.awardRealm(sender"));
        assertTrue(service.contains("realm.ruler()"));
        assertTrue(service.contains("realm.id().equals(targetRealm.id())"));
        assertTrue(service.contains("KingdomNationalHonorsData.get(server).grantVerified"));
    }
    @Test void npcAcknowledgesHonorButDoesNotMintOrGrantOffices()throws Exception{
        String npc=src("npc/NpcDialogueService.java");
        assertTrue(npc.contains("KingdomCivicHonorsData.get(player.getServer())"));
        assertTrue(npc.contains("localHonor.russian()"));
        String honors=src("social/KingdomCivicHonorsData.java");
        assertFalse(honors.contains("KingdomCurrency.credit("));
    }
}
