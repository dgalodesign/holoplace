package dev.holoplace.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Wraps the level's {@link SubmitNodeCollector} and multiplies the ghost's opacity into every colour
 * a block-entity renderer submits, so chests / signs / banners fade with the opacity slider like the
 * rest of the ghost. Render types that don't blend still ignore it — that's a hard limit of the
 * submit pipeline without per-type custom render pipelines.
 */
final class GhostSubmitCollector implements SubmitNodeCollector {

    private final SubmitNodeCollector delegate;
    private final float alpha;

    GhostSubmitCollector(SubmitNodeCollector delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    private int tint(int color) {
        return ARGB.multiplyAlpha(color == -1 ? 0xFFFFFFFF : color, alpha);
    }

    private int[] tint(int[] colors) {
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            out[i] = tint(colors[i]);
        }
        return out;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return delegate.order(order);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                int lightCoords, int overlayCoords, int tintedColor,
                                @Nullable TextureAtlasSprite sprite, int outlineColor,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        delegate.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords,
                tint(tintedColor), sprite, outlineColor, crumblingOverlay);
    }

    @Override
    public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType,
                                int lightCoords, int overlayCoords, @Nullable TextureAtlasSprite sprite,
                                boolean sheeted, boolean hasFoil, int tintedColor,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        delegate.submitModelPart(modelPart, poseStack, renderType, lightCoords, overlayCoords, sprite,
                sheeted, hasFoil, tint(tintedColor), crumblingOverlay, outlineColor);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
                                 int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        delegate.submitBlockModel(poseStack, renderType, parts, tint(tintLayers),
                lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string,
                           boolean dropShadow, Font.DisplayMode displayMode, int lightCoords, int color,
                           int backgroundColor, int outlineColor) {
        delegate.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords,
                tint(color), ARGB.multiplyAlpha(backgroundColor, alpha), outlineColor);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
        // Custom geometry (beacon beam, conduit, end portal…) — left opaque; rare for a ghost.
        delegate.submitCustomGeometry(poseStack, renderType, renderer);
    }

    // --- pure delegation -------------------------------------------------------

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        delegate.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset,
                              Component name, boolean seeThrough, int lightCoords,
                              double distanceToCameraSq, CameraRenderState camera) {
        delegate.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords,
                distanceToCameraSq, camera);
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        delegate.submitFlame(poseStack, renderState, rotation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        delegate.submitLeash(poseStack, leashState);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
        delegate.submitMovingBlock(poseStack, movingBlockRenderState);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
        delegate.submitBreakingBlockModel(poseStack, model, seed, progress);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
                           int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
                tint(tintLayers), quads, foilType);
    }

    @Override
    public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
        delegate.submitParticleGroup(particleGroupRenderer);
    }
}
