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

    @org.junit.jupiter.api.TestFactory
    java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> eachBuiltinMustRotate() {
        return java.util.Arrays.stream(KingdomBuildingTypes.Type.values())
            .flatMap(kind->java.util.stream.IntStream.range(0,4)
                .mapToObj(turns->org.junit.jupiter.api.DynamicTest.dynamicTest(
                    kind.name()+" rotation "+turns, ()->{
                        var blueprint=KingdomBuildingTypes.design(kind);
                        assertFalse(blueprint.blocks().isEmpty());
                        var rotated=KingdomArchitectDesignRotation.rotate(blueprint,turns);
                        assertFalse(rotated.blocks().isEmpty());
                        assertTrue(rotated.width()<=48 && rotated.length()<=48 && rotated.height()<=48);
                        for(var cell:rotated.blocks()){
                            assertTrue(cell.x()>=0 && cell.x()<rotated.width());
                            assertTrue(cell.y()>=0 && cell.y()<rotated.height());
                            assertTrue(cell.z()>=0 && cell.z()<rotated.length());
                        }
                    })));
    }
}
