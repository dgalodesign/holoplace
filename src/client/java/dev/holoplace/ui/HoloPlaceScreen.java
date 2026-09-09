package dev.holoplace.ui;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceKeys;
import dev.holoplace.SchematicImport;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.capture.CaptureController;
import dev.holoplace.capture.SelectionState;
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
import net.minecraft.client.gui.components.MultiLineTextWidget;
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
 * The {@code K} screen. Two tabs — <b>Build</b> (load a schematic, position it, build from it) and
 * <b>Create</b> (select an area of the world, save it as a {@code .litematic}) — the mod's two jobs,
 * visible from the first open. The Build tab is a status header + DISPLAY / BUILD ASSIST / SCHEMATICS
 * sections and a collapsible ADVANCED block. Content taller than the window scrolls with the wheel.
 */
public final class HoloPlaceScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int MARGIN = 10;
    private static final int ROW = 22;          // one checkbox / button row, with breathing room
    private static final int SECT_GAP = 8;      // extra space before a section header
    private static final int LIST_ROWS = 5;
    private static final int LIST_ROW_H = 14;

    private static final int TAB_BUILD = 0;
    private static final int TAB_CREATE = 1;

    private static boolean advancedOpen;
    private static int activeTab = TAB_BUILD;
    private static int scrollY;

    private final List<int[]> rules = new ArrayList<>();   // {x0, y0, x1, y1, argb}
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

        // ---- tab bar -------------------------------------------------
        int tw = (PANEL_W - 4) / 2;
        tab(x, y, tw, "holoplace.ui.tab.build", TAB_BUILD);
        tab(x + tw + 4, y, tw, "holoplace.ui.tab.create", TAB_CREATE);
        int accentX = activeTab == TAB_BUILD ? x : x + tw + 4;
        rules.add(new int[] {accentX, y + 20, accentX + tw, y + 22, 0xFF5EE7FF});
        y += 28;

        y = activeTab == TAB_CREATE
                ? createTab(x, y)
                : buildTab(g, pc, cfg, x, y, loaded);

        y += 8;
        button(x + PANEL_W - 100, y, 100, 20, CommonComponents.GUI_DONE, b -> onClose(), null);
        y += 26;

        this.contentBottom = y + scrollY;
        int maxScroll = Math.max(0, this.contentBottom + MARGIN - this.height);
        int clamped = Mth.clamp(scrollY, 0, maxScroll);
        if (clamped != scrollY) {
            scrollY = clamped;
            rebuildWidgets();
        }
    }

    // ---- BUILD tab -------------------------------------------------------

    private int buildTab(GhostState g, PlacementController pc, HoloPlaceConfig cfg, int x, int y, boolean loaded) {
        if (loaded) {
            label(x, y, "§b❖ §f" + safe(g.sourceName()));
            y += 13;
            label(x, y, "§7" + sizeStr(g) + "   §7" + tr("holoplace.hud.rot") + " §f" + rotLabel(g.rotation())
                    + "   §7" + tr("holoplace.hud.mirror") + " §f" + mirrorLabel(g.mirror()));
            y += 17;
            button(x, y, 110, 18,
                    Component.translatable(g.isVisible() ? "holoplace.ui.hide" : "holoplace.ui.show"),
                    b -> { g.setVisible(!g.isVisible()); rebuildWidgets(); }, null);
            y += 24;
        } else {
            y += wrapLabel(x, y, "holoplace.ui.pick_prompt", 0xA0A0A0) + 8;
        }

        // ---- DISPLAY -------------------------------------------------
        y = section(x, y, "holoplace.ui.sect.display");
        addRenderableWidget(new OpacitySlider(x, y, PANEL_W));
        y += 26;

        int rx = x;
        label(rx, y + 5, "§7" + tr("holoplace.ui.rotate"));
        rx += this.font.width(tr("holoplace.ui.rotate")) + 8;
        button(rx, y, 38, 18, Component.literal("-90°"),
                b -> { pc.rotate(false); rebuildWidgets(); }, tip("holoplace.tip.rotate")).active = loaded;
        rx += 42;
        button(rx, y, 38, 18, Component.literal("+90°"),
                b -> { pc.rotate(true); rebuildWidgets(); }, tip("holoplace.tip.rotate")).active = loaded;
        button(x + PANEL_W - 122, y, 78, 18,
                Component.literal(tr("holoplace.ui.mirror_label") + ": " + mirrorLabel(g.mirror())),
                b -> { pc.cycleMirror(); rebuildWidgets(); }, tip("holoplace.tip.mirror")).active = loaded;
        button(x + PANEL_W - 40, y, 40, 18, Component.translatable("holoplace.ui.reset"),
                b -> { pc.resetTransform(); rebuildWidgets(); }, null).active = loaded;
        y += 26;

        checkKey(x, y, "holoplace.ui.see_through", "holoplace.tip.see_through", HoloPlaceKeys.SEE_THROUGH,
                g.seeThrough(), v -> { g.setSeeThrough(v); cfg.seeThrough = v; HoloPlaceConfig.save(); }, true);
        y += ROW;

        // ---- BUILD ASSIST -----------------------------------------
        y = section(x, y, "holoplace.ui.sect.buildassist");
        checkKey(x, y, "holoplace.ui.hide_placed", "holoplace.tip.hide_placed", HoloPlaceKeys.BUILD_ASSIST,
                g.hideMatched(), v -> { g.setHideMatched(v); cfg.hideMatched = v; HoloPlaceConfig.save(); }, true);
        y += 19;
        if (g.hideMatched() && g.totalBlocks() > 0 && g.matchedBlocks() >= 0) {
            int placed = g.matchedBlocks();
            int total = g.totalBlocks();
            int pct = Math.round(placed * 100f / total);
            label(x + 18, y, "§8" + tr("holoplace.ui.placed", placed, total, pct));
            y += 14;
            if (dev.holoplace.render.GhostRenderer.buried()) {
                label(x + 18, y, "§e" + tr("holoplace.hud.buried"));
                y += 14;
            } else {
                int wrong = dev.holoplace.render.GhostRenderer.wrongMarkers();
                int wrongAll = dev.holoplace.render.GhostRenderer.wrongTotal();
                int extra = dev.holoplace.render.GhostRenderer.extraMarkers();
                String wrongStr = wrongAll > wrong ? wrong + "§8/" + wrongAll + "§c" : String.valueOf(wrong);
                if (wrongAll > 0 || extra > 0) {
                    label(x + 18, y, (wrongAll > 0 ? "§c" + wrongStr + " " + tr("holoplace.hud.wrong") : "")
                            + (wrongAll > 0 && extra > 0 ? "§8  ·  " : "")
                            + (extra > 0 ? "§6" + extra + " " + tr("holoplace.hud.extra") : ""));
                    y += 14;
                }
            }
        }
        y = layers(g, pc, x, y);

        // ---- SCHEMATICS -----------------------------------------
        y = section(x, y, "holoplace.ui.sect.schematics");
        List<Path> files = SchematicLibrary.list();
        SchematicMeta.retainOnly(files);
        int listH = Mth.clamp(Math.max(files.size(), 1), 1, LIST_ROWS) * LIST_ROW_H + 4;
        this.list = new SchematicList(this.minecraft, PANEL_W, listH, y, LIST_ROW_H, files, currentFile());
        this.list.updateSizeAndPosition(PANEL_W, listH, x, y);
        addRenderableWidget(this.list);
        y += listH + 5;
        y += wrapLabel(x, y, "holoplace.ui.drop_hint", 0x808080) + 4;
        button(x, y, PANEL_W, 18, Component.translatable("holoplace.ui.open_folder"),
                b -> Util.getPlatform().openPath(SchematicLibrary.primaryDir()), null);
        y += 24;

        // ---- ADVANCED (collapsible) ---------------------------
        button(x, y, 124, 16,
                Component.literal("§7" + (advancedOpen ? "▾ " : "▸ ") + tr("holoplace.ui.advanced")),
                b -> { advancedOpen = !advancedOpen; rebuildWidgets(); }, null);
        y += 20;
        if (advancedOpen) {
            check(x, y, "holoplace.ui.match_block_only", "holoplace.tip.match_block_only",
                    g.matchBlockOnly(),
                    v -> { g.setMatchBlockOnly(v); cfg.matchBlockOnly = v; HoloPlaceConfig.save(); }, true);
            y += ROW;
            check(x, y, "holoplace.ui.details", "holoplace.tip.details",
                    g.blockEntityModels() && g.showEntities(), v -> {
                        g.setBlockEntityModels(v);
                        g.setShowEntities(v);
                        cfg.blockEntityModels = v;
                        cfg.showEntities = v;
                        HoloPlaceConfig.save();
                    }, true);
            y += ROW;
            addRenderableWidget(new MarkerOpacitySlider(x, y, PANEL_W));
            y += 22;
        }
        return y;
    }

    // ---- CREATE tab ---------------------------------------------------

    private int createTab(int x, int y) {
        CaptureController cc = CaptureController.get();
        SelectionState sel = cc.selection();
        boolean selecting = cc.isSelecting();

        y += wrapLabel(x, y, "holoplace.ui.create.intro", 0xA0A0A0) + 8;

        button(x, y, PANEL_W, 18,
                Component.translatable(selecting ? "holoplace.ui.create.selecting" : "holoplace.ui.create.select_area"),
                b -> { cc.toggleSelecting(); if (cc.isSelecting()) rebuildWidgets(); else onClose(); },
                tip("holoplace.tip.capture_select"));
        y += 22;
        y += wrapLabel(x, y, "holoplace.ui.create.select_hint", 0x808080) + 8;

        BlockPos c1 = sel.corner1();
        BlockPos c2 = sel.corner2();
        label(x, y, "§7" + tr("holoplace.hud.capture_c1") + " " + cornerStr(c1));
        y += 13;
        label(x, y, "§7" + tr("holoplace.hud.capture_c2") + " " + cornerStr(c2));
        y += 13;
        if (sel.isComplete()) {
            Vec3i s = sel.size();
            label(x, y, "§7" + tr("holoplace.hud.capture_size") + " §f"
                    + s.getX() + "×" + s.getY() + "×" + s.getZ()
                    + " §8(" + sel.volume() + " " + tr("holoplace.hud.capture_cells") + ")");
            y += 15;
        }
        if (c1 != null || c2 != null) {
            button(x, y, 130, 16, Component.translatable("holoplace.ui.create.clear"),
                    b -> { cc.clearSelection(); rebuildWidgets(); }, null);
            y += 22;
        }

        y += 6;
        label(x, y, "§7" + tr("holoplace.ui.create.name"));
        y += 12;
        EditBox name = new EditBox(this.font, x, y, PANEL_W - 80, 18, Component.empty());
        name.setMaxLength(48);
        name.setHint(Component.translatable("holoplace.ui.capture_name_hint"));
        addRenderableWidget(name);
        button(x + PANEL_W - 76, y, 76, 18, Component.translatable("holoplace.ui.capture_save"),
                b -> { cc.save(name.getValue().isBlank() ? null : name.getValue()); onClose(); }, null)
                .active = sel.isComplete();
        y += 24;
        return y;
    }

    private static String cornerStr(@Nullable BlockPos p) {
        return p == null ? "§8—" : "§f" + p.getX() + " " + p.getY() + " " + p.getZ();
    }

    // ---- builders --------------------------------------------------------

    private void tab(int x, int y, int w, String key, int which) {
        boolean on = activeTab == which;
        button(x, y, w, 20, Component.literal((on ? "§f§l" : "§7") + tr(key)),
                bt -> {
                    if (activeTab != which) {
                        activeTab = which;
                        scrollY = 0;
                        rebuildWidgets();
                    }
                }, null);
    }

    private int section(int x, int y, String key) {
        y += SECT_GAP;
        String text = tr(key).toUpperCase(Locale.ROOT);
        label(x, y, "§7" + text);
        rules.add(new int[] {x + this.font.width(text) + 8, y + 4, x + PANEL_W, y + 5, 0x30FFFFFF});
        return y + 16;
    }

    private void label(int x, int y, String text) {
        StringWidget widget = new StringWidget(x, y, Math.min(this.font.width(text), PANEL_W), 9,
                Component.literal(text), this.font);
        widget.setMaxWidth(PANEL_W, StringWidget.TextOverflow.CLAMPED);
        addRenderableWidget(widget);
    }

    /** A sentence that wraps to the panel width. Returns the height it took so the caller can advance. */
    private int wrapLabel(int x, int y, String key, int color) {
        MultiLineTextWidget w = new MultiLineTextWidget(x, y,
                Component.translatable(key).withStyle(s -> s.withColor(color)), this.font);
        w.setMaxWidth(PANEL_W);
        addRenderableWidget(w);
        return w.getHeight();
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
            label(x, y + 2, "§7" + tr("holoplace.ui.layer_single"));
            return y + 18;
        }
        int hi = g.layerClip() && g.layerMax() != Integer.MAX_VALUE
                ? Math.min(g.layerMax(), layers - 1) : layers - 1;
        int lo = g.layerClip() ? Math.min(g.layerMin(), hi) : 0;

        label(x, y + 5, "§7" + tr("holoplace.ui.layers_label")
                + " §f" + layerSpan(lo, hi) + " §7/ " + layers);
        button(x + PANEL_W - 46, y, 46, 16, Component.translatable("holoplace.ui.all"),
                b -> { pc.clearLayers(); rebuildWidgets(); }, null).active = g.layerClip();
        y += 20;

        int[] range = {lo, hi};
        addRenderableWidget(new LayerSlider(x, y, PANEL_W, tr("holoplace.ui.layer_from"), layers - 1, range[0],
                v -> { range[0] = Math.min(v, range[1]); pc.setLayers(range[0], range[1]); }));
        y += 19;
        addRenderableWidget(new LayerSlider(x, y, PANEL_W, tr("holoplace.ui.layer_to"), layers - 1, range[1],
                v -> { range[1] = Math.max(v, range[0]); pc.setLayers(range[0], range[1]); }));
        return y + 22;
    }

    private static String layerSpan(int lo, int hi) {
        return lo == hi ? String.valueOf(lo) : lo + "–" + hi;
    }

    // ---- render override: section rules + hover metadata ----------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The in-world menu background is a light tint that doesn't hold up over bright terrain;
        // dim the whole screen and darken a band behind the panel so the text stays readable.
        int px = (this.width - PANEL_W) / 2;
        graphics.fill(0, 0, this.width, this.height, 0x8C0B0B10);
        graphics.fill(px - 10, 0, px + PANEL_W + 10, this.height, 0x66000008);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        for (int[] r : rules) {
            graphics.fill(r[0], r[1], r[2], r[3], r[4]);
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

    /** Build-assist marker opacity — its own control, separate from the ghost opacity. */
    private static final class MarkerOpacitySlider extends AbstractSliderButton {
        MarkerOpacitySlider(int x, int y, int width) {
            super(x, y, width, 16, Component.empty(),
                    Mth.clamp((GhostState.get().markerOpacity() - 0.15) / 0.85, 0.0, 1.0));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(tr("holoplace.ui.marker_opacity",
                    Math.round(GhostState.get().markerOpacity() * 100)) + "%"));
        }

        @Override
        protected void applyValue() {
            GhostState.get().setMarkerOpacity((float) (0.15 + this.value * 0.85));
            HoloPlaceConfig.get().markerOpacity = GhostState.get().markerOpacity();
            HoloPlaceConfig.save();
        }
    }
}
