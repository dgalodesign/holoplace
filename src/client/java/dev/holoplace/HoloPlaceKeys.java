package dev.holoplace;

import com.mojang.blaze3d.platform.InputConstants;
import dev.holoplace.placement.PlacementController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Key bindings and the per-tick pump for {@link PlacementController}. */
public final class HoloPlaceKeys {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HoloPlaceClient.MOD_ID, "main"));

    public static final KeyMapping OPEN_PICKER = new KeyMapping(
            "key.holoplace.open_picker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static final KeyMapping TOGGLE_GRAB = new KeyMapping(
            "key.holoplace.toggle_grab", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private HoloPlaceKeys() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(OPEN_PICKER);
        KeyMappingHelper.registerKeyMapping(TOGGLE_GRAB);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_PICKER.consumeClick()) {
                onOpenPicker(client);
            }
            while (TOGGLE_GRAB.consumeClick()) {
                PlacementController.get().toggleGrab();
            }
            PlacementController.get().tick();
        });
    }

    private static void onOpenPicker(Minecraft client) {
        // M5: open SchematicPickerScreen.
        actionBar(client, "§e[HoloPlace] picker not implemented yet — use §f/holoplace list");
        HoloPlaceClient.LOGGER.info("open_picker pressed");
    }

    private static void actionBar(Minecraft client, String message) {
        if (client.gui != null) {
            client.gui.setOverlayMessage(Component.literal(message), false);
        }
    }
}
