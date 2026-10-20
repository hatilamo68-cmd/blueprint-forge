package dev.blueprintforge;

/** Pure coordinate math, also used by the unit tests without launching Minecraft. */
public final class Geometry {
    private Geometry() {}
    public record Point(int x, int y, int z) {}
    public static Point rotate(int x, int y, int z, int width, int depth, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> new Point(x, y, z);
            case 1 -> new Point(depth - 1 - z, y, x);
            case 2 -> new Point(width - 1 - x, y, depth - 1 - z);
            default -> new Point(z, y, width - 1 - x);
        };
    }
}
