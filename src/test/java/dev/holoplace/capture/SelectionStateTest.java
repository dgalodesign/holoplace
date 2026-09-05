package dev.holoplace.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

class SelectionStateTest {

    @Test
    void incompleteUntilBothCornersSet() {
        SelectionState s = new SelectionState();
        assertFalse(s.isComplete());
        assertNull(s.min());
        assertNull(s.max());
        assertEquals(Vec3i.ZERO, s.size());
        assertEquals(0L, s.volume());

        s.setCorner1(new BlockPos(0, 0, 0));
        assertFalse(s.isComplete());
        assertNull(s.min());

        s.setCorner2(new BlockPos(2, 3, 4));
        assertTrue(s.isComplete());
    }

    @Test
    void minMaxIndependentOfClickOrder() {
        SelectionState a = new SelectionState();
        a.setCorner1(new BlockPos(10, 70, -5));
        a.setCorner2(new BlockPos(-3, 64, 8));

        SelectionState b = new SelectionState();
        b.setCorner1(new BlockPos(-3, 64, 8));
        b.setCorner2(new BlockPos(10, 70, -5));

        assertEquals(new BlockPos(-3, 64, -5), a.min());
        assertEquals(new BlockPos(10, 70, 8), a.max());
        assertEquals(a.min(), b.min());
        assertEquals(a.max(), b.max());
    }

    @Test
    void sizeIsInclusiveAndAtLeastOnePerAxis() {
        SelectionState point = new SelectionState();
        point.setCorner1(new BlockPos(5, 5, 5));
        point.setCorner2(new BlockPos(5, 5, 5));
        assertEquals(new Vec3i(1, 1, 1), point.size());
        assertEquals(1L, point.volume());

        SelectionState box = new SelectionState();
        box.setCorner1(new BlockPos(0, 0, 0));
        box.setCorner2(new BlockPos(2, 3, 4));
        assertEquals(new Vec3i(3, 4, 5), box.size());
        assertEquals(60L, box.volume());
    }

    @Test
    void clearResetsToIncomplete() {
        SelectionState s = new SelectionState();
        s.setCorner1(new BlockPos(1, 2, 3));
        s.setCorner2(new BlockPos(4, 5, 6));
        assertTrue(s.isComplete());

        s.clear();
        assertFalse(s.isComplete());
        assertNull(s.corner1());
        assertNull(s.corner2());
    }

    @Test
    void volumeUsesLongArithmetic() {
        SelectionState s = new SelectionState();
        s.setCorner1(new BlockPos(0, 0, 0));
        s.setCorner2(new BlockPos(2047, 511, 2047));
        // 2048 * 512 * 2048 = 2,147,483,648 — overflows int, must stay positive as a long
        assertEquals(2_147_483_648L, s.volume());
    }
}
