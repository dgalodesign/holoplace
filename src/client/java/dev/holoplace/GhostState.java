package dev.holoplace;

import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;

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
