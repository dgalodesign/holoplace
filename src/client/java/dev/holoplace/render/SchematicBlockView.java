package dev.holoplace.render;

import dev.holoplace.schematic.PlacementTransform;
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
 * Read-only {@link BlockAndTintGetter} over a placed, transformed {@link Schematic}, queried in
 * camera/world space. {@code anchor} is where the transformed footprint's minimum corner sits.
 * Lighting is reported full-bright; biome tint is not applied yet.
 */
public final class SchematicBlockView implements BlockAndTintGetter {

    private static final int FULL_BRIGHT = 15;

    private final Schematic schematic;
    private final BlockPos anchor;
    private final PlacementTransform transform;

    public SchematicBlockView(Schematic schematic, BlockPos anchor, PlacementTransform transform) {
        this.schematic = schematic;
        this.anchor = anchor.immutable();
        this.transform = transform;
    }

    @Override
    public BlockState getBlockState(BlockPos worldPos) {
        int fx = worldPos.getX() - anchor.getX();
        int fy = worldPos.getY() - anchor.getY();
        int fz = worldPos.getZ() - anchor.getZ();
        if (fx < 0 || fy < 0 || fz < 0
                || fx >= transform.footprintX() || fy >= transform.footprintY() || fz >= transform.footprintZ()) {
            return Blocks.AIR.defaultBlockState();
        }
        int[] authored = transform.inverse(fx, fy, fz);
        int ax = authored[0] + schematic.min().getX();
        int ay = authored[1] + schematic.min().getY();
        int az = authored[2] + schematic.min().getZ();

        for (SchematicRegion region : schematic.regions()) {
            BlockPos min = region.minCorner();
            int lx = ax - min.getX();
            int ly = ay - min.getY();
            int lz = az - min.getZ();
            if (lx >= 0 && ly >= 0 && lz >= 0
                    && lx < region.sizeX() && ly < region.sizeY() && lz < region.sizeZ()) {
                BlockState state = region.getBlockState(lx, ly, lz);
                if (!state.isAir()) {
                    return transform.applyToState(state);
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
        return Math.max(16, transform.footprintY());
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
