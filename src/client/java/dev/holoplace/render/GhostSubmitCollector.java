package dev.holoplace.render;

import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * The ghost's opacity-fading wrapper around the level's {@link SubmitNodeCollector}. All the tinting
 * lives in {@link GhostOrderedSubmitCollector}; this subclass only adds {@link #order(int)}, which
 * render layers use ({@code RenderLayer.renderColoredCutoutModel} → {@code collector.order(n)}) — a
 * villager's clothes, armour trims, etc. Without wrapping the ordered collector too, those submits
 * would skip the fade entirely.
 */
final class GhostSubmitCollector extends GhostOrderedSubmitCollector implements SubmitNodeCollector {

    GhostSubmitCollector(SubmitNodeCollector delegate, float alpha) {
        super(delegate, alpha);
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return new GhostOrderedSubmitCollector(((SubmitNodeCollector) delegate).order(order), alpha);
    }
}
