package ru.projectkingdom.core;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.administration.MunicipalRole;
import ru.projectkingdom.social.KingdomSocialBookSnapshot;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomBookFirstSpreadTest {
    @Test void serverOwnedCharacterProfileRoundTripsWithOccupation() {
        var before=new KingdomSocialBookSnapshot(6,85,"Авалон",5,12,3,
                "За заслуги","Орден королевства",true,false,
                "Королевство Авалон","Казначей","Основатель поселения","Предприниматель");
        var packet=new FriendlyByteBuf(Unpooled.buffer());
        try {
            before.write(packet);
            var after=KingdomSocialBookSnapshot.read(packet);
            assertEquals(before,after);
            assertEquals("Казначей",after.officeSummary());
            assertEquals("Предприниматель",after.occupation());
            assertEquals(0,packet.readableBytes());
        } finally {packet.release();}
    }
    @Test void municipalOfficeIsDerivedFromActiveServerRoster() {
        assertEquals("Нет",KingdomSocialBookSnapshot.officeNames(false,Set.of()));
        assertEquals("Мэр",KingdomSocialBookSnapshot.officeNames(true,Set.of()));
        assertEquals("Нет",KingdomSocialBookSnapshot.officeNames(false,Set.of(MunicipalRole.MAYOR)));
        assertEquals("Мэр, Казначей, Судья",
                KingdomSocialBookSnapshot.officeNames(true,
                        Set.of(MunicipalRole.TREASURER,MunicipalRole.JUDGE)));
    }
    @Test void newIdentitySpreadReplacesScreenshotPlaceholder() throws Exception {
        String screen=Files.readString(Path.of("src/main/java/ru/projectkingdom/core/KingdomBookScreen.java"));
        String book=Files.readString(Path.of("src/main/java/ru/projectkingdom/core/KingdomBookItem.java"));
        String menu=Files.readString(Path.of("src/main/java/ru/projectkingdom/core/KingdomBookMenu.java"));
        assertTrue(screen.contains("СОЦИАЛЬНЫЙ СТАТУС"));
        assertTrue(screen.contains("ДОЛЖНОСТЬ"));
        assertTrue(screen.contains("ТИТУЛ"));
        assertTrue(screen.contains("ГРАЖДАНСТВО"));
        assertTrue(screen.contains("РЕПУТАЦИЯ"));
        assertTrue(screen.contains("ЗАНЯТОСТЬ"));
        assertTrue(screen.contains("НАГРАДЫ"));
        assertTrue(screen.contains("social.officeSummary()"));
        assertTrue(screen.contains("social.occupation()"));
        assertFalse(screen.contains("Профессия, титул и репутация будут подключены позже."));
        assertTrue(book.contains("KingdomSocialBookSnapshot.of(serverPlayer)"));
        assertTrue(book.contains("social.write(buffer)"));
        assertTrue(menu.contains("KingdomSocialBookSnapshot.read(buffer)"));
    }
    @Test void snapshotDoesNotGrantPoliticalRights() throws Exception {
        String profile=Files.readString(Path.of("src/main/java/ru/projectkingdom/social/KingdomSocialBookSnapshot.java"));
        assertTrue(profile.contains("administration.roles(town.id(),person)"));
        assertTrue(profile.contains("person.equals(town.mayor())"));
        assertTrue(profile.contains("person.equals(nation.ruler())"));
        String protocol=Files.readString(Path.of("src/main/java/ru/projectkingdom/network/KingdomNetwork.java"));
        assertTrue(protocol.contains("PROTOCOL=\"6\""));
        assertEquals("Не выбрана",KingdomSocialBookSnapshot.empty().occupation());
    }
}
