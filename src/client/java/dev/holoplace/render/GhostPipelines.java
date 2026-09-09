package dev.holoplace.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.holoplace.HoloPlaceClient;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Render types for the ghost. The "see-through" variant mirrors vanilla's {@code translucentMovingBlock}
 * setup exactly — same sampler, crumbling, outline, and the item-entity output target so it composites
 * the same way — differing only in the pipeline's depth state (test always passes, no depth write), so
 * the ghost paints over the world even where the player's own blocks would occlude it. Matching the
 * output target matters: rendering see-through straight to the main target mid-pass let later passes
 * (weather, particles, the hand) draw over the ghost.
 *
 * <p>{@link #bootstrap()} must run during client init so the pipeline is registered before the shader
 * manager's first pre-compile pass.
 */
public final class GhostPipelines {

    public static final RenderPipeline SEE_THROUGH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
                    .withLocation(HoloPlaceClient.id("pipeline/see_through_block"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.01F)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build());

    public static final RenderType SEE_THROUGH = RenderType.create(
            "holoplace/see_through_block",
            RenderSetup.builder(SEE_THROUGH_PIPELINE)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
                            () -> RenderSystem.getSamplerCache().getSampler(
                                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                                    FilterMode.LINEAR, FilterMode.NEAREST, true))
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .sortOnUpload()
                    .createRenderSetup());

    /** Wire-line variant with the depth test forced to pass, so build-assist's error / extra-block
     *  markers stay visible through the walls the player has already built while see-through is on. */
    public static final RenderPipeline SEE_THROUGH_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(HoloPlaceClient.id("pipeline/see_through_lines"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build());

    public static final RenderType SEE_THROUGH_LINES = RenderType.create(
            "holoplace/see_through_lines",
            RenderSetup.builder(SEE_THROUGH_LINES_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());

    private GhostPipelines() {
    }

    /** Forces class-load (and pipeline registration) at a known point during client init. */
    public static void bootstrap() {
    }

    public static RenderType forGhost(boolean seeThrough) {
        return seeThrough ? SEE_THROUGH : RenderTypes.translucentMovingBlock();
    }

    public static RenderType linesForGhost(boolean seeThrough) {
        return seeThrough ? SEE_THROUGH_LINES : RenderTypes.lines();
    }
}
