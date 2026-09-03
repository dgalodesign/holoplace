package dev.holoplace.mixin;

import dev.holoplace.SchematicImport;
import dev.holoplace.placement.PlacementController;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks into the in-world mouse:
 * <ul>
 *   <li>{@code onScroll} — in HoloPlace grab mode the wheel drives reach / height / opacity.
 *   <li>{@code onDrop} — a {@code .litematic} dropped onto the window is imported and shown.
 * </ul>
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void holoplace$grabScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        if (PlacementController.get().handleScroll(yOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "onDrop(JLjava/util/List;I)V", at = @At("HEAD"))
    private void holoplace$onDrop(long handle, List<Path> files, int failedCount, CallbackInfo ci) {
        if (files.stream().anyMatch(SchematicImport::isLitematic)) {
            SchematicImport.importDropped(files);
        }
    }
}
