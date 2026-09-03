package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.Schematic;
import dev.holoplace.schematic.SchematicRegion;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * M2 — draws the current {@link GhostState} schematic as a textured, translucent ghost during
 * {@code AFTER_TRANSLUCENT_TERRAIN}. Immediate mode, rebuilt every frame (fine for MVP-sized
 * schematics; a per-section VBO cache is a later milestone). No AO, no biome tint, no fluids/BEs yet.
 */
public final class GhostRenderer {

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Direction[] FACES = Direction.values();

    private static final RandomSource RANDOM = RandomSource.create();
    private static final QuadInstance QUAD = new QuadInstance();
    private static final List<BlockStateModelPart> PARTS = new ArrayList<>();

    private GhostRenderer() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(GhostRenderer::render);
    }

    private static void render(LevelRenderContext ctx) {
        GhostState state = GhostState.get();
        Schematic schematic = state.schematic();
        if (!state.isVisible() || schematic == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        BlockPos anchor = state.anchor();
        SchematicBlockView view = new SchematicBlockView(schematic, anchor);
        BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        RenderType renderType = RenderTypes.translucentMovingBlock();
        VertexConsumer buffer = ctx.bufferSource().getBuffer(renderType);

        QUAD.setColor((state.opacityAlpha() << 24) | 0x00FFFFFF);
        QUAD.setLightCoords(FULL_BRIGHT);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        int emitted = 0;

        for (SchematicRegion region : schematic.regions()) {
            BlockPos regionOrigin = region.minCorner();
            int baseX = anchor.getX() + (regionOrigin.getX() - schematic.min().getX());
            int baseY = anchor.getY() + (regionOrigin.getY() - schematic.min().getY());
            int baseZ = anchor.getZ() + (regionOrigin.getZ() - schematic.min().getZ());

            for (int y = 0; y < region.sizeY(); y++) {
                for (int z = 0; z < region.sizeZ(); z++) {
                    for (int x = 0; x < region.sizeX(); x++) {
                        BlockState blockState = region.getBlockState(x, y, z);
                        if (blockState.isAir()) {
                            continue;
                        }
                        worldPos.set(baseX + x, baseY + y, baseZ + z);
                        emitted += emitBlock(buffer, view, models, blockState, worldPos, cam);
                    }
                }
            }
        }

        ctx.bufferSource().endBatch(renderType);

        if (emitted == 0) {
            HoloPlaceClient.LOGGER.debug("Ghost produced no quads for {}", state.sourceName());
        }
    }

    private static int emitBlock(VertexConsumer buffer, SchematicBlockView view, BlockStateModelSet models,
                                 BlockState blockState, BlockPos worldPos, Vec3 cam) {
        BlockStateModel model = models.get(blockState);
        RANDOM.setSeed(blockState.getSeed(worldPos));
        PARTS.clear();
        model.collectParts(RANDOM, PARTS);
        if (PARTS.isEmpty()) {
            return 0;
        }

        float rx = (float) (worldPos.getX() - cam.x);
        float ry = (float) (worldPos.getY() - cam.y);
        float rz = (float) (worldPos.getZ() - cam.z);

        int count = 0;
        for (BlockStateModelPart part : PARTS) {
            count += emitQuads(buffer, part.getQuads(null), rx, ry, rz);
            for (Direction face : FACES) {
                if (occludes(view, worldPos, face)) {
                    continue;
                }
                count += emitQuads(buffer, part.getQuads(face), rx, ry, rz);
            }
        }
        return count;
    }

    private static int emitQuads(VertexConsumer buffer, List<BakedQuad> quads, float rx, float ry, float rz) {
        for (BakedQuad quad : quads) {
            buffer.putBlockBakedQuad(rx, ry, rz, quad, QUAD);
        }
        return quads.size();
    }

    private static boolean occludes(SchematicBlockView view, BlockPos pos, Direction face) {
        return view.getBlockState(pos.relative(face)).isSolidRender();
    }
}
