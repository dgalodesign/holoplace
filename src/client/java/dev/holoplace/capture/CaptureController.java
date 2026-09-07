package dev.holoplace.capture;

import dev.holoplace.HoloPlaceClient;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.schematic.LitematicaSchematicWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Capture-area selection (M19) and full capture → {@code .litematic} (M20). A keybind toggles
 * "selection mode"; while it's on, left-click sets corner 1 and right-click sets corner 2 (via
 * Fabric's block-interaction events, so no new mixin — consumed only while selecting, so normal play
 * is untouched otherwise). {@code /holoplace capture save} writes the selected region as-is.
 */
public final class CaptureController {

    private static final CaptureController INSTANCE = new CaptureController();

    /** Above this many cells the HUD warns; kept in the same ballpark as the render-side guards. */
    private static final long VOLUME_WARN_LIMIT = 5_000_000L;
    /** Hard cap for a save — refuse rather than allocate a grid this large. */
    private static final long MAX_SAVE_VOLUME = 8_000_000L;

    private final SelectionState selection = new SelectionState();
    private boolean selecting;
    private boolean changesOnly = HoloPlaceConfig.get().captureChangesOnly;

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

    public boolean changesOnly() {
        return changesOnly;
    }

    public long volumeWarnLimit() {
        return VOLUME_WARN_LIMIT;
    }

    public void toggleSelecting() {
        selecting = !selecting;
        overlay(Component.translatable(selecting ? "holoplace.capture.select_on" : "holoplace.capture.select_off"));
    }

    /** {@code null} toggles; otherwise sets the mode. Full = everything in the box; changes = only
     *  the cells the passive {@link ChangeTracker} recorded this session. */
    public void setMode(@Nullable Boolean onlyChanges) {
        changesOnly = onlyChanges == null ? !changesOnly : onlyChanges;
        HoloPlaceConfig.get().captureChangesOnly = changesOnly;
        HoloPlaceConfig.save();
        overlay(Component.translatable(changesOnly
                ? "holoplace.capture.mode_changes" : "holoplace.capture.mode_full"));
    }

    /** @return true if the click was consumed (i.e. selection mode is on and a corner was set). */
    public boolean onCornerClick(BlockPos pos, boolean first) {
        if (!selecting) {
            return false;
        }
        setCorner(pos, first);
        return true;
    }

    /** Command path: set a corner from where the player is looking, regardless of selection mode. */
    public void setCornerFromLook(boolean first) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            setCorner(hit.getBlockPos(), first);
        } else {
            overlay(Component.translatable("holoplace.capture.no_target"));
        }
    }

    private void setCorner(BlockPos pos, boolean first) {
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
        overlay(Component.translatable("holoplace.capture.cleared"));
    }

    /**
     * Write the selection to {@code <name>.litematic}, using the current {@link #changesOnly()} mode:
     * changes → only cells the passive {@link ChangeTracker} recorded this session keep their block,
     * the rest become air; full → everything in the box as-is.
     */
    public void save(@Nullable String rawName) {
        boolean onlyChanges = changesOnly;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            chat(Component.translatable("holoplace.capture.save_no_world"));
            return;
        }
        if (!selection.isComplete()) {
            chat(Component.translatable("holoplace.capture.save_no_selection"));
            return;
        }
        long volume = selection.volume();
        if (volume > MAX_SAVE_VOLUME) {
            chat(Component.translatable("holoplace.capture.save_too_big",
                    String.format("%,d", volume), String.format("%,d", MAX_SAVE_VOLUME)));
            return;
        }
        ChangeLog changes = ChangeTracker.log();
        if (onlyChanges && changes.isEmpty()) {
            chat(Component.translatable("holoplace.capture.save_no_changes"));
            return;
        }

        String name = sanitize(rawName);
        try {
            SchematicLibrary.ensurePrimaryDir();
            Path file = SchematicLibrary.primaryDir().resolve(name + ".litematic");
            int dataVersion = SharedConstants.getCurrentVersion().dataVersion().version();
            String author = mc.getUser().getName();
            LitematicaSchematicWriter.Region region =
                    CaptureWriter.capture(name, selection, level, onlyChanges ? changes : null);
            LitematicaSchematicWriter.write(file, name, author, dataVersion, region);
            HoloPlaceClient.LOGGER.info(
                    "Capture '{}': mode={}, selection={} cells, change-log={}, non-air={}, entities={}",
                    name, onlyChanges ? "changes" : "full", volume, changes.size(),
                    region.countNonAir(), region.entities().size());
            chat(Component.translatable("holoplace.capture.saved", name,
                    String.format("%,d", region.countNonAir())));
            if (onlyChanges && changes.isFull()) {
                chat(Component.translatable("holoplace.capture.changes_capped"));
            }
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.error("Capture save failed", e);
            chat(Component.translatable("holoplace.capture.save_failed", String.valueOf(e.getMessage())));
        }
    }

    private static String sanitize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return "capture-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        }
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9 _.-]", "_");
        return cleaned.isBlank() ? "capture" : cleaned;
    }

    private static void overlay(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(message, false);
        }
    }

    private static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        } else {
            HoloPlaceClient.LOGGER.info(message.getString());
        }
    }
}
