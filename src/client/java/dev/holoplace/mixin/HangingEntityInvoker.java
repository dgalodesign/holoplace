package dev.holoplace.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code HangingEntity.setDirection} is {@code protected}. The ghost renderer needs it to point a
 * deserialised item frame / painting the right way (with the schematic's rotation/mirror applied) and
 * to trigger its bounding-box recalc, without ticking or spawning the entity.
 */
@Mixin(HangingEntity.class)
public interface HangingEntityInvoker {

    @Invoker("setDirection")
    void holoplace$setDirection(Direction direction);
}
