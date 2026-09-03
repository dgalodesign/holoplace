package dev.holoplace.placement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.holoplace.GhostState;
import dev.holoplace.config.HoloPlaceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
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
    }

    public void toggleGrab() {
        Minecraft mc = Minecraft.getInstance();
        GhostState ghost = GhostState.get();
        if (ghost.schematic() == null) {
            actionBar(mc, "§e[HoloPlace] no schematic — §f/holoplace show <file>");
            return;
        }
        if (grabbing) {
            stopGrab(true);
        } else {
            grabbing = true;
            ghost.setVisible(true);
            actionBar(mc, "§aGrab mode §7— look to position · wheel: distance · Shift+wheel: height · §fG§7 to lock");
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
            BlockPos a = GhostState.get().anchor();
            actionBar(mc, "§aLocked at §f" + a.getX() + " " + a.getY() + " " + a.getZ());
        }
    }

    /** @return true if the scroll was consumed (grab mode active). */
    public boolean handleScroll(double yOffset) {
        if (!grabbing || Minecraft.getInstance().screen != null) {
            return false;
        }
        int dir = (int) Math.signum(yOffset);
        if (dir == 0) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean shift = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (shift) {
            verticalOffset = Mth.clamp(verticalOffset + dir, -MAX_VOFFSET, MAX_VOFFSET);
        } else {
            reach = Mth.clamp(reach + dir, MIN_REACH, MAX_REACH);
        }
        return true;
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

        Vec3i size = GhostState.get().schematic().enclosingSize();
        int anchorX = target.getX() - Math.max(0, size.getX() - 1) / 2;
        int anchorZ = target.getZ() - Math.max(0, size.getZ() - 1) / 2;
        int anchorY = target.getY() + verticalOffset;
        return new BlockPos(anchorX, anchorY, anchorZ);
    }

    private static void actionBar(Minecraft mc, String message) {
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal(message), false);
        }
    }
}
