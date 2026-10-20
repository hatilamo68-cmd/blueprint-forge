package dev.blueprintforge.client;

import dev.blueprintforge.*;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/**
 * Client-side walking and normal right-click placement. No commands, teleporting or world writes.
 *
 * Speed model: one placement click every {@link #interval()} ticks. The floor is {@link #MIN_DELAY} ticks, which is the
 * item-use cooldown vanilla applies while a player holds right-click, so the mod never clicks faster than a person can.
 * Confirmation of earlier placements runs in parallel instead of blocking the next click, and the next block is chosen
 * (and the view turned toward it) while the interval is still running.
 */
public final class SurvivalBuilder {
    public static final int MIN_DELAY=4,MAX_DELAY=200;
    private static final int POOL=12,SCAN=192,MAX_PENDING=6,CONFIRM_TICKS=8,FAIL_TICKS=40;

    public boolean active,walking=false,refill=false;
    /** Ticks between placement clicks (clamped to at least {@link #MIN_DELAY}). */
    public int delay=6;
    public String status="Ready";

    private record Pending(Blueprint.Target target,int since) {}

    private int tick,cursor,placed,stuckTicks,armedTick,notBefore,idleUntil,settledLook,waypoint,unplaceable;
    private int lastAttemptTick=-1000;
    private boolean ownsMovement;
    private List<WalkPath.Node> path=List.of();
    private Blueprint.Target aiming,walkTarget,walkCandidate;
    private Vec3d lastPosition,previousTickPosition;
    private final ArrayDeque<Pending> pendings=new ArrayDeque<>();
    private final List<Blueprint.Target> pool=new ArrayList<>();
    private final Map<BlockPos,BlockState> recentPlacements=new LinkedHashMap<>();
    private final Map<BlockPos,Integer> blocked=new HashMap<>();

    public int interval() { return Math.max(MIN_DELAY,delay); }
    public static String speedName(int delay) {
        return delay<=MIN_DELAY?"Fast":delay==6?"Normal":delay==12?"Careful":"Custom";
    }

    public void start(MinecraftClient mc) {
        if (BlueprintClient.blueprint==null) throw new IllegalArgumentException("Import a blueprint first.");
        if (mc.player==null || !mc.player.getWorld().getRegistryKey().getValue().toString().equals(BlueprintClient.dimension))
            throw new IllegalArgumentException("Return to the blueprint dimension.");
        if(mc.player.getAbilities().flying || mc.player.isFallFlying() || mc.player.isSpectator())
            throw new IllegalArgumentException("Land and turn off flying before starting the builder.");
        if(!mc.player.isOnGround()) throw new IllegalArgumentException("Stand on solid ground before starting.");
        previousTickPosition=mc.player.getPos();recentPlacements.clear();pendings.clear();pool.clear();walkCandidate=null;
        active=true;cursor=0;armedTick=tick+6;notBefore=0;idleUntil=0;settledLook=0;status="Planning survival placements";
    }
    public void pause(String reason) {
        active=false;status=reason;path=List.of();walkTarget=null;walkCandidate=null;aiming=null;pool.clear();pendings.clear();
        release(MinecraftClient.getInstance());
    }
    public void reset() {
        pause("Ready");blocked.clear();recentPlacements.clear();previousTickPosition=null;
        placed=cursor=tick=notBefore=idleUntil=unplaceable=0;lastAttemptTick=-1000;
    }
    public int placed() { return placed; }
    public int unplaceable() { return unplaceable; }

    public void tick(MinecraftClient mc) {
        tick++;
        if (!active || mc.player==null || mc.world==null || mc.interactionManager==null) return;
        if (!mc.isWindowFocused()) { pause("Paused: game lost focus");return; }
        if (!mc.player.isAlive() || mc.player.getHealth()<8 || mc.player.getHungerManager().getFoodLevel()<6) { pause("Paused: health or food is low");return; }
        if (!mc.world.getRegistryKey().getValue().toString().equals(BlueprintClient.dimension)) { pause("Paused: dimension changed");return; }
        if (mc.currentScreen!=null || mc.player.currentScreenHandler!=mc.player.playerScreenHandler) { pause("Paused: a screen was opened");return; }
        if (tick>armedTick && manualInput(mc)) { pause("Paused: manual controls");return; }
        if(mc.player.getAbilities().flying || mc.player.isFallFlying() || mc.player.isSpectator()) { pause("Paused: flying is not supported");return; }
        if (mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.hasVehicle() || !mc.player.isOnGround()) {
            pause("Paused: stand on dry solid ground");return;
        }
        if(previousTickPosition!=null && mc.player.getPos().squaredDistanceTo(previousTickPosition)>2.25){
            pause("Paused: unexpected player movement or server correction");return;
        }
        previousTickPosition=mc.player.getPos();
        if(tick%2==0) for(var entry:recentPlacements.entrySet()){
            if(mc.world.isChunkLoaded(entry.getKey()) && !PlacementPlanner.matches(mc.world.getBlockState(entry.getKey()),entry.getValue())){
                pause("Paused: a placed block changed or was corrected. Check server permission.");return;
            }
        }
        if (!pendings.isEmpty() && checkPendings(mc)) return;
        if (aiming!=null) { aimAndPlace(mc);return; }
        if (!path.isEmpty()) { follow(mc);return; }
        release(mc);
        if (tick<idleUntil || pendings.size()>=MAX_PENDING) return;
        if (choose(mc)) return;

        List<Blueprint.Target> targets=BlueprintClient.targets;
        if (walking && walkCandidate!=null) {
            if (!workable(mc,walkCandidate)) walkCandidate=null;
            else if (pool.isEmpty() && tick%15==0) { Blueprint.Target c=walkCandidate;walkCandidate=null;navigate(mc,c);return; }
        }
        if (cursor>=targets.size() && pool.isEmpty()) {
            cursor=0;
            int unmatched=0,noItem=0;
            for (Blueprint.Target t:targets) {
                if (PlacementPlanner.item(t)==Items.AIR) { noItem++;continue; }
                if (!mc.world.isChunkLoaded(t.pos()) || !PlacementPlanner.matches(mc.world.getBlockState(t.pos()),t.state())) unmatched++;
            }
            unplaceable=noItem;
            if (unmatched==0 && pendings.isEmpty()) {
                pause("Blueprint complete"+(noItem>0?" ("+noItem+" positions have no placeable item)":""));BlueprintClient.message(status);
            } else if (unmatched>0) {
                status="Waiting: "+unmatched+" remaining. "+(walking?"No reachable route, support or item yet."
                        :"Move closer to the unfinished part, or add items/support.");
            }
            idleUntil=tick+10;
        }
    }

    private boolean checkPendings(MinecraftClient mc) {
        for (Iterator<Pending> it=pendings.iterator();it.hasNext();) {
            Pending p=it.next();
            int age=tick-p.since();
            if (age>=CONFIRM_TICKS && PlacementPlanner.matches(mc.world.getBlockState(p.target().pos()),p.target().state())) {
                it.remove();
                recentPlacements.put(p.target().pos(),p.target().state());
                if (recentPlacements.size()>24) recentPlacements.remove(recentPlacements.keySet().iterator().next());
                placed++;status="Building: "+placed+" placements observed";
            } else if (age>FAIL_TICKS) {
                blocked.put(p.target().pos(),tick+600);
                pause("Paused: placement did not stick. Check server rules, lag or protection.");
                return true;
            }
        }
        return false;
    }

    /** True when the target is loaded, unfinished, replaceable, not recently failed and its item is in reach of the inventory rules. */
    private boolean workable(MinecraftClient mc,Blueprint.Target t) {
        BlockPos pos=t.pos();
        if (!mc.world.isChunkLoaded(pos) || blocked.getOrDefault(pos,0)>tick) return false;
        BlockState state=mc.world.getBlockState(pos);
        if (PlacementPlanner.matches(state,t.state()) || !state.isReplaceable()) return false;
        Item item=PlacementPlanner.item(t);
        return item!=Items.AIR && inventorySlot(mc,item)>=0;
    }

    /** Fills a small pool of nearby placeable targets and picks the one needing the least head turning. */
    private boolean choose(MinecraftClient mc) {
        List<Blueprint.Target> targets=BlueprintClient.targets;
        if (targets.isEmpty()) return false;
        Vec3d eye=mc.player.getEyePos();
        double reach=Math.min(4.5,mc.player.getBlockInteractionRange()),near=(reach+1.8)*(reach+1.8);
        int scanned=0;
        while (pool.size()<POOL && scanned++<SCAN && cursor<targets.size()) {
            Blueprint.Target t=targets.get(cursor++);
            if (!workable(mc,t)) continue;
            double d=t.pos().getSquaredDistance(eye);
            if (d<=near) pool.add(t);
            else if (walking) noteWalkCandidate(mc,t,d,eye);
        }
        Blueprint.Target best=null;double bestCost=Double.MAX_VALUE;
        float yaw=mc.player.getYaw(),pitch=mc.player.getPitch();
        for (Iterator<Blueprint.Target> it=pool.iterator();it.hasNext();) {
            Blueprint.Target t=it.next();
            if (!workable(mc,t)) { it.remove();continue; }
            PlacementPlanner.Plan plan=PlacementPlanner.find(mc,t,eye);
            if (plan==null) {
                it.remove();
                if (walking) noteWalkCandidate(mc,t,t.pos().getSquaredDistance(eye),eye);
                continue;
            }
            double cost=Math.abs(MathHelper.wrapDegrees(plan.yaw()-yaw))+Math.abs(plan.pitch()-pitch);
            if (cost<bestCost) { best=t;bestCost=cost; }
        }
        if (best==null) return false;
        pool.remove(best);aiming=best;settledLook=0;status="Turning toward a reachable placement";
        return true;
    }
    private void noteWalkCandidate(MinecraftClient mc,Blueprint.Target t,double d,Vec3d eye) {
        if (d>=32*32 || !PlacementPlanner.hasSupport(mc,t.pos())) return;
        if (walkCandidate==null || d<walkCandidate.pos().getSquaredDistance(eye)) walkCandidate=t;
    }

    private void aimAndPlace(MinecraftClient mc){
        release(mc);
        if(PlacementPlanner.matches(mc.world.getBlockState(aiming.pos()),aiming.state())){aiming=null;return;}
        var plan=PlacementPlanner.find(mc,aiming,mc.player.getEyePos());
        if(plan==null){aiming=null;settledLook=0;return;}
        turn(mc,plan.yaw(),plan.pitch());
        if(Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()-plan.yaw()))>.5 || Math.abs(mc.player.getPitch()-plan.pitch())>.5){settledLook=0;return;}
        // The normal client movement update reports the settled view on the next tick, before the click is sent.
        if(++settledLook<2 || tick<notBefore || tick-lastAttemptTick<interval())return;
        HitResult ray=mc.player.raycast(Math.min(4.5,mc.player.getBlockInteractionRange()),1.0f,false);
        if(ray.getType()!=HitResult.Type.BLOCK || !(ray instanceof BlockHitResult actual) || !actual.getBlockPos().equals(plan.hit().getBlockPos()) || actual.getSide()!=plan.hit().getSide()){
            aiming=null;settledLook=0;status="Waiting: placement is not under the crosshair";return;
        }
        if(mc.crosshairTarget!=null && mc.crosshairTarget.getType()==HitResult.Type.ENTITY){
            settledLook=0;status="Waiting: an entity is in the way of the crosshair";return;
        }
        place(mc,aiming,plan,actual);
    }
    private static void turn(MinecraftClient mc,float yaw,float pitch){
        float dy=MathHelper.wrapDegrees(yaw-mc.player.getYaw()),dp=pitch-mc.player.getPitch();
        mc.player.setYaw(mc.player.getYaw()+ease(dy,32f));
        mc.player.setPitch(mc.player.getPitch()+ease(dp,22f));
    }
    /** Eases toward the target: big turns take large steps, the last degree or two snaps into place. */
    private static float ease(float diff,float max){
        float a=Math.abs(diff);
        if(a<=1.5f) return diff;
        return Math.copySign(Math.min(a,Math.min(max,Math.max(3f,a*.6f))),diff);
    }
    private void place(MinecraftClient mc,Blueprint.Target target,PlacementPlanner.Plan plan,BlockHitResult actual) {
        int slot=inventorySlot(mc,plan.item());
        if (slot<0) { aiming=null;status="Waiting: required item is no longer available";return; }
        if (slot>=9) {
            if (!refill) { aiming=null;return; }
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,slot,8,SlotActionType.SWAP,mc.player);
            mc.player.getInventory().selectedSlot=8;notBefore=tick+3;return;
        }
        // Select the item one tick before using it, like a player pressing a hotbar key and then clicking.
        if (mc.player.getInventory().selectedSlot!=slot) { mc.player.getInventory().selectedSlot=slot;notBefore=tick+1;return; }
        var context=new ItemPlacementContext(mc.player,Hand.MAIN_HAND,mc.player.getMainHandStack(),actual);
        BlockState predicted=((BlockItem)plan.item()).getBlock().getPlacementState(context);
        if(!context.getBlockPos().equals(target.pos()) || predicted==null || !PlacementPlanner.matches(predicted,target.state())){
            aiming=null;status="Waiting: crosshair placement state does not match";return;
        }
        lastAttemptTick=tick;
        var result=mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,actual);
        aiming=null;
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);pendings.add(new Pending(target,tick));
            status="Placing at "+target.pos().toShortString();
        } else { pause("Paused: placement was rejected. Inspect the position before resuming."); }
    }
    private int inventorySlot(MinecraftClient mc,Item item) {
        int limit=refill?36:9;
        for (int i=0;i<limit;i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private void navigate(MinecraftClient mc,Blueprint.Target target) {
        Set<WalkPath.Node> goals=new HashSet<>();
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) for (int y=-3;y<=0;y++) {
            var node=new WalkPath.Node(target.pos().getX()+x,target.pos().getY()+y,target.pos().getZ()+z);
            if (stand(mc,node) && PlacementPlanner.find(mc,target,new Vec3d(node.x()+.5,node.y()+1.62,node.z()+.5))!=null) goals.add(node);
        }
        var start=new WalkPath.Node(mc.player.getBlockX(),mc.player.getBlockY(),mc.player.getBlockZ());
        path=WalkPath.find(new WalkPath.Grid() {
            public boolean stand(WalkPath.Node p) { return SurvivalBuilder.stand(mc,p); }
            public boolean jumpClear(WalkPath.Node p) { return clear(mc,new BlockPos(p.x(),p.y()+2,p.z())); }
        },start,goals,32,4096,false);
        if (path.size()<2) { path=List.of();blocked.put(target.pos(),tick+200);return; }
        waypoint=1;walkTarget=target;lastPosition=mc.player.getPos();stuckTicks=0;status="Walking to a placement position";
    }
    private void follow(MinecraftClient mc) {
        if (waypoint>=path.size()) { release(mc);path=List.of();cursor=0;idleUntil=tick+4;return; }
        var p=path.get(waypoint);Vec3d goal=new Vec3d(p.x()+.5,p.y(),p.z()+.5);
        if (!stand(mc,p)) { release(mc);path=List.of();cursor=0;return; }
        if (Math.abs(mc.player.getY()-p.y())<.3 && mc.player.getPos().multiply(1,0,1).squaredDistanceTo(goal.multiply(1,0,1))<.02) {
            waypoint++;return;
        }
        if (++stuckTicks%20==0) {
            if (mc.player.getPos().squaredDistanceTo(lastPosition)<.04) {
                blocked.put(walkTarget.pos(),tick+300);release(mc);path=List.of();status="Path blocked; replanning";return;
            }
            lastPosition=mc.player.getPos();
        }
        float[] angles=PlacementPlanner.look(mc.player.getEyePos(),goal.add(0,1.62,0));
        turn(mc,angles[0],10);
        mc.player.setSprinting(false);
        ownsMovement=true;mc.options.forwardKey.setPressed(Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()-angles[0]))<20);
    }
    private static boolean clear(MinecraftClient mc,BlockPos p) {
        return mc.world.isChunkLoaded(p) && mc.world.getBlockState(p).getCollisionShape(mc.world,p).isEmpty()
                && mc.world.getFluidState(p).isEmpty() && !danger(mc.world.getBlockState(p));
    }
    private static boolean stand(MinecraftClient mc,WalkPath.Node n) {
        BlockPos p=new BlockPos(n.x(),n.y(),n.z());
        if (!clear(mc,p) || !clear(mc,p.up())) return false;
        BlockState floor=mc.world.getBlockState(p.down());
        return !danger(floor) && floor.isSideSolidFullSquare(mc.world,p.down(),Direction.UP)
                && mc.world.getWorldBorder().contains(p)
                && mc.world.isSpaceEmpty(mc.player,new Box(n.x()+.2,n.y(),n.z()+.2,n.x()+.8,n.y()+1.8,n.z()+.8));
    }
    private static boolean danger(BlockState s) {
        Block b=s.getBlock();
        return b==Blocks.LAVA || b==Blocks.FIRE || b==Blocks.SOUL_FIRE || b==Blocks.MAGMA_BLOCK || b==Blocks.CACTUS
                || b==Blocks.CAMPFIRE || b==Blocks.SOUL_CAMPFIRE || b==Blocks.SWEET_BERRY_BUSH || b==Blocks.POWDER_SNOW
                || b==Blocks.WITHER_ROSE || b==Blocks.POINTED_DRIPSTONE;
    }
    private static boolean manualInput(MinecraftClient mc) {
        long window=mc.getWindow().getHandle();
        for (int key:new int[]{GLFW.GLFW_KEY_W,GLFW.GLFW_KEY_A,GLFW.GLFW_KEY_S,GLFW.GLFW_KEY_D,GLFW.GLFW_KEY_SPACE,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_KEY_LEFT_CONTROL})
            if (InputUtil.isKeyPressed(window,key)) return true;
        return GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS;
    }
    private void release(MinecraftClient mc) {
        if (ownsMovement && mc.options!=null) { mc.options.forwardKey.setPressed(false); }
        ownsMovement=false;
    }
}
