package dev.holoplace.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LitematicaBitArrayTest {

    @Test
    void bitsForMatchesLitematicaFormula() {
        assertEquals(2, LitematicaBitArray.bitsFor(1));
        assertEquals(2, LitematicaBitArray.bitsFor(2));
        assertEquals(2, LitematicaBitArray.bitsFor(4));
        assertEquals(3, LitematicaBitArray.bitsFor(5));
        assertEquals(3, LitematicaBitArray.bitsFor(8));
        assertEquals(4, LitematicaBitArray.bitsFor(9));
        assertEquals(4, LitematicaBitArray.bitsFor(16));
        assertEquals(5, LitematicaBitArray.bitsFor(17));
        assertEquals(5, LitematicaBitArray.bitsFor(32));
        assertEquals(6, LitematicaBitArray.bitsFor(33));
        assertEquals(10, LitematicaBitArray.bitsFor(1024));
        assertEquals(11, LitematicaBitArray.bitsFor(1025));
    }

    @Test
    void roundTripAcrossWordBoundary() {
        // 3 bits per entry: entry 21 occupies bits 63..65, straddling word 0 and word 1.
        LitematicaBitArray a = new LitematicaBitArray(3, 64);
        for (int i = 0; i < 64; i++) {
            a.set(i, i % 8);
        }
        for (int i = 0; i < 64; i++) {
            assertEquals(i % 8, a.get(i), "index " + i);
        }
    }

    @Test
    void wordCountRoundsUp() {
        // 5 bits * 13 entries = 65 bits -> 2 words
        assertEquals(2, new LitematicaBitArray(5, 13).backing().length);
        // 4 bits * 16 entries = 64 bits -> 1 word
        assertEquals(1, new LitematicaBitArray(4, 16).backing().length);
    }

    @Test
    void randomisedRoundTrip() {
        Random rng = new Random(1234);
        for (int bits = 1; bits <= 16; bits++) {
            int size = 500;
            LitematicaBitArray a = new LitematicaBitArray(bits, size);
            int max = (1 << bits) - 1;
            int[] expected = new int[size];
            for (int i = 0; i < size; i++) {
                expected[i] = rng.nextInt(max + 1);
                a.set(i, expected[i]);
            }
            int[] actual = new int[size];
            for (int i = 0; i < size; i++) {
                actual[i] = a.get(i);
            }
            assertArrayEquals(expected, actual, "bits=" + bits);
        }
    }

    @Test
    void decodesKnownBackingArray() {
        // 2 bits per entry, values 0,1,2,3,0,1,2,3 packed little-endian into one long:
        // 0b 11 10 01 00 11 10 01 00  = 0xE4E4
        long[] backing = {0xE4E4L};
        LitematicaBitArray a = new LitematicaBitArray(2, 8, backing);
        int[] expected = {0, 1, 2, 3, 0, 1, 2, 3};
        for (int i = 0; i < 8; i++) {
            assertEquals(expected[i], a.get(i), "index " + i);
        }
    }
}
