package dev.holoplace.mixin;

import dev.holoplace.Diagnostics;
import net.minecraft.SystemReport;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a "HoloPlace" section to the crash report's system details, so a crash pasted without running
 * {@code /holoplace debug} still carries the mod version, GPU, relevant mods and the loaded ghost's
 * state.
 */
@Mixin(Minecraft.class)
abstract class MinecraftCrashReportMixin {

    @Inject(method = "fillSystemReport", at = @At("RETURN"))
    private static void holoplace$addCrashDetail(SystemReport report, CallbackInfoReturnable<SystemReport> cir) {
        try {
            report.setDetail("HoloPlace", () -> "\n\t\t" + String.join("\n\t\t", Diagnostics.lines()));
        } catch (Throwable ignored) {
            // never let diagnostics collection break the crash report
        }
    }
}
