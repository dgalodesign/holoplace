package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

/**
 * Draws the current {@link GhostState} schematic as a textured, translucent ghost during
 * {@code AFTER_TRANSLUCENT_TERRAIN}. Geometry is baked once off-thread by {@link GhostMesh} /
 * {@link GhostMeshBaker}, then uploaded to a persistent GPU buffer ({@link GhostGpuMesh}) and
 * redrawn each frame with one indexed call — rebuilt only when its content changes (opacity,
 * build-assist culling, layer slice). {@link #renderBlocksImmediate} is the fallback if the GPU
 * path throws. Biome tint (grass/leaves/water) is resolved against the real world and cached.
 */
public final class GhostRenderer {

    private static final int FULL_BRIGHT = 0x00F000F0;
    static final int NO_TINT = -1;
    private static final int MAX_QUADS = 4_000_000;
    private static final long SCAN_INTERVAL_NANOS = 250_000_000L;
    /** When this share of the schematic's blocks can't be placed because a non-air world block is in
     *  the way, treat it as buried: skip the per-cell markers (they'd be a wall of wireframe and
     *  useless — the answer is "clear the area"), show the footprint outline + the ghost model. */
    private static final int BURIED_PERCENT = 60;
    private static final int WRONG_COLOR = 0xC0FF3030;
    private static final int EXTRA_COLOR = 0xC0FF9933;
    /** {@code matchedBlocks} sentinel: the mesh is still baking off-thread (HUD shows "preparing"). */
    static final int MESH_BAKING = -3;
    private static final int OUTLINE_COLOR = 0xC05EE7FF;
    private static final int[] EMPTY_INT = new int[0];
    private static final net.minecraft.core.Direction[] FACES = net.minecraft.core.Direction.values();
    private static final QuadInstance QUAD = new QuadInstance();

    private static @Nullable GhostMesh mesh;
    private static @Nullable GhostGpuMesh gpuMesh;
    /** Set once if the persistent-buffer path throws — from then on the per-frame upload is used. */
    private static boolean gpuUnavailable;
    /** Bumped every time the build-assist scan rewrites its flags, so the GPU mesh rebuilds. */
    private static int scanVersion;
    private static int @Nullable [] blockTint;
    private static long tintKey;

    private static boolean @Nullable [] needsPlacing;
    private static boolean @Nullable [] wrongBlock;
    /** Subset of {@link #wrongBlock} that has an air world neighbour — the ones you can actually see
     *  and reach. Only these get a marker; a wrong block five layers into a wall is just cost. */
    private static boolean @Nullable [] wrongBlockVisible;
    private static boolean @Nullable [] wrongBE;
    private static boolean @Nullable [] wrongFluid;
    private static int @Nullable [] extraX;
    private static int @Nullable [] extraY;
    private static int @Nullable [] extraZ;
    private static long scanKey;
    private static long lastScanNanos;
    private static int placedCount;
    private static boolean warnedTooLarge;
    private static @Nullable GhostMesh loggedBeMesh;
    private static @Nullable GhostMesh loggedEntityMesh;

    private GhostRenderer() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(GhostRenderer::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::submitBlockEntities);
        LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::submitEntities);
    }

    public static void invalidate() {
        mesh = null;
        GhostMeshBaker.invalidate();
        closeGpuMesh();
        blockTint = null;
        needsPlacing = null;
        wrongBlock = null;
        wrongBlockVisible = null;
        wrongBE = null;
        wrongFluid = null;
        extraX = null;
        extraY = null;
        extraZ = null;
        warnedTooLarge = false;
    }

    private static void render(LevelRenderContext ctx) {
        GhostState state = GhostState.get();
        Schematic schematic = state.schematic();
        PlacementTransform transform = state.transform();
        if (!state.isVisible() || schematic == null || transform == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        GhostMesh baked = GhostMeshBaker.poll(schematic, transform);
        if (baked == null) {
            // Still baking (or a bake just failed) — show the footprint so positioning still works.
            renderFootprintOutline(transform, state.anchor(), ctx, mc);
            state.setRemainingBlocks(MESH_BAKING, 0);
            return;
        }
        if (baked != mesh) {
            mesh = baked;
            blockTint = null;
            needsPlacing = null;
            wrongBlock = null;
            wrongBlockVisible = null;
            wrongBE = null;
            wrongFluid = null;
            extraX = null;
            warnedTooLarge = false;
        }
        GhostMesh m = mesh;

        if (m.totalQuads() > MAX_QUADS) {
            if (!warnedTooLarge) {
                HoloPlaceClient.LOGGER.warn("Schematic '{}' is too large to render ({} quads)",
                        state.sourceName(), m.totalQuads());
                warnedTooLarge = true;
            }
            state.setRemainingBlocks(-2, m.blockCount());
            return;
        }

        BlockPos anchor = state.anchor();
        int[] tint = tintFor(m, anchor, level, mc);
        boolean hideMatched = state.hideMatched();
        boolean[] needs = hideMatched ? placementScan(m, transform, anchor, level, state) : null;
        // Build-assist always hides the ghost model on a wrongly-placed cell — a wrong block sitting
        // where a different one belongs, with the ghost drawn on top, is just noise. The red marker
        // (and the "should be X" crosshair tooltip) says what goes there. Only for *visible* wrong
        // cells though — a wrong cell buried in terrain keeps its faint ghost (no marker, so nothing
        // else shows what belongs there) and the buried case keeps the whole model.
        boolean hideWrongToo = hideMatched && !buried;
        boolean[] wrong = wrongBlockVisible;
        boolean layerClip = state.layerClip();

        var cam = mc.gameRenderer.getMainCamera().position();
        int alpha = state.opacityAlpha() << 24;
        boolean shade = state.shade();
        RenderType renderType = GhostPipelines.forGhost(state.seeThrough());

        // Fast path: the ghost's block quads live in a persistent GPU buffer, redrawn with one
        // indexed call. Rebuilt only when its content key changes (mesh, opacity, build-assist
        // culling, layer slice, coarse anchor). Falls back to the per-frame upload if it ever throws.
        boolean drewBlocks = false;
        if (!gpuUnavailable) {
            try {
                long gpuKey = System.identityHashCode(m);
                gpuKey = gpuKey * 1099511628211L + state.opacityAlpha();
                gpuKey = gpuKey * 1099511628211L + (layerClip ? state.layerMin() : -1);
                gpuKey = gpuKey * 1099511628211L + (layerClip ? state.layerMax() : -1);
                gpuKey = gpuKey * 1099511628211L + (hideMatched ? scanVersion : -1);
                gpuKey = gpuKey * 1099511628211L + (anchor.getX() >> 3);
                gpuKey = gpuKey * 1099511628211L + (anchor.getY() >> 3);
                gpuKey = gpuKey * 1099511628211L + (anchor.getZ() >> 3);
                if (gpuMesh == null || gpuMesh.key != gpuKey) {
                    GhostGpuMesh old = gpuMesh;
                    gpuMesh = GhostGpuMesh.build(gpuKey, m, needs, wrong, hideWrongToo, layerClip,
                            state, tint, alpha, shade);
                    if (old != null) {
                        old.close();
                    }
                }
                gpuMesh.draw(renderType, state.seeThrough(), cam, anchor);
                drewBlocks = true;
            } catch (Throwable t) {
                gpuUnavailable = true;
                HoloPlaceClient.LOGGER.error(
                        "Ghost GPU render failed — falling back to per-frame vertex upload", t);
                closeGpuMesh();
            }
        }

        VertexConsumer buffer = ctx.bufferSource().getBuffer(renderType);
        if (!drewBlocks) {
            renderBlocksImmediate(m, needs, wrong, hideWrongToo, layerClip, state, tint, alpha, shade,
                    (float) (anchor.getX() - cam.x), (float) (anchor.getY() - cam.y),
                    (float) (anchor.getZ() - cam.z), buffer);
        }

        if (m.fluidCount() > 0) {
            renderFluids(m, anchor, cam, level, mc, buffer, hideMatched, hideWrongToo);
        }
        ctx.bufferSource().endBatch(renderType);

        if (m.blockEntityCount() > 0) {
            renderBlockEntityMarkers(m, anchor, level, cam, ctx, hideMatched, hideWrongToo);
        }
        if (hideMatched && buried) {
            // Wall-of-wireframe territory — one outline of what to clear reads better than 20k cubes.
            renderFootprintOutline(transform, anchor, ctx, mc);
            wrongMarkers = 0;
            extraMarkers = 0;
        } else if (hideMatched) {
            wrongMarkers = renderMarkerSet(wrongBlockVisible, m::blockX, m::blockY, m::blockZ, m.blockCount(),
                    anchor, cam, ctx, state, WRONG_COLOR)
                    + renderMarkerSet(wrongBE, m::beX, m::beY, m::beZ, m.blockEntityCount(),
                            anchor, cam, ctx, state, WRONG_COLOR)
                    + renderMarkerSet(wrongFluid, m::fluidX, m::fluidY, m::fluidZ, m.fluidCount(),
                            anchor, cam, ctx, state, WRONG_COLOR);
            extraMarkers = renderExtraBlocks(anchor, cam, ctx, state);
        } else {
            wrongMarkers = 0;
            extraMarkers = 0;
        }

        state.setRemainingBlocks(hideMatched ? placedCount : -1, m.blockCount());
    }

    /** Cyan wire box around the placement footprint — shown while the mesh bakes so the schematic's
     *  position and size are still visible and adjustable. */
    private static void renderFootprintOutline(PlacementTransform t, BlockPos anchor,
                                               LevelRenderContext ctx, Minecraft mc) {
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        RenderType type = GhostPipelines.linesForGhost(true); // over walls: it's a transient hint
        VertexConsumer lines = ctx.bufferSource().getBuffer(type);
        ShapeRenderer.renderShape(new PoseStack(), lines,
                Shapes.box(0, 0, 0, t.footprintX(), t.footprintY(), t.footprintZ()),
                anchor.getX() - cam.x, anchor.getY() - cam.y, anchor.getZ() - cam.z,
                OUTLINE_COLOR, 2.5f);
        ctx.bufferSource().endBatch(type);
    }

    private interface IntLookup {
        int get(int index);
    }

    private static final float MARKER_LINE = 2.5f;

    private static int wrongMarkers;
    private static int wrongTotal;
    private static int extraMarkers;
    /** Set by {@link #placementScan}: the schematic is mostly inside solid terrain. */
    private static boolean buried;

    /** Wrong cells with a marker drawn (visible / reachable ones). */
    public static int wrongMarkers() {
        return wrongMarkers;
    }

    /** All wrong cells, including ones buried inside solid blocks with no marker. */
    public static int wrongTotal() {
        return wrongTotal;
    }

    public static int extraMarkers() {
        return extraMarkers;
    }

    public static boolean buried() {
        return buried;
    }

    /** {@code rgb} at the marker opacity (its own control — markers are alerts, not the ghost). */
    private static int markerColor(int rgb, GhostState state) {
        int a = Mth.clamp(Math.round(state.markerOpacity() * 255f), 8, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /** Wire cube for every flagged index, using the given per-index footprint-local coordinates.
     *  Returns how many cells were flagged. The buried case (a wall of these) is handled upstream. */
    private static int renderMarkerSet(boolean @Nullable [] flags, IntLookup x, IntLookup y, IntLookup z,
                                       int count, BlockPos anchor, Vec3 cam, LevelRenderContext ctx,
                                       GhostState state, int color) {
        if (flags == null || flags.length != count) {
            return 0;
        }
        boolean layerClip = state.layerClip();
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (flags[i] && (!layerClip || state.layerVisible(y.get(i)))) {
                n++;
            }
        }
        if (n == 0) {
            return 0;
        }

        int argb = markerColor(color, state);
        RenderType type = GhostPipelines.linesForGhost(state.seeThrough());
        VertexConsumer lines = ctx.bufferSource().getBuffer(type);
        PoseStack ps = new PoseStack();
        for (int i = 0; i < count; i++) {
            if (!flags[i] || (layerClip && !state.layerVisible(y.get(i)))) {
                continue;
            }
            ShapeRenderer.renderShape(ps, lines, Shapes.block(),
                    anchor.getX() + x.get(i) - cam.x,
                    anchor.getY() + y.get(i) - cam.y,
                    anchor.getZ() + z.get(i) - cam.z,
                    argb, MARKER_LINE);
        }
        ctx.bufferSource().endBatch(type);
        return n;
    }

    /** Orange wire cube where the world has a block but the schematic calls for nothing there. */
    private static int renderExtraBlocks(BlockPos anchor, Vec3 cam, LevelRenderContext ctx, GhostState state) {
        int[] xs = extraX;
        int[] ys = extraY;
        int[] zs = extraZ;
        if (xs == null || ys == null || zs == null || xs.length == 0) {
            return 0;
        }
        boolean layerClip = state.layerClip();
        int n = 0;
        for (int i = 0; i < xs.length; i++) {
            if (!layerClip || state.layerVisible(ys[i])) {
                n++;
            }
        }
        if (n == 0) {
            return 0;
        }

        int argb = markerColor(EXTRA_COLOR, state);
        RenderType type = GhostPipelines.linesForGhost(state.seeThrough());
        VertexConsumer lines = ctx.bufferSource().getBuffer(type);
        PoseStack ps = new PoseStack();
        for (int i = 0; i < xs.length; i++) {
            if (layerClip && !state.layerVisible(ys[i])) {
                continue;
            }
            ShapeRenderer.renderShape(ps, lines, Shapes.block(),
                    anchor.getX() + xs[i] - cam.x, anchor.getY() + ys[i] - cam.y,
                    anchor.getZ() + zs[i] - cam.z, argb, MARKER_LINE);
        }
        ctx.bufferSource().endBatch(type);
        return n;
    }

    /**
     * Per-block "still needs placing" flags for build-assist, rescanned against the world at most
     * every {@value #SCAN_INTERVAL_NANOS} ns (or immediately when the mesh / anchor / match mode
     * changes) instead of every frame. Also refreshes the "extra block" list (world has something
     * where the schematic calls for nothing) over the footprint's bounding volume.
     */
    private static boolean[] placementScan(GhostMesh m, PlacementTransform transform, BlockPos anchor,
                                           ClientLevel level, GhostState state) {
        long key = ((long) System.identityHashCode(m) << 20) ^ anchor.asLong()
                ^ (state.matchBlockOnly() ? 0x5555_5555L : 0L);
        long now = System.nanoTime();
        boolean[] cached = needsPlacing;
        if (cached != null && cached.length == m.blockCount() && key == scanKey
                && now - lastScanNanos < SCAN_INTERVAL_NANOS) {
            return cached;
        }
        boolean[] out = new boolean[m.blockCount()];
        boolean[] wrong = new boolean[m.blockCount()];
        boolean[] wrongVis = new boolean[m.blockCount()];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos npos = new BlockPos.MutableBlockPos();
        int placed = 0;
        int wrongCount = 0;
        for (int i = 0; i < out.length; i++) {
            pos.set(anchor.getX() + m.blockX(i), anchor.getY() + m.blockY(i), anchor.getZ() + m.blockZ(i));
            BlockState world = level.getBlockState(pos);
            boolean built = state.matches(world, m.blockState(i));
            out[i] = !built;
            wrong[i] = !built && !world.isAir();
            if (built) {
                placed++;
            } else if (wrong[i]) {
                wrongCount++;
                wrongVis[i] = hasAirNeighbour(level, pos, npos);
            }
        }
        needsPlacing = out;
        wrongBlock = wrong;
        wrongBlockVisible = wrongVis;
        wrongBE = scanWrongBlockEntities(m, anchor, level, state, pos);
        wrongFluid = scanWrongFluids(m, anchor, level, state, pos);
        scanKey = key;
        lastScanNanos = now;
        placedCount = placed;
        wrongTotal = wrongCount;
        scanVersion++;
        buried = out.length > 0 && (long) wrongCount * 100L >= (long) out.length * BURIED_PERCENT;
        if (buried) {
            extraX = extraY = extraZ = EMPTY_INT;
        } else {
            scanExtraBlocks(m, transform, anchor, level);
        }
        return out;
    }

    /**
     * Per-block-entity "wrong block in its place" flags — block entities (chests, signs, …) produce
     * no model quads of their own, so they're absent from {@link #wrongBlock} entirely; without this,
     * a wrong block sitting where a chest belongs was never flagged.
     */
    private static boolean[] scanWrongBlockEntities(GhostMesh m, BlockPos anchor, ClientLevel level,
                                                     GhostState state, BlockPos.MutableBlockPos pos) {
        boolean[] wrong = new boolean[m.blockEntityCount()];
        for (int i = 0; i < wrong.length; i++) {
            pos.set(anchor.getX() + m.beX(i), anchor.getY() + m.beY(i), anchor.getZ() + m.beZ(i));
            BlockState world = level.getBlockState(pos);
            wrong[i] = !state.matches(world, m.beState(i)) && !world.isAir();
        }
        return wrong;
    }

    /**
     * Per-fluid "wrong block in its place" flags. A pure water/lava source has no block model, so
     * (like block entities) it never enters {@link #wrongBlock} — a wrong block where water belongs
     * went unflagged. Waterlogged blocks are skipped here since their host block already covers them
     * via {@link #wrongBlock}.
     */
    private static boolean[] scanWrongFluids(GhostMesh m, BlockPos anchor, ClientLevel level,
                                             GhostState state, BlockPos.MutableBlockPos pos) {
        boolean[] wrong = new boolean[m.fluidCount()];
        for (int i = 0; i < wrong.length; i++) {
            BlockState ghost = m.fluidState(i);
            if (!(ghost.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
                continue;
            }
            pos.set(anchor.getX() + m.fluidX(i), anchor.getY() + m.fluidY(i), anchor.getZ() + m.fluidZ(i));
            BlockState world = level.getBlockState(pos);
            // "wrong" = a real different block is in the way; the same fluid at a different level
            // (a source vs. its own flow at the edges) isn't a mistake worth flagging.
            boolean sameFluid = !world.getFluidState().isEmpty()
                    && world.getFluidState().getType().isSame(ghost.getFluidState().getType());
            wrong[i] = !state.matches(world, ghost) && !world.isAir() && !sameFluid;
        }
        return wrong;
    }

    /**
     * World blocks sitting in the way of the build: a cell that touches at least one schematic block
     * (a face neighbour of one), is air in the schematic, and has a non-air block in the world. Walks
     * the schematic's own blocks (O(blocks), not O(footprint volume)), so it stays cheap and only
     * flags terrain actually clipping the build — not every stray block in the bounding box.
     */
    private static void scanExtraBlocks(GhostMesh m, PlacementTransform transform, BlockPos anchor,
                                        ClientLevel level) {
        int blocks = m.blockCount();
        java.util.HashSet<Long> schematicCells = new java.util.HashSet<>(blocks * 2);
        for (int i = 0; i < blocks; i++) {
            schematicCells.add(packLocal(m.blockX(i), m.blockY(i), m.blockZ(i)));
        }
        for (int i = 0, n = m.fluidCount(); i < n; i++) {
            schematicCells.add(packLocal(m.fluidX(i), m.fluidY(i), m.fluidZ(i)));
        }
        for (int i = 0, n = m.blockEntityCount(); i < n; i++) {
            schematicCells.add(packLocal(m.beX(i), m.beY(i), m.beZ(i)));
        }

        int fx = transform.footprintX();
        int fy = transform.footprintY();
        int fz = transform.footprintZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        java.util.List<int[]> found = new java.util.ArrayList<>();

        for (int i = 0; i < blocks; i++) {
            int bx = m.blockX(i);
            int by = m.blockY(i);
            int bz = m.blockZ(i);
            for (net.minecraft.core.Direction d : FACES) {
                int nx = bx + d.getStepX();
                int ny = by + d.getStepY();
                int nz = bz + d.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= fx || ny >= fy || nz >= fz) {
                    continue;
                }
                long packed = packLocal(nx, ny, nz);
                if (schematicCells.contains(packed) || !seen.add(packed)) {
                    continue; // the schematic fills this cell, or we already flagged it
                }
                pos.set(anchor.getX() + nx, anchor.getY() + ny, anchor.getZ() + nz);
                if (!level.getBlockState(pos).isAir()) {
                    found.add(new int[] {nx, ny, nz});
                }
            }
        }

        int n = found.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] zs = new int[n];
        for (int i = 0; i < n; i++) {
            int[] p = found.get(i);
            xs[i] = p[0];
            ys[i] = p[1];
            zs[i] = p[2];
        }
        extraX = xs;
        extraY = ys;
        extraZ = zs;
    }

    /** True if any of the six face neighbours is air in the world — a proxy for "on a surface the
     *  player can see and reach". A cell buried inside solid terrain fails this. */
    private static boolean hasAirNeighbour(ClientLevel level, BlockPos cell, BlockPos.MutableBlockPos scratch) {
        for (net.minecraft.core.Direction d : FACES) {
            scratch.setWithOffset(cell, d);
            if (level.getBlockState(scratch).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Pack a small non-negative footprint-local coord triple into one long (17 bits per axis). */
    private static long packLocal(int x, int y, int z) {
        return ((long) (x & 0x1FFFF) << 34) | ((long) (y & 0x1FFFF) << 17) | (z & 0x1FFFF);
    }

    /** Wire cube for block entities we can't (or are told not to) render as a real model. */
    private static void renderBlockEntityMarkers(GhostMesh m, BlockPos anchor, ClientLevel level,
                                                 Vec3 cam, LevelRenderContext ctx, boolean hideMatched,
                                                 boolean hideWrongToo) {
        GhostState state = GhostState.get();
        boolean models = state.blockEntityModels();
        int color = markerColor(0x55CCFF, state);
        VertexConsumer lines = ctx.bufferSource().getBuffer(GhostPipelines.linesForGhost(state.seeThrough()));
        PoseStack ps = new PoseStack();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        boolean any = false;

        boolean layerClip = state.layerClip();
        for (int i = 0, n = m.blockEntityCount(); i < n; i++) {
            if (models && m.blockEntity(i) != null) {
                continue;
            }
            if (layerClip && !state.layerVisible(m.beY(i))) {
                continue;
            }
            worldPos.set(anchor.getX() + m.beX(i), anchor.getY() + m.beY(i), anchor.getZ() + m.beZ(i));
            if (hideMatched && isBuiltOrHiddenWrong(level.getBlockState(worldPos), m.beState(i), state, hideWrongToo)) {
                continue;
            }
            ShapeRenderer.renderShape(ps, lines, Shapes.block(),
                    worldPos.getX() - cam.x, worldPos.getY() - cam.y, worldPos.getZ() - cam.z,
                    color, MARKER_LINE);
            any = true;
        }
        if (any) {
            ctx.bufferSource().endBatch(GhostPipelines.linesForGhost(state.seeThrough()));
        }
    }

    /** True when build-assist should skip drawing this cell: it's already built, or it's wrongly
     *  built and {@code hideWrongToo} says to hide those too. */
    private static boolean isBuiltOrHiddenWrong(BlockState world, BlockState ghost, GhostState state,
                                                boolean hideWrongToo) {
        if (state.matches(world, ghost)) {
            return true;
        }
        return hideWrongToo && !world.isAir();
    }

    /**
     * Submit real block-entity models (chests, signs, beds…) during the collect phase, through a
     * wrapper that fades their colours with the ghost opacity.
     */
    private static void submitBlockEntities(LevelRenderContext ctx) {
        GhostState state = GhostState.get();
        GhostMesh m = mesh;
        if (!state.isVisible() || !state.blockEntityModels() || m == null
                || m.blockEntityCount() == 0 || m.totalQuads() > MAX_QUADS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !m.matches(state.schematic(), state.transform())) {
            return;
        }

        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var camState = ctx.levelState().cameraRenderState;
        Vec3 camPos = camState.pos;
        GhostSubmitCollector collector = new GhostSubmitCollector(
                ctx.submitNodeCollector(), state.opacity());
        BlockPos anchor = state.anchor();
        boolean hideMatched = state.hideMatched();
        boolean hideWrongToo = hideMatched;
        boolean layerClip = state.layerClip();
        PoseStack ps = new PoseStack();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        int submitted = 0;

        for (int i = 0, n = m.blockEntityCount(); i < n; i++) {
            BlockEntity be = m.blockEntity(i);
            if (be == null || (layerClip && !state.layerVisible(m.beY(i)))) {
                continue;
            }
            int wx = anchor.getX() + m.beX(i);
            int wy = anchor.getY() + m.beY(i);
            int wz = anchor.getZ() + m.beZ(i);
            worldPos.set(wx, wy, wz);
            if (hideMatched && isBuiltOrHiddenWrong(level.getBlockState(worldPos), m.beState(i), state, hideWrongToo)) {
                continue;
            }
            try {
                BlockEntityRenderState s = extractState(dispatcher, be, partialTick, worldPos.immutable());
                if (s == null) {
                    continue;
                }
                ps.pushPose();
                ps.translate(wx - camPos.x, wy - camPos.y, wz - camPos.z);
                dispatcher.submit(s, ps, collector, camState);
                ps.popPose();
                submitted++;
            } catch (Exception e) {
                HoloPlaceClient.LOGGER.debug("Block entity submit failed for {}", m.beState(i), e);
            }
        }
        if (loggedBeMesh != m) {
            loggedBeMesh = m;
            HoloPlaceClient.LOGGER.info("Ghost block entities: {} of {} submitted",
                    submitted, m.blockEntityCount());
        }
    }

    /** Submit the schematic's entities (item frames, armour stands, paintings…), faded with opacity. */
    private static void submitEntities(LevelRenderContext ctx) {
        GhostState state = GhostState.get();
        GhostMesh m = mesh;
        if (!state.isVisible() || !state.showEntities() || m == null || m.entityCount() == 0
                || m.totalQuads() > MAX_QUADS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !m.matches(state.schematic(), state.transform())) {
            return;
        }

        var dispatcher = mc.getEntityRenderDispatcher();
        var camState = ctx.levelState().cameraRenderState;
        Vec3 camPos = camState.pos;
        GhostSubmitCollector collector = new GhostSubmitCollector(ctx.submitNodeCollector(), state.opacity());
        BlockPos anchor = state.anchor();
        boolean layerClip = state.layerClip();
        PoseStack ps = new PoseStack();
        int submitted = 0;

        for (int i = 0, n = m.entityCount(); i < n; i++) {
            GhostMesh.GhostEntity ge = m.entity(i);
            if (layerClip && !state.layerVisible((int) Math.floor(ge.y()))) {
                continue;
            }
            try {
                var entity = ge.entity();
                if (!(entity instanceof net.minecraft.world.entity.decoration.HangingEntity)) {
                    entity.setYRot(ge.yaw());
                }
                entity.setPos(anchor.getX() + ge.x(), anchor.getY() + ge.y(), anchor.getZ() + ge.z());
                entity.setOldPosAndRot();
                if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                    living.yBodyRot = living.yBodyRotO = ge.yaw();
                    living.yHeadRot = living.yHeadRotO = ge.yaw();
                }
                // partialTick 1.0 → every lerp (position, body/head rotation) resolves to the current
                // value, so a never-ticked entity holds still instead of wobbling.
                var s = dispatcher.extractEntity(entity, 1.0f);
                s.lightCoords = FULL_BRIGHT;
                dispatcher.submit(s, camState, s.x - camPos.x, s.y - camPos.y, s.z - camPos.z, ps, collector);
                submitted++;
            } catch (Exception e) {
                HoloPlaceClient.LOGGER.debug("Ghost entity submit failed", e);
            }
        }
        if (loggedEntityMesh != m) {
            loggedEntityMesh = m;
            HoloPlaceClient.LOGGER.info("Ghost entities: {} of {} submitted", submitted, m.entityCount());
        }
    }

    /**
     * Extract a render state directly (bypassing {@code tryExtractRenderState}'s distance cull, which
     * would drop the ghost's block entities because their built position is near the origin).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable BlockEntityRenderState extractState(
            BlockEntityRenderDispatcher dispatcher, BlockEntity be, float partialTick, BlockPos worldCell) {
        var renderer = dispatcher.getRenderer(be);
        if (renderer == null) {
            return null;
        }
        BlockEntityRenderState s = renderer.createRenderState();
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        ((net.minecraft.client.renderer.blockentity.BlockEntityRenderer) renderer)
                .extractRenderState(be, s, partialTick, camPos, null);
        s.blockPos = worldCell;
        s.lightCoords = FULL_BRIGHT;
        return s;
    }

    private static void renderFluids(GhostMesh m, BlockPos anchor, Vec3 cam, ClientLevel level,
                                     Minecraft mc, VertexConsumer buffer, boolean hideMatched,
                                     boolean hideWrongToo) {
        SchematicBlockView view = new SchematicBlockView(m.schematic(), anchor, m.transform());
        FluidRenderer fluidRenderer = new FluidRenderer(mc.getModelManager().getFluidStateModelSet());
        // FluidRenderer emits vertices in section-local space (pos & 15); shift each back to
        // camera-relative space via the section origin.
        OffsetVertexConsumer offset = new OffsetVertexConsumer(buffer);
        FluidRenderer.Output output = layer -> offset;
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        GhostState g = GhostState.get();
        boolean layerClip = g.layerClip();
        for (int i = 0, n = m.fluidCount(); i < n; i++) {
            if (layerClip && !g.layerVisible(m.fluidY(i))) {
                continue;
            }
            int wx = anchor.getX() + m.fluidX(i);
            int wy = anchor.getY() + m.fluidY(i);
            int wz = anchor.getZ() + m.fluidZ(i);
            worldPos.set(wx, wy, wz);
            BlockState state = m.fluidState(i);
            if (hideMatched && isBuiltOrHiddenWrong(level.getBlockState(worldPos), state, g, hideWrongToo)) {
                continue;
            }
            offset.setOffset((wx & ~15) - cam.x, (wy & ~15) - cam.y, (wz & ~15) - cam.z);
            fluidRenderer.tesselate(view, worldPos, output, state, state.getFluidState());
        }
    }

    /** Per-frame upload of every visible ghost quad — the fallback for when {@link GhostGpuMesh}
     *  can't be used. Same block / quad selection as {@link GhostGpuMesh#build}. */
    private static void renderBlocksImmediate(GhostMesh m, boolean @Nullable [] needs,
            boolean @Nullable [] wrong, boolean hideWrongToo, boolean layerClip, GhostState state,
            int[] tint, int alpha, boolean shade, float ox, float oy, float oz, VertexConsumer buffer) {
        QUAD.setLightCoords(FULL_BRIGHT);
        int blocks = m.blockCount();
        boolean wrongUsable = hideWrongToo && wrong != null && wrong.length == blocks;
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
            int baseRgb = tint[i] == NO_TINT ? 0x00FFFFFF : (tint[i] & 0x00FFFFFF);
            int end = m.quadStart(i + 1);
            for (int q = m.quadStart(i); q < end; q++) {
                GhostMesh.Quad quad = m.quad(q);
                int rgb = quad.tinted() ? baseRgb : 0x00FFFFFF;
                QUAD.setColor(shade ? alpha | shadeRgb(rgb, quad.quad()) : alpha | rgb);
                buffer.putBlockBakedQuad(quad.x() + ox, quad.y() + oy, quad.z() + oz, quad.quad(), QUAD);
            }
        }
    }

    private static void closeGpuMesh() {
        if (gpuMesh != null) {
            try {
                gpuMesh.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
            gpuMesh = null;
        }
    }

    /** Classic Minecraft per-face darkening (top bright, bottom dim), applied per quad. */
    static int shadeRgb(int rgb, net.minecraft.client.resources.model.geometry.BakedQuad quad) {
        if (!quad.materialInfo().shade()) {
            return rgb;
        }
        float f = CardinalLighting.DEFAULT.byFace(quad.direction());
        int r = Math.round(((rgb >> 16) & 0xFF) * f);
        int g = Math.round(((rgb >> 8) & 0xFF) * f);
        int b = Math.round((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Per-block tint colour (index 0), recomputed only when the mesh or anchor changes. */
    private static int[] tintFor(GhostMesh m, BlockPos anchor, ClientLevel level, Minecraft mc) {
        long key = ((long) System.identityHashCode(m) << 32) ^ anchor.asLong();
        int[] cached = blockTint;
        boolean haveUsable = cached != null && cached.length == m.blockCount();
        if (haveUsable && (key == tintKey || PlacementController.get().isGrabbing())) {
            return cached;
        }
        int[] tint = new int[m.blockCount()];
        var colors = mc.getBlockColors();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < tint.length; i++) {
            BlockState bs = m.blockState(i);
            List<BlockTintSource> sources = colors.getTintSources(bs);
            if (sources.isEmpty()) {
                tint[i] = NO_TINT;
                continue;
            }
            pos.set(anchor.getX() + m.blockX(i), anchor.getY() + m.blockY(i), anchor.getZ() + m.blockZ(i));
            tint[i] = sources.get(0).colorInWorld(bs, level, pos);
        }
        blockTint = tint;
        tintKey = key;
        return tint;
    }
}
