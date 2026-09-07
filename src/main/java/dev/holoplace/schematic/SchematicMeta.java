package dev.holoplace.schematic;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;

/**
 * Cheap header-only look at a {@code .litematic} for the picker's hover panel: reads the compressed
 * NBT and pulls just the {@code Metadata} block (size, block count, region count, MC data version) —
 * it never unpacks the bit-packed block data, so it's fast even for large files. Reads happen once
 * per path on a background thread; {@link #peek} returns the cached result or {@code null} while it
 * loads.
 */
public final class SchematicMeta {

    private static final long NBT_QUOTA_BYTES = 64L * 1024 * 1024;

    private static final Map<Path, CompletableFuture<SchematicMeta>> CACHE = new ConcurrentHashMap<>();
    private static final Executor EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "holoplace-schematic-meta");
        t.setDaemon(true);
        return t;
    });

    /** Non-null enclosing size, or {@code null} if the file doesn't record one. */
    public final @Nullable Vec3i size;
    /** Total non-air blocks, or {@code -1} if not recorded. */
    public final long blocks;
    /** Number of regions, or {@code -1} if unknown. */
    public final int regions;
    /** {@code MinecraftDataVersion}, or {@code 0} if absent. */
    public final int dataVersion;
    /** Non-null if the file could not be read. */
    public final @Nullable String error;

    private SchematicMeta(@Nullable Vec3i size, long blocks, int regions, int dataVersion,
                          @Nullable String error) {
        this.size = size;
        this.blocks = blocks;
        this.regions = regions;
        this.dataVersion = dataVersion;
        this.error = error;
    }

    /** Cached metadata for {@code file}, or {@code null} while the background read is in flight. */
    public static @Nullable SchematicMeta peek(Path file) {
        CompletableFuture<SchematicMeta> f = CACHE.get(file);
        if (f == null) {
            CACHE.put(file, CompletableFuture.supplyAsync(() -> read(file), EXEC));
            return null;
        }
        return f.isDone() && !f.isCompletedExceptionally() ? f.getNow(null) : null;
    }

    /** Drop cached entries for files no longer in the list (called when the picker rebuilds). */
    public static void retainOnly(java.util.Collection<Path> keep) {
        CACHE.keySet().retainAll(keep);
    }

    private static SchematicMeta read(Path file) {
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(NBT_QUOTA_BYTES));
            CompoundTag md = root.getCompoundOrEmpty("Metadata");

            CompoundTag sizeTag = md.getCompoundOrEmpty("EnclosingSize");
            Vec3i size = new Vec3i(sizeTag.getIntOr("x", 0), sizeTag.getIntOr("y", 0),
                    sizeTag.getIntOr("z", 0));
            boolean haveSize = !size.equals(Vec3i.ZERO);

            long blocks = md.getLongOr("TotalBlocks", -1L);
            int regions = md.getIntOr("RegionCount",
                    root.getCompoundOrEmpty("Regions").keySet().size());
            if (regions == 0) {
                regions = -1;
            }
            int dataVersion = root.getIntOr("MinecraftDataVersion", 0);

            return new SchematicMeta(haveSize ? size : null, blocks, regions, dataVersion, null);
        } catch (Exception e) {
            return new SchematicMeta(null, -1, -1, 0,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
