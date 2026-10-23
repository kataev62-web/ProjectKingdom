package ru.projectkingdom.npc;

import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Every entry in the architect menu must yield a viable, rotatable design. */
class KingdomArchitectBuiltinCatalogTest {
    @BeforeAll static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void allFiveBuiltinBuildingsAreConstructibleAndRotatable() {
        for (var kind: KingdomBuildingTypes.Type.values()) {
            var blueprint=KingdomBuildingTypes.design(kind);
            assertFalse(blueprint.blocks().isEmpty(),kind.name());
            for(int turns=0;turns<4;turns++){
                var rotated=assertDoesNotThrow(
                    ()->KingdomArchitectDesignRotation.rotate(blueprint,turns),
                    "Preview rejected "+kind.name()+" turn="+turns);
                assertFalse(rotated.blocks().isEmpty(),kind.name());
                assertTrue(rotated.width()<=48 && rotated.length()<=48 && rotated.height()<=48,kind.name());
                for (var cell:rotated.blocks()){
                    assertTrue(cell.x()>=0 && cell.x()<rotated.width(),kind.name());
                    assertTrue(cell.y()>=0 && cell.y()<rotated.height(),kind.name());
                    assertTrue(cell.z()>=0 && cell.z()<rotated.length(),kind.name());
                }
            }
        }
    }
}
