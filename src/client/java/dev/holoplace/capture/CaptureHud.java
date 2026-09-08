package dev.holoplace.capture;

import dev.holoplace.HoloPlaceClient;
import dev.holoplace.HoloPlaceKeys;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;

/** Top-right readout while a capture selection is being defined. Hidden when there's nothing to show. */
public final class CaptureHud {

    private CaptureHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(HoloPlaceClient.id("capture"), (graphics, deltaTracker) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        CaptureController cc = CaptureController.get();
        SelectionState sel = cc.selection();
        boolean anything = cc.isSelecting() || sel.corner1() != null || sel.corner2() != null;
        if (!anything) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            return;
        }
        Font font = mc.font;

        List<String> lines = new ArrayList<>();
        lines.add("§b❖ " + text("holoplace.hud.capture_title")
                + (cc.isSelecting() ? "  §e[" + text("holoplace.hud.capture_selecting") + "]" : ""));
        lines.add("§a" + text("holoplace.hud.capture_c1") + " §f" + coord(sel.corner1()));
        lines.add("§6" + text("holoplace.hud.capture_c2") + " §f" + coord(sel.corner2()));
        if (sel.isComplete()) {
            Vec3i s = sel.size();
            long vol = sel.volume();
            boolean big = vol > cc.volumeWarnLimit();
            lines.add("§7" + text("holoplace.hud.capture_size") + " §f"
                    + s.getX() + "×" + s.getY() + "×" + s.getZ()
                    + " §8(" + String.format("%,d", vol) + " " + text("holoplace.hud.capture_cells") + ")");
            if (big) {
                lines.add("§c" + text("holoplace.hud.capture_toobig"));
            }
        }
        lines.add("§8" + Component.translatable("holoplace.hud.capture_hint",
                HoloPlaceKeys.CAPTURE_SELECT.getTranslatedKeyMessage().getString()).getString());

        int pad = 3;
        int lineH = font.lineHeight + 1;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(Component.literal(line)));
        }
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW - width - pad - 4;
        int y = 4;
        graphics.fill(x - pad, y - pad, x + width + pad, y + lines.size() * lineH + pad - 1, 0xA0001018);
        for (String line : lines) {
            graphics.text(font, Component.literal(line), x, y, 0xFFFFFFFF, true);
            y += lineH;
        }
    }

    private static String coord(@org.jspecify.annotations.Nullable BlockPos p) {
        return p == null ? "§8—" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }
}
