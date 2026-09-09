package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.HoloPlaceKeys;
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
        String name = state.sourceName() == null ? "?" : state.sourceName();

        String progress = null;
        if (state.matchedBlocks() == GhostRenderer.MESH_BAKING) {
            progress = "§b" + text("holoplace.hud.baking");
        } else if (state.matchedBlocks() == -2) {
            progress = "§c" + text("holoplace.hud.too_large");
        } else if (state.hideMatched() && state.totalBlocks() > 0) {
            int placed = state.matchedBlocks();
            int total = state.totalBlocks();
            int pct = total == 0 ? 0 : Math.round(placed * 100f / total);
            progress = "§7" + text("holoplace.hud.build") + " §a" + placed + "§7/§f" + total + " §8(" + pct + "%)"
                    + (state.matchBlockOnly() ? " §8[" + text("holoplace.hud.block_only") + "]" : "");
            if (GhostRenderer.buried()) {
                progress += "  §e" + text("holoplace.hud.buried");
            } else {
                int wrong = GhostRenderer.wrongMarkers();
                int extra = GhostRenderer.extraMarkers();
                if (wrong > 0 || extra > 0) {
                    progress += "  " + (wrong > 0 ? "§c" + wrong + " " + text("holoplace.hud.wrong") : "")
                            + (wrong > 0 && extra > 0 ? "  " : "")
                            + (extra > 0 ? "§6" + extra + " " + text("holoplace.hud.extra") : "");
                }
            }
        }

        if (!grabbing) {
            // Locked: keep it out of the way — just the name, and progress if build-assist is on.
            lines.add("§b❖ §f" + name);
            if (progress != null) {
                lines.add(progress);
            }
        } else {
            lines.add("§b❖ HoloPlace  §e[" + text("holoplace.hud.grab") + "]");
            lines.add("§7" + name);
            lines.add("§7" + text("holoplace.hud.pos") + " §f" + a.getX() + " " + a.getY() + " " + a.getZ()
                    + (t == null ? "" : "  §7" + text("holoplace.hud.size") + " §f"
                            + t.footprintX() + "×" + t.footprintY() + "×" + t.footprintZ()));
            lines.add("§7" + text("holoplace.hud.rot") + " §f" + rotLabel(state.rotation())
                    + "  §7" + text("holoplace.hud.mirror") + " §f" + mirrorLabel(state.mirror())
                    + "  §7" + text("holoplace.hud.opacity") + " §f" + Math.round(state.opacity() * 100) + "%"
                    + (state.seeThrough() ? "  §b" + text("holoplace.hud.xray") : ""));
            if (progress != null) {
                lines.add(progress);
            }
            lines.add("§8" + hint());
        }

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

    private static String hint() {
        return Component.translatable("holoplace.hud.hint",
                key(HoloPlaceKeys.TOGGLE_GRAB), key(HoloPlaceKeys.ROTATE), key(HoloPlaceKeys.ROTATE),
                key(HoloPlaceKeys.MIRROR), key(HoloPlaceKeys.SEE_THROUGH), key(HoloPlaceKeys.BUILD_ASSIST),
                key(HoloPlaceKeys.OPEN_PICKER)).getString();
    }

    private static String key(net.minecraft.client.KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
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
