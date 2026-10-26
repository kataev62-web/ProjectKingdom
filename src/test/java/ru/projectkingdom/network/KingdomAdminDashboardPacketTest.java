package ru.projectkingdom.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Admin UI must never lose selected IDs, NPC details, or confirmation state. */
class KingdomAdminDashboardPacketTest {
    @Test void roundtripListsAndPendingConfirmation() {
        UUID session=UUID.randomUUID(),city=UUID.randomUUID(),nation=UUID.randomUUID(),
            mayor=UUID.randomUUID();
        var packet=new KingdomAdminDashboardPacket(session,true,
            2,39,List.of(new KingdomAdminDashboardPacket.City(city,"Авалон",
                "NPC #12 Мирон","NPC #9 Основатель",17)),
            0,1,List.of(new KingdomAdminDashboardPacket.Nation(nation,"Королевство",
                "NPC #12 Мирон",1)),
            1,28,List.of(new KingdomAdminDashboardPacket.Npc(12,"Мирон",mayor,32)),
            "Проверь удаление города",true,city,false);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            KingdomAdminDashboardPacket.encode(packet,buf);
            assertEquals(packet,KingdomAdminDashboardPacket.decode(buf));
        } finally {buf.release();}
    }
    @Test void actionRoundtripPreservesServerSessionAndSpecificNpc() {
        var original=new KingdomAdminActionPacket(UUID.randomUUID(),
            KingdomAdminActionPacket.APPOINT_MAYOR,UUID.randomUUID(),7,"",0);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            KingdomAdminActionPacket.encode(original,buf);
            assertEquals(original,KingdomAdminActionPacket.decode(buf));
        } finally {buf.release();}
    }
    @Test void oversizedResponsePageIsRejected() {
        var packet=new KingdomAdminDashboardPacket(UUID.randomUUID(),true,0,0,
            java.util.Collections.nCopies(25,new KingdomAdminDashboardPacket.City(
                UUID.randomUUID(),"X","Y","Z",0)),
            0,0,List.of(),0,0,List.of(),"",false,new UUID(0,0),false);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            KingdomAdminDashboardPacket.encode(packet,buf);
            assertThrows(IllegalArgumentException.class,()->KingdomAdminDashboardPacket.decode(buf));
        } finally {buf.release();}
    }
}
