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
        assertFalse(builder.contains("level.setBlock("));
        assertTrue(excavation.contains("KingdomMineSupportSafety.inspect(level,pos)"));
        assertTrue(excavation.contains("Block.getDrops("));
        assertTrue(miner.contains("KingdomMineSupportBuilder.tick(level,npc,worker,frame)"));
        assertTrue(miner.contains("KingdomMineSupportExcavation.tick(level,npc,worker,frame)"));
    }
    @Test void supportFrameIsPreparedBeforeCollapseProneRoofExcavation() throws Exception {
        String miner=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineWork.java"));
        int frame=miner.indexOf("KingdomMineSupportBuilder.tick(level,npc,worker,frame)");
        int safety=miner.indexOf("KingdomMineSupportSafety.inspect(level,target)");
        assertTrue(frame>=0&&safety>frame,
            "the native frame must be installed before the miner asks permission to remove collapse-prone roof rock");
    }

}
