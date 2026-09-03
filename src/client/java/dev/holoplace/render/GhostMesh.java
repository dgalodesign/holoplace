package dev.holoplace.render;

import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import dev.holoplace.schematic.SchematicRegion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Baked ghost geometry for one (schematic, rotation, mirror) combination. Model tesselation and face
 * culling happen once, in {@link #build}. The result is addressable per source block — a block's
 * footprint-local position, its transformed {@link BlockState} (for comparing against the real world)
 * and the slice of {@link #quads} it produced — so the renderer can skip whole blocks each frame
 * (e.g. build-assist mode) without any re-tesselation.
 */
final class GhostMesh {

    /** Quad plus its block origin in footprint-local space (0..footprint on each axis). */
    record Quad(float x, float y, float z, BakedQuad quad, boolean tinted) {
    }

    private static final Direction[] FACES = Direction.values();

    private final Schematic schematic;
    private final PlacementTransform transform;

    private final int[] blockX;
    private final int[] blockY;
    private final int[] blockZ;
    private final BlockState[] blockStates;
    private final int[] quadStart; // length blockCount + 1
    private final Quad[] quads;

    private final int[] fluidX;
    private final int[] fluidY;
    private final int[] fluidZ;
    private final BlockState[] fluidStates;

    /** Blocks with a block entity that render (almost) no model — chests, signs, beds, skulls, … */
    private final int[] beX;
    private final int[] beY;
    private final int[] beZ;
    private final BlockState[] beStates;

    private GhostMesh(Schematic schematic, PlacementTransform transform,
                     int[] blockX, int[] blockY, int[] blockZ, BlockState[] blockStates,
                     int[] quadStart, Quad[] quads,
                     int[] fluidX, int[] fluidY, int[] fluidZ, BlockState[] fluidStates,
                     int[] beX, int[] beY, int[] beZ, BlockState[] beStates) {
        this.schematic = schematic;
        this.transform = transform;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.blockStates = blockStates;
        this.quadStart = quadStart;
        this.quads = quads;
        this.fluidX = fluidX;
        this.fluidY = fluidY;
        this.fluidZ = fluidZ;
        this.fluidStates = fluidStates;
        this.beX = beX;
        this.beY = beY;
        this.beZ = beZ;
        this.beStates = beStates;
    }

    boolean matches(Schematic schematic, PlacementTransform transform) {
        return this.schematic == schematic && this.transform.equals(transform);
    }

    Schematic schematic() {
        return schematic;
    }

    PlacementTransform transform() {
        return transform;
    }

    int blockCount() {
        return blockX.length;
    }

    int blockX(int i) {
        return blockX[i];
    }

    int blockY(int i) {
        return blockY[i];
    }

    int blockZ(int i) {
        return blockZ[i];
    }

    BlockState blockState(int i) {
        return blockStates[i];
    }

    int quadStart(int i) {
        return quadStart[i];
    }

    Quad quad(int q) {
        return quads[q];
    }

    int totalQuads() {
        return quads.length;
    }

    int fluidCount() {
        return fluidX.length;
    }

    int fluidX(int i) {
        return fluidX[i];
    }

    int fluidY(int i) {
        return fluidY[i];
    }

    int fluidZ(int i) {
        return fluidZ[i];
    }

    BlockState fluidState(int i) {
        return fluidStates[i];
    }

    int blockEntityCount() {
        return beX.length;
    }

    int beX(int i) {
        return beX[i];
    }

    int beY(int i) {
        return beY[i];
    }

    int beZ(int i) {
        return beZ[i];
    }

    BlockState beState(int i) {
        return beStates[i];
    }

    static GhostMesh build(Schematic schematic, PlacementTransform transform) {
        Minecraft mc = Minecraft.getInstance();
        BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
        SchematicBlockView view = new SchematicBlockView(schematic, BlockPos.ZERO, transform);
        RandomSource random = RandomSource.create();
        List<BlockStateModelPart> parts = new ArrayList<>();

        List<int[]> blockPositions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Quad> allQuads = new ArrayList<>();
        List<int[]> fluidPositions = new ArrayList<>();
        List<BlockState> fluidStateList = new ArrayList<>();
        List<int[]> bePositions = new ArrayList<>();
        List<BlockState> beStateList = new ArrayList<>();

        int schMinX = schematic.min().getX();
        int schMinY = schematic.min().getY();
        int schMinZ = schematic.min().getZ();
        BlockPos.MutableBlockPos fp = new BlockPos.MutableBlockPos();

        for (SchematicRegion region : schematic.regions()) {
            BlockPos origin = region.minCorner();
            int authoredBaseX = origin.getX() - schMinX;
            int authoredBaseY = origin.getY() - schMinY;
            int authoredBaseZ = origin.getZ() - schMinZ;

            for (int y = 0; y < region.sizeY(); y++) {
                for (int z = 0; z < region.sizeZ(); z++) {
                    for (int x = 0; x < region.sizeX(); x++) {
                        BlockState raw = region.getBlockState(x, y, z);
                        if (raw.isAir()) {
                            continue;
                        }
                        int[] f = transform.forward(authoredBaseX + x, authoredBaseY + y, authoredBaseZ + z);
                        fp.set(f[0], f[1], f[2]);
                        BlockState state = transform.applyToState(raw);

                        if (!state.getFluidState().isEmpty()) {
                            fluidPositions.add(new int[] {f[0], f[1], f[2]});
                            fluidStateList.add(state);
                        }

                        int before = allQuads.size();
                        collectBlock(allQuads, view, models, random, parts, state, fp);
                        boolean producedQuads = allQuads.size() != before;

                        if (state.hasBlockEntity() && !producedQuads) {
                            bePositions.add(new int[] {f[0], f[1], f[2]});
                            beStateList.add(state);
                        }
                        if (!producedQuads) {
                            continue;
                        }
                        blockPositions.add(new int[] {f[0], f[1], f[2]});
                        states.add(state);
                        starts.add(before);
                    }
                }
            }
        }

        int blockCount = blockPositions.size();
        int[] bx = new int[blockCount];
        int[] by = new int[blockCount];
        int[] bz = new int[blockCount];
        int[] quadStart = new int[blockCount + 1];
        BlockState[] stateArr = states.toArray(new BlockState[0]);
        for (int i = 0; i < blockCount; i++) {
            int[] p = blockPositions.get(i);
            bx[i] = p[0];
            by[i] = p[1];
            bz[i] = p[2];
            quadStart[i] = starts.get(i);
        }
        quadStart[blockCount] = allQuads.size();

        return new GhostMesh(schematic, transform, bx, by, bz, stateArr, quadStart,
                allQuads.toArray(new Quad[0]),
                col(fluidPositions, 0), col(fluidPositions, 1), col(fluidPositions, 2),
                fluidStateList.toArray(new BlockState[0]),
                col(bePositions, 0), col(bePositions, 1), col(bePositions, 2),
                beStateList.toArray(new BlockState[0]));
    }

    private static int[] col(List<int[]> rows, int axis) {
        int[] out = new int[rows.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = rows.get(i)[axis];
        }
        return out;
    }

    private static void collectBlock(List<Quad> out, SchematicBlockView view, BlockStateModelSet models,
                                     RandomSource random, List<BlockStateModelPart> parts,
                                     BlockState state, BlockPos pos) {
        BlockStateModel model = models.get(state);
        random.setSeed(state.getSeed(pos));
        parts.clear();
        model.collectParts(random, parts);
        if (parts.isEmpty()) {
            return;
        }
        float px = pos.getX();
        float py = pos.getY();
        float pz = pos.getZ();
        for (BlockStateModelPart part : parts) {
            addQuads(out, part.getQuads(null), px, py, pz);
            for (Direction face : FACES) {
                if (view.getBlockState(pos.relative(face)).isSolidRender()) {
                    continue;
                }
                addQuads(out, part.getQuads(face), px, py, pz);
            }
        }
    }

    private static void addQuads(List<Quad> out, List<BakedQuad> quads, float px, float py, float pz) {
        for (BakedQuad quad : quads) {
            out.add(new Quad(px, py, pz, quad, quad.materialInfo().isTinted()));
        }
    }
}
