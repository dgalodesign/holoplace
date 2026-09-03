package dev.holoplace.render;

import dev.holoplace.schematic.Schematic;
import dev.holoplace.schematic.SchematicRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Read-only {@link BlockAndTintGetter} over a placed {@link Schematic}, queried in camera/world
 * space. {@code anchor} is the world position where the schematic's {@link Schematic#min()} corner
 * sits. Lighting is reported full-bright; biome tint is not applied yet (M4).
 */
public final class SchematicBlockView implements BlockAndTintGetter {

    private static final int FULL_BRIGHT = 15;

    private final Schematic schematic;
    private final BlockPos anchor;
    private final int offX;
    private final int offY;
    private final int offZ;

    public SchematicBlockView(Schematic schematic, BlockPos anchor) {
        this.schematic = schematic;
        this.anchor = anchor.immutable();
        // world -> authored: authored = world - anchor + schematic.min()
        this.offX = schematic.min().getX() - anchor.getX();
        this.offY = schematic.min().getY() - anchor.getY();
        this.offZ = schematic.min().getZ() - anchor.getZ();
    }

    @Override
    public BlockState getBlockState(BlockPos worldPos) {
        int ax = worldPos.getX() + offX;
        int ay = worldPos.getY() + offY;
        int az = worldPos.getZ() + offZ;
        for (SchematicRegion region : schematic.regions()) {
            BlockPos min = region.minCorner();
            int lx = ax - min.getX();
            int ly = ay - min.getY();
            int lz = az - min.getZ();
            if (lx >= 0 && ly >= 0 && lz >= 0
                    && lx < region.sizeX() && ly < region.sizeY() && lz < region.sizeZ()) {
                BlockState state = region.getBlockState(lx, ly, lz);
                if (!state.isAir()) {
                    return state;
                }
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return FULL_BRIGHT;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return FULL_BRIGHT;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return -1;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public int getHeight() {
        return Math.max(16, schematic.enclosingSize().getY());
    }

    @Override
    public int getMinY() {
        return anchor.getY();
    }

    public Schematic schematic() {
        return schematic;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public Vec3i size() {
        return schematic.enclosingSize();
    }
}
