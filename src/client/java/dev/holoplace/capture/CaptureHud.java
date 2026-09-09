package dev.holoplace.capture;

import dev.holoplace.HoloPlaceKeys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The capture-selection readout. Rendered by {@link dev.holoplace.render.GhostHud}, which stacks it
 * under the ghost panel at the chosen corner — this class just supplies the lines.
 */
public final class CaptureHud {

    private CaptureHud() {
    }

    /** The lines to show while a capture selection is being defined, or {@code null} if there's
     *  nothing to show. */
    public static @Nullable List<String> lines() {
        CaptureController cc = CaptureController.get();
        SelectionState sel = cc.selection();
        if (!cc.isSelecting() && sel.corner1() == null && sel.corner2() == null) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        lines.add("§b❖ " + text("holoplace.hud.capture_title")
                + (cc.isSelecting() ? "  §e[" + text("holoplace.hud.capture_selecting") + "]" : ""));
        lines.add("§a" + text("holoplace.hud.capture_c1") + " §f" + coord(sel.corner1()));
        lines.add("§6" + text("holoplace.hud.capture_c2") + " §f" + coord(sel.corner2()));
        if (sel.isComplete()) {
            Vec3i s = sel.size();
            long vol = sel.volume();
            lines.add("§7" + text("holoplace.hud.capture_size") + " §f"
                    + s.getX() + "×" + s.getY() + "×" + s.getZ()
                    + " §8(" + String.format("%,d", vol) + " " + text("holoplace.hud.capture_cells") + ")");
            if (vol > cc.volumeWarnLimit()) {
                lines.add("§c" + text("holoplace.hud.capture_toobig"));
            }
        }
        lines.add("§8" + Component.translatable("holoplace.hud.capture_hint",
                HoloPlaceKeys.CAPTURE_SELECT.getTranslatedKeyMessage().getString()).getString());
        return lines;
    }

    private static String coord(@Nullable BlockPos p) {
        return p == null ? "§8—" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }
}
