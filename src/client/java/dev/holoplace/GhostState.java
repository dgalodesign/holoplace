package dev.holoplace;

import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Mutable singleton: which schematic is shown, where, and how. Read by the renderer each frame. */
public final class GhostState {

    private static final GhostState INSTANCE = new GhostState();

    private @Nullable Schematic schematic;
    private @Nullable String sourceName;
    private BlockPos anchor = BlockPos.ZERO;
    private float opacity = 0.55f;
    private boolean visible;
    private boolean seeThrough;
    private boolean hideMatched;
    private boolean matchBlockOnly;
    private boolean blockEntityModels = true;
    private boolean showEntities = true;
    private boolean shade = true;
    private float markerOpacity = 0.85f;
    private int layerMin = 0;
    private int layerMax = Integer.MAX_VALUE;
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;

    /** -1 = not tracking; otherwise blocks already matching the world, out of {@link #totalBlocks}. */
    private int matchedBlocks = -1;
    private int totalBlocks;

    private GhostState() {
    }

    public static GhostState get() {
        return INSTANCE;
    }

    public @Nullable Schematic schematic() {
        return schematic;
    }

    public @Nullable String sourceName() {
        return sourceName;
    }

    public void setSchematic(@Nullable Schematic schematic, @Nullable String sourceName) {
        this.schematic = schematic;
        this.sourceName = sourceName;
        this.visible = schematic != null;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public void setAnchor(BlockPos anchor) {
        this.anchor = anchor.immutable();
    }

    public boolean isVisible() {
        return visible && schematic != null;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean seeThrough() {
        return seeThrough;
    }

    public void setSeeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
    }

    public boolean hideMatched() {
        return hideMatched;
    }

    public void setHideMatched(boolean hideMatched) {
        this.hideMatched = hideMatched;
    }

    public boolean matchBlockOnly() {
        return matchBlockOnly;
    }

    public void setMatchBlockOnly(boolean matchBlockOnly) {
        this.matchBlockOnly = matchBlockOnly;
    }

    public boolean blockEntityModels() {
        return blockEntityModels;
    }

    public void setBlockEntityModels(boolean blockEntityModels) {
        this.blockEntityModels = blockEntityModels;
    }

    public boolean showEntities() {
        return showEntities;
    }

    public void setShowEntities(boolean showEntities) {
        this.showEntities = showEntities;
    }

    public boolean shade() {
        return shade;
    }

    public void setShade(boolean shade) {
        this.shade = shade;
    }

    /** Opacity of the build-assist wire markers (wrong / extra / block-entity), independent of the
     *  ghost opacity — they're alerts. */
    public float markerOpacity() {
        return markerOpacity;
    }

    public void setMarkerOpacity(float markerOpacity) {
        this.markerOpacity = Mth.clamp(markerOpacity, 0.15f, 1.0f);
    }

    public boolean layerClip() {
        return layerMin > 0 || layerMax != Integer.MAX_VALUE;
    }

    public int layerMin() {
        return layerMin;
    }

    public int layerMax() {
        return layerMax;
    }

    /** Clip the ghost to footprint-local Y in {@code [min, max]} (0 = bottom layer). */
    public void setLayers(int min, int max) {
        this.layerMin = Math.max(0, Math.min(min, max));
        this.layerMax = Math.max(min, max);
    }

    public void clearLayers() {
        this.layerMin = 0;
        this.layerMax = Integer.MAX_VALUE;
    }

    public boolean layerVisible(int localY) {
        return localY >= layerMin && localY <= layerMax;
    }

    /** True when the world state at a ghost cell counts as "already built". */
    public boolean matches(BlockState world, BlockState ghost) {
        return matchBlockOnly ? world.is(ghost.getBlock()) : world == ghost;
    }

    public void setRemainingBlocks(int matchedBlocks, int totalBlocks) {
        this.matchedBlocks = matchedBlocks;
        this.totalBlocks = totalBlocks;
    }

    /** Blocks already matching the world, or -1 when build-assist is off. */
    public int matchedBlocks() {
        return matchedBlocks;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public float opacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Mth.clamp(opacity, 0.05f, 1.0f);
    }

    public int opacityAlpha() {
        return Mth.clamp(Math.round(opacity * 255.0f), 1, 255);
    }

    public Rotation rotation() {
        return rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    public Mirror mirror() {
        return mirror;
    }

    public void setMirror(Mirror mirror) {
        this.mirror = mirror;
    }

    /** Transform for the current schematic + rotation + mirror, or {@code null} when no schematic. */
    public @Nullable PlacementTransform transform() {
        Schematic s = schematic;
        if (s == null) {
            return null;
        }
        Vec3i size = s.enclosingSize();
        return new PlacementTransform(
                Math.max(1, size.getX()), Math.max(1, size.getY()), Math.max(1, size.getZ()),
                mirror, rotation);
    }
}
