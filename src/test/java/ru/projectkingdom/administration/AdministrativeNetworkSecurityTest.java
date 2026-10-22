package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

final class AdministrativeNetworkSecurityTest {
    @Test void textValidationRejectsOversizeFormattingAndControls() {
        assertEquals("Новая запись",AdministrativeTextPacket.validatedText("  Новая запись  "));
        assertNull(AdministrativeTextPacket.validatedText(""));
        assertNull(AdministrativeTextPacket.validatedText("x".repeat(49)));
        assertNull(AdministrativeTextPacket.validatedText("цвет §4"));
        assertNull(AdministrativeTextPacket.validatedText("строка\nдругая"));
    }
    @Test void packetRequiresExactOpenMenuAndStillValid() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java"));
        assertTrue(source.contains("sender.containerMenu instanceof AdministrativeCoreMenu"));
        assertTrue(source.contains("packet.townId.equals(menu.townId())"));
        assertTrue(source.contains("packet.kind==menu.kind()"));
        assertTrue(source.contains("menu.stillValid(sender)"));
        assertTrue(source.contains("menu.manager()"));
    }
    @Test void bindingNeverForceLoadsAndRechecksDistanceAndTown() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeAccessService.java"));
        assertTrue(source.contains("hasChunkAt(pos)"));
        assertTrue(source.contains("pos.distSqr(player.blockPosition())>36"));
        assertTrue(source.contains("townAt(level,pos)"));
        assertFalse(source.contains("getChunk("));
    }
    @Test void physicalBindingsRoundTripWithoutDuplication() {
        UUID town=UUID.randomUUID();var data=new KingdomAdministrationData();
        assertTrue(data.bindContainer(town,AdministrativeBindingRole.WAREHOUSE_STORAGE,"minecraft:overworld",10L));
        assertFalse(data.bindContainer(town,AdministrativeBindingRole.WAREHOUSE_STORAGE,"minecraft:overworld",10L));
        var loaded=KingdomAdministrationData.fromTag(data.save(new CompoundTag()));
        assertEquals(1,loaded.containerBindings(town,AdministrativeBindingRole.WAREHOUSE_STORAGE).size());
    }
}
