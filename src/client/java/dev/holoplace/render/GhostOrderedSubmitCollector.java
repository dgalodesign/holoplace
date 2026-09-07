package dev.holoplace.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
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
 * Wraps an {@link OrderedSubmitNodeCollector} and multiplies the ghost's opacity into every colour a
 * renderer submits, so chests / signs / banners / entities fade with the opacity slider like the rest
 * of the ghost. Opaque render types are swapped for a translucent one on the same texture so the tint
 * can actually blend; geometry that arrives as a FRAPI mesh or raw item quads is rebuilt with faded
 * colours. {@link GhostSubmitCollector} adds the {@code order(int)} entry point on top of this.
 */
class GhostOrderedSubmitCollector implements OrderedSubmitNodeCollector {

    final OrderedSubmitNodeCollector delegate;
    final float alpha;

    GhostOrderedSubmitCollector(OrderedSubmitNodeCollector delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    final int tint(int color) {
        return ARGB.multiplyAlpha(color == -1 ? 0xFFFFFFFF : color, alpha);
    }

    /** Swap an opaque render type for a translucent one on the same texture, so the tint can blend. */
    final RenderType blendable(RenderType renderType) {
        if (alpha >= 0.995f || renderType.hasBlending()) {
            return renderType;
        }
        Identifier texture = RenderTypeTextures.of(renderType);
        return texture == null ? renderType : RenderTypes.entityTranslucent(texture);
    }

    private int[] tint(int[] colors) {
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            out[i] = tint(colors[i]);
        }
        return out;
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                int lightCoords, int overlayCoords, int tintedColor,
                                @Nullable TextureAtlasSprite sprite, int outlineColor,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        delegate.submitModel(model, state, poseStack, blendable(renderType), lightCoords, overlayCoords,
                tint(tintedColor), sprite, outlineColor, crumblingOverlay);
    }

    @Override
    public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType,
                                int lightCoords, int overlayCoords, @Nullable TextureAtlasSprite sprite,
                                boolean sheeted, boolean hasFoil, int tintedColor,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        delegate.submitModelPart(modelPart, poseStack, blendable(renderType), lightCoords, overlayCoords,
                sprite, sheeted, hasFoil, tint(tintedColor), crumblingOverlay, outlineColor);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
                                 int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        // Block-model render types (e.g. an item frame's frame model) aren't safe to swap for
        // entityTranslucent — the vertex format / shader differ — so tint only, leave the type alone.
        delegate.submitBlockModel(poseStack, renderType, parts, tint(tintLayers),
                lightCoords, overlayCoords, outlineColor);
    }

    /**
     * Fabric-renderer-api's extended overload: when a block model bakes into a FRAPI {@code Mesh}
     * (the item frame's frame model does), the vanilla {@code parts} list is empty and the geometry
     * lives in {@code mesh}. The default forwarding impl drops the mesh, so a wrapping collector that
     * doesn't override this makes the model vanish — forward it to the real collector, which stores it
     * as an {@code ExtendedBlockModelSubmit} that {@code BlockFeatureRenderer} then draws. Fade it like
     * the rest of the ghost: multiply the opacity into the mesh's vertex colours, swap the (opaque)
     * render type for a blending one and route it through the translucent feature pass.
     */
    @Override
    public void submitBlockModel(PoseStack poseStack,
                                 java.util.function.Function<net.minecraft.client.renderer.chunk.ChunkSectionLayer,
                                         RenderType> renderTypeByLayer,
                                 boolean hasBlending, List<BlockStateModelPart> parts,
                                 net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh mesh,
                                 int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        if (alpha >= 0.995f) {
            delegate.submitBlockModel(poseStack, renderTypeByLayer, hasBlending, parts, mesh,
                    tintLayers, lightCoords, overlayCoords, outlineColor);
            return;
        }
        var fadedMesh = fade(mesh);
        delegate.submitBlockModel(poseStack, renderTypeByLayer.andThen(this::blendable),
                hasBlending || fadedMesh != mesh, parts, fadedMesh,
                tint(tintLayers), lightCoords, overlayCoords, outlineColor);
    }

    /** Multiply the ghost opacity into a FRAPI mesh's vertex colours. Returns the input unchanged if
     *  it is empty or the active renderer is unavailable. */
    @SuppressWarnings("unchecked")
    private <T extends net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView> T fade(T mesh) {
        if (mesh == null || mesh.size() == 0) {
            return mesh;
        }
        try {
            var mutable = net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();
            var emitter = mutable.emitter();
            int mul = (Math.round(alpha * 255f) << 24) | 0x00FFFFFF;
            mesh.forEach(quad -> {
                emitter.copyFrom(quad);
                emitter.multiplyColor(mul);
                emitter.emit();
            });
            return (T) mutable.immutableCopy();
        } catch (RuntimeException | LinkageError e) {
            return mesh;
        }
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
        // Custom geometry writes straight to a buffer (a painting's picture, a beacon beam…). Fade it
        // by swapping the render type for a blending one and wrapping the buffer so every vertex
        // colour picks up the ghost opacity.
        if (alpha >= 0.995f) {
            delegate.submitCustomGeometry(poseStack, renderType, renderer);
            return;
        }
        delegate.submitCustomGeometry(poseStack, blendable(renderType),
                (pose, buffer) -> renderer.render(pose, new TintingVertexConsumer(buffer, alpha)));
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
                           int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        if (alpha >= 0.995f) {
            delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
                    tintLayers, quads, foilType);
            return;
        }
        // An item's quads carry white vertex colours and mostly no tint index, so ItemFeatureRenderer
        // draws them at full opacity regardless of tintLayers. Re-point every quad at tint slot 0 and
        // a blending render type, and hand it a faded colour in that slot.
        delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
                fadedItemTints(tintLayers), fadeItemQuads(quads, tintLayers.length), foilType);
    }

    /**
     * Fabric-renderer-api's extended item overload (geometry in a {@code MeshView}). The default impl
     * drops the mesh, so a wrapping collector that doesn't override this loses mesh-baked item models
     * (a framed item). Forward it, fading the mesh's vertex colours.
     */
    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
                           int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                           net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView mesh,
                           ItemStackRenderState.FoilType foilType) {
        if (alpha >= 0.995f) {
            delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
                    tintLayers, quads, mesh, foilType);
            return;
        }
        delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
                fadedItemTints(tintLayers), fadeItemQuads(quads, tintLayers.length), fade(mesh), foilType);
    }

    /** A tint-layer array where slot 0 (and any slot without a real tint) is the ghost's faded white. */
    private int[] fadedItemTints(int[] tintLayers) {
        int[] out = new int[Math.max(1, tintLayers.length)];
        java.util.Arrays.fill(out, tint(0xFFFFFFFF));
        for (int i = 0; i < tintLayers.length; i++) {
            out[i] = tint(tintLayers[i]);
        }
        return out;
    }

    private List<BakedQuad> fadeItemQuads(List<BakedQuad> quads, int tintLayerCount) {
        List<BakedQuad> out = new java.util.ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            BakedQuad.MaterialInfo m = q.materialInfo();
            int tintIndex = m.tintIndex() >= 0 && m.tintIndex() < tintLayerCount ? m.tintIndex() : 0;
            BakedQuad.MaterialInfo faded = new BakedQuad.MaterialInfo(m.sprite(), m.layer(),
                    blendable(m.itemRenderType()), tintIndex, m.shade(), m.lightEmission());
            out.add(new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                    q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), q.direction(), faded));
        }
        return out;
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
    public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
        delegate.submitParticleGroup(particleGroupRenderer);
    }
}
