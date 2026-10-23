package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomCivicsData;

/** Regressions for public town hall admission without opening privileged controls. */
final class TownHallVisitorTest {
    @Test void townHallIsPublicButOtherPrivateServicesStayRestricted() {
        assertTrue(AdministrativePermissionService.mayUsePublic(AdministrativeKind.TOWN_HALL));
        assertTrue(AdministrativePermissionService.mayUsePublic(AdministrativeKind.BANK));
        assertFalse(AdministrativePermissionService.mayUsePublic(AdministrativeKind.TREASURY));
        assertFalse(AdministrativePermissionService.mayUsePublic(AdministrativeKind.LAND_OFFICE));
        assertFalse(AdministrativePermissionService.mayUsePublic(AdministrativeKind.PRISON));
        var outsider=UUID.randomUUID();
        assertFalse(AdministrativePermissionService.mayManage(outsider,
                UUID.randomUUID(),UUID.randomUUID(),Set.of(),AdministrativeKind.TOWN_HALL));
    }

    @Test void joiningIsOneTownOnlyAndNeverMakesApplicantMayor() {
        var civic=new KingdomCivicsData();
        UUID mayorOne=UUID.randomUUID(),mayorTwo=UUID.randomUUID(),visitor=UUID.randomUUID();
        var first=civic.foundTown(mayorOne,"Первый");
        var second=civic.foundTown(mayorTwo,"Второй");
        assertNotNull(first);assertNotNull(second);
        assertNull(civic.townOf(visitor));
        assertTrue(civic.joinTown(visitor,first.id()));
        assertFalse(civic.joinTown(visitor,second.id()));
        assertEquals(first.id(),civic.townOf(visitor).id());
        assertFalse(civic.townOf(visitor).mayor().equals(visitor));
        assertTrue(civic.isMember(first.id(),visitor));
        assertFalse(civic.isMember(second.id(),visitor));
    }

    @Test void townHallExemptionRequiresPhysicalRegisteredCoreAndJoinRequiresOpenMenu() throws Exception {
        String guard=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomClaimProtection.java"));
        assertTrue(guard.contains("core.kind()!=AdministrativeKind.TOWN_HALL"));
        assertTrue(guard.contains("KingdomAdministrationData.get(player.getServer())"));
        assertTrue(guard.contains("binding.packedPos()==pos.asLong()"));
        String packet=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java"));
        assertTrue(packet.contains("sender.containerMenu instanceof AdministrativeCoreMenu"));
        assertTrue(packet.contains("menu.stillValid(sender)"));
        assertTrue(packet.contains("packet.kind!=AdministrativeKind.TOWN_HALL"));
        assertTrue(packet.contains("civics.townOf(sender.getUUID())!=null"));
        assertTrue(packet.contains("civics.joinTown(sender.getUUID(),town.id())"));
        assertTrue(packet.contains("!publicFinance&&!promote&&!petitionFile&&!petitionReview&&!menu.manager()"));
    }
}
