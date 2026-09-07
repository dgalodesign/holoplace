package dev.holoplace.schematic;

import static dev.holoplace.schematic.SchematicFixtures.airEntry;
import static dev.holoplace.schematic.SchematicFixtures.index;
import static dev.holoplace.schematic.SchematicFixtures.palette;
import static dev.holoplace.schematic.SchematicFixtures.region;
import static dev.holoplace.schematic.SchematicFixtures.schematic;
import static dev.holoplace.schematic.SchematicFixtures.simpleEntry;
import static dev.holoplace.schematic.SchematicFixtures.stairsEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LitematicaSchematicReaderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void readsBlocksPaletteAndDimensions() throws IOException {
        LitematicaBitArray bits = new LitematicaBitArray(LitematicaBitArray.bitsFor(3), 8);
        bits.set(index(0, 0, 0, 2, 2), 1); // stone
        bits.set(index(1, 0, 0, 2, 2), 2); // oak_stairs[facing=east]

        CompoundTag root = schematic(6, region("main", 0, 0, 0, 2, 2, 2,
                palette(airEntry(), simpleEntry("minecraft:stone"),
                        stairsEntry("minecraft:oak_stairs", "east")),
                bits.backing()));

        Schematic schem = LitematicaSchematicReader.fromNbt(root, "fallback");

        assertEquals(1, schem.regions().size());
        SchematicRegion r = schem.regions().get(0);
        assertEquals(2, r.sizeX());
        assertEquals(2, r.sizeY());
        assertEquals(2, r.sizeZ());

        assertTrue(r.getBlockState(0, 0, 0).is(Blocks.STONE));
        BlockState stairs = r.getBlockState(1, 0, 0);
        assertTrue(stairs.is(Blocks.OAK_STAIRS));
        assertEquals(Direction.EAST, stairs.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertTrue(r.getBlockState(1, 1, 1).isAir());

        assertEquals(2, schem.totalNonAirBlocks());
        assertEquals(8, schem.totalVolume());
        assertEquals("Test Schematic", schem.name());
        assertTrue(schem.missingBlocks().isEmpty());
    }

    @Test
    void normalisesNegativeRegionSize() throws IOException {
        // Position (10,0,10), Size (-3,2,-3) -> min corner (8,0,8), dims 3x2x3.
        CompoundTag root = schematic(6, region("neg", 10, 0, 10, -3, 2, -3,
                palette(airEntry(), simpleEntry("minecraft:stone")),
                new LitematicaBitArray(2, 18).backing()));

        Schematic schem = LitematicaSchematicReader.fromNbt(root, "fallback");
        SchematicRegion r = schem.regions().get(0);
        assertEquals(3, r.sizeX());
        assertEquals(2, r.sizeY());
        assertEquals(3, r.sizeZ());
        assertEquals(8, r.minCorner().getX());
        assertEquals(0, r.minCorner().getY());
        assertEquals(8, r.minCorner().getZ());
        assertEquals(8, schem.min().getX());
    }

    @Test
    void recordsMissingBlocks() throws IOException {
        LitematicaBitArray bits = new LitematicaBitArray(2, 1);
        bits.set(0, 1);
        CompoundTag root = schematic(6, region("main", 0, 0, 0, 1, 1, 1,
                palette(airEntry(), simpleEntry("holoplace:does_not_exist")),
                bits.backing()));

        Schematic schem = LitematicaSchematicReader.fromNbt(root, "fallback");
        assertTrue(schem.missingBlocks().contains(Identifier.parse("holoplace:does_not_exist")));
        assertTrue(schem.regions().get(0).getBlockState(0, 0, 0).isAir());
    }

    @Test
    void rejectsOldFormatVersion() {
        CompoundTag root = schematic(2, region("main", 0, 0, 0, 1, 1, 1,
                palette(airEntry()), new long[1]));
        assertThrows(IOException.class, () -> LitematicaSchematicReader.fromNbt(root, "x"));
    }

    @Test
    void rejectsAnImplausiblyLargeRegion() {
        // A hostile / corrupt file: an absurd Size would overflow the bit array or OOM.
        CompoundTag hugeAxis = schematic(6, region("main", 0, 0, 0, 2_000_000, 1, 1,
                palette(airEntry()), new long[0]));
        assertThrows(IOException.class, () -> LitematicaSchematicReader.fromNbt(hugeAxis, "x"));

        CompoundTag hugeVolume = schematic(6, region("main", 0, 0, 0, 4000, 4000, 4000,
                palette(airEntry()), new long[0]));
        assertThrows(IOException.class, () -> LitematicaSchematicReader.fromNbt(hugeVolume, "x"));
    }

    @Test
    void readsMultipleRegions() throws IOException {
        LitematicaBitArray a = new LitematicaBitArray(2, 1);
        a.set(0, 1);
        LitematicaBitArray b = new LitematicaBitArray(2, 1);
        b.set(0, 1);
        CompoundTag root = schematic(6,
                region("a", 0, 0, 0, 1, 1, 1, palette(airEntry(), simpleEntry("minecraft:stone")), a.backing()),
                region("b", 5, 0, 0, 1, 1, 1, palette(airEntry(), simpleEntry("minecraft:dirt")), b.backing()));
        Schematic schem = LitematicaSchematicReader.fromNbt(root, "x");
        assertEquals(2, schem.regions().size());
        assertEquals(2, schem.totalNonAirBlocks());
        assertEquals(0, schem.min().getX());
        assertFalse(schem.enclosingSize().equals(net.minecraft.core.Vec3i.ZERO));
    }

}
