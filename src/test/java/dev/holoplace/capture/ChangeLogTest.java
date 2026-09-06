package dev.holoplace.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ChangeLogTest {

    @Test
    void recordsAndLooksUpByCoordinates() {
        ChangeLog log = new ChangeLog();
        assertTrue(log.isEmpty());

        log.record(new BlockPos(10, 64, -20));
        log.record(new BlockPos(11, 64, -20));

        assertTrue(log.contains(10, 64, -20));
        assertTrue(log.contains(new BlockPos(11, 64, -20)));
        assertFalse(log.contains(12, 64, -20));
        assertEquals(2, log.size());
    }

    @Test
    void dedupesRepeatedRecords() {
        ChangeLog log = new ChangeLog();
        for (int i = 0; i < 5; i++) {
            log.record(new BlockPos(3, 3, 3));
        }
        assertEquals(1, log.size());
    }

    @Test
    void clearResetsSizeAndFullFlag() {
        ChangeLog log = new ChangeLog();
        log.record(new BlockPos(1, 1, 1));
        log.clear();
        assertTrue(log.isEmpty());
        assertFalse(log.isFull());
        assertFalse(log.contains(1, 1, 1));
    }

    @Test
    void stopsGrowingAtCapacity() {
        ChangeLog log = new ChangeLog();
        for (long i = 0; i < ChangeLog.CAPACITY + 100L; i++) {
            log.record(i);
        }
        assertTrue(log.isFull());
        assertEquals(ChangeLog.CAPACITY, log.size());
    }
}
