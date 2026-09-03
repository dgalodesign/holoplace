package dev.holoplace.ui;

import dev.holoplace.GhostState;
import dev.holoplace.SchematicImport;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.placement.PlacementController;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * One flat screen for everything: the schematic list plus every display control (opacity, x-ray,
 * build-assist). No folders, no nested menus.
 */
public final class HoloPlaceScreen extends Screen {

    private static final int MAX_BUTTONS = 8;
    private static final int ROW_WIDTH = 320;

    public HoloPlaceScreen() {
        super(Component.literal("HoloPlace"));
    }

    @Override
    protected void init() {
        LinearLayout layout = LinearLayout.vertical().spacing(4);
        layout.addChild(new StringWidget(getTitle(), this.font));

        layout.addChild(new OpacitySlider(0, 0, ROW_WIDTH));

        LinearLayout toggles = LinearLayout.horizontal().spacing(10);
        toggles.addChild(toggle("See-through", GhostState.get()::seeThrough, v -> {
            GhostState.get().setSeeThrough(v);
            HoloPlaceConfig.get().seeThrough = v;
            HoloPlaceConfig.save();
        }));
        toggles.addChild(toggle("Hide placed", GhostState.get()::hideMatched, v -> {
            GhostState.get().setHideMatched(v);
            HoloPlaceConfig.get().hideMatched = v;
            HoloPlaceConfig.save();
        }));
        toggles.addChild(toggle("Match block only", GhostState.get()::matchBlockOnly, v -> {
            GhostState.get().setMatchBlockOnly(v);
            HoloPlaceConfig.get().matchBlockOnly = v;
            HoloPlaceConfig.save();
        }));
        layout.addChild(toggles);

        LinearLayout actions = LinearLayout.horizontal().spacing(6);
        boolean loaded = GhostState.get().schematic() != null;
        actions.addChild(Button.builder(
                Component.literal(GhostState.get().isVisible() ? "Hide" : "Show"), b -> {
                    GhostState g = GhostState.get();
                    g.setVisible(!g.isVisible());
                    onClose();
                }).width(90).build()).active = loaded;
        actions.addChild(Button.builder(Component.literal("Reset rot/mirror"),
                b -> PlacementController.get().resetTransform()).width(140).build()).active = loaded;
        layout.addChild(actions);

        layout.addChild(new StringWidget(ROW_WIDTH, 9, Component.literal("§7Schematics"), this.font));
        List<Path> files = SchematicLibrary.list();
        if (files.isEmpty()) {
            layout.addChild(Button.builder(Component.literal("Open schematics folder"),
                            b -> Util.getPlatform().openPath(SchematicLibrary.primaryDir()))
                    .width(ROW_WIDTH).build());
        } else {
            int shown = Math.min(files.size(), MAX_BUTTONS);
            for (int i = 0; i < shown; i++) {
                Path file = files.get(i);
                layout.addChild(Button.builder(Component.literal(bareName(file)), b -> {
                    onClose();
                    SchematicImport.show(file, true);
                }).width(ROW_WIDTH).build());
            }
            if (files.size() > shown) {
                layout.addChild(new StringWidget(ROW_WIDTH, 9, Component.literal(
                        "§8… " + (files.size() - shown) + " more — /holoplace show <name>"), this.font));
            }
        }

        layout.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(120).build());

        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, 0, 0, this.width, this.height);
        layout.visitWidgets(this::addRenderableWidget);
    }

    private Checkbox toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        return Checkbox.builder(Component.literal(label), this.font)
                .selected(get.getAsBoolean())
                .onValueChange((checkbox, selected) -> set.accept(selected))
                .build();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    private static String bareName(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase().endsWith(".litematic") ? name.substring(0, name.length() - 10) : name;
    }

    private static final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), toSlider(GhostState.get().opacity()));
            updateMessage();
        }

        private static double toSlider(float opacity) {
            return Mth.clamp((opacity - 0.05) / 0.95, 0.0, 1.0);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Opacity: " + Math.round(GhostState.get().opacity() * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            float opacity = (float) (0.05 + this.value * 0.95);
            GhostState.get().setOpacity(opacity);
            HoloPlaceConfig.get().opacity = GhostState.get().opacity();
            HoloPlaceConfig.save();
        }
    }
}
