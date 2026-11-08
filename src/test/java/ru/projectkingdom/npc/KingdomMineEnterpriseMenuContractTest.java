package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Protects the move of mine controls out of individual NPC menus. */
class KingdomMineEnterpriseMenuContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+file));
    }

    @Test void allMineControlsBelongToEnterpriseCore() throws Exception {
        String menu=read("enterprise/EnterpriseCoreMenu.java");
        String screen=read("enterprise/EnterpriseCoreScreen.java");
        String npc=read("npc/KingdomNpcMenu.java");
        for(String action:new String[]{
            "OPEN_MINE_SURVEY","MINE_START","MINE_LEVELS","MINE_HEADING"
        }) {
            assertTrue(menu.contains(action),action);
            assertTrue(screen.contains("EnterpriseCoreMenu."+action),action);
        }
        assertTrue(menu.contains("data.kind(enterpriseId)!=EnterpriseKind.MINE"));
        assertTrue(menu.contains("enterprise.owner().equals(server.getUUID())"));
        assertTrue(npc.contains("настраиваются через блок предприятия"));
        assertFalse(npc.contains("KingdomMineSelection.begin(viewer"));
    }

    @Test void actualDepthAndFloorConstructionAreBoundToSavedSettings() throws Exception {
        String settings=read("npc/KingdomMineSettings.java");
        String work=read("npc/KingdomMineWork.java");
        String selection=read("npc/KingdomMineSelection.java");
        assertTrue(settings.contains("LEVEL_INTERVAL=5"));
        assertTrue(settings.contains("MAX_LEVELS=4"));
        assertTrue(work.contains("settings.levels(id)"));
        assertTrue(work.contains("settings.heading(id)"));
        assertTrue(work.contains("floorAt("));
        assertTrue(selection.contains("levels(p.mine())*5"));
        assertTrue(read("network/KingdomVolumeSelectionPacket.java")
            .contains("b.writeVarInt(p.depth())"));
    }
}
