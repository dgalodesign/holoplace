package dev.holoplace.capture;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * The set of world cells whose block state changed while a world was loaded — fed passively by
 * {@code LevelBlockChangeMixin}, read at save time by the "automatic" capture mode (only cells in
 * here are exported; everything else in the selection becomes air).
 *
 * <p>Positions are packed with {@link BlockPos#asLong}. Once {@link #isFull()} the log stops growing
 * (a runaway session shouldn't eat unbounded memory); the automatic capture then warns it may be
 * incomplete.
 */
public final class ChangeLog {

    /** ~24 MB of longs — far more cells than any hand build, small enough not to matter. */
    public static final int CAPACITY = 3_000_000;

    private final LongOpenHashSet touched = new LongOpenHashSet();
    private boolean full;

    public void record(BlockPos pos) {
        record(pos.asLong());
    }

    public void record(long packed) {
        if (full) {
            return;
        }
        if (touched.size() >= CAPACITY) {
            full = true;
            return;
        }
        touched.add(packed);
    }

    public boolean contains(int x, int y, int z) {
        return touched.contains(BlockPos.asLong(x, y, z));
    }

    public boolean contains(BlockPos pos) {
        return touched.contains(pos.asLong());
    }

    public int size() {
        return touched.size();
    }

    public boolean isEmpty() {
        return touched.isEmpty();
    }

    public boolean isFull() {
        return full;
    }

    public void clear() {
        touched.clear();
        full = false;
    }
}
