package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.PlacementTransform;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Always-visible one-panel status readout while a ghost is shown. No nested menus. */
public final class GhostHud {

    private GhostHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                HoloPlaceClient.id("status"), (graphics, deltaTracker) -> render(graphics));
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        GhostState state = GhostState.get();
        if (!state.isVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        BlockPos a = state.anchor();
        PlacementTransform t = state.transform();
        boolean grabbing = PlacementController.get().isGrabbing();

        List<String> lines = new ArrayList<>();
        lines.add("§b❖ HoloPlace" + (grabbing ? "  §e[grab]" : ""));
        lines.add("§7" + (state.sourceName() == null ? "?" : state.sourceName()));
        lines.add("§7pos §f" + a.getX() + " " + a.getY() + " " + a.getZ()
                + (t == null ? "" : "  §7size §f" + t.footprintX() + "×" + t.footprintY() + "×" + t.footprintZ()));
        lines.add("§7rot §f" + rotLabel(state.rotation()) + "  §7mirror §f" + mirrorLabel(state.mirror())
                + "  §7opacity §f" + Math.round(state.opacity() * 100) + "%"
                + (state.seeThrough() ? "  §bx-ray" : ""));
        if (state.hideMatched() && state.totalBlocks() > 0) {
            int placed = state.matchedBlocks();
            int total = state.totalBlocks();
            int pct = total == 0 ? 0 : Math.round(placed * 100f / total);
            lines.add("§7build §a" + placed + "§7/§f" + total + " §8(" + pct + "%)");
        }
        lines.add("§8G grab · R/⇧R rotate · M mirror · X x-ray · H build · ⎇wheel opacity");

        int pad = 3;
        int lineH = font.lineHeight + 1;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(Component.literal(line)));
        }
        int x = 4;
        int y = 4;
        graphics.fill(x - pad, y - pad, x + width + pad, y + lines.size() * lineH + pad - 1, 0xA0100010);
        for (String line : lines) {
            graphics.text(font, Component.literal(line), x, y, 0xFFFFFFFF, true);
            y += lineH;
        }
    }

    private static String rotLabel(Rotation r) {
        return switch (r) {
            case NONE -> "0°";
            case CLOCKWISE_90 -> "90°";
            case CLOCKWISE_180 -> "180°";
            case COUNTERCLOCKWISE_90 -> "270°";
        };
    }

    private static String mirrorLabel(Mirror m) {
        return switch (m) {
            case NONE -> "—";
            case FRONT_BACK -> "FB";
            case LEFT_RIGHT -> "LR";
        };
    }
}
