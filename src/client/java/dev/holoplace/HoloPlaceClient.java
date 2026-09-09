package dev.holoplace;

import dev.holoplace.capture.CaptureController;
import dev.holoplace.capture.CaptureHud;
import dev.holoplace.capture.SelectionRenderer;
import dev.holoplace.command.HoloPlaceCommand;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.config.WorldPlacements;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.render.GhostHud;
import dev.holoplace.render.GhostRenderer;
import dev.holoplace.render.GhostTooltipHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HoloPlaceClient implements ClientModInitializer {
    public static final String MOD_ID = "holoplace";
    public static final Logger LOGGER = LoggerFactory.getLogger("HoloPlace");

    @Override
    public void onInitializeClient() {
        SchematicLibrary.ensurePrimaryDir();

        HoloPlaceConfig config = HoloPlaceConfig.get();
        GhostState.get().setOpacity(config.opacity);
        GhostState.get().setSeeThrough(config.seeThrough);
        GhostState.get().setHideMatched(config.hideMatched);
        GhostState.get().setMatchBlockOnly(config.matchBlockOnly);
        GhostState.get().setBlockEntityModels(config.blockEntityModels);
        GhostState.get().setShowEntities(config.showEntities);
        GhostState.get().setMarkerOpacity(config.markerOpacity);
        // Not user-facing any more — the ghost always shades faces (looks like a real block).
        GhostState.get().setShade(true);
        if (config.layerMax != Integer.MAX_VALUE || config.layerMin != 0) {
            GhostState.get().setLayers(config.layerMin, config.layerMax);
        }
        PlacementController.get().loadPrefs();

        HoloPlaceKeys.register();
        HoloPlaceCommand.register();
        GhostRenderer.register();
        GhostHud.register();
        GhostTooltipHud.register();
        CaptureController.register();
        SelectionRenderer.register();
        CaptureHud.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            WorldPlacements.restoreForCurrentWorld();
            maybeShowIntro(client);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                WorldPlacements.onDisconnect());

        LOGGER.info("HoloPlace ready — schematics folder: {}", SchematicLibrary.primaryDir());
    }

    private static void maybeShowIntro(net.minecraft.client.Minecraft client) {
        HoloPlaceConfig config = HoloPlaceConfig.get();
        if (config.seenIntro || client.player == null) {
            return;
        }
        config.seenIntro = true;
        HoloPlaceConfig.save();
        client.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("holoplace.welcome"));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
