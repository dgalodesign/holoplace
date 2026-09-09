package dev.holoplace.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Ghost render types. Both the normal and see-through ghost use vanilla pipelines
 * ({@code translucentMovingBlock}, {@code lines}) so shader mods recognise them — a custom
 * depth-always pipeline gets dropped from Iris's program list ("Missing program … in override
 * list") and then its draws are silently skipped in a packaged build.
 *
 * <p>See-through / x-ray is instead done at the render-pass level in {@link GhostGpuMesh#draw}: the
 * pass is created without a depth attachment, so the depth test can't reject fragments behind the
 * world. (The line markers and the bake outline can't do this — they go through the shared buffer
 * source — so they stay depth-tested even in see-through mode.)
 */
public final class GhostPipelines {

    private GhostPipelines() {
    }

    /** No-op — kept so {@code HoloPlaceClient} has a stable init hook; nothing to register now. */
    public static void bootstrap() {
    }

    /** The ghost's textured translucent type. {@code seeThrough} is handled in the render pass, not
     *  here, so the same vanilla type is returned either way. */
    public static RenderType forGhost(boolean seeThrough) {
        return RenderTypes.translucentMovingBlock();
    }

    public static RenderType linesForGhost(boolean seeThrough) {
        return RenderTypes.lines();
    }
}
