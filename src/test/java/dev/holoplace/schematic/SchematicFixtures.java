package dev.holoplace.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Hand-built {@code .litematic} NBT for tests. */
final class SchematicFixtures {

    private SchematicFixtures() {
    }

    record Region(String name, CompoundTag tag) {
    }

    static int index(int x, int y, int z, int sizeX, int sizeZ) {
        return y * sizeX * sizeZ + z * sizeX + x;
    }

    static CompoundTag schematic(int version, Region... regions) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", version);
        root.putInt("SubVersion", 1);
        root.putInt("MinecraftDataVersion", 4000);
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "Test Schematic");
        meta.putString("Author", "tester");
        root.put("Metadata", meta);
        CompoundTag regionsTag = new CompoundTag();
        for (Region region : regions) {
            regionsTag.put(region.name(), region.tag());
        }
        root.put("Regions", regionsTag);
        return root;
    }

    static Region region(String name, int px, int py, int pz, int sx, int sy, int sz,
                         ListTag palette, long[] blockStates) {
        CompoundTag region = new CompoundTag();
        region.put("Position", vec(px, py, pz));
        region.put("Size", vec(sx, sy, sz));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", blockStates);
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());
        return new Region(name, region);
    }

    static CompoundTag vec(int x, int y, int z) {
        CompoundTag t = new CompoundTag();
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        return t;
    }

    static ListTag palette(CompoundTag... entries) {
        ListTag list = new ListTag();
        for (CompoundTag e : entries) {
            list.add(e);
        }
        return list;
    }

    static CompoundTag airEntry() {
        return simpleEntry("minecraft:air");
    }

    static CompoundTag simpleEntry(String name) {
        CompoundTag t = new CompoundTag();
        t.putString("Name", name);
        return t;
    }

    static CompoundTag stairsEntry(String name, String facing) {
        CompoundTag t = simpleEntry(name);
        CompoundTag props = new CompoundTag();
        props.putString("facing", facing);
        props.putString("half", "bottom");
        props.putString("shape", "straight");
        props.putString("waterlogged", "false");
        t.put("Properties", props);
        return t;
    }
}
