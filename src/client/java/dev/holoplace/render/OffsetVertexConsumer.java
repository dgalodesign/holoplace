package dev.holoplace.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Wraps a {@link VertexConsumer} and adds a fixed offset to every position. Needed because
 * {@code FluidRenderer} emits vertices in section-local space ({@code pos & 15}); this shifts them
 * back to the camera-relative space the ghost buffer expects.
 */
final class OffsetVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private float dx;
    private float dy;
    private float dz;

    OffsetVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    void setOffset(double x, double y, double z) {
        this.dx = (float) x;
        this.dy = (float) y;
        this.dz = (float) z;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x + dx, y + dy, z + dz);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        delegate.setColor(color);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
