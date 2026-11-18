package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real block/tool classification, plus real physical-executor wiring checks. */
final class KingdomMineFirstCutBlockToolTest {
    @Test void dirtRequiresShovelAndStoneRequiresPickaxe() {
        assertEquals(KingdomMineTools.Tool.SHOVEL,
            KingdomMineTools.required(Blocks.DIRT.defaultBlockState()));
        assertEquals(KingdomMineTools.Tool.SHOVEL,
            KingdomMineTools.required(Blocks.GRAVEL.defaultBlockState()));
        assertEquals(KingdomMineTools.Tool.PICKAXE,
            KingdomMineTools.required(Blocks.STONE.defaultBlockState()));
        assertEquals(KingdomMineTools.Tool.PICKAXE,
            KingdomMineTools.required(Blocks.DEEPSLATE.defaultBlockState()));
    }
    @Test void shovelAndPickaxeAreNotInterchangeable() {
        var shovel=new ItemStack(Items.IRON_SHOVEL);
        var pickaxe=new ItemStack(Items.IRON_PICKAXE);
        assertTrue(KingdomMineTools.matches(shovel,KingdomMineTools.Tool.SHOVEL));
        assertTrue(KingdomMineTools.matches(pickaxe,KingdomMineTools.Tool.PICKAXE));
        assertFalse(KingdomMineTools.matches(shovel,KingdomMineTools.Tool.PICKAXE));
        assertFalse(KingdomMineTools.matches(pickaxe,KingdomMineTools.Tool.SHOVEL));
    }
    @Test void noSoilFirstGateAndMenuExecutesImmediately() throws Exception {
        String work=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomMineFirstCut.java"));
        String menu=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMenu.java"));
        assertTrue(work.contains("KingdomMineTools.required(state)"));
        assertTrue(work.contains("worker.order=\"stay\""));
        assertFalse(work.contains("DONE.computeIfAbsent"));
        assertTrue(menu.contains("KingdomMineFirstCut.tryNow(viewer,w)"));
    }
}
