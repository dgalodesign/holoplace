package dev.holoplace;

import dev.holoplace.schematic.Schematic;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Mutable singleton: which schematic is shown, where, and how. Read by the renderer each frame. */
public final class GhostState {

    private static final GhostState INSTANCE = new GhostState();

    private @Nullable Schematic schematic;
    private @Nullable String sourceName;
    private BlockPos anchor = BlockPos.ZERO;
    private float opacity = 0.55f;
    private boolean visible;

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

    public float opacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Mth.clamp(opacity, 0.05f, 1.0f);
    }

    public int opacityAlpha() {
        return Mth.clamp(Math.round(opacity * 255.0f), 1, 255);
    }
}
