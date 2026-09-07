package dev.holoplace.ui;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceKeys;
import dev.holoplace.SchematicImport;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.capture.CaptureController;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.SchematicMeta;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * One screen for everything, in sections: a status header, then DISPLAY, BUILD ASSIST, SCHEMATICS,
 * CREATE, and a collapsible ADVANCED block. No tabs, no nested menus. If the content is taller than
 * the window it scrolls with the wheel.
 */
public final class HoloPlaceScreen extends Screen {

    private static final int PANEL_W = 316;
    private static final int MARGIN = 8;
    private static final int LIST_ROWS = 4;
    private static final int LIST_ROW_H = 12;

    private static boolean advancedOpen;
    private static int scrollY;

    private final List<int[]> rules = new ArrayList<>();
    private @Nullable SchematicList list;
    private int contentBottom;

    public HoloPlaceScreen() {
        super(Component.literal("HoloPlace"));
    }

    @Override
    protected void init() {
        rules.clear();
        GhostState g = GhostState.get();
        PlacementController pc = PlacementController.get();
        HoloPlaceConfig cfg = HoloPlaceConfig.get();
        boolean loaded = g.schematic() != null;

        int x = (this.width - PANEL_W) / 2;
        int y = MARGIN - scrollY;

        // ---- header ----------------------------------------------------
        if (loaded) {
            label(x, y, "§b❖ " + safe(g.sourceName()));
            y += 11;
            label(x, y, "§7" + sizeStr(g) + "  §7" + tr("holoplace.hud.rot") + " §f" + rotLabel(g.rotation())
                    + "  §7" + tr("holoplace.hud.mirror") + " §f" + mirrorLabel(g.mirror()));
            y += 12;
            button(x, y, 150, 16,
                    Component.translatable(g.isVisible() ? "holoplace.ui.hide" : "holoplace.ui.show"),
                    b -> { g.setVisible(!g.isVisible()); rebuildWidgets(); }, null);
            y += 20;
        } else {
            label(x, y, "§7" + tr("holoplace.ui.pick_prompt"));
            y += 15;
        }
        y += 3;

        // ---- DISPLAY -------------------------------------------------
        y = section(x, y, "holoplace.ui.sect.display");
        addRenderableWidget(new OpacitySlider(x, y, PANEL_W));
        y += 22;

        int rx = x;
        label(rx, y + 5, "§7" + tr("holoplace.ui.rotate"));
        rx += this.font.width(tr("holoplace.ui.rotate")) + 6;
        button(rx, y, 20, 18, Component.literal("↺"),
                b -> { pc.rotate(false); rebuildWidgets(); }, tip("holoplace.tip.rotate")).active = loaded;
        rx += 22;
        label(rx, y + 5, "§f" + rotLabel(g.rotation()));
        rx += 34;
        button(rx, y, 20, 18, Component.literal("↻"),
                b -> { pc.rotate(true); rebuildWidgets(); }, tip("holoplace.tip.rotate")).active = loaded;
        button(x + PANEL_W - 116, y, 74, 18,
                Component.literal(tr("holoplace.ui.mirror_label") + " " + mirrorLabel(g.mirror())),
                b -> { pc.cycleMirror(); rebuildWidgets(); }, tip("holoplace.tip.mirror")).active = loaded;
        button(x + PANEL_W - 40, y, 40, 18, Component.translatable("holoplace.ui.reset"),
                b -> { pc.resetTransform(); rebuildWidgets(); }, null).active = loaded;
        y += 22;

        checkKey(x, y, "holoplace.ui.see_through", "holoplace.tip.see_through", HoloPlaceKeys.SEE_THROUGH,
                g.seeThrough(), v -> { g.setSeeThrough(v); cfg.seeThrough = v; HoloPlaceConfig.save(); }, true);
        y += 18;
        check(x, y, "holoplace.ui.shading", "holoplace.tip.shading",
                g.shade(), v -> { g.setShade(v); cfg.ambientOcclusion = v; HoloPlaceConfig.save(); }, true);
        y += 18;
        y += 3;

        // ---- BUILD ASSIST -----------------------------------------
        y = section(x, y, "holoplace.ui.sect.buildassist");
        checkKey(x, y, "holoplace.ui.hide_placed", "holoplace.tip.hide_placed", HoloPlaceKeys.BUILD_ASSIST,
                g.hideMatched(), v -> { g.setHideMatched(v); cfg.hideMatched = v; HoloPlaceConfig.save(); }, true);
        y += 17;
        if (g.hideMatched() && g.totalBlocks() > 0 && g.matchedBlocks() >= 0) {
            int placed = g.matchedBlocks();
            int total = g.totalBlocks();
            int pct = Math.round(placed * 100f / total);
            label(x + 16, y, "§8" + tr("holoplace.ui.placed", placed, total, pct));
            y += 11;
        }
        check(x + 16, y, "holoplace.ui.match_block_only", "holoplace.tip.match_block_only",
                g.matchBlockOnly(), v -> { g.setMatchBlockOnly(v); cfg.matchBlockOnly = v; HoloPlaceConfig.save(); },
                g.hideMatched());
        y += 17;
        check(x + 16, y, "holoplace.ui.hide_wrong_too", "holoplace.tip.hide_wrong_too",
                g.hideWrongToo(), v -> { g.setHideWrongToo(v); cfg.hideWrongToo = v; HoloPlaceConfig.save(); },
                g.hideMatched());
        y += 17;
        y = layers(g, pc, x, y);
        y += 3;

        // ---- SCHEMATICS -----------------------------------------
        y = section(x, y, "holoplace.ui.sect.schematics");
        List<Path> files = SchematicLibrary.list();
        SchematicMeta.retainOnly(files);
        int listH = LIST_ROWS * LIST_ROW_H;
        this.list = new SchematicList(this.minecraft, PANEL_W, listH, y, LIST_ROW_H, files, currentFile());
        this.list.updateSizeAndPosition(PANEL_W, listH, x, y);
        addRenderableWidget(this.list);
        y += listH + 3;
        button(x, y, PANEL_W, 16, Component.translatable("holoplace.ui.open_folder"),
                b -> Util.getPlatform().openPath(SchematicLibrary.primaryDir()), null);
        y += 20;
        y += 3;

        // ---- CREATE --------------------------------------------
        y = section(x, y, "holoplace.ui.sect.create");
        CaptureController cc = CaptureController.get();
        EditBox name = new EditBox(this.font, x + 92, y, PANEL_W - 92 - 52, 18, Component.empty());
        name.setMaxLength(48);
        name.setHint(Component.translatable("holoplace.ui.capture_name_hint"));
        button(x, y, 88, 18, Component.translatable("holoplace.ui.capture_select"),
                b -> { cc.toggleSelecting(); onClose(); }, tip("holoplace.tip.capture_select"));
        addRenderableWidget(name);
        button(x + PANEL_W - 48, y, 48, 18, Component.translatable("holoplace.ui.capture_save"),
                b -> { cc.save(name.getValue().isBlank() ? null : name.getValue()); onClose(); }, null);
        y += 22;
        y += 3;

        // ---- ADVANCED (collapsible) ---------------------------
        button(x, y, 120, 14,
                Component.literal("§7" + (advancedOpen ? "▾ " : "▸ ") + tr("holoplace.ui.advanced")),
                b -> { advancedOpen = !advancedOpen; rebuildWidgets(); }, null);
        y += 16;
        if (advancedOpen) {
            BlockPos a = g.anchor();
            EditBox bx = coordBox(x + 24, y, String.valueOf(a.getX()));
            EditBox by = coordBox(x + 74, y, String.valueOf(a.getY()));
            EditBox bz = coordBox(x + 124, y, String.valueOf(a.getZ()));
            label(x, y + 5, "§7" + tr("holoplace.ui.move"));
            addRenderableWidget(bx);
            addRenderableWidget(by);
            addRenderableWidget(bz);
            button(x + 176, y, 40, 18, Component.translatable("holoplace.ui.go"), b -> {
                Integer ix = parseInt(bx.getValue());
                Integer iy = parseInt(by.getValue());
                Integer iz = parseInt(bz.getValue());
                if (ix != null && iy != null && iz != null) {
                    pc.moveTo(ix, iy, iz);
                    rebuildWidgets();
                }
            }, null).active = loaded;
            y += 21;
            check(x, y, "holoplace.ui.block_entity_models", "holoplace.tip.block_entity_models",
                    g.blockEntityModels(),
                    v -> { g.setBlockEntityModels(v); cfg.blockEntityModels = v; HoloPlaceConfig.save(); }, true);
            y += 17;
            check(x, y, "holoplace.ui.entities", "holoplace.tip.entities",
                    g.showEntities(),
                    v -> { g.setShowEntities(v); cfg.showEntities = v; HoloPlaceConfig.save(); }, true);
            y += 18;
        }
        y += 6;

        button(x + PANEL_W - 100, y, 100, 18, CommonComponents.GUI_DONE, b -> onClose(), null);
        y += 22;

        this.contentBottom = y + scrollY;
        int maxScroll = Math.max(0, this.contentBottom + MARGIN - this.height);
        int clamped = Mth.clamp(scrollY, 0, maxScroll);
        if (clamped != scrollY) {
            scrollY = clamped;
            rebuildWidgets();
        }
    }

    // ---- builders --------------------------------------------------------

    private int section(int x, int y, String key) {
        String text = tr(key).toUpperCase(Locale.ROOT);
        label(x, y, "§7" + text);
        rules.add(new int[] {x + this.font.width(text) + 6, y + 4, x + PANEL_W});
        return y + 13;
    }

    private void label(int x, int y, String text) {
        addRenderableWidget(new StringWidget(x, y, this.font.width(text), 9, Component.literal(text), this.font));
    }

    private Button button(int x, int y, int w, int h, Component text, Button.OnPress onPress,
                          @Nullable Tooltip tooltip) {
        return addRenderableWidget(Button.builder(text, onPress).bounds(x, y, w, h).tooltip(tooltip).build());
    }

    private Checkbox check(int x, int y, String labelKey, String tipKey, boolean selected,
                           Consumer<Boolean> set, boolean enabled) {
        Checkbox c = Checkbox.builder(Component.translatable(labelKey), this.font)
                .pos(x, y).selected(selected).maxWidth(PANEL_W - 8)
                .tooltip(tip(tipKey))
                .onValueChange((box, v) -> set.accept(v))
                .build();
        c.active = enabled;
        return addRenderableWidget(c);
    }

    /** Checkbox with a {@code [key]} chip drawn after the label. */
    private void checkKey(int x, int y, String labelKey, String tipKey, KeyMapping key,
                          boolean selected, Consumer<Boolean> set, boolean enabled) {
        Checkbox c = check(x, y, labelKey, tipKey, selected, set, enabled);
        if (!key.isUnbound()) {
            label(c.getX() + c.getWidth() + 4, y + 4,
                    "§8[§7" + key.getTranslatedKeyMessage().getString() + "§8]");
        }
    }

    private @Nullable Tooltip tip(@Nullable String key) {
        return key == null ? null : Tooltip.create(Component.translatable(key));
    }

    private int layers(GhostState g, PlacementController pc, int x, int y) {
        PlacementTransform t = g.transform();
        int layers = t == null ? 0 : t.footprintY();
        if (layers <= 1) {
            label(x, y, "§7" + tr("holoplace.ui.layer_single"));
            return y + 12;
        }
        int hi = g.layerClip() && g.layerMax() != Integer.MAX_VALUE
                ? Math.min(g.layerMax(), layers - 1) : layers - 1;
        int lo = g.layerClip() ? Math.min(g.layerMin(), hi) : 0;

        label(x, y + 4, "§7" + tr("holoplace.ui.layers_label")
                + " §f" + layerSpan(lo, hi) + " §7/ " + layers);
        button(x + PANEL_W - 44, y, 44, 16, Component.translatable("holoplace.ui.all"),
                b -> { pc.clearLayers(); rebuildWidgets(); }, null).active = g.layerClip();
        y += 17;

        int[] range = {lo, hi};
        addRenderableWidget(new LayerSlider(x, y, PANEL_W, tr("holoplace.ui.layer_from"), layers - 1, range[0],
                v -> { range[0] = Math.min(v, range[1]); pc.setLayers(range[0], range[1]); }));
        y += 16;
        addRenderableWidget(new LayerSlider(x, y, PANEL_W, tr("holoplace.ui.layer_to"), layers - 1, range[1],
                v -> { range[1] = Math.max(v, range[0]); pc.setLayers(range[0], range[1]); }));
        return y + 18;
    }

    private static String layerSpan(int lo, int hi) {
        return lo == hi ? String.valueOf(lo) : lo + "–" + hi;
    }

    private EditBox coordBox(int x, int y, String value) {
        EditBox box = new EditBox(this.font, x, y, 46, 18, Component.empty());
        box.setMaxLength(8);
        box.setValue(value);
        return box;
    }

    // ---- render override: section rules + hover metadata ----------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        for (int[] r : rules) {
            graphics.fill(r[0], r[1], r[2], r[1] + 1, 0x30FFFFFF);
        }
        if (this.list != null) {
            SchematicList.Row row = this.list.hoveredRow();
            if (row != null) {
                graphics.setTooltipForNextFrame(this.font, metaLines(row.file), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<Component> metaLines(Path file) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§f" + bareName(file)));
        SchematicMeta meta = SchematicMeta.peek(file);
        if (meta == null) {
            lines.add(Component.translatable("holoplace.ui.meta.loading").withStyle(s -> s.withColor(0xA0A0A0)));
            return lines;
        }
        if (meta.error != null) {
            lines.add(Component.translatable("holoplace.ui.meta.error").withStyle(s -> s.withColor(0xFF6060)));
            return lines;
        }
        if (meta.size != null) {
            Vec3i s = meta.size;
            lines.add(Component.literal("§7" + tr("holoplace.hud.size") + " §f"
                    + s.getX() + "×" + s.getY() + "×" + s.getZ()));
        }
        if (meta.blocks >= 0) {
            lines.add(Component.literal("§7" + tr("holoplace.ui.meta.blocks") + " §f" + meta.blocks));
        }
        if (meta.regions > 1) {
            lines.add(Component.literal("§7" + tr("holoplace.ui.meta.regions") + " §f" + meta.regions));
        }
        if (meta.dataVersion > 0) {
            lines.add(Component.literal("§8" + tr("holoplace.ui.meta.mc") + " " + meta.dataVersion));
        }
        return lines;
    }

    // ---- scroll ------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double dy) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, dy)) {
            return true;
        }
        int maxScroll = Math.max(0, this.contentBottom + MARGIN - this.height);
        if (maxScroll == 0) {
            return false;
        }
        scrollY = Mth.clamp(scrollY - (int) Math.round(dy) * 16, 0, maxScroll);
        rebuildWidgets();
        return true;
    }

    // ---- misc -----------------------------------------------------

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    private static @Nullable Path currentFile() {
        String last = HoloPlaceConfig.get().lastSchematic;
        return last == null || last.isBlank() ? null : SchematicLibrary.resolve(last).orElse(null);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String safe(@Nullable String s) {
        return s == null ? "?" : s;
    }

    private static String sizeStr(GhostState g) {
        PlacementTransform t = g.transform();
        return t == null ? "" : t.footprintX() + "×" + t.footprintY() + "×" + t.footprintZ();
    }

    private static @Nullable Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String bareName(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".litematic")
                ? name.substring(0, name.length() - 10) : name;
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

    // ---- schematic list --------------------------------------------

    private final class SchematicList extends ObjectSelectionList<SchematicList.Row> {

        SchematicList(Minecraft mc, int width, int height, int y, int itemHeight,
                      List<Path> files, @Nullable Path current) {
            super(mc, width, height, y, itemHeight);
            this.centerListVertically = false;
            for (Path f : files) {
                Row row = new Row(f);
                addEntry(row);
                if (f.equals(current)) {
                    setSelected(row);
                }
            }
        }

        @Nullable Row hoveredRow() {
            return getHovered();
        }

        @Override
        public int getRowWidth() {
            return this.width - 8;
        }

        final class Row extends ObjectSelectionList.Entry<Row> {
            final Path file;
            private final String label;

            Row(Path file) {
                this.file = file;
                this.label = bareName(file);
            }

            @Override
            public Component getNarration() {
                return Component.literal(label);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       boolean hovered, float a) {
                int ty = getY() + (getHeight() - 9) / 2;
                boolean current = getSelected() == this;
                int color = current ? 0xFF7DE8FF : hovered ? 0xFFFFFFFF : 0xFFB8B8B8;
                graphics.text(HoloPlaceScreen.this.font, Component.literal(label), getX() + 3, ty, color, false);
                if (current) {
                    graphics.text(HoloPlaceScreen.this.font, Component.literal("●"),
                            getX() + getWidth() - 14, ty, 0xFF66D16B, false);
                }
                if (hovered) {
                    SchematicMeta.peek(file);
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                onClose();
                SchematicImport.show(file, true);
                return true;
            }
        }
    }

    // ---- sliders -------------------------------------------------

    private static final class LayerSlider extends AbstractSliderButton {
        private final String name;
        private final int max;
        private final IntConsumer onChange;

        LayerSlider(int x, int y, int width, String name, int max, int initial, IntConsumer onChange) {
            super(x, y, width, 16, Component.empty(),
                    max == 0 ? 0.0 : Mth.clamp((double) initial / max, 0.0, 1.0));
            this.name = name;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private int current() {
            return (int) Math.round(this.value * max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + current()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
            updateMessage();
        }
    }

    private static final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(),
                    Mth.clamp((GhostState.get().opacity() - 0.05) / 0.95, 0.0, 1.0));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(
                    tr("holoplace.ui.opacity", Math.round(GhostState.get().opacity() * 100)) + "%"));
        }

        @Override
        protected void applyValue() {
            GhostState.get().setOpacity((float) (0.05 + this.value * 0.95));
            HoloPlaceConfig.get().opacity = GhostState.get().opacity();
            HoloPlaceConfig.save();
        }
    }
}
