package dev.holoplace.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LitematicaSchematicWriterTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Write a hand-built grid, read it back with the real reader, and expect it byte-identical. */
    @Test
    void roundTripsThroughReader() throws IOException {
        int sx = 3;
        int sy = 2;
        int sz = 4;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);

        LitematicaSchematicWriter.Region region = emptyRegion("build", sx, sy, sz);
        region.blocks()[region.index(0, 0, 0)] = stone;
        region.blocks()[region.index(2, 1, 3)] = stairs;
        region.blocks()[region.index(1, 0, 2)] = stone;

        CompoundTag nbt = LitematicaSchematicWriter.toNbt("build", "tester", 4790, region);
        Schematic schem = LitematicaSchematicReader.fromNbt(nbt, "fallback");

        assertEquals("build", schem.name());
        assertEquals(1, schem.regions().size());
        SchematicRegion r = schem.regions().get(0);
        assertEquals(sx, r.sizeX());
        assertEquals(sy, r.sizeY());
        assertEquals(sz, r.sizeZ());

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    BlockState expected = region.blocks()[region.index(x, y, z)];
                    if (expected == null) {
                        expected = air;
                    }
                    assertSame(expected, r.getBlockState(x, y, z),
                            "mismatch at " + x + "," + y + "," + z);
                }
            }
        }
        assertEquals(3, schem.totalNonAirBlocks());
        assertEquals((long) sx * sy * sz, schem.totalVolume());
        assertTrue(schem.missingBlocks().isEmpty());
    }

    @Test
    void roundTripsAnAllAirSelection() throws IOException {
        LitematicaSchematicWriter.Region region = emptyRegion("empty", 2, 2, 2);
        CompoundTag nbt = LitematicaSchematicWriter.toNbt("empty", "", 4790, region);
        Schematic schem = LitematicaSchematicReader.fromNbt(nbt, "fallback");

        SchematicRegion r = schem.regions().get(0);
        assertEquals(0, schem.totalNonAirBlocks());
        assertTrue(r.getBlockState(1, 1, 1).isAir());
    }

    @Test
    void preservesBlockEntityNbtByLocalPos() throws IOException {
        LitematicaSchematicWriter.Region region = emptyRegion("chest", 2, 1, 1);
        region.blocks()[region.index(1, 0, 0)] = Blocks.CHEST.defaultBlockState();

        CompoundTag chestData = new CompoundTag();
        chestData.putString("LootTable", "minecraft:chests/simple_dungeon");
        chestData.putInt("x", 1);
        chestData.putInt("y", 0);
        chestData.putInt("z", 0);

        LitematicaSchematicWriter.Region withBe = new LitematicaSchematicWriter.Region(
                region.name(), region.sizeX(), region.sizeY(), region.sizeZ(), region.blocks(),
                List.of(chestData), List.of());

        CompoundTag nbt = LitematicaSchematicWriter.toNbt("chest", "", 4790, withBe);
        Schematic schem = LitematicaSchematicReader.fromNbt(nbt, "fallback");
        SchematicRegion r = schem.regions().get(0);

        CompoundTag back = r.blockEntityNbt(1, 0, 0);
        assertTrue(back != null);
        assertEquals("minecraft:chests/simple_dungeon", back.getStringOr("LootTable", ""));
    }

    private static LitematicaSchematicWriter.Region emptyRegion(String name, int sx, int sy, int sz) {
        return new LitematicaSchematicWriter.Region(
                name, sx, sy, sz, new BlockState[sx * sy * sz], List.of(), List.of());
    }
}
