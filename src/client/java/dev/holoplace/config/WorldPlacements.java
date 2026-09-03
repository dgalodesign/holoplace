package dev.holoplace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.render.GhostRenderer;
import dev.holoplace.schematic.LitematicaSchematicReader;
import dev.holoplace.schematic.Schematic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Remembers where each schematic was left in each world. Keyed by save-folder name (singleplayer) or
 * server address (multiplayer); stored at {@code config/holoplace/placements.json}. Restored on world
 * join, cleared when the ghost is hidden.
 */
public final class WorldPlacements {

    /** One saved placement. Public fields for Gson. */
    public static final class Record {
        public String schematic = "";
        public int x;
        public int y;
        public int z;
        public String rotation = "NONE";
        public String mirror = "NONE";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("holoplace").resolve("placements.json");

    private static Map<String, Record> data;

    private WorldPlacements() {
    }

    private static Map<String, Record> data() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    private static Map<String, Record> load() {
        try {
            if (Files.isRegularFile(FILE)) {
                Map<String, Record> loaded = GSON.fromJson(Files.readString(FILE),
                        new TypeToken<HashMap<String, Record>>() { }.getType());
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.warn("Could not read placements.json", e);
        }
        return new HashMap<>();
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data()));
        } catch (IOException e) {
            HoloPlaceClient.LOGGER.warn("Could not write placements.json", e);
        }
    }

    /** Save the current ghost placement for the current world (no-op if nothing is loaded). */
    public static void saveCurrent() {
        String key = worldKey();
        GhostState ghost = GhostState.get();
        if (key == null || ghost.schematic() == null || ghost.sourceName() == null) {
            return;
        }
        Record r = new Record();
        r.schematic = ghost.sourceName();
        r.x = ghost.anchor().getX();
        r.y = ghost.anchor().getY();
        r.z = ghost.anchor().getZ();
        r.rotation = ghost.rotation().name();
        r.mirror = ghost.mirror().name();
        data().put(key, r);
        save();
    }

    /** Forget the current world's placement (called when the ghost is hidden). */
    public static void clearCurrent() {
        String key = worldKey();
        if (key != null && data().remove(key) != null) {
            save();
        }
    }

    /** Load and show the saved placement for the world just joined, if any. */
    public static void restoreForCurrentWorld() {
        String key = worldKey();
        if (key == null) {
            return;
        }
        Record r = data().get(key);
        if (r == null || r.schematic.isBlank()) {
            return;
        }
        Optional<Path> file = SchematicLibrary.resolve(r.schematic);
        if (file.isEmpty()) {
            HoloPlaceClient.LOGGER.info("Saved placement for {} references missing schematic {}", key, r.schematic);
            return;
        }
        try {
            Schematic schematic = LitematicaSchematicReader.read(file.get());
            GhostState ghost = GhostState.get();
            ghost.setRotation(parse(Rotation.values(), r.rotation, Rotation.NONE));
            ghost.setMirror(parse(Mirror.values(), r.mirror, Mirror.NONE));
            ghost.setAnchor(new BlockPos(r.x, r.y, r.z));
            ghost.setSchematic(schematic, file.get().getFileName().toString());
            GhostRenderer.invalidate();
            HoloPlaceClient.LOGGER.info("Restored {} at {} {} {} in {}", r.schematic, r.x, r.y, r.z, key);
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.error("Could not restore placement for {}", key, e);
        }
    }

    /** Persist the placement (unless mid-drag), then clear session ghost state on disconnect. */
    public static void onDisconnect() {
        if (!PlacementController.get().isGrabbing()) {
            saveCurrent();
        }
        GhostState.get().setSchematic(null, null);
        GhostState.get().setVisible(false);
        PlacementController.get().stopGrab(false);
        GhostRenderer.invalidate();
    }

    private static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            return "sp/" + sanitize(root.getFileName().toString());
        }
        if (mc.getCurrentServer() != null) {
            return "mp/" + sanitize(mc.getCurrentServer().ip);
        }
        return null;
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static <E extends Enum<E>> E parse(E[] values, String name, E fallback) {
        for (E value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return fallback;
    }
}
