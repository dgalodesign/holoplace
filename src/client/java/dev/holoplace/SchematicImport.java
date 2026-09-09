package dev.holoplace;

import dev.holoplace.config.WorldPlacements;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.LitematicaSchematicReader;
import dev.holoplace.schematic.Schematic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Shared "load this file and show it" flow, used by the command, the picker and OS file-drop. */
public final class SchematicImport {

    private SchematicImport() {
    }

    public static boolean isLitematic(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".litematic");
    }

    /** Copy any dropped {@code .litematic} files into the library, then show the first one. */
    public static void importDropped(List<Path> files) {
        Path firstImported = null;
        for (Path file : files) {
            if (!isLitematic(file) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                SchematicLibrary.ensurePrimaryDir();
                Path dest = SchematicLibrary.primaryDir().resolve(file.getFileName().toString());
                if (!Files.exists(dest) || !Files.isSameFile(file, dest)) {
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                if (firstImported == null) {
                    firstImported = dest;
                }
            } catch (Exception e) {
                HoloPlaceClient.LOGGER.error("Failed to import dropped schematic {}", file, e);
                message(text("holoplace.import.failed", file.getFileName()));
            }
        }
        if (firstImported != null) {
            show(firstImported, true);
        }
    }

    /** Load {@code file} into the ghost. When {@code enterGrab}, drop straight into grab mode. */
    public static void show(Path file, boolean enterGrab) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            message(text("holoplace.import.join_world"));
            return;
        }
        try {
            Schematic schematic = LitematicaSchematicReader.read(file);
            GhostState ghost = GhostState.get();
            // A freshly loaded schematic starts "as authored" — it must not inherit the previous
            // schematic's rotation / mirror / layer slice.
            PlacementController.get().resetForNewSchematic();
            ghost.setAnchor(mc.player.blockPosition());
            ghost.setSchematic(schematic, file.getFileName().toString());
            WorldPlacements.saveCurrent();
            if (enterGrab && !PlacementController.get().isGrabbing()) {
                PlacementController.get().toggleGrab();
            }
            message(text("holoplace.import.showing", schematic.name()));
            if (!schematic.missingBlocks().isEmpty()) {
                message(text("holoplace.import.unknown_blocks", schematic.missingBlocks().size()));
            }
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.error("Failed to read {}", file, e);
            message(text("holoplace.import.read_failed", file.getFileName(), e.getMessage()));
        }
    }

    private static void message(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        } else {
            HoloPlaceClient.LOGGER.info(message);
        }
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
