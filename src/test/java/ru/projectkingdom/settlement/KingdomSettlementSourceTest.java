package ru.projectkingdom.settlement;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class KingdomSettlementSourceTest {
    private String source(String file)throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+file));
    }
    @Test void foundationOnlyFromActualPlacedCoreThroughAuthenticatedMenu()throws Exception {
        String founding=source("settlement/KingdomSettlementFounding.java");
        String packet=source("settlement/KingdomSettlementFoundPacket.java");
        assertTrue(founding.contains("((AdministrativeCoreBlockEntity)placed).placer()"));
        assertTrue(founding.contains("((KingdomSettlementCharterEntity)placed).placer()"));
        assertTrue(founding.contains("legacyClaimOwner"));
        assertTrue(founding.contains("civic.foundTown("));
        assertTrue(founding.contains("civic.claim("));
        assertTrue(founding.contains("admin.ensureFounder("));
        assertTrue(packet.contains("player.containerMenu instanceof KingdomSettlementMenu"));
        assertTrue(packet.contains("menu.stillValid(player)"));
        assertTrue(source("network/KingdomNetwork.java").contains("KingdomSettlementFoundPacket"));
    }
    @Test void expiringOpenLandDoesNotSpawnLootOrForceChunks() throws Exception {
        var breakCode=source("settlement/KingdomOpenLandEvents.java");
        var queue=source("settlement/KingdomTemporaryBlocks.java");
        assertTrue(breakCode.contains("event.setCanceled(true)"));
        assertTrue(breakCode.contains("AdministrativeCoreBlockEntity hall"));
        assertTrue(source("settlement/KingdomTemporaryLand.java").contains("KingdomAdministrationRegistry.TOWN_HALL.get()"));
        assertTrue(breakCode.contains("Blocks.AIR.defaultBlockState()"));
        assertTrue(queue.contains("LIFETIME_MS=10_000L"));
        assertTrue(queue.contains("level.hasChunkAt(pos)"));
        assertTrue(queue.contains("new ListTag()"));
        assertTrue(queue.contains("getDataStorage().computeIfAbsent("));
        assertTrue(queue.contains("setBlock(pos,net.minecraft.world.level.block.Blocks.AIR"));
    }
    @Test void legacyFoundingBlockHasAssetsButNoNewRecipe()throws Exception {
        assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/models/item/settlement_charter.json"));
        assertNull(getClass().getResourceAsStream("/data/kingdomnpc/recipes/settlement_charter.json"));
        try(InputStream in=getClass().getResourceAsStream("/assets/kingdomnpc/textures/block/settlement_charter.png")){
            assertNotNull(in);
            BufferedImage png=ImageIO.read(in);
            assertNotNull(png);
            assertEquals(16,png.getWidth());
            assertEquals(16,png.getHeight());
        }
        String lang=Files.readString(Path.of("src/main/resources/assets/kingdomnpc/lang/ru_ru.json"));
        assertTrue(lang.contains("block.kingdomnpc.settlement_charter"));
        assertTrue(source("npc/KingdomNpcMod.java").contains("KingdomSettlementRegistry.register()"));
    }
}
