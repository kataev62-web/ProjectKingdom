package ru.projectkingdom.social;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSocialCrossSystemIntegrationTest {
    private static String src(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+file));
    }
    @Test void landPaymentsAndRankPromotionDoNotReuseActionNumber() throws Exception {
        var source=src("administration/AdministrativeActionPacket.java");
        assertTrue(source.contains("PAY_RESERVED_LAND=204"));
        assertTrue(source.contains("SOCIAL_PROMOTE=220"));
        assertTrue(source.contains("KingdomSocialPromotionService.promote(sender,packet.townId)"));
        assertTrue(source.contains("KingdomLandReservations.get(sender.getServer()).payAtHall"));
    }
    @Test void mapArchitectAndBookNetworkPacketsHaveDistinctIds() throws Exception {
        String net=src("network/KingdomNetwork.java");
        var pattern=Pattern.compile("CHANNEL\\.registerMessage\\((\\d+),");
        var matcher=pattern.matcher(net);
        var ids=new HashSet<Integer>();
        int count=0;
        while(matcher.find()){
            assertTrue(ids.add(Integer.parseInt(matcher.group(1))),"Duplicate Forge channel id: "+matcher.group(1));
            count++;
        }
        assertTrue(count>=15,"All existing atlas and architect packets must survive");
        assertTrue(net.contains("PROTOCOL=\"2\""));
    }
    @Test void physicalTownVaultIsUsedWithoutBypassingNewTownReservation() throws Exception {
        var service=src("social/KingdomSocialPromotionService.java");
        var payment=src("administration/AdministrativeActionPacket.java");
        assertTrue(service.contains("KingdomPhysicalCash.deposit(player,vault,amount)"));
        assertTrue(service.contains("KingdomPhysicalCash.townVault"));
        assertTrue(service.contains("KingdomPhysicalCash.withdraw(player,vault,amount)"));
        assertTrue(payment.contains("if(packet.action==PAY_RESERVED_LAND)"));
    }
    @Test void nonMayorCitizensCanSeeTheirOwnSocialPages() throws Exception {
        var screen=src("administration/AdministrativeCoreScreen.java");
        var menu=src("administration/AdministrativeCoreMenu.java");
        assertTrue(screen.contains("menu.member()?List.of("));
        assertTrue(screen.contains("AdministrativeMenuLayout.Tab.SOCIAL"));
        assertTrue(screen.contains("AdministrativeMenuLayout.Tab.CIVIC"));
        assertTrue(menu.contains("social.write(buffer);civic.write(buffer)"));
        assertTrue(menu.contains("buffer.writeBoolean(member);buffer.writeBoolean(canJoin)"));
    }
}
