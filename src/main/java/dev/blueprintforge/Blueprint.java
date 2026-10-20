package dev.blueprintforge;

import com.google.gson.*;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.BlockRotation;

import java.util.*;

public record Blueprint(String name, int width, int height, int depth, List<Cell> cells) {
    public static final int MAX_BLOCKS = 400000;
    public static final int MAX_VOLUME = 16777216;
    public static final int MAX_JSON_BYTES = 8388608;
    public record Cell(int x, int y, int z, BlockState state) {}
    public record Target(BlockPos pos, BlockState state) {}

    public Blueprint {
        if (width < 1 || height < 1 || depth < 1 || width > MAX_VOLUME || height > MAX_VOLUME || depth > MAX_VOLUME
                || (long) width * height * depth > MAX_VOLUME)
            throw new IllegalArgumentException("Blueprint volume must be 1.." + MAX_VOLUME + " blocks.");
        if (name == null || name.isBlank() || name.length() > 64)
            throw new IllegalArgumentException("Blueprint name must contain 1..64 characters.");
        if (cells.isEmpty() || cells.size() > MAX_BLOCKS)
            throw new IllegalArgumentException("Blueprint must contain 1.." + MAX_BLOCKS + " non-air blocks.");
        Set<BlockPos> unique = new HashSet<>();
        for (Cell c : cells) {
            if (c.x < 0 || c.y < 0 || c.z < 0 || c.x >= width || c.y >= height || c.z >= depth
                    || !unique.add(new BlockPos(c.x, c.y, c.z)))
                throw new IllegalArgumentException("Invalid or duplicate blueprint coordinate.");
            if (c.state.isAir())
                throw new IllegalArgumentException("Air entries are not stored.");
        }
        cells = cells.stream().sorted(Comparator.comparingInt(Cell::y)
                .thenComparingInt(Cell::z).thenComparingInt(Cell::x)).toList();
    }

    public List<Target> targets(BlockPos origin, int turns, int layer) {
        BlockRotation rotation = BlockRotation.values()[Math.floorMod(turns, 4)];
        List<Target> result = new ArrayList<>();
        for (Cell c : cells) {
            if (layer >= 0 && c.y != layer) continue;
            Geometry.Point p = Geometry.rotate(c.x, c.y, c.z, width, depth, turns);
            result.add(new Target(origin.add(p.x(), p.y(), p.z()), c.state.rotate(rotation)));
        }
        return List.copyOf(result);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1); root.addProperty("name", name);
        root.addProperty("width", width); root.addProperty("height", height); root.addProperty("depth", depth);
        JsonArray blocks = new JsonArray();
        for (Cell cell : cells) {
            JsonObject b = new JsonObject();
            b.addProperty("x", cell.x); b.addProperty("y", cell.y); b.addProperty("z", cell.z);
            b.addProperty("block", Registries.BLOCK.getId(cell.state.getBlock()).toString());
            JsonObject props = new JsonObject();
            cell.state.getEntries().forEach((property, value) -> props.addProperty(property.getName(), propertyName(property, value)));
            b.add("properties", props); blocks.add(b);
        }
        root.add("blocks", blocks);
        String json = new Gson().toJson(root);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Blueprint exceeds the 8 MiB format limit.");
        return json;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(Property property, Comparable value) { return property.name(value); }

    public static Blueprint fromJson(String json) {
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Blueprint exceeds the 8 MiB format limit.");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported blueprint format.");
        List<Cell> cells = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("blocks");
        if (array.size() > MAX_BLOCKS) throw new IllegalArgumentException("Too many blueprint blocks.");
        for (JsonElement e : array) {
            JsonObject b = e.getAsJsonObject();
            Identifier id = Identifier.of(b.get("block").getAsString());
            if (!Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Unknown block: " + id);
            Block block = Registries.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            if (b.has("properties")) {
                for (Map.Entry<String, JsonElement> prop : b.getAsJsonObject("properties").entrySet()) {
                    Property<?> property = block.getStateManager().getProperty(prop.getKey());
                    if (property == null) throw new IllegalArgumentException("Unknown property: " + prop.getKey());
                    state = apply(state, property, prop.getValue().getAsString());
                }
            }
            cells.add(new Cell(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt(), state));
        }
        return new Blueprint(root.get("name").getAsString(), root.get("width").getAsInt(),
                root.get("height").getAsInt(), root.get("depth").getAsInt(), cells);
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> p, String value) {
        return state.with(p, p.parse(value).orElseThrow(() -> new IllegalArgumentException("Invalid property value: " + value)));
    }

    public static Blueprint demo() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y <= 5; y++) for (int z = 0; z < 7; z++) for (int x = 0; x < 9; x++) {
            BlockState state = null;
            if (y == 0) state = Blocks.STONE_BRICKS.getDefaultState();
            else if (y == 5) state = Blocks.SPRUCE_PLANKS.getDefaultState();
            else if (x == 0 || x == 8 || z == 0 || z == 6) {
                if (z == 0 && x == 4 && y <= 2) continue;
                boolean corner = (x == 0 || x == 8) && (z == 0 || z == 6);
                state = (corner ? Blocks.OAK_LOG : y == 3 ? Blocks.GLASS : Blocks.OAK_PLANKS).getDefaultState();
            }
            if (state != null) cells.add(new Cell(x, y, z, state));
        }
        return new Blueprint("demo-cabin", 9, 6, 7, cells);
    }
}
