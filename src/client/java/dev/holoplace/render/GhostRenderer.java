package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Draws the current {@link GhostState} schematic as a textured, translucent ghost during
 * {@code AFTER_TRANSLUCENT_TERRAIN}. Geometry is baked once by {@link GhostMesh}; each frame the
 * renderer only walks the baked blocks, optionally skipping any that already match the world
 * (build-assist), and replays their quads with a per-frame translate and colour.
 */
public final class GhostRenderer {

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final QuadInstance QUAD = new QuadInstance();

    private static @Nullable GhostMesh mesh;

    private GhostRenderer() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(GhostRenderer::render);
    }

    public static void invalidate() {
        mesh = null;
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
            HoloPlaceClient.LOGGER.debug("Rebuilt ghost mesh: {} blocks / {} quads in {} ms",
                    mesh.blockCount(), mesh.totalQuads(), (System.nanoTime() - start) / 1_000_000);
        }
        GhostMesh m = mesh;

        var cam = mc.gameRenderer.getMainCamera().position();
        BlockPos anchor = state.anchor();
        float ox = (float) (anchor.getX() - cam.x);
        float oy = (float) (anchor.getY() - cam.y);
        float oz = (float) (anchor.getZ() - cam.z);

        RenderType renderType = GhostPipelines.forGhost(state.seeThrough());
        VertexConsumer buffer = ctx.bufferSource().getBuffer(renderType);
        QUAD.setColor((state.opacityAlpha() << 24) | 0x00FFFFFF);
        QUAD.setLightCoords(FULL_BRIGHT);

        boolean hideMatched = state.hideMatched();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        int shown = 0;

        for (int i = 0, blocks = m.blockCount(); i < blocks; i++) {
            if (hideMatched) {
                worldPos.set(anchor.getX() + m.blockX(i), anchor.getY() + m.blockY(i),
                        anchor.getZ() + m.blockZ(i));
                if (level.getBlockState(worldPos) == m.blockState(i)) {
                    continue;
                }
            }
            int end = m.quadStart(i + 1);
            for (int q = m.quadStart(i); q < end; q++) {
                GhostMesh.Quad quad = m.quad(q);
                buffer.putBlockBakedQuad(quad.x() + ox, quad.y() + oy, quad.z() + oz, quad.quad(), QUAD);
            }
            shown++;
        }

        ctx.bufferSource().endBatch(renderType);

        if (hideMatched) {
            state.setRemainingBlocks(m.blockCount() - shown, m.blockCount());
        } else {
            state.setRemainingBlocks(-1, m.blockCount());
        }
    }
}
