package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Team invitations are opt-in, owner-controlled, bounded and saved with the site. */
class KingdomConstructionTeamsTest {
    @Test void invitedPlayersCanAssistAndOwnerCanRevoke() {
        var data = new KingdomConstructionData();
        UUID owner = UUID.randomUUID(), helper = UUID.randomUUID(), stranger = UUID.randomUUID();
        var site = data.add(owner, "minecraft:overworld", "HOUSE", 10, 70, 20);
        assertTrue(data.mayAssist(site.id(), owner));
        assertFalse(data.mayAssist(site.id(), helper));
        assertFalse(data.mayAssist(site.id(), stranger));
        assertFalse(data.toggleAssistant(site.id(), stranger, helper));
        assertFalse(data.toggleAssistant(site.id(), owner, owner));

        assertTrue(data.toggleAssistant(site.id(), owner, helper));
        assertTrue(data.mayAssist(site.id(), helper));
        assertEquals(1, data.assistants(site.id()).size());
        assertTrue(data.toggleAssistant(site.id(), owner, helper));
        assertFalse(data.mayAssist(site.id(), helper));
        assertTrue(data.assistants(site.id()).isEmpty());
    }

    @Test void assistantListSurvivesSaveAndLoadWithoutLosingProgress() {
        var data = new KingdomConstructionData();
        UUID owner = UUID.randomUUID(), helper = UUID.randomUUID();
        var site = data.add(owner, "minecraft:overworld", "HOUSE", 10, 70, 20);
        data.advance(site.id(), 6, 99);
        assertTrue(data.toggleAssistant(site.id(), owner, helper));
        CompoundTag saved = data.save(new CompoundTag());
        var restored = KingdomConstructionData.load(saved);

        assertEquals(6, restored.progress(site.id()));
        assertEquals(KingdomConstructionData.Status.BUILDING, restored.find(site.id()).status());
        assertTrue(restored.mayAssist(site.id(), helper));
        assertEquals(1, restored.assistants(site.id()).size());
        assertTrue(restored.toggleAssistant(site.id(), owner, helper));
        assertFalse(restored.mayAssist(site.id(), helper));
    }

    @Test void invitationLimitAndCancelledSitesAreEnforced() {
        var data = new KingdomConstructionData();
        UUID owner = UUID.randomUUID();
        var site = data.add(owner, "minecraft:overworld", "SMITHY", 0, 70, 0);
        for (int i = 0; i < 8; i++)
            assertTrue(data.toggleAssistant(site.id(), owner, UUID.randomUUID()));
        assertEquals(8, data.assistants(site.id()).size());
        UUID overflow = UUID.randomUUID();
        assertFalse(data.toggleAssistant(site.id(), owner, overflow));
        assertFalse(data.mayAssist(site.id(), overflow));

        assertTrue(data.setStatus(site.id(), owner, KingdomConstructionData.Status.CANCELLED));
        assertFalse(data.mayAssist(site.id(), owner));
        assertFalse(data.toggleAssistant(site.id(), owner, overflow));
    }

    @Test void legacyProjectsWithoutAssistantsStillLoad() {
        var data = new KingdomConstructionData();
        UUID owner = UUID.randomUUID();
        var site = data.add(owner, "minecraft:overworld", "WAREHOUSE", 0, 70, 0);
        var saved = data.save(new CompoundTag());
        for (var element : saved.getList("Projects", net.minecraft.nbt.Tag.TAG_COMPOUND))
            ((CompoundTag) element).remove("Assistants");
        var restored = KingdomConstructionData.load(saved);
        assertTrue(restored.mayAssist(site.id(), owner));
        assertTrue(restored.assistants(site.id()).isEmpty());
    }
}
