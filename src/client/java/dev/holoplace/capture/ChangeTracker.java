package dev.holoplace.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Client-side owner of the passive {@link ChangeLog}. Always on — from the moment a world is loaded,
 * every block-state change in it (via {@code LevelBlockChangeMixin}) is recorded, with no "start"
 * step for the player to forget. Reset on world join / disconnect so each session starts clean;
 * persisting it across relogs is M23.
 */
public final class ChangeTracker {

    private static final ChangeLog LOG = new ChangeLog();

    private ChangeTracker() {
    }

    public static ChangeLog log() {
        return LOG;
    }

    /** Called from the mixin for every client-side {@code Level.setBlock}. */
    public static void onBlockChange(Level level, BlockPos pos) {
        if (level == Minecraft.getInstance().level) {
            LOG.record(pos);
        }
    }

    public static void reset() {
        LOG.clear();
    }
}
