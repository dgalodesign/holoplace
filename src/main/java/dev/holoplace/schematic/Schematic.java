package dev.holoplace.schematic;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;

/** A parsed {@code .litematic} file: metadata plus one or more {@link SchematicRegion}s. */
public final class Schematic {
    private final String name;
    private final String author;
    private final String description;
    private final int schematicVersion;
    private final int minecraftDataVersion;
    private final List<SchematicRegion> regions;
    private final BlockPos min;
    private final Vec3i enclosingSize;
    private final Set<Identifier> missingBlocks;

    public Schematic(String name, String author, String description, int schematicVersion,
                     int minecraftDataVersion, List<SchematicRegion> regions,
                     BlockPos min, Vec3i enclosingSize, Set<Identifier> missingBlocks) {
        this.name = name;
        this.author = author;
        this.description = description;
        this.schematicVersion = schematicVersion;
        this.minecraftDataVersion = minecraftDataVersion;
        this.regions = List.copyOf(regions);
        this.min = min;
        this.enclosingSize = enclosingSize;
        this.missingBlocks = Set.copyOf(missingBlocks);
    }

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    public int schematicVersion() {
        return schematicVersion;
    }

    public int minecraftDataVersion() {
        return minecraftDataVersion;
    }

    public List<SchematicRegion> regions() {
        return regions;
    }

    /** Minimum corner of the schematic's bounding box, in its authored coordinate space. */
    public BlockPos min() {
        return min;
    }

    public Vec3i enclosingSize() {
        return enclosingSize;
    }

    /** Block ids referenced by the palette that are not present in this game instance's registry. */
    public Set<Identifier> missingBlocks() {
        return missingBlocks;
    }

    public long totalVolume() {
        long v = 0;
        for (SchematicRegion r : regions) {
            v += r.volume();
        }
        return v;
    }

    public long totalNonAirBlocks() {
        long v = 0;
        for (SchematicRegion r : regions) {
            v += r.countNonAir();
        }
        return v;
    }
}
