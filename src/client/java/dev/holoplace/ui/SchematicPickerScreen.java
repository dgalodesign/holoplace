package dev.holoplace.ui;

import dev.holoplace.SchematicImport;
import dev.holoplace.SchematicLibrary;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Flat one-screen schematic picker: the {@code .litematic} files in the library as a single column of
 * buttons, one click to load and drop into grab mode. No folders, no nested menus.
 */
public final class SchematicPickerScreen extends Screen {

    private static final int MAX_BUTTONS = 14;
    private static final int ROW_WIDTH = 320;

    private final Screen parent;

    public SchematicPickerScreen(Screen parent) {
        super(Component.literal("HoloPlace — Schematics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LinearLayout layout = LinearLayout.vertical().spacing(4);
        layout.addChild(new StringWidget(getTitle(), this.font));

        List<Path> files = SchematicLibrary.list();
        if (files.isEmpty()) {
            layout.addChild(new StringWidget(ROW_WIDTH, 9,
                    Component.literal("No .litematic files. Drop one on the window, or put it in:"), this.font));
            layout.addChild(new StringWidget(ROW_WIDTH, 9,
                    Component.literal("§7" + SchematicLibrary.primaryDir()), this.font));
            layout.addChild(Button.builder(Component.literal("Open folder"),
                            b -> Util.getPlatform().openPath(SchematicLibrary.primaryDir()))
                    .width(ROW_WIDTH).build());
        } else {
            int shown = Math.min(files.size(), MAX_BUTTONS);
            for (int i = 0; i < shown; i++) {
                Path file = files.get(i);
                layout.addChild(Button.builder(Component.literal(displayName(file)), b -> {
                    onClose();
                    SchematicImport.show(file, true);
                }).width(ROW_WIDTH).build());
            }
            if (files.size() > shown) {
                layout.addChild(new StringWidget(ROW_WIDTH, 9,
                        Component.literal("§7… " + (files.size() - shown)
                                + " more — use §f/holoplace show <name>"), this.font));
            }
        }

        layout.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(120).build());

        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, 0, 0, this.width, this.height);
        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static String displayName(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase().endsWith(".litematic") ? name.substring(0, name.length() - 10) : name;
    }
}
