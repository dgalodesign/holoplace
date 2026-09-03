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
 * Baked ghost geometry for one (schematic, rotation, mirror) combination: a flat list of quads with
 * their footprint-local block origins. Built once (model tesselation + face culling happen here),
 * then replayed every frame with a per-frame translation and colour — so dragging the anchor and
 * changing opacity never trigger a rebuild.
 */
final class GhostMesh {

    /** Quad plus its block origin in footprint-local space (0..footprint on each axis). */
    record Quad(float x, float y, float z, BakedQuad quad) {
    }

    private static final Direction[] FACES = Direction.values();

    private final Schematic schematic;
    private final PlacementTransform transform;
    private final List<Quad> quads;

    private GhostMesh(Schematic schematic, PlacementTransform transform, List<Quad> quads) {
        this.schematic = schematic;
        this.transform = transform;
        this.quads = quads;
    }

    boolean matches(Schematic schematic, PlacementTransform transform) {
        return this.schematic == schematic && this.transform.equals(transform);
    }

    List<Quad> quads() {
        return quads;
    }

    static GhostMesh build(Schematic schematic, PlacementTransform transform) {
        Minecraft mc = Minecraft.getInstance();
        BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
        SchematicBlockView view = new SchematicBlockView(schematic, BlockPos.ZERO, transform);
        RandomSource random = RandomSource.create();
        List<BlockStateModelPart> parts = new ArrayList<>();
        List<Quad> out = new ArrayList<>();

        int schMinX = schematic.min().getX();
        int schMinY = schematic.min().getY();
        int schMinZ = schematic.min().getZ();
        BlockPos.MutableBlockPos footprintPos = new BlockPos.MutableBlockPos();

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
                        footprintPos.set(f[0], f[1], f[2]);
                        BlockState state = transform.applyToState(raw);
                        collectBlock(out, view, models, random, parts, state, footprintPos);
                    }
                }
            }
        }
        return new GhostMesh(schematic, transform, out);
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
            out.add(new Quad(px, py, pz, quad));
        }
    }
}
