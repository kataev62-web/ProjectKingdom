package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Immutable schematic orientation for architect staging and later frozen
 * construction plans. This class does not alter world blocks, inventories or
 * the original source schematic.
 *
 * Works with negative local overhangs in older built-in templates by
 * normalizing the occupied bounds. Minecraft blockstate rotation respects
 * vanilla facing and delegates mod-specific rotation to the mod block.
 */
public final class KingdomArchitectDesignRotation {
    private KingdomArchitectDesignRotation() {}

    public static KingdomDesignEngine.Design rotate(KingdomDesignEngine.Design original, int quarterTurns) {
        if (original == null || original.blocks().isEmpty())
            throw new IllegalArgumentException("An architect needs a nonempty blueprint");
        int minX=Integer.MAX_VALUE, minY=Integer.MAX_VALUE, minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE, maxY=Integer.MIN_VALUE, maxZ=Integer.MIN_VALUE;
        for (var cell : original.blocks()) {
            minX=Math.min(minX,cell.x()); maxX=Math.max(maxX,cell.x());
            minY=Math.min(minY,cell.y()); maxY=Math.max(maxY,cell.y());
            minZ=Math.min(minZ,cell.z()); maxZ=Math.max(maxZ,cell.z());
        }
        int width=Math.addExact(Math.subtractExact(maxX,minX),1);
        int length=Math.addExact(Math.subtractExact(maxZ,minZ),1);
        int height=Math.addExact(Math.subtractExact(maxY,minY),1);
        var size=KingdomArchitectGeometry.footprint(width,length,quarterTurns);
        if (size.width()>48 || size.length()>48 || height>48
                || (long)size.width()*size.length()*height>65_536)
            throw new IllegalArgumentException("Building is too large for the current schematic importer");
        Rotation rotation=switch(KingdomArchitectGeometry.normalized(quarterTurns)) {
            case 0 -> Rotation.NONE;
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new AssertionError();
        };
        var cells=new ArrayList<KingdomDesignEngine.Cell>(original.blocks().size());
        for (var cell:original.blocks()) {
            var at=KingdomArchitectGeometry.rotate(cell.x()-minX,cell.z()-minZ,
                width,length,quarterTurns);
            BlockState originalState=KingdomBlockStateCodec.parse(cell.material());
            BlockState turned=originalState.rotate(rotation);
            // The transformed state must also be buildable; never ignore
            // unsupported modded state mutations.
            if (!KingdomBlockStateCodec.buildable(turned))
                throw new IllegalArgumentException("Unsupported rotated state: "+cell.material());
            cells.add(new KingdomDesignEngine.Cell(at.x(), cell.y()-minY,
                at.z(), encode(turned)));
        }
        return new KingdomDesignEngine.Design(original.name(),
            size.width(),size.length(),height,cells);
    }

    /** Serialize every property, not just 'facing', to preserve stairs and decorations. */
    public static String encode(BlockState state) {
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id==null)throw new IllegalArgumentException("Unknown block state");
        var properties=new ArrayList<>(state.getProperties());
        properties.sort(Comparator.comparing(Property::getName));
        if (properties.isEmpty())return id.toString();
        var s=new StringBuilder(id.toString()).append('[');
        for (int i=0;i<properties.size();i++) {
            if (i>0)s.append(',');
            Property<?> property=properties.get(i);
            s.append(property.getName()).append('=').append(name(state,property));
        }
        return s.append(']').toString();
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
