package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

/**
 * Draws the current {@link GhostState} schematic as a textured, translucent ghost during
 * {@code AFTER_TRANSLUCENT_TERRAIN}. Geometry is baked once by {@link GhostMesh}; each frame the
 * renderer only walks the baked blocks, optionally skipping any that already match the world
 * (build-assist), and replays their quads with a per-frame translate and colour. Biome tint
 * (grass/leaves/water) is resolved against the real world and cached until the anchor moves.
 */
public final class GhostRenderer {

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int NO_TINT = -1;
    private static final QuadInstance QUAD = new QuadInstance();

    private static @Nullable GhostMesh mesh;
    private static int @Nullable [] blockTint;
    private static long tintKey;

    private GhostRenderer() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(GhostRenderer::render);
    }

    public static void invalidate() {
        mesh = null;
        blockTint = null;
    }

    private static void render(LevelRenderContext ctx) {
        GhostState state = GhostState.get();
        Schematic schematic = state.schematic();
        PlacementTransform transform = state.transform();
        if (!state.isVisible() || schematic == null || transform == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        if (mesh == null || !mesh.matches(schematic, transform)) {
            long start = System.nanoTime();
            mesh = GhostMesh.build(schematic, transform);
            blockTint = null;
            HoloPlaceClient.LOGGER.debug("Rebuilt ghost mesh: {} blocks / {} quads in {} ms",
                    mesh.blockCount(), mesh.totalQuads(), (System.nanoTime() - start) / 1_000_000);
        }
        GhostMesh m = mesh;

        BlockPos anchor = state.anchor();
        int[] tint = tintFor(m, anchor, level, mc);

        var cam = mc.gameRenderer.getMainCamera().position();
        float ox = (float) (anchor.getX() - cam.x);
        float oy = (float) (anchor.getY() - cam.y);
        float oz = (float) (anchor.getZ() - cam.z);
        int alpha = state.opacityAlpha() << 24;
        int white = alpha | 0x00FFFFFF;

        RenderType renderType = GhostPipelines.forGhost(state.seeThrough());
        VertexConsumer buffer = ctx.bufferSource().getBuffer(renderType);
        QUAD.setLightCoords(FULL_BRIGHT);

        boolean hideMatched = state.hideMatched();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        int shown = 0;

        for (int i = 0, blocks = m.blockCount(); i < blocks; i++) {
            if (hideMatched) {
                worldPos.set(anchor.getX() + m.blockX(i), anchor.getY() + m.blockY(i),
                        anchor.getZ() + m.blockZ(i));
                if (state.matches(level.getBlockState(worldPos), m.blockState(i))) {
                    continue;
                }
            }
            int tinted = tint[i] == NO_TINT ? white : alpha | (tint[i] & 0x00FFFFFF);
            int end = m.quadStart(i + 1);
            for (int q = m.quadStart(i); q < end; q++) {
                GhostMesh.Quad quad = m.quad(q);
                QUAD.setColor(quad.tinted() ? tinted : white);
                buffer.putBlockBakedQuad(quad.x() + ox, quad.y() + oy, quad.z() + oz, quad.quad(), QUAD);
            }
            shown++;
        }

        if (m.fluidCount() > 0) {
            renderFluids(m, anchor, level, mc, buffer, hideMatched);
        }
        ctx.bufferSource().endBatch(renderType);

        if (m.blockEntityCount() > 0) {
            renderBlockEntityMarkers(m, anchor, level, cam, ctx, hideMatched);
        }

        state.setRemainingBlocks(hideMatched ? m.blockCount() - shown : -1, m.blockCount());
    }

    /** Block entities render (almost) nothing as a model, so mark their cells with a wire cube. */
    private static void renderBlockEntityMarkers(GhostMesh m, BlockPos anchor, ClientLevel level,
                                                 net.minecraft.world.phys.Vec3 cam,
                                                 LevelRenderContext ctx, boolean hideMatched) {
        int color = (GhostState.get().opacityAlpha() << 24) | 0x0055CCFF;
        VertexConsumer lines = ctx.bufferSource().getBuffer(RenderTypes.lines());
        PoseStack ps = new PoseStack();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        for (int i = 0, n = m.blockEntityCount(); i < n; i++) {
            worldPos.set(anchor.getX() + m.beX(i), anchor.getY() + m.beY(i), anchor.getZ() + m.beZ(i));
            if (hideMatched && GhostState.get().matches(level.getBlockState(worldPos), m.beState(i))) {
                continue;
            }
            ShapeRenderer.renderShape(ps, lines, Shapes.block(),
                    worldPos.getX() - cam.x, worldPos.getY() - cam.y, worldPos.getZ() - cam.z,
                    color, 2.0f);
        }
        ctx.bufferSource().endBatch(RenderTypes.lines());
    }

    private static void renderFluids(GhostMesh m, BlockPos anchor, ClientLevel level, Minecraft mc,
                                     VertexConsumer buffer, boolean hideMatched) {
        SchematicBlockView view = new SchematicBlockView(m.schematic(), anchor, m.transform());
        FluidRenderer fluidRenderer = new FluidRenderer(mc.getModelManager().getFluidStateModelSet());
        FluidRenderer.Output output = layer -> buffer;
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        for (int i = 0, n = m.fluidCount(); i < n; i++) {
            worldPos.set(anchor.getX() + m.fluidX(i), anchor.getY() + m.fluidY(i),
                    anchor.getZ() + m.fluidZ(i));
            BlockState state = m.fluidState(i);
            if (hideMatched && GhostState.get().matches(level.getBlockState(worldPos), state)) {
                continue;
            }
            fluidRenderer.tesselate(view, worldPos, output, state, state.getFluidState());
        }
    }

    /** Per-block tint colour (index 0), recomputed only when the mesh or anchor changes. */
    private static int[] tintFor(GhostMesh m, BlockPos anchor, ClientLevel level, Minecraft mc) {
        long key = ((long) System.identityHashCode(m) << 32) ^ anchor.asLong();
        int[] cached = blockTint;
        if (cached != null && key == tintKey && cached.length == m.blockCount()) {
            return cached;
        }
        int[] tint = new int[m.blockCount()];
        var colors = mc.getBlockColors();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < tint.length; i++) {
            BlockState bs = m.blockState(i);
            List<BlockTintSource> sources = colors.getTintSources(bs);
            if (sources.isEmpty()) {
                tint[i] = NO_TINT;
                continue;
            }
            pos.set(anchor.getX() + m.blockX(i), anchor.getY() + m.blockY(i), anchor.getZ() + m.blockZ(i));
            tint[i] = sources.get(0).colorInWorld(bs, level, pos);
        }
        blockTint = tint;
        tintKey = key;
        return tint;
    }
}
