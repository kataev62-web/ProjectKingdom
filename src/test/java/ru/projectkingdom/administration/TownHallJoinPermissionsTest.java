package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomCivicsData;

/** Verified town hall membership and exclusive mayor handover regressions (#81). */
final class TownHallJoinPermissionsTest {
    private static KingdomCivicsData roundTrip(KingdomCivicsData original) throws Exception {
        // The SavedData loader is private; use the production serializer, not a mock.
        Method load = KingdomCivicsData.class.getDeclaredMethod("load", CompoundTag.class);
        load.setAccessible(true);
        return (KingdomCivicsData) load.invoke(null, original.save(new CompoundTag()));
    }

    @Test void residentCitizenshipPersistsAndNeverJoinsTwoTowns() throws Exception {
        KingdomCivicsData civics = new KingdomCivicsData();
        UUID founderA = UUID.randomUUID(), founderB = UUID.randomUUID(), visitor = UUID.randomUUID();
        var a = civics.foundTown(founderA, "Авалон");
        var b = civics.foundTown(founderB, "Другой город");
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(civics.joinTown(visitor, a.id()));
        assertFalse(civics.joinTown(visitor, a.id()), "Repeat click cannot enroll twice");
        assertFalse(civics.joinTown(visitor, b.id()), "An existing resident cannot change town on click");
        assertFalse(civics.joinTown(founderA, b.id()), "An existing founder cannot join another town");
        assertNull(civics.foundTown(visitor, "Третий город"), "A resident cannot found a second town");
        var loaded = roundTrip(civics);
        assertEquals(a.id(), loaded.townOf(visitor).id());
        assertTrue(loaded.isMember(a.id(), visitor));
        assertFalse(loaded.isMember(b.id(), visitor));
        assertEquals(founderA, loaded.townById(a.id()).mayor());
        assertEquals(founderB, loaded.townById(b.id()).mayor());
        assertFalse(loaded.joinTown(visitor, b.id()), "Restart cannot erase the single-city rule");
        assertEquals(0, loaded.townById(a.id()).treasury(), "Joining must never change city money");
        assertTrue(loaded.townPlots(a.id(), 0, 10).isEmpty(), "Joining must not claim land");
    }

    @Test void newlyAdmittedResidentHasNoFoundingMayorOrOfficePowers() {
        KingdomCivicsData civics = new KingdomCivicsData();
        UUID founder = UUID.randomUUID(), visitor = UUID.randomUUID();
        var city = civics.foundTown(founder, "Город");
        assertNotNull(city);
        assertTrue(civics.joinTown(visitor, city.id()));
        KingdomAdministrationData admin = new KingdomAdministrationData();
        assertTrue(admin.ensureFounder(city.id(), founder));
        for (AdministrativeKind kind : AdministrativeKind.values())
            assertFalse(AdministrativePermissionService.mayManage(
                visitor, admin.founder(city.id()), city.mayor(),
                admin.roles(city.id(), visitor), kind), "Accidental rights for " + kind);
        assertFalse(AdministrativePermissionService.mayPerformFounderOnly(visitor, founder));
        assertNull(civics.foundTown(visitor, "Ещё один город"));
    }

    @Test void mayorSuccessionRevokesLegacyBadgesButKeepsOtherJobsAndFounder() {
        UUID city = UUID.randomUUID(), founder = UUID.randomUUID();
        UUID oldMayor = UUID.randomUUID(), staleMayor = UUID.randomUUID();
        UUID currentMayor = UUID.randomUUID(), successor = UUID.randomUUID();
        var admin = new KingdomAdministrationData();
        assertTrue(admin.ensureFounder(city, founder));
        assertTrue(admin.appoint(city, founder, oldMayor, oldMayor, MunicipalRole.MAYOR));
        assertTrue(admin.appoint(city, founder, oldMayor, oldMayor, MunicipalRole.TREASURER));
        // Simulate the former code's append-only succession after multiple handovers.
        assertTrue(admin.appoint(city, founder, oldMayor, staleMayor, MunicipalRole.MAYOR));

        admin.adminChangeMayor(city, oldMayor, currentMayor);
        assertEquals(Set.of(MunicipalRole.TREASURER), admin.roles(city, oldMayor));
        assertTrue(admin.roles(city, staleMayor).isEmpty());
        assertEquals(Set.of(MunicipalRole.MAYOR), admin.roles(city, currentMayor));
        assertFalse(AdministrativePermissionService.mayManage(oldMayor, founder, currentMayor,
            admin.roles(city, oldMayor), AdministrativeKind.TOWN_HALL));
        assertFalse(AdministrativePermissionService.mayManage(staleMayor, founder, currentMayor,
            admin.roles(city, staleMayor), AdministrativeKind.TOWN_HALL));
        assertTrue(AdministrativePermissionService.mayManage(oldMayor, founder, currentMayor,
            admin.roles(city, oldMayor), AdministrativeKind.BANK), "Independent treasury job remains");
        assertTrue(AdministrativePermissionService.mayPerformFounderOnly(founder, admin.founder(city)));
        assertFalse(AdministrativePermissionService.mayPerformFounderOnly(currentMayor, admin.founder(city)));

        admin.adminChangeMayor(city, currentMayor, successor);
        assertFalse(AdministrativePermissionService.mayManage(currentMayor, founder, successor,
            admin.roles(city, currentMayor), AdministrativeKind.TOWN_HALL));
        assertEquals(Set.of(MunicipalRole.MAYOR), admin.roles(city, successor));
        var reloaded = KingdomAdministrationData.fromTag(admin.save(new CompoundTag()));
        assertEquals(Set.of(MunicipalRole.TREASURER), reloaded.roles(city, oldMayor));
        assertTrue(reloaded.roles(city, staleMayor).isEmpty());
        assertTrue(reloaded.roles(city, currentMayor).isEmpty());
        assertEquals(Set.of(MunicipalRole.MAYOR), reloaded.roles(city, successor));
        assertEquals(founder, reloaded.founder(city));
    }

    @Test void packetAndMayorTransferUseAuthoritativeServerState() throws Exception {
        String action=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java"));
        String text=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeTextPacket.java"));
        String menu=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreMenu.java"));
        String civics=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomCivicsData.java"));
        assertTrue(action.contains("sender.containerMenu instanceof AdministrativeCoreMenu"));
        assertTrue(action.contains("packet.townId.equals(menu.townId())"));
        assertTrue(action.contains("menu.stillValid(sender)"));
        assertTrue(action.contains("civics.townOf(sender.getUUID())!=null"));
        assertTrue(action.contains("civics.joinTown(sender.getUUID(),town.id())"));
        assertTrue(action.contains("sender.closeContainer()"));
        assertTrue(menu.contains("active.packedPos()==corePos.asLong()"));
        assertTrue(menu.contains("corePos.distSqr(player.blockPosition())>64"));
        assertTrue(text.contains("KingdomTownHallCandidates.eligible("));
        assertTrue(text.contains("sender.getUUID().equals(admin.founder(town.id()))"));
        assertTrue(text.contains("AdministrativePermissionService.mayManage(sender.getUUID()"));
        assertTrue(civics.contains("administration.adminChangeMayor(townId,town.mayor(),replacement)"));
        assertFalse(civics.contains("administration.appoint(townId,actor,town.mayor(),replacement,MunicipalRole.MAYOR)"));
    }
}
