package ru.projectkingdom.npc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests read exactly the .schem bytes packaged into the real mod JAR;
 * no procedural stand-in or renderer-only fake material list.
 */
class KingdomBundledAvalonSchematicsTest {
    @TempDir Path folder;

    @BeforeAll static void minecraftBootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private KingdomDesignEngine.Design load(String name) throws Exception {
        String resource="/assets/kingdomnpc/avalon/"+name+".schem";
        Path imported=folder.resolve(name+".schem");
        try (InputStream in=KingdomBundledAvalonSchematicsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "JAR must ship the genuine Sponge v2 schematic: "+resource);
            Files.copy(in, imported);
        }
        assertTrue(Files.size(imported)>100);
        return KingdomSchematicLibrary.readDesign(imported, name);
    }

    private void check(String name,int width,int height,int length,int minBlocks)
            throws Exception {
        var design=load(name);
        assertEquals(width,design.width());
        assertEquals(height,design.height());
        assertEquals(length,design.length());
        assertTrue(width*height*length<=65_536);
        assertTrue(design.blocks().size()>=minBlocks,
            "Buildings must contain the whole shell, not a dummy foundation");
        Set<net.minecraft.core.BlockPos> coordinates=new HashSet<>();
        for (var cell:design.blocks()) {
            assertTrue(coordinates.add(new net.minecraft.core.BlockPos(cell.x(),cell.y(),cell.z())),
                "Schematic contains overlapping cells");
            assertTrue(cell.x()>=0&&cell.x()<width);
            assertTrue(cell.y()>=0&&cell.y()<height);
            assertTrue(cell.z()>=0&&cell.z()<length);
            assertTrue(KingdomBlockStateCodec.buildable(
                KingdomBlockStateCodec.parse(cell.material())),
                "NPC cannot construct: "+cell.material());
        }
        for(int rotation=0;rotation<4;rotation++){
            var turned=KingdomArchitectDesignRotation.rotate(design,rotation);
            assertEquals(design.blocks().size(),turned.blocks().size());
            assertNotNull(ru.projectkingdom.network.ArchitectPreviewPacket.fromDesign(
                turned,java.util.UUID.randomUUID(),new net.minecraft.core.BlockPos(0,64,0),name));
        }
    }

    @Test void royalGate() throws Exception {
        check("avalon_royal_gate",31,44,17,5000);
    }

    @Test void merchantHouse() throws Exception {
        check("avalon_merchant_house",17,28,19,1850);
    }

    @Test void smithyWorkshopShell() throws Exception {
        check("avalon_tfc_smithy_shell",21,29,23,2250);
    }
}
