package dev.holoplace.schematic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads the open, documented {@code .litematic} format (GZIP-compressed NBT) into a {@link Schematic}.
 *
 * <p>Supports schematic {@code Version} 4–7 (the modern layout with a {@code long[]} {@code BlockStates}
 * array and a {@code {Name, Properties}} palette). Older Sponge/Schematica-derived layouts are not
 * handled.
 */
public final class LitematicaSchematicReader {

    /** Lowest schematic {@code Version} whose region layout this reader understands. */
    public static final int MIN_SUPPORTED_VERSION = 4;
    public static final int MAX_TESTED_VERSION = 7;

    private LitematicaSchematicReader() {
    }

    public static Schematic read(Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        return fromNbt(root, defaultName(file));
    }

    public static Schematic fromNbt(CompoundTag root, String fallbackName) throws IOException {
        int version = root.getIntOr("Version", -1);
        if (version < MIN_SUPPORTED_VERSION) {
            throw new IOException("Unsupported .litematic Version " + version
                    + " (need >= " + MIN_SUPPORTED_VERSION + ")");
        }
        int mcDataVersion = root.getIntOr("MinecraftDataVersion", 0);

        CompoundTag metadata = root.getCompoundOrEmpty("Metadata");
        String name = metadata.getStringOr("Name", fallbackName);
        String author = metadata.getStringOr("Author", "");
        String description = metadata.getStringOr("Description", "");
        Vec3i enclosingSize = readVec3i(metadata.getCompoundOrEmpty("EnclosingSize"));

        CompoundTag regionsTag = root.getCompoundOrEmpty("Regions");
        if (regionsTag.isEmpty()) {
            throw new IOException("No Regions in .litematic");
        }

        List<SchematicRegion> regions = new ArrayList<>();
        Set<Identifier> missing = new LinkedHashSet<>();
        BlockPos schematicMin = null;

        for (String regionName : regionsTag.keySet()) {
            CompoundTag regionTag = regionsTag.getCompoundOrEmpty(regionName);
            SchematicRegion region = readRegion(regionName, regionTag, missing);
            if (region == null) {
                continue;
            }
            regions.add(region);
            schematicMin = schematicMin == null
                    ? region.minCorner()
                    : BlockPos.min(schematicMin, region.minCorner());
        }

        if (regions.isEmpty()) {
            throw new IOException("No readable regions in .litematic");
        }
        if (schematicMin == null) {
            schematicMin = BlockPos.ZERO;
        }
        if (enclosingSize.equals(Vec3i.ZERO)) {
            enclosingSize = computeEnclosingSize(regions, schematicMin);
        }

        return new Schematic(name, author, description, version, mcDataVersion,
                regions, schematicMin, enclosingSize, missing);
    }

    private static SchematicRegion readRegion(String name, CompoundTag regionTag, Set<Identifier> missing) {
        Vec3i pos = readVec3i(regionTag.getCompoundOrEmpty("Position"));
        Vec3i size = readVec3i(regionTag.getCompoundOrEmpty("Size"));
        if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
            return null;
        }

        // Normalise: local (0,0,0) is the minimum corner; dimensions become positive.
        BlockPos endRel = relativeEnd(size).offset(pos.getX(), pos.getY(), pos.getZ());
        BlockPos posA = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockPos minCorner = BlockPos.min(posA, endRel);
        BlockPos maxCorner = BlockPos.max(posA, endRel);
        int sizeX = maxCorner.getX() - minCorner.getX() + 1;
        int sizeY = maxCorner.getY() - minCorner.getY() + 1;
        int sizeZ = maxCorner.getZ() - minCorner.getZ() + 1;
        long volume = (long) sizeX * sizeY * sizeZ;

        BlockState[] palette = readPalette(regionTag.getListOrEmpty("BlockStatePalette"), missing);
        if (palette.length == 0) {
            palette = new BlockState[] {Blocks.AIR.defaultBlockState()};
        }

        long[] words = regionTag.getLongArray("BlockStates").orElse(new long[0]);
        int bits = LitematicaBitArray.bitsFor(palette.length);
        LitematicaBitArray blocks = new LitematicaBitArray(bits, volume, words.length == 0 ? null : words);

        List<CompoundTag> blockEntities = copyCompounds(regionTag.getListOrEmpty("TileEntities"));
        List<CompoundTag> entities = copyCompounds(regionTag.getListOrEmpty("Entities"));

        return new SchematicRegion(name, minCorner, sizeX, sizeY, sizeZ, palette, blocks,
                blockEntities, entities);
    }

    private static BlockState[] readPalette(ListTag paletteList, Set<Identifier> missing) {
        BlockState[] palette = new BlockState[paletteList.size()];
        for (int i = 0; i < paletteList.size(); i++) {
            CompoundTag entry = paletteList.getCompoundOrEmpty(i);
            String rawName = entry.getStringOr("Name", "minecraft:air");
            Identifier id = Identifier.tryParse(rawName);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                if (id != null) {
                    missing.add(id);
                }
                palette[i] = Blocks.AIR.defaultBlockState();
            } else {
                palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, entry);
            }
        }
        return palette;
    }

    private static List<CompoundTag> copyCompounds(ListTag list) {
        List<CompoundTag> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getCompoundOrEmpty(i).copy());
        }
        return out;
    }

    /** Per-axis: {@code v>=0 ? v-1 : v+1}. Mirrors litematica's {@code getRelativeEndPositionFromAreaSize}. */
    private static BlockPos relativeEnd(Vec3i size) {
        return new BlockPos(shrinkTowardZero(size.getX()), shrinkTowardZero(size.getY()),
                shrinkTowardZero(size.getZ()));
    }

    private static int shrinkTowardZero(int v) {
        return v >= 0 ? v - 1 : v + 1;
    }

    private static Vec3i readVec3i(CompoundTag tag) {
        return new Vec3i(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
    }

    private static Vec3i computeEnclosingSize(List<SchematicRegion> regions, BlockPos schematicMin) {
        int maxX = schematicMin.getX();
        int maxY = schematicMin.getY();
        int maxZ = schematicMin.getZ();
        for (SchematicRegion r : regions) {
            maxX = Math.max(maxX, r.minCorner().getX() + r.sizeX());
            maxY = Math.max(maxY, r.minCorner().getY() + r.sizeY());
            maxZ = Math.max(maxZ, r.minCorner().getZ() + r.sizeZ());
        }
        return new Vec3i(maxX - schematicMin.getX(), maxY - schematicMin.getY(), maxZ - schematicMin.getZ());
    }

    private static String defaultName(Path file) {
        String fn = file.getFileName().toString();
        int dot = fn.lastIndexOf('.');
        return dot > 0 ? fn.substring(0, dot) : fn;
    }
}
