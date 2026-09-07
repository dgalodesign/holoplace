package dev.holoplace.schematic;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps positions between a schematic's authored bounding-box space {@code [0, size)} and the
 * transformed footprint space produced by applying a {@link Mirror} then a {@link Rotation} about the
 * vertical axis (mirror first, then rotate).
 *
 * <p>{@code sizeX/Y/Z} are the <b>authored</b> enclosing dimensions. After a 90°/270° rotation the
 * footprint's X and Z extents swap — see {@link #footprintX()} / {@link #footprintZ()}.
 */
public record PlacementTransform(int sizeX, int sizeY, int sizeZ, Mirror mirror, Rotation rotation) {

    public boolean isIdentity() {
        return mirror == Mirror.NONE && rotation == Rotation.NONE;
    }

    public int footprintX() {
        return swapsXZ() ? sizeZ : sizeX;
    }

    public int footprintY() {
        return sizeY;
    }

    public int footprintZ() {
        return swapsXZ() ? sizeX : sizeZ;
    }

    private boolean swapsXZ() {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    /** Authored bounding-box coords → transformed footprint coords. */
    public int[] forward(int x, int y, int z) {
        int mx = mirror == Mirror.FRONT_BACK ? sizeX - 1 - x : x;
        int mz = mirror == Mirror.LEFT_RIGHT ? sizeZ - 1 - z : z;
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[] {sizeZ - 1 - mz, y, mx};
            case COUNTERCLOCKWISE_90 -> new int[] {mz, y, sizeX - 1 - mx};
            case CLOCKWISE_180 -> new int[] {sizeX - 1 - mx, y, sizeZ - 1 - mz};
            default -> new int[] {mx, y, mz};
        };
    }

    /**
     * Continuous version of {@link #forward} for fractional positions (entities). Reflects about the
     * box edges ({@code size - p}) rather than block-index parity ({@code size - 1 - p}).
     */
    public double[] forwardExact(double x, double y, double z) {
        double mx = mirror == Mirror.FRONT_BACK ? sizeX - x : x;
        double mz = mirror == Mirror.LEFT_RIGHT ? sizeZ - z : z;
        return switch (rotation) {
            case CLOCKWISE_90 -> new double[] {sizeZ - mz, y, mx};
            case COUNTERCLOCKWISE_90 -> new double[] {mz, y, sizeX - mx};
            case CLOCKWISE_180 -> new double[] {sizeX - mx, y, sizeZ - mz};
            default -> new double[] {mx, y, mz};
        };
    }

    /** Transformed footprint coords → authored bounding-box coords (inverse of {@link #forward}). */
    public int[] inverse(int fx, int fy, int fz) {
        int mx;
        int mz;
        switch (rotation) {
            case CLOCKWISE_90 -> {
                mx = fz;
                mz = sizeZ - 1 - fx;
            }
            case COUNTERCLOCKWISE_90 -> {
                mx = sizeX - 1 - fz;
                mz = fx;
            }
            case CLOCKWISE_180 -> {
                mx = sizeX - 1 - fx;
                mz = sizeZ - 1 - fz;
            }
            default -> {
                mx = fx;
                mz = fz;
            }
        }
        int x = mirror == Mirror.FRONT_BACK ? sizeX - 1 - mx : mx;
        int z = mirror == Mirror.LEFT_RIGHT ? sizeZ - 1 - mz : mz;
        return new int[] {x, fy, z};
    }

    public BlockState applyToState(BlockState state) {
        return state.mirror(mirror).rotate(rotation);
    }

    public PlacementTransform withRotation(Rotation rotation) {
        return new PlacementTransform(sizeX, sizeY, sizeZ, mirror, rotation);
    }

    public PlacementTransform withMirror(Mirror mirror) {
        return new PlacementTransform(sizeX, sizeY, sizeZ, mirror, rotation);
    }

    public static Rotation rotateCw(Rotation r) {
        return switch (r) {
            case NONE -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.NONE;
        };
    }

    public static Rotation rotateCcw(Rotation r) {
        return switch (r) {
            case NONE -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.NONE;
        };
    }

    public static Mirror cycleMirror(Mirror m) {
        return switch (m) {
            case NONE -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.LEFT_RIGHT;
            case LEFT_RIGHT -> Mirror.NONE;
        };
    }
}
