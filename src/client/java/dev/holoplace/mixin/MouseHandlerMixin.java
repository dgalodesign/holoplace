package dev.holoplace.mixin;

import dev.holoplace.placement.PlacementController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While HoloPlace grab mode is active, the mouse wheel adjusts the ghost's reach distance
 * (Shift: vertical offset) instead of switching the hotbar slot.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void holoplace$grabScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        if (PlacementController.get().handleScroll(yOffset)) {
            ci.cancel();
        }
    }
}
