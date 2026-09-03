package dev.holoplace.schematic;

import static dev.holoplace.schematic.SchematicFixtures.airEntry;
import static dev.holoplace.schematic.SchematicFixtures.palette;
import static dev.holoplace.schematic.SchematicFixtures.region;
import static dev.holoplace.schematic.SchematicFixtures.schematic;
import static dev.holoplace.schematic.SchematicFixtures.simpleEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaterialListTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Schematic sample() throws IOException {
        // 2x1x3 = 6 cells: 4 stone, 1 dirt, 1 air.
        LitematicaBitArray bits = new LitematicaBitArray(LitematicaBitArray.bitsFor(3), 6);
        int[] ids = {1, 1, 1, 1, 2, 0};
        for (int i = 0; i < ids.length; i++) {
            bits.set(i, ids[i]);
        }
        var root = schematic(6, region("main", 0, 0, 0, 2, 1, 3,
                palette(airEntry(), simpleEntry("minecraft:stone"), simpleEntry("minecraft:dirt")),
                bits.backing()));
        return LitematicaSchematicReader.fromNbt(root, "x");
    }

    @Test
    void totalsCountByItemIgnoringAir() throws IOException {
        List<MaterialList.Entry> totals = MaterialList.totals(sample());
        Map<Item, Integer> byItem = new HashMap<>();
        totals.forEach(e -> byItem.put(e.item(), e.count()));

        assertEquals(4, byItem.get(Items.STONE));
        assertEquals(1, byItem.get(Items.DIRT));
        assertEquals(2, totals.size());
        assertTrue(totals.get(0).count() >= totals.get(1).count(), "sorted descending");
    }

    @Test
    void remainingSubtractsPlaced() throws IOException {
        Map<Item, Integer> placed = new HashMap<>();
        placed.put(Items.STONE, 3);
        List<MaterialList.Entry> remaining = MaterialList.remaining(sample(), placed);
        Map<Item, Integer> byItem = new HashMap<>();
        remaining.forEach(e -> byItem.put(e.item(), e.count()));

        assertEquals(1, byItem.get(Items.STONE)); // 4 - 3
        assertEquals(1, byItem.get(Items.DIRT));
    }

    @Test
    void remainingDropsFullySatisfiedItems() throws IOException {
        Map<Item, Integer> placed = new HashMap<>();
        placed.put(Items.STONE, 10);
        placed.put(Items.DIRT, 1);
        List<MaterialList.Entry> remaining = MaterialList.remaining(sample(), placed);
        assertTrue(remaining.isEmpty());
    }
}
