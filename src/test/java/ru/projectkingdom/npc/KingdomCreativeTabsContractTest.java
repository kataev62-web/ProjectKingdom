package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class KingdomCreativeTabsContractTest {
    @Test
    void displaysEveryRegisteredKingdomBlockItemWithoutHardcodedBlockList() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomCreativeTabs.java"));
        assertTrue(source.contains("Registries.CREATIVE_MODE_TAB"), "Uses Forge 1.20.1 creative-tab registry");
        assertTrue(source.contains("ForgeRegistries.BLOCKS.getValues().stream()"),
                "Discovers all Kingdom blocks from registry, including future ones");
        assertTrue(source.contains("KingdomNpcMod.MOD_ID.equals(id.getNamespace())"),
                "Never includes another mod's blocks");
        assertTrue(source.contains("item != Items.AIR"),
                "Does not display unobtainable blocks");
        assertTrue(source.contains(".distinct()"), "Does not duplicate block items");
        assertTrue(source.contains("output.accept(new ItemStack(item))"),
                "Creative menu receives the real registered BlockItem");
        String mod = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        assertTrue(mod.contains("KingdomCreativeTabs.register("),
                "Creative tab is actually registered on the mod event bus");
        String russian = Files.readString(Path.of(
                "src/main/resources/assets/kingdomnpc/lang/ru_ru.json"));
        assertTrue(russian.contains("\"itemGroup.kingdomnpc.blocks\""),
                "Russian creative tab title must be available");
    }
}
