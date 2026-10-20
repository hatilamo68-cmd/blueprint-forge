package dev.blueprintforge.client;

import dev.blueprintforge.Blueprint;
import net.minecraft.util.math.BlockPos;

/** One loaded schematic with its own origin, rotation and layer filter (like a Litematica placement). */
public final class Placement {
    public final Blueprint blueprint;
    public String name;
    public BlockPos origin;
    public String dimension;
    public int rotation,layer=-1;
    public Placement(Blueprint blueprint,BlockPos origin,String dimension){
        this.blueprint=blueprint;this.name=blueprint.name();this.origin=origin;this.dimension=dimension;
    }
}
