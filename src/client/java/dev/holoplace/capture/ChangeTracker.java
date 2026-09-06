package dev.holoplace.capture;

import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

/**
 * Client-side owner of the passive {@link ChangeLog}. Always on — from world load, block changes are
 * recorded with no "start" step to forget. Reset on world join / disconnect so each session starts
 * clean (persisting across relogs is M23).
 *
 * <p>To keep it to <em>the player's</em> edits and not the whole world, a change is only logged if it
 * lands within {@link #WINDOW_NANOS} of the player interacting with a block (place / break / use).
 * That window covers the immediate cascade of a placement — redstone, water flow, a piston — while
 * dropping ambient churn (worldgen post-processing, grass spread, leaf decay, another player far off).
 */
public final class ChangeTracker {

    private static final ChangeLog LOG = new ChangeLog();
    /** How long after a block interaction changes still count as "caused by the player". */
    private static final long WINDOW_NANOS = 1_500_000_000L;

    private static long lastInteractionNanos = Long.MIN_VALUE;

    private ChangeTracker() {
    }

    public static ChangeLog log() {
        return LOG;
    }

    /** Registers the "player touched a block" signals. Called once from client init. */
    public static void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            markInteraction(level);
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            markInteraction(level);
            return InteractionResult.PASS;
        });
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> markInteraction(level));
    }

    private static void markInteraction(Level level) {
        if (level.isClientSide()) {
            lastInteractionNanos = System.nanoTime();
        }
    }

    /** Called from {@code LevelBlockChangeMixin} for every client-side {@code Level.setBlock}. */
    public static void onBlockChange(Level level, BlockPos pos) {
        if (level != Minecraft.getInstance().level) {
            return;
        }
        if (System.nanoTime() - lastInteractionNanos <= WINDOW_NANOS) {
            LOG.record(pos);
        }
    }

    public static void reset() {
        LOG.clear();
        lastInteractionNanos = Long.MIN_VALUE;
    }
}
