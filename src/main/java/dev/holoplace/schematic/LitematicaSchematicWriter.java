package dev.holoplace.schematic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Writes a single-region {@code .litematic} (GZIP NBT) — the inverse of
 * {@link LitematicaSchematicReader}. Round-tripping a grid of block states through the reader and
 * writer is the main regression test for this class.
 */
public final class LitematicaSchematicWriter {

    /** Format version we emit — inside the 4..7 range {@link LitematicaSchematicReader} accepts. */
    private static final int SCHEMATIC_VERSION = 6;

    private LitematicaSchematicWriter() {
    }

    /**
     * One captured region: a dense block grid plus its block entities / entities in the format's
     * layout (region-relative {@code x}/{@code y}/{@code z} int keys, no vanilla {@code id}).
     */
    public record Region(
            String name,
            int sizeX, int sizeY, int sizeZ,
            BlockState[] blocks,
            List<CompoundTag> blockEntities,
            List<CompoundTag> entities) {

        /** Grid index for a local cell: {@code y*sizeX*sizeZ + z*sizeX + x} (matches the reader). */
        public int index(int x, int y, int z) {
            return y * sizeX * sizeZ + z * sizeX + x;
        }

        public long volume() {
            return (long) sizeX * sizeY * sizeZ;
        }

        public int countNonAir() {
            int n = 0;
            for (BlockState s : blocks) {
                if (s != null && !s.isAir()) {
                    n++;
                }
            }
            return n;
        }
    }

    public static void write(Path file, String name, String author, int minecraftDataVersion, Region region)
            throws IOException {
        NbtIo.writeCompressed(toNbt(name, author, minecraftDataVersion, region), file);
    }

    public static CompoundTag toNbt(String name, String author, int minecraftDataVersion, Region region) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", SCHEMATIC_VERSION);
        root.putInt("MinecraftDataVersion", minecraftDataVersion);

        CompoundTag metadata = new CompoundTag();
        metadata.putString("Name", name);
        metadata.putString("Author", author);
        metadata.putString("Description", "");
        metadata.put("EnclosingSize", vec3i(region.sizeX(), region.sizeY(), region.sizeZ()));
        long now = System.currentTimeMillis();
        metadata.putLong("TimeCreated", now);
        metadata.putLong("TimeModified", now);
        metadata.putInt("RegionCount", 1);
        metadata.putInt("TotalBlocks", region.countNonAir());
        metadata.putLong("TotalVolume", region.volume());
        root.put("Metadata", metadata);

        CompoundTag regions = new CompoundTag();
        regions.put(region.name(), regionTag(region));
        root.put("Regions", regions);
        return root;
    }

    private static CompoundTag regionTag(Region region) {
        CompoundTag tag = new CompoundTag();
        tag.put("Position", vec3i(0, 0, 0));
        tag.put("Size", vec3i(region.sizeX(), region.sizeY(), region.sizeZ()));

        BlockState air = Blocks.AIR.defaultBlockState();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        palette.add(air);
        paletteIndex.put(air, 0);
        for (BlockState s : region.blocks()) {
            BlockState state = s == null ? air : s;
            paletteIndex.computeIfAbsent(state, k -> {
                palette.add(k);
                return palette.size() - 1;
            });
        }

        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("BlockStatePalette", paletteTag);

        int bits = LitematicaBitArray.bitsFor(palette.size());
        LitematicaBitArray bitArray = new LitematicaBitArray(bits, region.volume());
        BlockState[] blocks = region.blocks();
        for (int i = 0; i < blocks.length; i++) {
            BlockState state = blocks[i] == null ? air : blocks[i];
            bitArray.set(i, paletteIndex.get(state));
        }
        tag.putLongArray("BlockStates", bitArray.backing());

        tag.put("TileEntities", copyList(region.blockEntities()));
        tag.put("Entities", copyList(region.entities()));
        tag.put("PendingBlockTicks", new ListTag());
        tag.put("PendingFluidTicks", new ListTag());
        return tag;
    }

    private static ListTag copyList(List<CompoundTag> tags) {
        ListTag list = new ListTag();
        for (CompoundTag tag : tags) {
            list.add(tag);
        }
        return list;
    }

    private static CompoundTag vec3i(int x, int y, int z) {
        CompoundTag t = new CompoundTag();
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        return t;
    }
}
