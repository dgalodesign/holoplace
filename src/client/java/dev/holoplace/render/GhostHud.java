package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.HoloPlaceKeys;
import dev.holoplace.capture.CaptureHud;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.PlacementTransform;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The on-screen status readout: the ghost / build panel and, stacked directly below it, the capture
 * panel — two visually separate boxes sharing one corner. The corner is
 * {@link HoloPlaceConfig#hudCorner} (0 = top-left … 3 = bottom-left); whichever panel is showing
 * sits at that corner, and if both show they stack.
 */
public final class GhostHud {

    private static final int MARGIN = 4;
    private static final int GAP = 4;
    private static final int PAD = 3;
    private static final int GHOST_BG = 0xA0100010;
    private static final int CAPTURE_BG = 0xA0001018;

    private GhostHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                HoloPlaceClient.id("status"), (graphics, deltaTracker) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<String> ghost = GhostState.get().isVisible() ? buildGhostLines(mc) : null;
        List<String> capture = CaptureHud.lines();
        if (ghost == null && capture == null) {
            return;
        }

        int lineH = font.lineHeight + 1;
        List<Panel> stack = new ArrayList<>(2);
        if (ghost != null) {
            stack.add(new Panel(ghost, measureWidth(font, ghost) + PAD * 2,
                    ghost.size() * lineH + PAD * 2 - 1, GHOST_BG));
        }
        if (capture != null) {
            stack.add(new Panel(capture, measureWidth(font, capture) + PAD * 2,
                    capture.size() * lineH + PAD * 2 - 1, CAPTURE_BG));
        }

        int corner = Mth.clamp(HoloPlaceConfig.get().hudCorner, 0, 3);
        boolean right = corner == 1 || corner == 2;
        boolean bottom = corner == 2 || corner == 3;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int totalH = -GAP;
        for (Panel p : stack) {
            totalH += p.h + GAP;
        }

        int y = bottom ? sh - MARGIN - totalH : MARGIN;
        for (Panel p : stack) {
            int x = right ? sw - MARGIN - p.w : MARGIN;
            graphics.fill(x, y, x + p.w, y + p.h, p.bg);
            int ty = y + PAD;
            for (String line : p.lines) {
                graphics.text(font, Component.literal(line), x + PAD, ty, 0xFFFFFFFF, true);
                ty += lineH;
            }
            y += p.h + GAP;
        }
    }

    private record Panel(List<String> lines, int w, int h, int bg) {
    }

    private static int measureWidth(Font font, List<String> lines) {
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, font.width(Component.literal(line)));
        }
        return w;
    }

    private static List<String> buildGhostLines(Minecraft mc) {
        GhostState state = GhostState.get();
        BlockPos a = state.anchor();
        PlacementTransform t = state.transform();
        boolean grabbing = PlacementController.get().isGrabbing();
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
            int wrong = GhostRenderer.wrongMarkers();
            int wrongAll = GhostRenderer.wrongTotal();
            int extra = GhostRenderer.extraMarkers();
            String wrongStr = wrongAll > wrong ? wrong + "§8/" + wrongAll + "§c" : String.valueOf(wrong);
            if (wrongAll > 0 || extra > 0) {
                progress += "  " + (wrongAll > 0 ? "§c" + wrongStr + " " + text("holoplace.hud.wrong") : "")
                        + (wrongAll > 0 && extra > 0 ? "  " : "")
                        + (extra > 0 ? "§6" + extra + " " + text("holoplace.hud.extra") : "");
            }
        }

        List<String> lines = new ArrayList<>();
        if (!grabbing) {
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
        return lines;
    }

    /** Cycle the anchor corner and persist. */
    public static void cycleCorner() {
        HoloPlaceConfig cfg = HoloPlaceConfig.get();
        cfg.hudCorner = (Mth.clamp(cfg.hudCorner, 0, 3) + 1) % 4;
        HoloPlaceConfig.save();
    }

    public static String cornerLabel() {
        return switch (Mth.clamp(HoloPlaceConfig.get().hudCorner, 0, 3)) {
            case 1 -> text("holoplace.ui.corner_tr");
            case 2 -> text("holoplace.ui.corner_br");
            case 3 -> text("holoplace.ui.corner_bl");
            default -> text("holoplace.ui.corner_tl");
        };
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
