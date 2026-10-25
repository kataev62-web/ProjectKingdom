package ru.projectkingdom.npc;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomTfcSpecialPotWorkTest {
    @Test void onlyNativeTfcRecipeTypesAreApproved() {
        assertEquals("",KingdomTfcSpecialPotWork.recipeMode(new Object()));
        assertEquals("",KingdomTfcSpecialPotWork.recipeMode(null));
        assertNotEquals(KingdomTfcSpecialPotWork.SOUP,KingdomTfcSpecialPotWork.JAM);
    }

    @Test void nativePortionsHaveOnePersistentAuthorizedOwner() {
        var owner=UUID.randomUUID();
        var batches=KingdomTfcPotData.load(new CompoundTag());
        var special=new KingdomTfcPotData.Batch(owner,"minecraft:overworld",123456L,
            List.of("tfc:food/vegetables","tfc:food/meat"),
            List.of(KingdomTfcSpecialPotWork.SOUP,KingdomTfcSpecialPotWork.SOUP));
        batches.put(special);
        var saved=KingdomTfcPotData.load(batches.save(new CompoundTag()));
        assertEquals(special,saved.get(owner));
        assertTrue(saved.reserved(UUID.randomUUID(),"minecraft:overworld",123456L));
        assertFalse(saved.reserved(owner,"minecraft:overworld",123456L));
        saved.remove(owner);
        assertNull(saved.get(owner));
    }
}
