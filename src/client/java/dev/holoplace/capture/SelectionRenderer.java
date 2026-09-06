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
import org.jspecify.annotations.Nullable;

/**
 * Draws the capture selection: a wire box with translucent shaded faces for the bounds, plus a
 * distinctly coloured cube on each corner (green = corner 1 / left-click, orange = corner 2 /
 * right-click). Reuses the same line pipeline as the ghost's block markers.
 */
public final class SelectionRenderer {

    private static final int BOX_LINE_COLOR = 0xFFBFE3FF;
    private static final int FACE_COLOR = 0x2233CCFF;
    private static final int CORNER1_COLOR = 0xFF3BE05A;
    private static final int CORNER2_COLOR = 0xFFFF9A2E;

    private SelectionRenderer() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(SelectionRenderer::render);
    }

    private static void render(LevelRenderContext ctx) {
        SelectionState sel = CaptureController.get().selection();
        BlockPos c1 = sel.corner1();
        BlockPos c2 = sel.corner2();
        if (c1 == null && c2 == null) {
            return;
        }

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        PoseStack ps = new PoseStack();

        if (sel.isComplete()) {
            BlockPos lo = sel.min();
            Vec3i s = sel.size();
            double lx = lo.getX() - cam.x;
            double ly = lo.getY() - cam.y;
            double lz = lo.getZ() - cam.z;
            double hx = lx + s.getX();
            double hy = ly + s.getY();
            double hz = lz + s.getZ();

            VertexConsumer faces = ctx.bufferSource().getBuffer(RenderTypes.debugQuads());
            fillBox(faces, lx, ly, lz, hx, hy, hz);
            ctx.bufferSource().endBatch(RenderTypes.debugQuads());

            VertexConsumer lines = ctx.bufferSource().getBuffer(RenderTypes.lines());
            ShapeRenderer.renderShape(ps, lines,
                    Shapes.box(0.0, 0.0, 0.0, s.getX(), s.getY(), s.getZ()),
                    lx, ly, lz, BOX_LINE_COLOR, 2.0f);
            ctx.bufferSource().endBatch(RenderTypes.lines());
        }

        // Corner cubes, always shown once set, so corner 1 vs corner 2 stays readable.
        VertexConsumer cornerLines = ctx.bufferSource().getBuffer(RenderTypes.lines());
        cornerCube(ps, cornerLines, c1, cam, CORNER1_COLOR);
        cornerCube(ps, cornerLines, c2, cam, CORNER2_COLOR);
        ctx.bufferSource().endBatch(RenderTypes.lines());
    }

    private static void cornerCube(PoseStack ps, VertexConsumer lines, @Nullable BlockPos c, Vec3 cam, int color) {
        if (c == null) {
            return;
        }
        ShapeRenderer.renderShape(ps, lines, Shapes.block(),
                c.getX() - cam.x, c.getY() - cam.y, c.getZ() - cam.z, color, 3.0f);
    }

    /** Six translucent quads over the box faces. {@code debugQuads()} is POSITION_COLOR / QUADS, no cull. */
    private static void fillBox(VertexConsumer vc, double lx, double ly, double lz,
                                double hx, double hy, double hz) {
        face(vc, lx, ly, lz, hx, ly, lz, hx, ly, hz, lx, ly, hz); // bottom
        face(vc, lx, hy, lz, lx, hy, hz, hx, hy, hz, hx, hy, lz); // top
        face(vc, lx, ly, lz, lx, hy, lz, hx, hy, lz, hx, ly, lz); // north
        face(vc, lx, ly, hz, hx, ly, hz, hx, hy, hz, lx, hy, hz); // south
        face(vc, lx, ly, lz, lx, ly, hz, lx, hy, hz, lx, hy, lz); // west
        face(vc, hx, ly, lz, hx, hy, lz, hx, hy, hz, hx, ly, hz); // east
    }

    private static void face(VertexConsumer vc,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        vc.addVertex((float) x1, (float) y1, (float) z1).setColor(FACE_COLOR);
        vc.addVertex((float) x2, (float) y2, (float) z2).setColor(FACE_COLOR);
        vc.addVertex((float) x3, (float) y3, (float) z3).setColor(FACE_COLOR);
        vc.addVertex((float) x4, (float) y4, (float) z4).setColor(FACE_COLOR);
    }
}
