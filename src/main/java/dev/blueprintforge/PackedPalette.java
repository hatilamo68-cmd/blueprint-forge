package dev.blueprintforge;

/** Litematic continuous bit packing, including values spanning two 64-bit words. */
public final class PackedPalette {
    private PackedPalette() {}
    public static int bits(int paletteSize) {
        if (paletteSize < 1 || paletteSize > 65536) throw new IllegalArgumentException("Invalid palette size.");
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }
    public static int get(long[] data, int bits, int index) {
        if (bits < 2 || bits > 16 || index < 0) throw new IllegalArgumentException("Invalid packed index.");
        long start = (long) index * bits;
        int word = (int)(start >>> 6), offset = (int)(start & 63);
        if (word >= data.length) throw new IllegalArgumentException("Truncated block-state array.");
        long value = data[word] >>> offset;
        if (offset + bits > 64) {
            if (word + 1 >= data.length) throw new IllegalArgumentException("Truncated block-state array.");
            value |= data[word + 1] << (64 - offset);
        }
        return (int)(value & ((1L << bits) - 1));
    }
    public static int regionMin(int position, int signedSize) {
        if (signedSize == 0 || signedSize == Integer.MIN_VALUE) throw new IllegalArgumentException("Invalid region size.");
        return Math.addExact(position, Math.min(0, signedSize + 1));
    }
}
