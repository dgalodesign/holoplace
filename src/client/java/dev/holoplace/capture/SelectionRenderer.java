package dev.holoplace.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Wire box for the capture selection — reuses the same line pipeline as the ghost's block markers. */
public final class SelectionRenderer {

    private static final int BOX_COLOR = 0xFF33CCFF;
    private static final int CORNER_COLOR = 0xFFFFDD33;

    private SelectionRenderer() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(SelectionRenderer::render);
    }

    private static void render(LevelRenderContext ctx) {
        SelectionState sel = CaptureController.get().selection();
        BlockPos c1 = sel.corner1();
        BlockPos c2 = sel.corner2();
        BlockPos lo = sel.min();
        BlockPos single = c1 != null ? c1 : c2;
        if (lo == null && single == null) {
            return;
        }

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        VertexConsumer lines = ctx.bufferSource().getBuffer(RenderTypes.lines());
        PoseStack ps = new PoseStack();

        if (lo != null) {
            Vec3i s = sel.size();
            ShapeRenderer.renderShape(ps, lines,
                    Shapes.box(0.0, 0.0, 0.0, s.getX(), s.getY(), s.getZ()),
                    lo.getX() - cam.x, lo.getY() - cam.y, lo.getZ() - cam.z,
                    BOX_COLOR, 2.0f);
        } else {
            // Only one corner set so far — mark it so the next click has context.
            ShapeRenderer.renderShape(ps, lines, Shapes.block(),
                    single.getX() - cam.x, single.getY() - cam.y, single.getZ() - cam.z,
                    CORNER_COLOR, 2.5f);
        }

        ctx.bufferSource().endBatch(RenderTypes.lines());
    }
}
