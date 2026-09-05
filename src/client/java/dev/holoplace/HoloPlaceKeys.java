package dev.holoplace;

import com.mojang.blaze3d.platform.InputConstants;
import dev.holoplace.capture.CaptureController;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.ui.HoloPlaceScreen;
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

    public static final KeyMapping OPEN_PICKER = key("open_picker", GLFW.GLFW_KEY_K);
    public static final KeyMapping TOGGLE_GRAB = key("toggle_grab", GLFW.GLFW_KEY_G);
    public static final KeyMapping ROTATE = key("rotate", GLFW.GLFW_KEY_R);
    public static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_M);
    public static final KeyMapping SEE_THROUGH = key("see_through", GLFW.GLFW_KEY_X);
    public static final KeyMapping BUILD_ASSIST = key("build_assist", GLFW.GLFW_KEY_H);
    public static final KeyMapping CAPTURE_SELECT = key("capture_select", GLFW.GLFW_KEY_B);

    private HoloPlaceKeys() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.holoplace." + name, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void register() {
        for (KeyMapping k : new KeyMapping[]
                {OPEN_PICKER, TOGGLE_GRAB, ROTATE, MIRROR, SEE_THROUGH, BUILD_ASSIST, CAPTURE_SELECT}) {
            KeyMappingHelper.registerKeyMapping(k);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_PICKER.consumeClick()) {
                onOpenPicker(client);
            }
            while (TOGGLE_GRAB.consumeClick()) {
                PlacementController.get().toggleGrab();
            }
            while (ROTATE.consumeClick()) {
                PlacementController.get().rotate(!shiftDown(client));
            }
            while (MIRROR.consumeClick()) {
                PlacementController.get().cycleMirror();
            }
            while (SEE_THROUGH.consumeClick()) {
                PlacementController.get().toggleSeeThrough();
            }
            while (BUILD_ASSIST.consumeClick()) {
                PlacementController.get().toggleBuildAssist();
            }
            while (CAPTURE_SELECT.consumeClick()) {
                CaptureController.get().toggleSelecting();
            }
            PlacementController.get().tick();
        });
    }

    private static boolean shiftDown(Minecraft client) {
        return InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static void onOpenPicker(Minecraft client) {
        client.setScreen(new HoloPlaceScreen());
    }
}
