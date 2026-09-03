package dev.holoplace;

import dev.holoplace.command.HoloPlaceCommand;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.render.GhostHud;
import dev.holoplace.render.GhostPipelines;
import dev.holoplace.render.GhostRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HoloPlaceClient implements ClientModInitializer {
    public static final String MOD_ID = "holoplace";
    public static final Logger LOGGER = LoggerFactory.getLogger("HoloPlace");

    @Override
    public void onInitializeClient() {
        SchematicLibrary.ensurePrimaryDir();

        GhostPipelines.bootstrap();

        HoloPlaceConfig config = HoloPlaceConfig.get();
        GhostState.get().setOpacity(config.opacity);
        GhostState.get().setSeeThrough(config.seeThrough);
        PlacementController.get().loadPrefs();

        HoloPlaceKeys.register();
        HoloPlaceCommand.register();
        GhostRenderer.register();
        GhostHud.register();

        LOGGER.info("HoloPlace ready — schematics folder: {}", SchematicLibrary.primaryDir());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
