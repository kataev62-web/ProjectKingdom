package ru.projectkingdom.administration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Contract checks for a privileged, destructive operation. */
final class KingdomAdminEnterpriseConsoleTest {
    private static String file(String path)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }
    @Test void onlyServerOperatorsCanOpenAndMutate()throws Exception{
        String menu=file("administration/KingdomAdminEnterpriseMenu.java");
        String packet=file("administration/KingdomAdminEnterprisePacket.java");
        String cmd=file("npc/KingdomNpcMod.java");
        assertTrue(cmd.contains("Commands.literal(\"kingdomadmin\")"));
        assertTrue(cmd.contains("source.hasPermission(2)"));
        assertTrue(menu.contains("operator.hasPermissions(2)"));
        assertTrue(packet.contains("!actor.hasPermissions(2)"));
        assertTrue(packet.contains("menu.contains(p.enterprise)"));
        assertTrue(packet.contains("menu.consumeArm(p.enterprise"));
        assertTrue(menu.contains("now-armedAt<=20_000"));
    }
    @Test void adminDoesNotBypassClosureSafeguards()throws Exception{
        String registry=file("npc/KingdomEnterpriseData.java");
        String service=file("administration/KingdomAdminEnterpriseService.java");
        assertTrue(registry.contains("return closeBusinessInternal(operator,business,pos,true)"));
        assertTrue(registry.contains("if(firm.cash()!=0)return ClosureStatus.CASH_REMAINING"));
        assertTrue(registry.contains("if(unresolved(business))return ClosureStatus.TRADE_NOT_SETTLED"));
        assertTrue(registry.contains("if(updated.owed()!=0)return ClosureStatus.STAFF_DEBT"));
        assertTrue(registry.contains("if(!container.isEmpty())return ClosureStatus.STORAGE_NOT_EMPTY"));
        assertTrue(registry.contains("if(administrator){"));
        assertTrue(registry.contains("Block.popResource(level,pos,fresh)"));
        assertTrue(service.contains("companies.adminCloseBusiness(operator,firmId"));
        assertTrue(service.contains("KingdomAdminEnterpriseAudit.get(server).recordVerified"));
    }
    @Test void territoryIsDerivedFromActualPlacedRegisteredCore()throws Exception{
        String service=file("administration/KingdomAdminEnterpriseService.java");
        assertTrue(service.contains("companies.core(enterprise)"));
        assertTrue(service.contains("KingdomCivicsData.get(server).plot(level,at)"));
        assertTrue(service.contains("enterprise.equals(actual.enterpriseId())"));
        assertTrue(service.contains("!located.loaded()"));
        assertTrue(service.contains("operator.serverLevel().dimension()"));
    }
    @Test void screenIsRegisteredWithUniquePacketId()throws Exception{
        String registry=file("administration/KingdomAdministrationRegistry.java");
        String client=file("core/KingdomBookClient.java");
        String network=file("network/KingdomNetwork.java");
        assertTrue(registry.contains("ADMIN_ENTERPRISE_MENU"));
        assertTrue(client.contains("KingdomAdminEnterpriseScreen::new"));
        assertTrue(network.contains("KingdomAdminEnterprisePacket::handle"));
        var match=Pattern.compile("CHANNEL\\.registerMessage\\((\\d+),").matcher(network);
        var ids=new HashSet<Integer>();
        int n=0;while(match.find()){assertTrue(ids.add(Integer.parseInt(match.group(1))));n++;}
        assertTrue(n>15);
    }
}
