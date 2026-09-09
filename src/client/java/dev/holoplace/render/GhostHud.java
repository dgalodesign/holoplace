package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.HoloPlaceKeys;
import dev.holoplace.config.HoloPlaceConfig;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Always-visible one-panel status readout while a ghost is shown. No nested menus. Position is a
 *  fraction of the free screen space ({@link HoloPlaceConfig#hudX}/{@code hudY}); {@link #placing}
 *  makes it follow the cursor for the "move HUD" mode of the {@code K} screen. */
public final class GhostHud {

    /** When set, the panel follows the cursor instead of its saved position (K-screen "move HUD"). */
    public static boolean placing;

    private GhostHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                HoloPlaceClient.id("status"), (graphics, deltaTracker) -> render(graphics));
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        GhostState state = GhostState.get();
        if (!state.isVisible() && !placing) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<String> lines = new ArrayList<>();
        if (!state.isVisible()) {
            // "Move HUD" mode with nothing loaded — show a representative panel to position.
            lines.add("§b❖ §fHoloPlace");
            lines.add("§7" + text("holoplace.hud.build") + " §a0§7/§f999 §8(0%)");
            drawPanel(graphics, font, lines, mc);
            return;
        }

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

        drawPanel(graphics, font, lines, mc);
    }

    /** Draw the panel at its saved screen position, or under the cursor while {@link #placing}. The
     *  saved position ({@code hudX}/{@code hudY}) is the top-left corner as a fraction of the screen;
     *  it's clamped so the whole panel stays on screen whatever its current size. */
    private static void drawPanel(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Font font,
                                  List<String> lines, Minecraft mc) {
        int pad = 3;
        int lineH = font.lineHeight + 1;
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, font.width(Component.literal(line)));
        }
        int boxW = textW + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 - 1;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int bx;
        int by;
        if (placing) {
            bx = (int) Math.round(mc.mouseHandler.getScaledXPos(mc.getWindow()));
            by = (int) Math.round(mc.mouseHandler.getScaledYPos(mc.getWindow()));
        } else {
            HoloPlaceConfig cfg = HoloPlaceConfig.get();
            bx = Math.round(cfg.hudX * sw);
            by = Math.round(cfg.hudY * sh);
        }
        bx = Mth.clamp(bx, 0, Math.max(0, sw - boxW));
        by = Mth.clamp(by, 0, Math.max(0, sh - boxH));

        graphics.fill(bx, by, bx + boxW, by + boxH, placing ? 0xC0402C00 : 0xA0100010);
        if (placing) {
            graphics.fill(bx, by, bx + boxW, by + 1, 0xFFE0A030);
            graphics.fill(bx, by + boxH - 1, bx + boxW, by + boxH, 0xFFE0A030);
        }
        int ty = by + pad;
        for (String line : lines) {
            graphics.text(font, Component.literal(line), bx + pad, ty, 0xFFFFFFFF, true);
            ty += lineH;
        }
    }

    /** Save the panel's top-left at GUI-scaled {@code (px, py)} as a fraction of the screen, and
     *  leave placing mode. Called by the K screen when the player clicks. */
    public static void commitPlacement(double px, double py) {
        Minecraft mc = Minecraft.getInstance();
        HoloPlaceConfig cfg = HoloPlaceConfig.get();
        cfg.hudX = Mth.clamp((float) (px / mc.getWindow().getGuiScaledWidth()), 0f, 1f);
        cfg.hudY = Mth.clamp((float) (py / mc.getWindow().getGuiScaledHeight()), 0f, 1f);
        HoloPlaceConfig.save();
        placing = false;
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
