package ru.projectkingdom.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Validated block-state palette entries from a WorldEdit/Sponge schematic.
 * Never silently replace an unknown mod block with vanilla air or a default.
 */
public final class KingdomBlockStateCodec {
    private KingdomBlockStateCodec() {}

    public static String blockId(String specification) {
        int index = specification.indexOf('[');
        return index < 0 ? specification : specification.substring(0, index);
    }

    public static BlockState parse(String specification) {
        if (specification == null || specification.isBlank() || specification.length() > 240)
            throw new IllegalArgumentException("Invalid block state");
        String name = blockId(specification);
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id))
            throw new IllegalArgumentException("Unknown block (missing mod?): " + name);
        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        int open = specification.indexOf('[');
        if (open != -1) {
            if (!specification.endsWith("]") || specification.indexOf('[', open + 1) != -1)
                throw new IllegalArgumentException("Malformed block state: " + specification);
            String properties = specification.substring(open + 1, specification.length() - 1);
            if (properties.isBlank()) throw new IllegalArgumentException("Empty block properties");
            for (String pair : properties.split(",")) {
                String[] parts = pair.split("=", -1);
                if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
                    throw new IllegalArgumentException("Invalid state property: " + pair);
                Property<?> property = block.getStateDefinition().getProperty(parts[0].trim());
                if (property == null)
                    throw new IllegalArgumentException("Unknown state property: " + pair);
                state = setProperty(state, property, parts[1].trim());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, Property<T> property, String value) {
        T resolved = property.getValue(value).orElseThrow(
            () -> new IllegalArgumentException("Unknown " + property.getName() + "=" + value));
        return state.setValue(property, resolved);
    }

    public static boolean buildable(BlockState state) {
        Block block = state.getBlock();
        if (state.isAir() || !(block.asItem() instanceof BlockItem item)
                || item.getBlock() != block || !state.getFluidState().isEmpty()) return false;
        if (block instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String name = id.getPath();
        // Never allow state import to create privileged or indestructible blocks.
        return !name.equals("bedrock") && !name.equals("barrier")
            && !name.equals("structure_void") && !name.equals("structure_block")
            && !name.equals("jigsaw") && !name.equals("command_block")
            && !name.equals("repeating_command_block") && !name.equals("chain_command_block")
            && !name.equals("spawner") && !name.equals("end_portal_frame");
    }
}
