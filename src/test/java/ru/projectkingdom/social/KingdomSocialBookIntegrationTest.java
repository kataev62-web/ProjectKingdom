package ru.projectkingdom.social;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSocialBookIntegrationTest {
    @Test void playerBookPayloadRoundTripIsBoundedAndReadOnly(){
        var expected=new KingdomSocialBookSnapshot(5,85,"Авалон",12,35,7,
                "Почётный знак","Орден королевства",true,true);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.write(buffer);
            assertEquals(expected,KingdomSocialBookSnapshot.read(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void playerBookWithoutCitizenTownStillShowsRankAndOwnership(){
        var unknown=KingdomSocialBookSnapshot.empty();
        assertEquals(1,unknown.rank());
        assertEquals("Простолюдин",unknown.rankName());
        assertEquals("Неизвестный житель",unknown.standing());
        assertFalse(unknown.founder());
        assertEquals("Нет гражданства",unknown.city());
    }
    @Test void staleAdminAuthorityCannotBeTakenFromTheBook() throws Exception {
        var bookMenu=java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/ru/projectkingdom/core/KingdomBookMenu.java"));
        var bookItem=java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/ru/projectkingdom/core/KingdomBookItem.java"));
        var civic=java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/ru/projectkingdom/social/KingdomCivicService.java"));
        var text=java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/ru/projectkingdom/administration/AdministrativeTextPacket.java"));
        assertTrue(bookItem.contains("KingdomSocialBookSnapshot.of(serverPlayer)"));
        assertTrue(bookMenu.contains("KingdomSocialBookSnapshot.read(buffer)"));
        assertTrue(text.contains("if(!menu.manager()&&!realmAward)return"));
        assertTrue(civic.contains("actor.getUUID().equals(realm.ruler())"));
    }
}
