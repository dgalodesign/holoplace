package dev.holoplace.ui;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceKeys;
import dev.holoplace.SchematicImport;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.PlacementTransform;
import net.minecraft.client.KeyMapping;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * One flat screen for everything: the schematic list plus every display control. No folders, no
 * nested menus.
 */
public final class HoloPlaceScreen extends Screen {

    private static final int PAGE_SIZE = 10;
    private static final int ROW_WIDTH = 330;

    private static int page;

    public HoloPlaceScreen() {
        super(Component.literal("HoloPlace"));
    }

    @Override
    protected void init() {
        GhostState g = GhostState.get();
        LinearLayout root = LinearLayout.vertical().spacing(3);
        root.addChild(new StringWidget(getTitle(), this.font));
        root.addChild(new OpacitySlider(ROW_WIDTH));

        LinearLayout t1 = LinearLayout.horizontal().spacing(8);
        t1.addChild(check(withKey("See-through", HoloPlaceKeys.SEE_THROUGH), g::seeThrough, v -> {
            g.setSeeThrough(v);
            HoloPlaceConfig.get().seeThrough = v;
            HoloPlaceConfig.save();
        }));
        t1.addChild(check(withKey("Hide placed", HoloPlaceKeys.BUILD_ASSIST), g::hideMatched, v -> {
            g.setHideMatched(v);
            HoloPlaceConfig.get().hideMatched = v;
            HoloPlaceConfig.save();
        }));
        t1.addChild(check("Match block only", g::matchBlockOnly, v -> {
            g.setMatchBlockOnly(v);
            HoloPlaceConfig.get().matchBlockOnly = v;
            HoloPlaceConfig.save();
        }));
        root.addChild(t1);

        LinearLayout t2 = LinearLayout.horizontal().spacing(8);
        t2.addChild(check("Block entity models", g::blockEntityModels, v -> {
            g.setBlockEntityModels(v);
            HoloPlaceConfig.get().blockEntityModels = v;
            HoloPlaceConfig.save();
        }));
        t2.addChild(check("Shading", g::shade, v -> {
            g.setShade(v);
            HoloPlaceConfig.get().ambientOcclusion = v;
            HoloPlaceConfig.save();
        }));
        root.addChild(t2);

        root.addChild(coordRow(g));
        root.addChild(layerRow(g));

        LinearLayout actions = LinearLayout.horizontal().spacing(6);
        boolean loaded = g.schematic() != null;
        actions.addChild(Button.builder(Component.literal(g.isVisible() ? "Hide" : "Show"), b -> {
            g.setVisible(!g.isVisible());
            onClose();
        }).width(80).build()).active = loaded;
        actions.addChild(Button.builder(Component.literal("Reset rot/mirror"),
                b -> PlacementController.get().resetTransform()).width(130).build()).active = loaded;
        root.addChild(actions);

        root.addChild(new StringWidget(ROW_WIDTH, 9, Component.literal("§7Schematics"), this.font));
        addSchematicList(root);

        root.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(120).build());

        root.arrangeElements();
        FrameLayout.centerInRectangle(root, 0, 0, this.width, this.height);
        root.visitWidgets(this::addRenderableWidget);
    }

    private LinearLayout coordRow(GhostState g) {
        LinearLayout row = LinearLayout.horizontal().spacing(4);
        BlockPos a = g.anchor();
        EditBox x = intBox(String.valueOf(a.getX()));
        EditBox y = intBox(String.valueOf(a.getY()));
        EditBox z = intBox(String.valueOf(a.getZ()));
        row.addChild(new StringWidget(14, 18, Component.literal("§7xyz"), this.font));
        row.addChild(x);
        row.addChild(y);
        row.addChild(z);
        row.addChild(Button.builder(Component.literal("Move"), b -> {
            Integer ix = parseInt(x.getValue());
            Integer iy = parseInt(y.getValue());
            Integer iz = parseInt(z.getValue());
            if (ix != null && iy != null && iz != null) {
                PlacementController.get().moveTo(ix, iy, iz);
            }
        }).width(50).build()).active = g.schematic() != null;
        return row;
    }

    private net.minecraft.client.gui.layouts.LayoutElement layerRow(GhostState g) {
        PlacementTransform t = g.transform();
        int layers = t == null ? 0 : t.footprintY();
        LinearLayout col = LinearLayout.vertical().spacing(2);

        if (layers <= 1) {
            col.addChild(new StringWidget(ROW_WIDTH, 9,
                    Component.literal("§7Layers: §8single layer"), this.font));
            return col;
        }

        int hi = g.layerClip() && g.layerMax() != Integer.MAX_VALUE
                ? Math.min(g.layerMax(), layers - 1) : layers - 1;
        int lo = g.layerClip() ? Math.min(g.layerMin(), hi) : 0;

        LinearLayout header = LinearLayout.horizontal().spacing(6);
        StringWidget label = new StringWidget(230, 12, layerLabel(lo, hi, layers), this.font);
        header.addChild(label);
        header.addChild(Button.builder(Component.literal("All"),
                b -> PlacementController.get().clearLayers()).width(40).build());
        col.addChild(header);

        int[] range = {lo, hi};
        Runnable apply = () -> {
            label.setMessage(layerLabel(range[0], range[1], layers));
            PlacementController.get().setLayers(range[0], range[1]);
        };
        col.addChild(new LayerSlider("From", layers - 1, range[0], v -> {
            range[0] = Math.min(v, range[1]);
            apply.run();
        }));
        col.addChild(new LayerSlider("To", layers - 1, range[1], v -> {
            range[1] = Math.max(v, range[0]);
            apply.run();
        }));
        return col;
    }

    private static Component layerLabel(int lo, int hi, int total) {
        String span = lo == hi ? "layer " + lo : "layers " + lo + "–" + hi;
        return Component.literal("§7" + span + " §8of " + total);
    }

    private void addSchematicList(LinearLayout root) {
        List<Path> files = SchematicLibrary.list();
        if (files.isEmpty()) {
            root.addChild(Button.builder(Component.literal("Open schematics folder"),
                            b -> Util.getPlatform().openPath(SchematicLibrary.primaryDir()))
                    .width(ROW_WIDTH).build());
            return;
        }
        int pages = (files.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Mth.clamp(page, 0, pages - 1);
        int from = page * PAGE_SIZE;
        int to = Math.min(files.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Path file = files.get(i);
            root.addChild(Button.builder(Component.literal(bareName(file)), b -> {
                onClose();
                SchematicImport.show(file, true);
            }).width(ROW_WIDTH).build());
        }
        if (pages > 1) {
            LinearLayout pager = LinearLayout.horizontal().spacing(6);
            pager.addChild(Button.builder(Component.literal("<"), b -> {
                page--;
                rebuildWidgets();
            }).width(40).build()).active = page > 0;
            pager.addChild(new StringWidget(90, 18,
                    Component.literal("§7page " + (page + 1) + "/" + pages), this.font));
            pager.addChild(Button.builder(Component.literal(">"), b -> {
                page++;
                rebuildWidgets();
            }).width(40).build()).active = page < pages - 1;
            root.addChild(pager);
        }
    }

    private EditBox intBox(String value) {
        EditBox box = new EditBox(this.font, 46, 18, Component.empty());
        box.setMaxLength(8);
        box.setValue(value);
        return box;
    }

    private static String withKey(String base, KeyMapping key) {
        return key.isUnbound() ? base
                : base + " (" + key.getTranslatedKeyMessage().getString() + ")";
    }

    private Checkbox check(String label, BooleanSupplier get, Consumer<Boolean> set) {
        return Checkbox.builder(Component.literal(label), this.font)
                .selected(get.getAsBoolean())
                .onValueChange((checkbox, selected) -> set.accept(selected))
                .build();
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    private static String bareName(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase().endsWith(".litematic") ? name.substring(0, name.length() - 10) : name;
    }

    private final class LayerSlider extends AbstractSliderButton {
        private final String name;
        private final int max;
        private final IntConsumer onChange;

        LayerSlider(String name, int max, int initial, IntConsumer onChange) {
            super(0, 0, ROW_WIDTH, 18, Component.empty(),
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

    private final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int width) {
            super(0, 0, width, 20, Component.empty(),
                    Mth.clamp((GhostState.get().opacity() - 0.05) / 0.95, 0.0, 1.0));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Opacity: " + Math.round(GhostState.get().opacity() * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            GhostState.get().setOpacity((float) (0.05 + this.value * 0.95));
            HoloPlaceConfig.get().opacity = GhostState.get().opacity();
            HoloPlaceConfig.save();
        }
    }
}
