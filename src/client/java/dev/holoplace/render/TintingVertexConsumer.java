package dev.holoplace.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

/**
 * Wraps a {@link VertexConsumer} and multiplies a fixed alpha into every vertex colour, so geometry
 * written straight to a buffer (e.g. a painting's picture, submitted via {@code submitCustomGeometry})
 * fades with the ghost opacity like the rest of the ghost.
 */
final class TintingVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alpha;

    TintingVertexConsumer(VertexConsumer delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, Math.round(a * alpha));
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        delegate.setColor(ARGB.multiplyAlpha(color, alpha));
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
