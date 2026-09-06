package dev.holoplace.capture;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

/**
 * M19 — capture-area selection. A keybind toggles "selection mode"; while it's on, left-click on a
 * block sets corner 1 and right-click sets corner 2 (via Fabric's block-interaction events, so no new
 * mixin). The clicks are consumed only while selection mode is active, so normal play is untouched
 * otherwise. Writing the schematic itself lands in M20.
 */
public final class CaptureController {

    private static final CaptureController INSTANCE = new CaptureController();

    /** Above this many cells the HUD warns; kept in the same ballpark as the render-side guards. */
    private static final long VOLUME_WARN_LIMIT = 5_000_000L;

    private final SelectionState selection = new SelectionState();
    private boolean selecting;

    private CaptureController() {
    }

    public static CaptureController get() {
        return INSTANCE;
    }

    /**
     * Registers the block-click hooks. Called once from client init. Returns {@link
     * InteractionResult#FAIL} (not {@code SUCCESS}) to consume a click: {@code SUCCESS} would still
     * let Fabric fire the prediction and send the action packet, and the server would then break /
     * place the block anyway (instantly, in creative). {@code FAIL} cancels the action outright.
     */
    public static void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                level.isClientSide() && get().onCornerClick(pos, true)
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                level.isClientSide() && get().onCornerClick(hit.getBlockPos(), false)
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
    }

    public SelectionState selection() {
        return selection;
    }

    public boolean isSelecting() {
        return selecting;
    }

    public long volumeWarnLimit() {
        return VOLUME_WARN_LIMIT;
    }

    public void toggleSelecting() {
        selecting = !selecting;
        actionBar(selecting ? "holoplace.capture.select_on" : "holoplace.capture.select_off");
    }

    /** @return true if the click was consumed (i.e. selection mode is on and a corner was set). */
    public boolean onCornerClick(BlockPos pos, boolean first) {
        if (!selecting) {
            return false;
        }
        BlockPos p = pos.immutable();
        if (first) {
            selection.setCorner1(p);
        } else {
            selection.setCorner2(p);
        }
        overlay(Component.translatable(
                first ? "holoplace.capture.corner1" : "holoplace.capture.corner2",
                p.getX(), p.getY(), p.getZ()));
        return true;
    }

    public void setCornerFromLook(boolean first) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            onCornerClickForced(hit.getBlockPos(), first);
        } else {
            actionBar("holoplace.capture.no_target");
        }
    }

    private void onCornerClickForced(BlockPos pos, boolean first) {
        BlockPos p = pos.immutable();
        if (first) {
            selection.setCorner1(p);
        } else {
            selection.setCorner2(p);
        }
        overlay(Component.translatable(
                first ? "holoplace.capture.corner1" : "holoplace.capture.corner2",
                p.getX(), p.getY(), p.getZ()));
    }

    public void clearSelection() {
        selection.clear();
        actionBar("holoplace.capture.cleared");
    }

    private static void actionBar(String key) {
        overlay(Component.translatable(key));
    }

    private static void overlay(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(message, false);
        }
    }
}
