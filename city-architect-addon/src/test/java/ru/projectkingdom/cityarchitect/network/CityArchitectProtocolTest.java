package ru.projectkingdom.cityarchitect.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CityArchitectProtocolTest {
    @Test
    void previewAndDecisionPacketsRoundTripWithinBounds() {
        var preview = new CityArchitectPreviewPacket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Этап 1", "короткий сухой маршрут", 1200L,
                List.of(new BlockPos(1, 64, 2), new BlockPos(2, 64, 2)));
        FriendlyByteBuf previewBuffer = new FriendlyByteBuf(Unpooled.buffer());
        CityArchitectPreviewPacket.encode(preview, previewBuffer);
        assertEquals(preview, CityArchitectPreviewPacket.decode(previewBuffer));

        var decision = new CityArchitectDecisionPacket(preview.token(), true);
        FriendlyByteBuf decisionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        CityArchitectDecisionPacket.encode(decision, decisionBuffer);
        assertEquals(decision, CityArchitectDecisionPacket.decode(decisionBuffer));
    }

    @Test
    void serverSessionBindsEveryAuthorityFieldAndRejectsForgeries() {
        UUID owner = UUID.randomUUID();
        UUID assistant = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID stage = UUID.randomUUID();
        CityArchitectNetwork.SessionRegistry sessions = new CityArchitectNetwork.SessionRegistry();
        var opened = sessions.open(owner, assistant, "minecraft:overworld", plan, stage, 1_000L);

        assertEquals(CityArchitectNetwork.DecisionStatus.BAD_OWNER,
                sessions.decide(UUID.randomUUID(), assistant, "minecraft:overworld", 4,
                        opened.token(), true, 900L).status());
        assertEquals(CityArchitectNetwork.DecisionStatus.BAD_ASSISTANT,
                sessions.decide(owner, UUID.randomUUID(), "minecraft:overworld", 4,
                        opened.token(), true, 900L).status());
        assertEquals(CityArchitectNetwork.DecisionStatus.BAD_DIMENSION,
                sessions.decide(owner, assistant, "minecraft:the_nether", 4,
                        opened.token(), true, 900L).status());
        assertEquals(CityArchitectNetwork.DecisionStatus.NO_PERMISSION,
                sessions.decide(owner, assistant, "minecraft:overworld", 3,
                        opened.token(), true, 900L).status());
        assertEquals(CityArchitectNetwork.DecisionStatus.BAD_TOKEN,
                sessions.decide(owner, assistant, "minecraft:overworld", 4,
                        UUID.randomUUID(), true, 900L).status());
        assertEquals(CityArchitectNetwork.DecisionStatus.ACCEPTED,
                sessions.decide(owner, assistant, "minecraft:overworld", 4,
                        opened.token(), true, 900L).status());
        assertEquals(CityArchitectNetwork.DecisionStatus.BAD_TOKEN,
                sessions.decide(owner, assistant, "minecraft:overworld", 4,
                        opened.token(), true, 900L).status());

        var expired = sessions.open(owner, assistant, "minecraft:overworld", plan, stage, 1_000L);
        assertEquals(CityArchitectNetwork.DecisionStatus.EXPIRED,
                sessions.decide(owner, assistant, "minecraft:overworld", 4,
                        expired.token(), false, 1_001L).status());
    }
}
