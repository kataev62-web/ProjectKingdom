package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomDesignEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class AssistantCreativeBuildServiceTest {
    private static final UUID OWNER = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT = UUID.fromString("22000000-0000-0000-0000-000000000002");
    private static final String DIMENSION = "minecraft:overworld";

    @Test void replacesAirAndHarmlessTerrainWithoutMaterialsAtSixteenChangesPerTick() {
        Fixture world = new Fixture();
        KingdomDesignEngine.Design design = line(20, "minecraft:stone_bricks");
        for (int x = 0; x < 20; x += 2)
            world.blocks.put(new BlockPos(x, 64, 0), "minecraft:dirt");
        var service = service(world, design);

        var queued = service.queue(OWNER, ASSISTANT, DIMENSION, "plan", new BlockPos(0, 64, 0));
        assertEquals(AssistantCreativeBuildService.QueueStatus.QUEUED, queued.status());

        var first = service.tick();
        assertEquals(16, first.changed());
        assertEquals(16, world.writes);
        var second = service.tick();
        assertEquals(4, second.changed());
        assertEquals(20, world.writes);
        for (int x = 0; x < 20; x++)
            assertEquals("minecraft:stone_bricks", world.blocks.get(new BlockPos(x, 64, 0)));
    }

    @Test void refusesContainerAndUnbreakableTargets() {
        KingdomDesignEngine.Design design = line(1, "minecraft:stone_bricks");
        for (boolean container : List.of(true, false)) {
            Fixture world = new Fixture();
            BlockPos target = new BlockPos(0, 64, 0);
            world.blocks.put(target, container ? "minecraft:chest" : "minecraft:bedrock");
            if (container) world.blockEntities.add(target); else world.unbreakable.add(target);

            var result = service(world, design).queue(
                    OWNER, ASSISTANT, DIMENSION, "plan", target);

            assertEquals(AssistantCreativeBuildService.QueueStatus.UNSAFE_TARGET, result.status());
            assertEquals(0, world.writes);
        }
    }

    @Test void refusesForeignLandAndOutOfWorldHeight() {
        Fixture foreign = new Fixture();
        foreign.authorized = false;
        assertEquals(AssistantCreativeBuildService.QueueStatus.UNAUTHORIZED,
                service(foreign, line(1, "minecraft:stone_bricks"))
                        .queue(OWNER, ASSISTANT, DIMENSION, "plan", new BlockPos(0, 64, 0)).status());

        Fixture height = new Fixture();
        assertEquals(AssistantCreativeBuildService.QueueStatus.OUT_OF_WORLD,
                service(height, line(1, "minecraft:stone_bricks"))
                        .queue(OWNER, ASSISTANT, DIMENSION, "plan", new BlockPos(0, 320, 0)).status());
        assertEquals(0, foreign.writes + height.writes);
    }

    @Test void unloadedChunkPausesWithoutReadingOrWritingThenResumes() {
        Fixture world = new Fixture();
        world.loaded = false;
        var service = service(world, line(1, "minecraft:stone_bricks"));
        assertEquals(AssistantCreativeBuildService.QueueStatus.QUEUED,
                service.queue(OWNER, ASSISTANT, DIMENSION, "plan", new BlockPos(0, 64, 0)).status());

        var paused = service.tick();
        assertEquals(1, paused.pausedUnloaded());
        assertEquals(0, world.reads);
        assertEquals(0, world.writes);

        world.loaded = true;
        assertEquals(1, service.tick().changed());
        assertEquals(1, world.writes);
    }

    @Test void rejectsPlanWithEightThousandOneHundredNinetyThreeCells() {
        Fixture world = new Fixture();
        var cells = new ArrayList<KingdomDesignEngine.Cell>();
        for (int index = 0; index < 8_193; index++)
            cells.add(new KingdomDesignEngine.Cell(index, 0, 0, "minecraft:stone_bricks"));
        var design = new KingdomDesignEngine.Design("huge", 8_193, 1, 1, cells);

        var result = service(world, design).queue(
                OWNER, ASSISTANT, DIMENSION, "huge", new BlockPos(0, 64, 0));

        assertEquals(AssistantCreativeBuildService.QueueStatus.TOO_LARGE, result.status());
        assertEquals(0, world.reads + world.writes);
    }

    private static AssistantCreativeBuildService service(
            Fixture world, KingdomDesignEngine.Design design) {
        return new AssistantCreativeBuildService(new KingdomAssistantBuildData(), world,
                key -> "plan".equals(key) || "huge".equals(key) ? design : null);
    }

    static KingdomDesignEngine.Design line(int count, String material) {
        List<KingdomDesignEngine.Cell> cells = new ArrayList<>();
        for (int x = 0; x < count; x++) cells.add(new KingdomDesignEngine.Cell(x, 0, 0, material));
        return new KingdomDesignEngine.Design("line", count, 1, 1, cells);
    }

    static final class Fixture implements AssistantCreativeBuildService.WorldAccess {
        final Map<BlockPos, String> blocks = new HashMap<>();
        final Set<BlockPos> blockEntities = new HashSet<>();
        final Set<BlockPos> unbreakable = new HashSet<>();
        boolean loaded = true;
        boolean authorized = true;
        int reads;
        int writes;

        @Override public int minBuildHeight(String dimension) { return -64; }
        @Override public int maxBuildHeight(String dimension) { return 320; }
        @Override public boolean isLoaded(String dimension, BlockPos position) { return loaded; }
        @Override public boolean mayBuild(UUID owner, String dimension, BlockPos position) {
            return authorized;
        }
        @Override public AssistantCreativeBuildService.WorldCell inspect(
                String dimension, BlockPos position) {
            reads++;
            String state = blocks.getOrDefault(position, "minecraft:air");
            return new AssistantCreativeBuildService.WorldCell(state,
                    state.equals("minecraft:air"), blockEntities.contains(position),
                    unbreakable.contains(position),
                    !blockEntities.contains(position) && !unbreakable.contains(position));
        }
        @Override public boolean set(String dimension, BlockPos position, String encodedState) {
            writes++;
            blocks.put(position.immutable(), encodedState);
            return true;
        }
    }
}
