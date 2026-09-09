package dev.holoplace.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The ghost's block quads baked into a persistent {@link GpuBuffer} and redrawn each frame with one
 * indexed draw, instead of walking the baked {@link GhostMesh} and calling
 * {@code putBlockBakedQuad} per quad every frame ({@link GhostRenderer}'s original path, kept as a
 * fallback). Vertices are in footprint-local space, so moving the ghost only shifts the model-view
 * translation — no rebuild. Rebuilt only when {@link #key} changes (mesh, opacity, build-assist
 * culling, layer slice, coarse anchor for biome tint).
 *
 * <p>Translucent quads are not depth-sorted (a shared sequential index buffer is used); at the
 * ghost's typical opacity the ordering artifacts on overlapping faces are minor, and it saves a
 * per-frame sort of every quad.
 */
final class GhostGpuMesh implements AutoCloseable {

    private static final int FULL_BRIGHT = 0x00F000F0;

    final long key;
    private @Nullable GpuBuffer vertexBuffer;
    private int indexCount;
    private final RenderSystem.AutoStorageIndexBuffer sequentialIndices =
            RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

    private GhostGpuMesh(long key) {
        this.key = key;
    }

    /**
     * Bake the visible ghost quads into a GPU buffer. {@code needs} / {@code wrong} come from the
     * build-assist scan (both may be {@code null}); {@code tint} is the per-block biome colour
     * ({@link GhostRenderer#NO_TINT} for untinted); {@code alphaShifted} is the opacity already in
     * the high byte; {@code shade} applies vanilla per-face darkening.
     */
    static GhostGpuMesh build(long key, GhostMesh m, boolean @Nullable [] needs, boolean @Nullable [] wrong,
                              boolean hideWrongToo, boolean layerClip, GhostState state,
                              int[] tint, int alphaShifted, boolean shade) {
        GhostGpuMesh out = new GhostGpuMesh(key);
        int blocks = m.blockCount();
        ByteBufferBuilder bytes = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE);
        try {
            BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            QuadInstance quad = new QuadInstance();
            quad.setLightCoords(FULL_BRIGHT);
            boolean wrongUsable = hideWrongToo && wrong != null && wrong.length == blocks;
            boolean anyVertex = false;

            for (int i = 0; i < blocks; i++) {
                if (needs != null && !needs[i]) {
                    continue;
                }
                if (wrongUsable && wrong[i]) {
                    continue;
                }
                if (layerClip && !state.layerVisible(m.blockY(i))) {
                    continue;
                }
                int baseRgb = tint[i] == GhostRenderer.NO_TINT ? 0x00FFFFFF : (tint[i] & 0x00FFFFFF);
                int end = m.quadStart(i + 1);
                for (int q = m.quadStart(i); q < end; q++) {
                    GhostMesh.Quad gq = m.quad(q);
                    int rgb = gq.tinted() ? baseRgb : 0x00FFFFFF;
                    quad.setColor(shade ? alphaShifted | GhostRenderer.shadeRgb(rgb, gq.quad())
                            : alphaShifted | rgb);
                    bb.putBlockBakedQuad(gq.x(), gq.y(), gq.z(), gq.quad(), quad);
                    anyVertex = true;
                }
            }

            if (!anyVertex) {
                return out; // nothing visible — a valid empty mesh
            }
            try (MeshData mesh = bb.buildOrThrow()) {
                out.indexCount = mesh.drawState().indexCount();
                out.vertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "HoloPlace ghost vertices",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        mesh.vertexBuffer());
            }
        } finally {
            bytes.close();
        }
        return out;
    }

    boolean isEmpty() {
        return vertexBuffer == null || indexCount == 0;
    }

    /** One indexed draw of the whole ghost, offset so footprint-local {@code (0,0,0)} sits at
     *  {@code anchor}. The camera-relative offset goes in the {@code DynamicTransforms} model-offset
     *  vector (vanilla's {@code WorldBorderRenderer} does the same). */
    void draw(RenderType type, Vec3 cam, BlockPos anchor) {
        if (isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                new Vector3f((float) (anchor.getX() - cam.x),
                        (float) (anchor.getY() - cam.y),
                        (float) (anchor.getZ() - cam.z)),
                new Matrix4f());

        var renderTarget = type.outputTarget().getRenderTarget();
        GpuTextureView color = renderTarget.getColorTextureView();
        GpuTextureView depth = renderTarget.useDepth ? renderTarget.getDepthTextureView() : null;

        var atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        GpuTextureView lightmap = mc.gameRenderer.lightmap();
        var lightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        GpuBuffer indices = sequentialIndices.getBuffer(indexCount);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "HoloPlace ghost", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(type.pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
            pass.bindTexture("Sampler2", lightmap, lightmapSampler);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indices, sequentialIndices.type());
            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    @Override
    public void close() {
        if (vertexBuffer != null) {
            try {
                vertexBuffer.close();
            } catch (RuntimeException e) {
                HoloPlaceClient.LOGGER.debug("Ghost GPU buffer close failed", e);
            }
            vertexBuffer = null;
        }
        indexCount = 0;
    }
}
