package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Draws the current {@link GhostState} schematic as a textured, translucent ghost during
 * {@code AFTER_TRANSLUCENT_TERRAIN}. Geometry is baked once by {@link GhostMesh} and only rebuilt
 * when the schematic / rotation / mirror changes — anchor drags and opacity changes are free.
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

    /** Force a rebuild on the next frame (e.g. after a schematic reload). */
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
        if (mc.level == null) {
            return;
        }

        if (mesh == null || !mesh.matches(schematic, transform)) {
            long start = System.nanoTime();
            mesh = GhostMesh.build(schematic, transform);
            HoloPlaceClient.LOGGER.debug("Rebuilt ghost mesh: {} quads in {} ms",
                    mesh.quads().size(), (System.nanoTime() - start) / 1_000_000);
        }

        var cam = mc.gameRenderer.getMainCamera().position();
        BlockPos anchor = state.anchor();
        float ox = (float) (anchor.getX() - cam.x);
        float oy = (float) (anchor.getY() - cam.y);
        float oz = (float) (anchor.getZ() - cam.z);

        RenderType renderType = GhostPipelines.forGhost(state.seeThrough());
        VertexConsumer buffer = ctx.bufferSource().getBuffer(renderType);
        QUAD.setColor((state.opacityAlpha() << 24) | 0x00FFFFFF);
        QUAD.setLightCoords(FULL_BRIGHT);

        List<GhostMesh.Quad> quads = mesh.quads();
        for (int i = 0, n = quads.size(); i < n; i++) {
            GhostMesh.Quad q = quads.get(i);
            buffer.putBlockBakedQuad(q.x() + ox, q.y() + oy, q.z() + oz, q.quad(), QUAD);
        }

        ctx.bufferSource().endBatch(renderType);
    }
}
