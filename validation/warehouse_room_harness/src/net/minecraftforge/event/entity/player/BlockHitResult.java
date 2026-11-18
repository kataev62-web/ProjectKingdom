package net.minecraftforge.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BlockHitResult {
    private final Vec3 location;
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean inside;

    public BlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        this.location = location;
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
    }

    public Vec3 getLocation() { return location; }
    public Direction getDirection() { return direction; }
    public BlockPos getBlockPos() { return blockPos; }
    public boolean isInside() { return inside; }
}