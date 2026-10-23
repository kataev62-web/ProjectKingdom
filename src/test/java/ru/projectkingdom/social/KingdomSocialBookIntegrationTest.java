package ru.projectkingdom.social;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.administration.MunicipalRole;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSocialBookIntegrationTest {
    @Test void playerBookPayloadRoundTripHasOfficeTitleNationAndNoTrailingBytes(){
        var expected=new KingdomSocialBookSnapshot(5,85,"Авалон",12,35,7,
                "Почётный знак","Орден королевства",true,true,
                "Королевство Авалон","Мэр, Казначей","Основатель поселения");
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.write(buffer);
            var actual=KingdomSocialBookSnapshot.read(buffer);
            assertEquals(expected,actual);
            assertEquals("Мэр (ещё 1)",actual.officeSummary());
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void defaultCoverMakesAbsenceOfOfficeAndCitizenTownExplicit(){
        var unknown=KingdomSocialBookSnapshot.empty();
        assertEquals(1,unknown.rank());
        assertEquals("Простолюдин",unknown.rankName());
        assertEquals("Неизвестный житель",unknown.standing());
        assertFalse(unknown.founder());
        assertEquals("Нет гражданства",unknown.city());
        assertEquals("Нет",unknown.officeSummary());
        assertEquals("Нет",unknown.legalTitle());
        assertEquals("Нет",unknown.nation());
    }

    @Test void officeIsFromLiveMunicipalRecordsNotRankOrStaleMayorFlag(){
        assertEquals("Нет",KingdomSocialBookSnapshot.officeNames(false,Set.of()));
        assertEquals("Мэр",KingdomSocialBookSnapshot.officeNames(true,Set.of()));
        // A revoked mayor's legacy MAYOR role must not impersonate the current mayor.
        assertEquals("Нет",KingdomSocialBookSnapshot.officeNames(false,Set.of(MunicipalRole.MAYOR)));
        String posts=KingdomSocialBookSnapshot.officeNames(true,
                Set.of(MunicipalRole.TREASURER,MunicipalRole.JUDGE));
        assertEquals("Мэр, Казначей, Судья",posts);
        assertEquals("Земельный управляющий",KingdomSocialBookSnapshot.officeNames(
                false,Set.of(MunicipalRole.LAND_MANAGER)));
    }

    @Test void firstBookTabShowsStatusOfficeAndTitleOnLeftAndCivicDataOnRight() throws Exception {
        var screen=Files.readString(Path.of("src/main/java/ru/projectkingdom/core/KingdomBookScreen.java"));
        assertTrue(screen.contains("selected = BookModel.Section.PLAYER"));
        assertTrue(screen.contains("social.rankName()"));
        assertTrue(screen.contains("social.officeSummary()"));
        assertTrue(screen.contains("social.legalTitle()"));
        assertTrue(screen.contains("social.city()"));
        assertTrue(screen.contains("social.nation()"));
        assertTrue(screen.contains("social.standing()"));
        assertTrue(screen.contains("social.civicHonor()"));
        assertTrue(screen.contains("social.nationalHonor()"));
        assertFalse(screen.contains("Профессия, титул и репутация будут подключены позже."));
    }

    @Test void bookIsServerReadOnlyAndOfficesDoNotGrantPermissions() throws Exception {
        var bookMenu=Files.readString(Path.of("src/main/java/ru/projectkingdom/core/KingdomBookMenu.java"));
        var bookItem=Files.readString(Path.of("src/main/java/ru/projectkingdom/core/KingdomBookItem.java"));
        var profile=Files.readString(Path.of("src/main/java/ru/projectkingdom/social/KingdomSocialBookSnapshot.java"));
        var civic=Files.readString(Path.of("src/main/java/ru/projectkingdom/social/KingdomCivicService.java"));
        var handler=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeTextPacket.java"));
        assertTrue(bookItem.contains("KingdomSocialBookSnapshot.of(serverPlayer)"));
        assertTrue(bookMenu.contains("KingdomSocialBookSnapshot.read(buffer)"));
        assertTrue(profile.contains("administration.roles(town.id(),person)"));
        assertTrue(profile.contains("person.equals(town.mayor())"));
        assertTrue(profile.contains("person.equals(nation.ruler())"));
        assertTrue(handler.contains("if(!menu.manager()&&!realmAward)return"));
        assertTrue(civic.contains("actor.getUUID().equals(realm.ruler())"));
    }
}
