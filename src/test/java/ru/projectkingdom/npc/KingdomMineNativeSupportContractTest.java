package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-level safeguards until a full Forge+TFC game test is available. */
class KingdomMineNativeSupportContractTest {
    @Test void supportsUseNativeItemPlacementAndNoSyntheticSupportBlocks() throws Exception {
        String builder=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineSupportBuilder.java"));
        String excavation=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineSupportExcavation.java"));
        String miner=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineWork.java"));
        assertTrue(builder.contains("support.useOn(new UseOnContext("));
        assertTrue(builder.contains("FakePlayerFactory.getMinecraft(level)"));
        assertTrue(builder.contains("consumed>0"));
        assertTrue(builder.contains("return Result.SUPPLIED;"));
        assertTrue(builder.contains("return placed && consumed>0 ? Result.PLACED"));

        assertTrue(builder.contains("frameComplete(level,frame)"));
        assertTrue(builder.contains("KingdomMineSupportSafety.hasHorizontalSupport("));
        String safety=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineSupportSafety.java"));
        assertTrue(safety.contains("net.dries007.tfc.util.Support"));
        assertTrue(safety.contains("isSupported"));
        assertFalse(safety.contains("floorSupports"));
        assertFalse(builder.contains("level.setBlock("));
        assertTrue(excavation.contains("KingdomMineSupportSafety.inspect(level,pos)"));
        assertTrue(excavation.contains("Block.getDrops("));
        assertTrue(miner.contains("KingdomMineSupportBuilder.tick(level,npc,worker,frame)"));
        assertTrue(miner.contains("KingdomMineSupportExcavation.tick(level,npc,worker,frame)"));
    }
}
