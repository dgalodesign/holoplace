package dev.holoplace.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Aggregates a schematic's blocks by the item you'd place to build them. This is a rough count:
 * blocks with no obtainable item are dropped, and multi-block pieces (doors, beds, tall flowers,
 * large amethyst buds…) are counted per block state, so a door shows as 2.
 */
public final class MaterialList {

    public record Entry(Item item, int count) {
    }

    private MaterialList() {
    }

    public static List<Entry> totals(Schematic schematic) {
        return toEntries(count(schematic));
    }

    /**
     * @param placed  optional per-item map of blocks already satisfied in the world; when given, the
     *                returned counts are the <em>remaining</em> amount (total − placed, floored at 0).
     */
    public static List<Entry> remaining(Schematic schematic, Map<Item, Integer> placed) {
        Map<Item, Integer> totals = count(schematic);
        Map<Item, Integer> out = new HashMap<>();
        for (Map.Entry<Item, Integer> e : totals.entrySet()) {
            int left = e.getValue() - placed.getOrDefault(e.getKey(), 0);
            if (left > 0) {
                out.put(e.getKey(), left);
            }
        }
        return toEntries(out);
    }

    /** Item a block state would be placed from, or {@code null} if there is no obtainable item. */
    public static Item itemFor(BlockState state) {
        if (state.isAir()) {
            return null;
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    private static Map<Item, Integer> count(Schematic schematic) {
        Map<Item, Integer> counts = new HashMap<>();
        for (SchematicRegion region : schematic.regions()) {
            for (int y = 0; y < region.sizeY(); y++) {
                for (int z = 0; z < region.sizeZ(); z++) {
                    for (int x = 0; x < region.sizeX(); x++) {
                        Item item = itemFor(region.getBlockState(x, y, z));
                        if (item != null) {
                            counts.merge(item, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return counts;
    }

    private static List<Entry> toEntries(Map<Item, Integer> counts) {
        List<Entry> entries = new ArrayList<>(counts.size());
        counts.forEach((item, count) -> entries.add(new Entry(item, count)));
        entries.sort(Comparator.comparingInt(Entry::count).reversed());
        return entries;
    }
}
