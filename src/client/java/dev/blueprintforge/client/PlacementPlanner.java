package dev.blueprintforge.client;

import dev.blueprintforge.Blueprint;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.Set;

/** Predicts an ordinary BlockItem placement; it never writes a world state. */
public final class PlacementPlanner {
    public record Plan(BlockHitResult hit, float yaw, float pitch, Item item) {}
    private static final Direction[] SIDES={Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.DOWN};
    /** Properties the game derives from neighbours; they never block completion. */
    private static final Set<String> DERIVED=Set.of("north","south","east","west","up","down","distance","persistent","snowy","shape","hinge");
    /** Properties an ordinary item placement cannot choose (powered, open, waterlogged, age, ...). A block that differs
     *  only in these still counts as placed, otherwise the builder would wait on it forever. */
    private static final Set<String> NOT_PLACEABLE=Set.of("waterlogged","powered","open","lit","power","delay","locked","triggered","age",
            "bites","charges","inverted","note","instrument","rotation","attached","disarmed","enabled","stage","moisture","eye",
            "conditional","extended","orientation","tilt","occupied","berries","candles","pickles","eggs","hatch","layers");
    private PlacementPlanner() {}

    private static boolean ignored(Block block,String name) {
        return DERIVED.contains(name) || NOT_PLACEABLE.contains(name) || name.startsWith("has_") || name.startsWith("slot_occupied")
                || block instanceof ChestBlock && name.equals("type");
    }
    public static boolean matches(BlockState actual,BlockState desired) {
        if (actual.getBlock()!=desired.getBlock()) return false;
        for (Property<?> p:desired.getProperties()) {
            if (ignored(desired.getBlock(),p.getName())) continue;
            if (!actual.get(p).equals(desired.get(p))) return false;
        }
        return true;
    }
    public static Item item(Blueprint.Target target) {
        Item item=target.state().getBlock().asItem();
        return item instanceof BlockItem ? item : Items.AIR;
    }
    public static boolean hasSupport(MinecraftClient mc,BlockPos target) {
        for (Direction side:SIDES) if (usableSupport(mc,target.offset(side.getOpposite()))) return true;
        return false;
    }
    private static boolean usableSupport(MinecraftClient mc,BlockPos p) {
        BlockState s=mc.world.getBlockState(p); Block b=s.getBlock();
        return !s.getCollisionShape(mc.world,p).isEmpty() && !s.hasBlockEntity()
                && !(b instanceof CraftingTableBlock || b instanceof DoorBlock || b instanceof TrapdoorBlock
                || b instanceof FenceGateBlock || b instanceof LeverBlock || b instanceof ButtonBlock
                || b instanceof BedBlock || b instanceof AnvilBlock || b instanceof NoteBlock || b instanceof ComposterBlock);
    }
    public static Plan find(MinecraftClient mc,Blueprint.Target target,Vec3d eye) {
        if (!mc.world.isChunkLoaded(target.pos()) || !mc.world.getBlockState(target.pos()).isReplaceable()) return null;
        Item item=item(target); if (item==Items.AIR) return null;
        double reach=Math.min(4.5,mc.player.getBlockInteractionRange());
        // Cheap early exit: a block this far away has no face within reach (saves up to 18 raycasts).
        if (eye.squaredDistanceTo(Vec3d.ofCenter(target.pos()))>(reach+1.8)*(reach+1.8)) return null;
        // Stay out of the destination, even if the server would reject the collision later.
        Box body=new Box(eye.x-.3,eye.y-1.62,eye.z-.3,eye.x+.3,eye.y+.18,eye.z+.3);
        if (body.intersects(new Box(target.pos()))) return null;
        for (Direction side:SIDES) {
            BlockPos support=target.pos().offset(side.getOpposite());
            if (!usableSupport(mc,support)) continue;
            for (double height:new double[]{.5,.25,.75}) {
                Vec3d hit=Vec3d.ofCenter(support).add(side.getOffsetX()*.5,side.getOffsetY()*.5,side.getOffsetZ()*.5);
                if (side.getAxis().isHorizontal()) hit=hit.add(0,height-.5,0);
                if (eye.squaredDistanceTo(hit)>reach*reach) continue;
                Vec3d end=hit.add(-side.getOffsetX()*.002,-side.getOffsetY()*.002,-side.getOffsetZ()*.002);
                BlockHitResult ray=mc.world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
                if (ray.getType()!=HitResult.Type.BLOCK || !ray.getBlockPos().equals(support) || ray.getSide()!=side) continue;
                float[] angles=look(eye,hit); float oldYaw=mc.player.getYaw(),oldPitch=mc.player.getPitch();
                try {
                    mc.player.setYaw(angles[0]); mc.player.setPitch(angles[1]);
                    var click=new BlockHitResult(hit,side,support,false);
                    var context=new ItemPlacementContext(mc.player,Hand.MAIN_HAND,new ItemStack(item),click);
                    BlockState predicted=((BlockItem)item).getBlock().getPlacementState(context);
                    if (context.getBlockPos().equals(target.pos()) && predicted!=null && matches(predicted,target.state())
                            && predicted.canPlaceAt(mc.world,target.pos())) return new Plan(click,angles[0],angles[1],item);
                } finally { mc.player.setYaw(oldYaw); mc.player.setPitch(oldPitch); }
            }
        }
        return null;
    }
    public static float[] look(Vec3d eye,Vec3d point) {
        Vec3d d=point.subtract(eye);
        return new float[]{MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(d.z,d.x))-90),
                (float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)))};
    }
}
