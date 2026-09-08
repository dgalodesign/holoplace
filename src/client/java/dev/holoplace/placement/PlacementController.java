package dev.holoplace.placement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.holoplace.GhostState;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.config.WorldPlacements;
import dev.holoplace.schematic.PlacementTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * M3 — grab mode. While active, the ghost's anchor follows a ray from the camera, snapping the
 * schematic's footprint centre to the block face under the crosshair. Mouse wheel changes the reach
 * distance; Shift+wheel nudges the vertical offset. Toggle with the grab key; toggling off locks it.
 */
public final class PlacementController {

    private static final PlacementController INSTANCE = new PlacementController();

    private static final double MIN_REACH = 1.0;
    private static final double MAX_REACH = 64.0;
    private static final int MAX_VOFFSET = 64;

    private boolean grabbing;
    private double reach = 8.0;
    private int verticalOffset;

    private PlacementController() {
    }

    public static PlacementController get() {
        return INSTANCE;
    }

    public boolean isGrabbing() {
        return grabbing;
    }

    public void loadPrefs() {
        HoloPlaceConfig config = HoloPlaceConfig.get();
        this.reach = Mth.clamp(config.reach, MIN_REACH, MAX_REACH);
        this.verticalOffset = Mth.clamp(config.verticalOffset, -MAX_VOFFSET, MAX_VOFFSET);
        GhostState.get().setRotation(parseEnum(Rotation.values(), config.rotation, Rotation.NONE));
        GhostState.get().setMirror(parseEnum(Mirror.values(), config.mirror, Mirror.NONE));
    }

    private static <E extends Enum<E>> E parseEnum(E[] values, String name, E fallback) {
        for (E value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return fallback;
    }

    public void toggleGrab() {
        Minecraft mc = Minecraft.getInstance();
        GhostState ghost = GhostState.get();
        if (ghost.schematic() == null) {
            actionBar(mc, text("holoplace.action.no_schematic"));
            return;
        }
        if (grabbing) {
            stopGrab(true);
        } else {
            grabbing = true;
            ghost.setVisible(true);
            actionBar(mc, text("holoplace.action.grab_on"));
        }
    }

    public void stopGrab(boolean lock) {
        if (!grabbing) {
            return;
        }
        grabbing = false;
        Minecraft mc = Minecraft.getInstance();
        HoloPlaceConfig.get().reach = this.reach;
        HoloPlaceConfig.get().verticalOffset = this.verticalOffset;
        HoloPlaceConfig.save();
        if (lock) {
            WorldPlacements.saveCurrent();
            BlockPos a = GhostState.get().anchor();
            actionBar(mc, text("holoplace.action.locked", a.getX(), a.getY(), a.getZ()));
        }
    }

    /**
     * @return true if the scroll was consumed. In grab mode the wheel drives reach/height;
     *     whenever the ghost is visible, Alt+wheel drives opacity.
     */
    public boolean handleScroll(double yOffset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            return false;
        }
        int dir = (int) Math.signum(yOffset);
        if (dir == 0) {
            return grabbing;
        }
        boolean alt = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
        if (alt && GhostState.get().isVisible()) {
            adjustOpacity(dir);
            return true;
        }
        if (!grabbing) {
            return false;
        }
        boolean shift = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (shift) {
            verticalOffset = Mth.clamp(verticalOffset + dir, -MAX_VOFFSET, MAX_VOFFSET);
        } else {
            reach = Mth.clamp(reach + dir, MIN_REACH, MAX_REACH);
        }
        return true;
    }

    public void rotate(boolean clockwise) {
        if (notReady()) {
            return;
        }
        GhostState ghost = GhostState.get();
        Rotation next = clockwise
                ? PlacementTransform.rotateCw(ghost.rotation())
                : PlacementTransform.rotateCcw(ghost.rotation());
        keepFootprintCentre(ghost, () -> ghost.setRotation(next));
        HoloPlaceConfig.get().rotation = next.name();
        HoloPlaceConfig.save();
        WorldPlacements.saveCurrent();
        actionBar(Minecraft.getInstance(), text("holoplace.action.rotation", label(next)));
    }

    /** Change the transform while keeping the footprint's centre fixed (so a locked ghost doesn't jump). */
    private void keepFootprintCentre(GhostState ghost, Runnable change) {
        PlacementTransform before = ghost.transform();
        if (grabbing || before == null) {
            change.run();
            return;
        }
        BlockPos anchor = ghost.anchor();
        double cx = anchor.getX() + before.footprintX() / 2.0;
        double cz = anchor.getZ() + before.footprintZ() / 2.0;
        change.run();
        PlacementTransform after = ghost.transform();
        if (after != null) {
            int ax = Mth.floor(cx - after.footprintX() / 2.0);
            int az = Mth.floor(cz - after.footprintZ() / 2.0);
            ghost.setAnchor(new BlockPos(ax, anchor.getY(), az));
        }
    }

    public void cycleMirror() {
        if (notReady()) {
            return;
        }
        GhostState ghost = GhostState.get();
        Mirror next = PlacementTransform.cycleMirror(ghost.mirror());
        ghost.setMirror(next);
        HoloPlaceConfig.get().mirror = next.name();
        HoloPlaceConfig.save();
        WorldPlacements.saveCurrent();
        actionBar(Minecraft.getInstance(), text("holoplace.action.mirror", label(next)));
    }

    public void moveTo(int x, int y, int z) {
        if (notReady()) {
            return;
        }
        stopGrab(false);
        GhostState.get().setAnchor(new BlockPos(x, y, z));
        WorldPlacements.saveCurrent();
        actionBar(Minecraft.getInstance(), text("holoplace.action.moved", x, y, z));
    }

    public void nudge(net.minecraft.core.Direction dir, int amount) {
        if (notReady()) {
            return;
        }
        stopGrab(false);
        BlockPos a = GhostState.get().anchor().relative(dir, amount);
        GhostState.get().setAnchor(a);
        WorldPlacements.saveCurrent();
        actionBar(Minecraft.getInstance(), text("holoplace.action.nudged", a.getX(), a.getY(), a.getZ()));
    }

    public void resetTransform() {
        if (notReady()) {
            return;
        }
        GhostState.get().setRotation(Rotation.NONE);
        GhostState.get().setMirror(Mirror.NONE);
        HoloPlaceConfig.get().rotation = Rotation.NONE.name();
        HoloPlaceConfig.get().mirror = Mirror.NONE.name();
        HoloPlaceConfig.save();
        WorldPlacements.saveCurrent();
        actionBar(Minecraft.getInstance(), text("holoplace.action.reset"));
    }

    public void toggleSeeThrough() {
        if (notReady()) {
            return;
        }
        GhostState ghost = GhostState.get();
        boolean next = !ghost.seeThrough();
        ghost.setSeeThrough(next);
        HoloPlaceConfig.get().seeThrough = next;
        HoloPlaceConfig.save();
        actionBar(Minecraft.getInstance(), text(next
                ? "holoplace.action.seethrough_on"
                : "holoplace.action.seethrough_off"));
    }

    public void setLayers(int min, int max) {
        if (notReady()) {
            return;
        }
        GhostState.get().setLayers(min, max);
        persistLayers();
        actionBar(Minecraft.getInstance(), min == max
                ? text("holoplace.action.layer_single", min)
                : text("holoplace.action.layer_range", GhostState.get().layerMin(), GhostState.get().layerMax()));
    }

    public void clearLayers() {
        GhostState.get().clearLayers();
        persistLayers();
        actionBar(Minecraft.getInstance(), text("holoplace.action.layers_off"));
    }

    private void persistLayers() {
        HoloPlaceConfig.get().layerMin = GhostState.get().layerMin();
        HoloPlaceConfig.get().layerMax = GhostState.get().layerMax();
        HoloPlaceConfig.save();
    }

    public void toggleBuildAssist() {
        if (notReady()) {
            return;
        }
        GhostState ghost = GhostState.get();
        boolean next = !ghost.hideMatched();
        ghost.setHideMatched(next);
        HoloPlaceConfig.get().hideMatched = next;
        HoloPlaceConfig.save();
        actionBar(Minecraft.getInstance(), text(next
                ? "holoplace.action.buildassist_on"
                : "holoplace.action.buildassist_off"));
    }

    public void adjustOpacity(int dir) {
        if (notReady()) {
            return;
        }
        GhostState ghost = GhostState.get();
        ghost.setOpacity(ghost.opacity() + dir * 0.05f);
        HoloPlaceConfig.get().opacity = ghost.opacity();
        HoloPlaceConfig.save();
        actionBar(Minecraft.getInstance(),
                text("holoplace.action.opacity", Math.round(ghost.opacity() * 100)) + "%");
    }

    private static boolean notReady() {
        return GhostState.get().schematic() == null;
    }

    private static String label(Rotation r) {
        return switch (r) {
            case NONE -> text("holoplace.rotation.0");
            case CLOCKWISE_90 -> text("holoplace.rotation.90cw");
            case CLOCKWISE_180 -> text("holoplace.rotation.180");
            case COUNTERCLOCKWISE_90 -> text("holoplace.rotation.90ccw");
        };
    }

    private static String label(Mirror m) {
        return switch (m) {
            case NONE -> text("holoplace.mirror.none");
            case FRONT_BACK -> text("holoplace.mirror.front_back");
            case LEFT_RIGHT -> text("holoplace.mirror.left_right");
        };
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    public void tick() {
        if (!grabbing) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GhostState ghost = GhostState.get();
        if (mc.player == null || mc.level == null || ghost.schematic() == null || !ghost.isVisible()) {
            grabbing = false;
            return;
        }
        ghost.setAnchor(computeAnchor(mc));
    }

    private BlockPos computeAnchor(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);

        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));

        BlockPos target;
        if (hit.getType() == HitResult.Type.BLOCK) {
            target = hit.getBlockPos().relative(hit.getDirection());
        } else {
            target = BlockPos.containing(end);
        }

        PlacementTransform transform = GhostState.get().transform();
        int footX = transform == null ? 1 : transform.footprintX();
        int footZ = transform == null ? 1 : transform.footprintZ();
        int anchorX = target.getX() - Math.max(0, footX - 1) / 2;
        int anchorZ = target.getZ() - Math.max(0, footZ - 1) / 2;
        int anchorY = target.getY() + verticalOffset;
        return new BlockPos(anchorX, anchorY, anchorZ);
    }

    private static void actionBar(Minecraft mc, String message) {
        if (mc.gui != null) {
            mc.gui.hud.setOverlayMessage(Component.literal(message), false);
        }
    }
}
