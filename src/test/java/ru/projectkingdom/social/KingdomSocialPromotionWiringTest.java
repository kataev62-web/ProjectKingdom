package ru.projectkingdom.social;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSocialPromotionWiringTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+relative));
    }
    @Test void actionUsesLiveServerSideValidationNotClientRank() throws Exception {
        String packet=source("administration/AdministrativeActionPacket.java");
        assertTrue(packet.contains("SOCIAL_PROMOTE"));
        assertTrue(packet.contains("menu.stillValid(sender)"));
        assertTrue(packet.contains("KingdomSocialPromotionService.promote(sender,packet.townId)"));
        String service=source("social/KingdomSocialPromotionService.java");
        assertTrue(service.contains("preview(player,townId)"));
        assertTrue(service.contains("KingdomCurrency.debit(player,amount)"));
        assertTrue(service.contains("civics.depositTownTo(townId,player.getUUID(),amount)"));
        assertTrue(service.contains("KingdomSocialData.get(server).grantVerified"));
    }
    @Test void publicSocialTabUsesBoundTownHallWithoutGrantingAdmin() throws Exception {
        String layout=source("administration/AdministrativeMenuLayout.java");
        String menu=source("administration/AdministrativeCoreMenu.java");
        String screen=source("administration/AdministrativeCoreScreen.java");
        assertTrue(layout.contains("Tab.OFFICES,Tab.SOCIAL"));
        assertTrue(menu.contains("KingdomSocialPromotionService.preview(player,town.id())"));
        assertTrue(menu.contains("social.write(buffer)"));
        assertTrue(screen.contains("promoteSocial.active=menu.social().ready()"));
        assertTrue(screen.contains("case SOCIAL->renderSocial(g)"));
    }
}
