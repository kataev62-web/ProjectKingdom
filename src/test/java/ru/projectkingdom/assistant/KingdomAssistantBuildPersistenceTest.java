package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class KingdomAssistantBuildPersistenceTest {
    private static final UUID OWNER = UUID.fromString("33000000-0000-0000-0000-000000000003");
    private static final UUID ASSISTANT = UUID.fromString("44000000-0000-0000-0000-000000000004");

    @Test void saveLoadAfterPartialBatchResumesAtNextCell() {
        var world = new AssistantCreativeBuildServiceTest.Fixture();
        var design = AssistantCreativeBuildServiceTest.line(20, "minecraft:stone_bricks");
        var beforeData = new KingdomAssistantBuildData();
        var before = new AssistantCreativeBuildService(beforeData, world, key -> design);
        var queued = before.queue(OWNER, ASSISTANT, "minecraft:overworld", "plan",
                new BlockPos(0, 64, 0));
        assertEquals(16, before.tick().changed());

        var restoredData = KingdomAssistantBuildData.load(beforeData.save(new CompoundTag()));
        var after = new AssistantCreativeBuildService(restoredData, world, key -> design);

        assertEquals(4, after.tick().changed());
        assertEquals(20, world.writes);
        assertEquals(KingdomAssistantBuildData.Status.COMPLETE,
                restoredData.build(queued.buildId()).orElseThrow().status());
    }

    @Test void undoRestoresOnlyUnchangedPlacedBlocksAndIsIdempotent() {
        var world = new AssistantCreativeBuildServiceTest.Fixture();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1, 64, 0);
        world.blocks.put(first, "minecraft:dirt");
        world.blocks.put(second, "minecraft:grass_block");
        var design = AssistantCreativeBuildServiceTest.line(2, "minecraft:stone_bricks");
        var service = new AssistantCreativeBuildService(
                new KingdomAssistantBuildData(), world, key -> design);
        UUID build = service.queue(OWNER, ASSISTANT, "minecraft:overworld", "plan", first).buildId();
        assertEquals(2, service.tick().changed());
        world.blocks.put(second, "minecraft:gold_block");

        var firstUndo = service.undo(OWNER, build);

        assertEquals(1, firstUndo.restored());
        assertEquals(1, firstUndo.conflicts());
        assertEquals("minecraft:dirt", world.blocks.get(first));
        assertEquals("minecraft:gold_block", world.blocks.get(second));
        int writes = world.writes;
        var secondUndo = service.undo(OWNER, build);
        assertEquals(firstUndo, secondUndo);
        assertEquals(writes, world.writes);
    }

    @Test void undoPausesOnUnloadedChunkAndResumesWithoutConflict() {
        var world = new AssistantCreativeBuildServiceTest.Fixture();
        var design = AssistantCreativeBuildServiceTest.line(1, "minecraft:stone_bricks");
        var service = new AssistantCreativeBuildService(
                new KingdomAssistantBuildData(), world, key -> design);
        UUID build = service.queue(OWNER, ASSISTANT, "minecraft:overworld", "plan",
                new BlockPos(0, 64, 0)).buildId();
        service.tick();
        world.loaded = false;

        assertEquals(1, service.undo(OWNER, build).unloaded());
        int reads = world.reads;
        world.loaded = true;
        service.tick();

        assertEquals(reads + 1, world.reads);
        assertEquals("minecraft:air", world.blocks.get(new BlockPos(0, 64, 0)));
    }
}
