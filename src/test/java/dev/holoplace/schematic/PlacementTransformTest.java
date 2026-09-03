package dev.holoplace.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

class PlacementTransformTest {

    @Test
    void footprintSwapsOnQuarterTurns() {
        var t = new PlacementTransform(3, 5, 7, Mirror.NONE, Rotation.CLOCKWISE_90);
        assertEquals(7, t.footprintX());
        assertEquals(5, t.footprintY());
        assertEquals(3, t.footprintZ());

        var half = new PlacementTransform(3, 5, 7, Mirror.NONE, Rotation.CLOCKWISE_180);
        assertEquals(3, half.footprintX());
        assertEquals(7, half.footprintZ());
    }

    @Test
    void forwardThenInverseIsIdentityForEveryCombo() {
        int sx = 4;
        int sy = 3;
        int sz = 6;
        for (Mirror mirror : Mirror.values()) {
            for (Rotation rotation : Rotation.values()) {
                var t = new PlacementTransform(sx, sy, sz, mirror, rotation);
                for (int x = 0; x < sx; x++) {
                    for (int y = 0; y < sy; y++) {
                        for (int z = 0; z < sz; z++) {
                            int[] f = t.forward(x, y, z);
                            assertTrue(f[0] >= 0 && f[0] < t.footprintX(), mirror + "/" + rotation + " fx=" + f[0]);
                            assertTrue(f[2] >= 0 && f[2] < t.footprintZ(), mirror + "/" + rotation + " fz=" + f[2]);
                            int[] back = t.inverse(f[0], f[1], f[2]);
                            assertArrayEquals(new int[] {x, y, z}, back, mirror + "/" + rotation);
                        }
                    }
                }
            }
        }
    }

    @Test
    void forwardIsABijectionOntoTheFootprint() {
        var t = new PlacementTransform(3, 1, 5, Mirror.LEFT_RIGHT, Rotation.COUNTERCLOCKWISE_90);
        boolean[][] hit = new boolean[t.footprintX()][t.footprintZ()];
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 5; z++) {
                int[] f = t.forward(x, 0, z);
                assertTrue(!hit[f[0]][f[2]], "collision at " + f[0] + "," + f[2]);
                hit[f[0]][f[2]] = true;
            }
        }
        for (boolean[] row : hit) {
            for (boolean cell : row) {
                assertTrue(cell, "footprint cell not covered");
            }
        }
    }

    @Test
    void knownCornerMapping() {
        // 2x1x3 box, rotate 90 CW: authored (0,0,0) -> footprint corner.
        var t = new PlacementTransform(2, 1, 3, Mirror.NONE, Rotation.CLOCKWISE_90);
        assertArrayEquals(new int[] {2, 0, 0}, t.forward(0, 0, 0));
        assertArrayEquals(new int[] {0, 0, 1}, t.forward(1, 0, 2));
    }

    @Test
    void rotationCyclesAreConsistent() {
        Rotation r = Rotation.NONE;
        for (int i = 0; i < 4; i++) {
            r = PlacementTransform.rotateCw(r);
        }
        assertEquals(Rotation.NONE, r);
        for (int i = 0; i < 4; i++) {
            r = PlacementTransform.rotateCcw(r);
        }
        assertEquals(Rotation.NONE, r);
        assertEquals(Rotation.CLOCKWISE_90, PlacementTransform.rotateCw(Rotation.NONE));
        assertEquals(Rotation.NONE, PlacementTransform.rotateCcw(PlacementTransform.rotateCw(Rotation.NONE)));
    }
}
