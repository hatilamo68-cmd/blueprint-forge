package dev.blueprintforge;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    @Test void clockwiseQuarterTurnKeepsRectangularFootprintAndHeight() {
        assertEquals(new Geometry.Point(2, 5, 0), Geometry.rotate(0, 5, 0, 9, 3, 1));
        assertEquals(new Geometry.Point(0, 5, 8), Geometry.rotate(8, 5, 2, 9, 3, 1));
    }
    @Test void rotationsAreBijectionsWithinTheRotatedBounds() {
        for (int width = 1; width <= 9; width++) for (int depth = 1; depth <= 9; depth++) {
            for (int turns = 0; turns < 4; turns++) {
                var unique = new HashSet<Geometry.Point>();
                int rw = turns % 2 == 0 ? width : depth, rd = turns % 2 == 0 ? depth : width;
                for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
                    var p = Geometry.rotate(x, 2, z, width, depth, turns);
                    assertTrue(p.x() >= 0 && p.x() < rw && p.z() >= 0 && p.z() < rd);
                    assertEquals(2, p.y()); assertTrue(unique.add(p));
                }
                assertEquals(width*depth, unique.size());
            }
        }
    }
    @Test void fourQuarterTurnsReturnEveryCellToItsOriginalCoordinate() {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 3; z++) {
            var p = new Geometry.Point(x, 4, z); int w = 9, d = 3;
            for (int i = 0; i < 4; i++) {
                p = Geometry.rotate(p.x(), p.y(), p.z(), w, d, 1);
                int old = w; w = d; d = old;
            }
            assertEquals(new Geometry.Point(x, 4, z), p);
        }
    }
}
