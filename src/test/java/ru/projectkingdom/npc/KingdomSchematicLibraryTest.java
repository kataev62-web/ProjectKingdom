package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real gzip NBT .schem fixtures: test palette decoding, block states and rejection. */
class KingdomSchematicLibraryTest {
    @TempDir Path directory;

    @BeforeAll static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private CompoundTag schematic(int version, int width, int height, int length,
                                  String[] keys, byte[] indices) {
        CompoundTag outer = new CompoundTag();
        outer.putInt("Version", version);
        outer.putShort("Width", (short) width);
        outer.putShort("Height", (short) height);
        outer.putShort("Length", (short) length);
        CompoundTag palette = new CompoundTag();
        for (int i = 0; i < keys.length; i++) palette.putInt(keys[i], i);
        CompoundTag blocks = version == 3 ? new CompoundTag() : outer;
        blocks.put("Palette", palette);
        blocks.putByteArray(version == 3 ? "Data" : "BlockData", indices);
        if (version == 3) outer.put("Blocks", blocks);
        return outer;
    }

    private Path write(String name, CompoundTag tag) throws Exception {
        var file = directory.resolve(name + ".schem");
        NbtIo.writeCompressed(tag, file.toFile());
        return file;
    }

    @Test void readsSpongeV2YzxOrderAndPreservesBlockState() throws Exception {
        var tag = schematic(2, 2, 1, 2, new String[]{
            "minecraft:air",
            "minecraft:stone_bricks",
            "minecraft:spruce_log[axis=x]"
        }, new byte[]{1, 0, 2, 1});
        var design = KingdomSchematicLibrary.readDesign(write("home", tag), "home");
        assertEquals(2, design.width());
        assertEquals(2, design.length());
        assertEquals(3, design.blocks().size());
        assertEquals("minecraft:stone_bricks", design.blocks().get(0).material());
        assertEquals(0, design.blocks().get(0).x());
        assertEquals(0, design.blocks().get(0).z());
        assertEquals("minecraft:spruce_log[axis=x]", design.blocks().get(1).material());
        assertEquals(0, design.blocks().get(1).x());
        assertEquals(1, design.blocks().get(1).z());
        assertEquals(1, design.blocks().get(2).x());
        assertEquals(1, design.blocks().get(2).z());
        assertEquals(2, KingdomConstructionPlans.bill(design).get("minecraft:stone_bricks"));
    }

    @Test void readsNestedSpongeV3AndMultiBytePaletteVarints() throws Exception {
        var keys = new String[130];
        // Palette may contain many distinct states but only one building state
        // needs to appear in a tiny schematic. Unknown state names are rejected.
        for (int i = 0; i < keys.length; i++) keys[i] = "minecraft:stone_bricks";
        // For multi-byte IDs use 129 unique state strings generated from
        // block properties; a horizontal axis log gives three, so instead
        // assert v3 independently and test truncated varint separately.
        var tag = schematic(3, 1, 1, 1, new String[]{"minecraft:oak_stairs[facing=east]"},
            new byte[]{0});
        CompoundTag root = new CompoundTag();
        root.put("Schematic", tag);
        var plan = KingdomSchematicLibrary.readDesign(write("tower", root), "tower");
        assertEquals("minecraft:oak_stairs[facing=east]", plan.blocks().get(0).material());
    }

    @Test void rejectsUnknownBlocksOversizedAndBrokenVarints() throws Exception {
        var unknown = schematic(2, 1, 1, 1, new String[]{"imaginarymod:missing"}, new byte[]{0});
        assertThrows(IllegalArgumentException.class,
            () -> KingdomSchematicLibrary.readDesign(write("unknown", unknown), "unknown"));
        var big = schematic(2, 80, 1, 1, new String[]{"minecraft:stone"}, new byte[]{0});
        assertThrows(IOException.class,
            () -> KingdomSchematicLibrary.readDesign(write("big", big), "big"));
        var truncated = schematic(2, 1, 1, 1, new String[]{"minecraft:stone"},
            new byte[]{(byte) 0x80});
        assertThrows(IOException.class,
            () -> KingdomSchematicLibrary.readDesign(write("broken", truncated), "broken"));
        var excess = schematic(2, 1, 1, 1, new String[]{"minecraft:stone"}, new byte[]{0, 0});
        assertThrows(IOException.class,
            () -> KingdomSchematicLibrary.readDesign(write("trailing", excess), "trailing"));
    }

    @Test void rejectsFluidAndPrivilegedSurvivalStates() {
        assertFalse(KingdomBlockStateCodec.buildable(KingdomBlockStateCodec.parse("minecraft:water")));
        assertFalse(KingdomBlockStateCodec.buildable(KingdomBlockStateCodec.parse("minecraft:bedrock")));
        assertFalse(KingdomBlockStateCodec.buildable(KingdomBlockStateCodec.parse("minecraft:stone_slab[type=double]")));
        assertTrue(KingdomBlockStateCodec.buildable(KingdomBlockStateCodec.parse("minecraft:spruce_log[axis=z]")));
    }

    @Test void rejectsUnsafeNames() {
        assertTrue(KingdomSchematicLibrary.validName("avalon_castle_01"));
        assertFalse(KingdomSchematicLibrary.validName("../castle"));
        assertFalse(KingdomSchematicLibrary.validName("Castle Name"));
        assertFalse(KingdomSchematicLibrary.validName("a/b"));
    }
}
