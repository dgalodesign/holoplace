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
                message("§cCould not import " + file.getFileName());
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
            message("§cJoin a world first");
            return;
        }
        try {
            Schematic schematic = LitematicaSchematicReader.read(file);
            GhostState ghost = GhostState.get();
            ghost.setAnchor(mc.player.blockPosition());
            ghost.setSchematic(schematic, file.getFileName().toString());
            WorldPlacements.saveCurrent();
            if (enterGrab && !PlacementController.get().isGrabbing()) {
                PlacementController.get().toggleGrab();
            }
            message("§aShowing §e" + schematic.name() + " §7— look to position, §fG§7 to lock");
            if (!schematic.missingBlocks().isEmpty()) {
                message("  §c" + schematic.missingBlocks().size() + " unknown block id(s) will be invisible");
            }
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.error("Failed to read {}", file, e);
            message("§cFailed to read " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        } else {
            HoloPlaceClient.LOGGER.info(text);
        }
    }
}
