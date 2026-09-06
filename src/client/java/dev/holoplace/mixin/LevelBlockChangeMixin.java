package dev.holoplace.mixin;

import dev.holoplace.capture.ChangeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds the passive capture change-log. {@code Level.setBlock(...)} is the one method both the
 * client's own predicted placement/break and server block-update packets funnel through — bulk chunk
 * streaming does not (it fills sections directly), so this naturally sees only real, incremental
 * changes. The {@code isClientSide()} guard drops the integrated server's own calls in singleplayer.
 */
@Mixin(Level.class)
abstract class LevelBlockChangeMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void holoplace$trackBlockChange(BlockPos pos, BlockState state, int flags, int limit,
                                            CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (self.isClientSide()) {
            ChangeTracker.onBlockChange(self, pos.immutable());
        }
    }
}
