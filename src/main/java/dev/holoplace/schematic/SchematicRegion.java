package dev.holoplace.schematic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One sub-region of a {@code .litematic}. Coordinates are normalised so that local {@code (0,0,0)} is
 * the minimum corner and all dimensions are positive; {@link #minCorner()} is that corner in the
 * schematic's own coordinate space (as authored, before the user places it).
 */
public final class SchematicRegion {
    private final String name;
    private final BlockPos minCorner;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockState[] palette;
    private final LitematicaBitArray blocks;
    private final List<CompoundTag> blockEntities;
    private final List<CompoundTag> entities;
    private final Map<Long, CompoundTag> blockEntityByLocalPos;

    public SchematicRegion(String name, BlockPos minCorner, int sizeX, int sizeY, int sizeZ,
                           BlockState[] palette, LitematicaBitArray blocks,
                           List<CompoundTag> blockEntities, List<CompoundTag> entities) {
        this.name = name;
        this.minCorner = minCorner;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.blocks = blocks;
        this.blockEntities = List.copyOf(blockEntities);
        this.entities = List.copyOf(entities);

        this.blockEntityByLocalPos = new HashMap<>();
        for (CompoundTag te : this.blockEntities) {
            this.blockEntityByLocalPos.put(
                    key(te.getIntOr("x", 0), te.getIntOr("y", 0), te.getIntOr("z", 0)), te);
        }
    }

    private static long key(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    /** Raw block-entity NBT at normalised local coords, or {@code null}. */
    public @Nullable CompoundTag blockEntityNbt(int x, int y, int z) {
        return blockEntityByLocalPos.get(key(x, y, z));
    }

    public String name() {
        return name;
    }

    /** Minimum corner in the schematic's authored coordinate space. */
    public BlockPos minCorner() {
        return minCorner;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    public BlockState[] palette() {
        return palette;
    }

    public List<CompoundTag> blockEntities() {
        return blockEntities;
    }

    public List<CompoundTag> entities() {
        return entities;
    }

    /** Block state at normalised local coordinates ({@code 0..size-1} on each axis). */
    public BlockState getBlockState(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return Blocks.AIR.defaultBlockState();
        }
        long index = (long) y * sizeX * sizeZ + (long) z * sizeX + x;
        int id = blocks.get(index);
        if (id < 0 || id >= palette.length) {
            return Blocks.AIR.defaultBlockState();
        }
        return palette[id];
    }

    /** Number of non-air block states in the region. */
    public long countNonAir() {
        long count = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (!getBlockState(x, y, z).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public Vec3i size() {
        return new Vec3i(sizeX, sizeY, sizeZ);
    }
}
