package dev.holoplace.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.jspecify.annotations.Nullable;

/**
 * The two corners of a capture area. Corners are kept exactly as the player clicked them; the
 * inclusive min/max box, its size and its volume are all derived, so a selection is valid no matter
 * which order the corners were set in.
 */
public final class SelectionState {

    private @Nullable BlockPos corner1;
    private @Nullable BlockPos corner2;

    public void setCorner1(BlockPos pos) {
        this.corner1 = pos.immutable();
    }

    public void setCorner2(BlockPos pos) {
        this.corner2 = pos.immutable();
    }

    public void clear() {
        this.corner1 = null;
        this.corner2 = null;
    }

    public @Nullable BlockPos corner1() {
        return corner1;
    }

    public @Nullable BlockPos corner2() {
        return corner2;
    }

    public boolean isComplete() {
        return corner1 != null && corner2 != null;
    }

    /** Lowest corner of the inclusive box, or {@code null} while both corners aren't set. */
    public @Nullable BlockPos min() {
        BlockPos a = corner1;
        BlockPos b = corner2;
        if (a == null || b == null) {
            return null;
        }
        return new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
    }

    /** Highest corner of the inclusive box, or {@code null} while both corners aren't set. */
    public @Nullable BlockPos max() {
        BlockPos a = corner1;
        BlockPos b = corner2;
        if (a == null || b == null) {
            return null;
        }
        return new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    /** Inclusive dimensions (each axis at least 1), or {@link Vec3i#ZERO} while the box is incomplete. */
    public Vec3i size() {
        BlockPos lo = min();
        BlockPos hi = max();
        if (lo == null || hi == null) {
            return Vec3i.ZERO;
        }
        return new Vec3i(
                hi.getX() - lo.getX() + 1,
                hi.getY() - lo.getY() + 1,
                hi.getZ() - lo.getZ() + 1);
    }

    /** Cell count of the inclusive box, or 0 while incomplete. */
    public long volume() {
        Vec3i s = size();
        return (long) s.getX() * s.getY() * s.getZ();
    }
}
