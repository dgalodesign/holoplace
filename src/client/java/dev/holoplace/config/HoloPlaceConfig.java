package dev.holoplace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.holoplace.HoloPlaceClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Persisted preferences (not world-specific placement). Stored at {@code config/holoplace/config.json}. */
public final class HoloPlaceConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("holoplace").resolve("config.json");

    private static HoloPlaceConfig instance;

    public float opacity = 0.55f;
    public double reach = 8.0;
    public int verticalOffset = 0;
    public boolean seeThrough = false;
    public boolean hideMatched = false;
    public boolean matchBlockOnly = false;
    public boolean blockEntityModels = true;
    public boolean showEntities = true;
    public boolean ambientOcclusion = true;
    public float markerOpacity = 0.85f;
    /** Which screen corner the status panels anchor to: 0 = top-left, 1 = top-right, 2 = bottom-right,
     *  3 = bottom-left. */
    public int hudCorner = 0;
    public int layerMin = 0;
    public int layerMax = Integer.MAX_VALUE;
    public boolean seenIntro = false;
    public String rotation = "NONE";
    public String mirror = "NONE";
    public String lastSchematic = "";

    public static HoloPlaceConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static HoloPlaceConfig load() {
        try {
            if (Files.isRegularFile(FILE)) {
                HoloPlaceConfig loaded = GSON.fromJson(Files.readString(FILE), HoloPlaceConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.warn("Could not read config, using defaults", e);
        }
        return new HoloPlaceConfig();
    }

    public static void save() {
        if (instance == null) {
            return;
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException e) {
            HoloPlaceClient.LOGGER.warn("Could not write config", e);
        }
    }
}
