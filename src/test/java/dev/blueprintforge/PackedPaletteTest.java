package dev.blueprintforge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedPaletteTest {
    @Test void minimumTwoBitsAndPowerOfTwoTransitions(){
        assertEquals(2,PackedPalette.bits(1));assertEquals(2,PackedPalette.bits(4));
        assertEquals(3,PackedPalette.bits(5));assertEquals(4,PackedPalette.bits(16));assertEquals(5,PackedPalette.bits(17));
    }
    @Test void decodesFiveBitValueCrossingAWordBoundary(){
        // Index 12 occupies bits 60..64. Value 31 has 4 bits in word 0 and 1 bit in word 1.
        assertEquals(31,PackedPalette.get(new long[]{15L<<60,1},5,12));
        assertEquals(0,PackedPalette.get(new long[]{15L<<60,1},5,11));
    }
    @Test void handlesUnsignedTopBitsAndFollowingValues(){
        long[] packed={Long.MIN_VALUE,0b1110};
        assertEquals(2,PackedPalette.get(packed,2,31));assertEquals(2,PackedPalette.get(packed,2,32));assertEquals(3,PackedPalette.get(packed,2,33));
    }
    @Test void rejectsTruncatedCrossBoundaryValue(){
        assertThrows(IllegalArgumentException.class,()->PackedPalette.get(new long[]{0},5,12));
    }
    @Test void negativeRegionDimensionsUseInclusiveOppositeCorner(){
        assertEquals(2,PackedPalette.regionMin(10,-9));assertEquals(10,PackedPalette.regionMin(10,9));
        assertEquals(-10,PackedPalette.regionMin(-10,-1));assertThrows(IllegalArgumentException.class,()->PackedPalette.regionMin(0,0));
    }
}
