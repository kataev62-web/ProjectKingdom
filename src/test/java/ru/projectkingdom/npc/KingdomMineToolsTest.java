package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The tools of the resident.
 *
 * <p>Switching the tool of the resident must move the tool, never copy it:
 * two shovels in the bag of a resident would be two shovels in the world.
 */
final class KingdomMineToolsTest {

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static KingdomNpcData.Worker worker() {
        return new KingdomNpcData.Worker(1, UUID.randomUUID(), "Resident", "minecraft:overworld", 0);
    }

    @Test void theToolOfTheHandIsFoundWithoutACopy() {
        KingdomNpcData.Worker worker = worker();
        worker.fieldTool = new ItemStack(Items.IRON_PICKAXE);
        assertTrue(KingdomMineTools.selectInView(worker, KingdomMineTools.Tool.PICKAXE),
            "the pickaxe is already in the hand");
        assertTrue(worker.fieldInventory.isEmpty(), "nothing was moved");
    }

    @Test void switchingTheToolMovesTheOldOneIntoTheCargo() {
        KingdomNpcData.Worker worker = worker();
        worker.fieldTool = new ItemStack(Items.IRON_PICKAXE);
        worker.fieldInventory.add(new ItemStack(Items.IRON_SHOVEL));

        assertTrue(KingdomMineTools.selectInView(worker, KingdomMineTools.Tool.SHOVEL),
            "the shovel is taken from the cargo");
        assertTrue(worker.fieldTool.is(Items.IRON_SHOVEL), "the shovel is now in the hand");
        assertEquals(1, worker.fieldInventory.size(), "the pickaxe went into the cargo");
        assertTrue(worker.fieldInventory.get(0).is(Items.IRON_PICKAXE),
            "the pickaxe was moved, not copied");
        assertEquals(1, worker.fieldInventory.stream()
            .filter(stack -> stack.is(Items.IRON_PICKAXE) || stack.is(Items.IRON_SHOVEL)).count(),
            "there is still exactly one of each tool");
    }

    @Test void aToolThatIsNotThereIsNotCreated() {
        KingdomNpcData.Worker worker = worker();
        worker.fieldTool = new ItemStack(Items.IRON_PICKAXE);
        assertFalse(KingdomMineTools.selectInView(worker, KingdomMineTools.Tool.SHOVEL),
            "there is no shovel in the bag");
        assertTrue(worker.fieldTool.is(Items.IRON_PICKAXE), "the tool of the hand stayed");
        assertTrue(worker.fieldInventory.isEmpty(), "nothing was invented");
    }

    @Test void theToolOfTheBlockIsKnown() {
        assertEquals(KingdomMineTools.Tool.SHOVEL,
            KingdomMineTools.required(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()),
            "the ground wants a shovel");
        assertEquals(KingdomMineTools.Tool.PICKAXE,
            KingdomMineTools.required(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
            "the stone wants a pickaxe");
        assertEquals(KingdomMineTools.Tool.BLOCKED,
            KingdomMineTools.required(net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()),
            "a chest is not mined");
    }

    @Test void theMatchingOfTheToolIsExact() {
        assertTrue(KingdomMineTools.matches(new ItemStack(Items.IRON_SHOVEL), KingdomMineTools.Tool.SHOVEL));
        assertFalse(KingdomMineTools.matches(new ItemStack(Items.IRON_SHOVEL), KingdomMineTools.Tool.PICKAXE));
        assertFalse(KingdomMineTools.matches(ItemStack.EMPTY, KingdomMineTools.Tool.PICKAXE));
        assertFalse(KingdomMineTools.matches(new ItemStack(Items.IRON_PICKAXE), KingdomMineTools.Tool.BLOCKED));
    }
}
