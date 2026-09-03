package dev.holoplace.schematic;

/**
 * Fixed-width bit-packed integer array as used by the {@code .litematic} format's {@code BlockStates}.
 *
 * <p>Unlike vanilla's {@code net.minecraft.util.BitStorage} (which since MC 1.16 never lets an entry
 * straddle a {@code long} boundary), litematica packs entries contiguously: an entry may span two
 * adjacent longs. This class implements that contiguous packing.
 *
 * <p>The number of bits per entry is <b>not</b> stored in the file; it is derived from the palette
 * size by the caller, see {@link #bitsFor(int)}.
 */
public final class LitematicaBitArray {
    private final long[] words;
    private final int bitsPerEntry;
    private final long maxEntryValue;
    private final long size;

    public LitematicaBitArray(int bitsPerEntry, long size) {
        this(bitsPerEntry, size, null);
    }

    public LitematicaBitArray(int bitsPerEntry, long size, long[] backing) {
        if (bitsPerEntry < 1 || bitsPerEntry > 32) {
            throw new IllegalArgumentException("bitsPerEntry out of range: " + bitsPerEntry);
        }
        this.bitsPerEntry = bitsPerEntry;
        this.size = size;
        this.maxEntryValue = (1L << bitsPerEntry) - 1L;

        int wordCount = (int) ((size * bitsPerEntry + 63L) / 64L);
        if (backing == null) {
            this.words = new long[wordCount];
        } else if (backing.length == wordCount) {
            this.words = backing;
        } else {
            // Be lenient with slightly-off files: pad short arrays, keep the head of long ones.
            this.words = new long[wordCount];
            System.arraycopy(backing, 0, this.words, 0, Math.min(backing.length, wordCount));
        }
    }

    public int get(long index) {
        long bitIndex = index * bitsPerEntry;
        int startWord = (int) (bitIndex >> 6);
        int endWord = (int) (((index + 1L) * bitsPerEntry - 1L) >> 6);
        int startBit = (int) (bitIndex & 63L);

        if (startWord == endWord) {
            return (int) ((words[startWord] >>> startBit) & maxEntryValue);
        }
        int lowBits = 64 - startBit;
        return (int) (((words[startWord] >>> startBit) | (words[endWord] << lowBits)) & maxEntryValue);
    }

    public void set(long index, int value) {
        long bitIndex = index * bitsPerEntry;
        int startWord = (int) (bitIndex >> 6);
        int endWord = (int) (((index + 1L) * bitsPerEntry - 1L) >> 6);
        int startBit = (int) (bitIndex & 63L);

        long v = value & maxEntryValue;
        words[startWord] = (words[startWord] & ~(maxEntryValue << startBit)) | (v << startBit);

        if (startWord != endWord) {
            int lowBits = 64 - startBit;
            int highBits = bitsPerEntry - lowBits;
            words[endWord] = ((words[endWord] >>> highBits) << highBits) | (v >>> lowBits);
        }
    }

    public long size() {
        return size;
    }

    public int bitsPerEntry() {
        return bitsPerEntry;
    }

    public long[] backing() {
        return words;
    }

    /**
     * Bits per entry for a palette of {@code paletteSize} entries. Matches litematica:
     * {@code max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1))}.
     */
    public static int bitsFor(int paletteSize) {
        return Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
    }
}
