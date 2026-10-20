package dev.blueprintforge.client;

import dev.blueprintforge.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

public final class BlueprintClient implements ClientModInitializer {
    public static final int MAX_PLACEMENTS=16;
    /** The active placement is mirrored in these fields; the others wait in {@link #placements}. */
    public static Blueprint blueprint;
    public static BlockPos origin=BlockPos.ORIGIN;
    public static String dimension="";
    public static int rotation,layer=-1;
    public static List<Blueprint.Target> targets=List.of();
    public static final SurvivalBuilder BUILDER=new SurvivalBuilder();
    public static final List<Placement> placements=new ArrayList<>();
    public static int activeIndex=-1;
    public static boolean visible=true,hud=true;
    public static int missing,occupied,correct,unloaded;
    private static int scanIndex,scanWait;
    private static int sMissing,sOccupied,sCorrect,sUnloaded;
    private static List<Blueprint.Target> scanList=List.of(),scanShown=new ArrayList<>();
    private static Supplier<Screen> pendingScreen;
    private static List<Blueprint.Target> renderTargets=List.of();

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("blueprint-forge/blueprints");
    }
    public static List<Path> folders() {
        return List.of(folder(),FabricLoader.getInstance().getGameDir().resolve("schematics"));
    }
    @Override public void onInitializeClient() {
        Settings.load(BUILDER);
        ClientCommands.register();
        KeyBinding menu=key("key.blueprintforge.menu",GLFW.GLFW_KEY_G);
        KeyBinding toggle=key("key.blueprintforge.toggle",GLFW.GLFW_KEY_B);
        KeyBinding pause=key("key.blueprintforge.pause",GLFW.GLFW_KEY_P);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(menu.wasPressed()) if(client.player!=null && client.currentScreen==null) client.setScreen(new MainMenuScreen());
            while(toggle.wasPressed()) {visible=!visible;Settings.save(BUILDER);}
            while(pause.wasPressed()) if(client.currentScreen==null) safe(()->{toggleBuild();return null;});
            if(pendingScreen!=null){
                Supplier<Screen> next=pendingScreen;pendingScreen=null;client.setScreen(next.get());
            }
            BUILDER.tick(client);
            if(client.world!=null && client.player!=null && blueprint!=null) countStep(client);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            MinecraftClient mc=MinecraftClient.getInstance();
            if(!visible || mc.world==null || blueprint==null || context.matrixStack()==null || context.consumers()==null
                    || !mc.world.getRegistryKey().getValue().toString().equals(dimension)) return;
            var matrices=context.matrixStack();var camera=context.camera().getPos();
            var lines=context.consumers().getBuffer(RenderLayer.getLines());
            matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
            for(Blueprint.Target t:renderTargets) {
                var state=mc.world.getBlockState(t.pos());
                if(PlacementPlanner.matches(state,t.state())) continue;
                boolean empty=state.isReplaceable();
                WorldRenderer.drawBox(matrices,lines,new Box(t.pos()).expand(.002),empty?.12f:1f,empty?.85f:.26f,empty?1f:.30f,.8f);
            }
            matrices.pop();
        });
        HudRenderCallback.EVENT.register((draw,counter)->{
            var mc=MinecraftClient.getInstance();
            if(!hud || !visible || blueprint==null || mc.world==null || mc.options.hudHidden
                    || !mc.world.getRegistryKey().getValue().toString().equals(dimension)) return;
            int width=Math.min(330,mc.getWindow().getScaledWidth()-16);
            draw.fill(6,6,width+10,74,0xC010202A);
            String[] rows={"BLUEPRINT FORGE / "+blueprint.name(),
                    "Correct "+correct+"  Missing "+missing+"  Wrong "+occupied+"  Unloaded "+unloaded,
                    "Layer "+(layer<0?"ALL":layer)+"  Rotation "+rotation*90+"  Speed "+SurvivalBuilder.speedName(BUILDER.delay)+" ("+BUILDER.interval()+"t)",
                    BUILDER.status,"G: menu   B: preview   P: start/pause"};
            for(int i=0;i<rows.length;i++) draw.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(rows[i],width-4),10,10+i*12,i==0?0xFF63E3FF:0xFFE0EDF4);
        });
    }
    private static KeyBinding key(String name,int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name,InputUtil.Type.KEYSYM,key,"category.blueprintforge"));
    }

    /** Opens a screen on the next client tick, after a chat screen that ran a command has finished closing itself. */
    public static void openLater(Supplier<Screen> screen) { pendingScreen=screen; }

    /** Imports a file by name from the blueprint folders (used by /bf import). */
    public static void load(String filename) throws Exception {
        if(filename.isBlank() || filename.length()>200 || filename.contains("/") || filename.contains("\\") || filename.equals(".") || filename.equals(".."))
            throw new IllegalArgumentException("Enter only a filename from the blueprint folder.");
        Files.createDirectories(folder());
        Path file=null;
        for(Path dir:folders()) if(Files.isRegularFile(dir.resolve(filename)) && !Files.isSymbolicLink(dir.resolve(filename))) {file=dir.resolve(filename);break;}
        if(file==null) throw new IllegalArgumentException("File not found. Put it in config/blueprint-forge/blueprints or schematics.");
        loadPath(file);
    }
    /** Imports a file that the schematic browser listed. */
    public static void loadPath(Path file) throws Exception {
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null || mc.world==null) throw new IllegalArgumentException("Join a world first.");
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file)) throw new IllegalArgumentException("Not a regular file.");
        String filename=file.getFileName().toString();
        BUILDER.pause("Importing blueprint");
        Blueprint loaded;
        if(filename.toLowerCase(Locale.ROOT).endsWith(".litematic")) {
            var result=LitematicReader.read(file);loaded=result.blueprint();
            if(result.ignoredBlockEntities()>0 || result.ignoredEntities()>0)
                message("Imported block states. Container contents, sign text and entities are not copied ("+result.ignoredBlockEntities()+" block-entity tags, "+result.ignoredEntities()+" entities ignored).");
        } else if(filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            if(Files.size(file)>Blueprint.MAX_JSON_BYTES) throw new IllegalArgumentException("JSON exceeds 8 MiB.");
            loaded=Blueprint.fromJson(Files.readString(file));
        } else throw new IllegalArgumentException("Supported files: .litematic and Blueprint Forge .json");
        set(loaded,mc.player.getBlockPos());
        message("Loaded "+loaded.name()+" ("+loaded.cells().size()+" blocks). Set its position in Schematic Placements, then start the builder.");
    }

    /** Adds a new placement and makes it the active one. */
    public static void set(Blueprint b,BlockPos pos) {
        var mc=MinecraftClient.getInstance();
        saveActive();BUILDER.reset();
        if(placements.size()>=MAX_PLACEMENTS) placements.remove(0);
        placements.add(new Placement(b,pos.toImmutable(),mc.world.getRegistryKey().getValue().toString()));
        applyPlacement(placements.size()-1);
    }
    private static void saveActive() {
        if(activeIndex<0 || activeIndex>=placements.size() || blueprint==null) return;
        Placement p=placements.get(activeIndex);
        p.origin=origin;p.rotation=rotation;p.layer=layer;p.dimension=dimension;
    }
    private static void applyPlacement(int index) {
        Placement p=placements.get(index);activeIndex=index;
        blueprint=p.blueprint;origin=p.origin;rotation=p.rotation;layer=p.layer;dimension=p.dimension;
        refresh();
    }
    public static void select(int index) {
        if(index<0 || index>=placements.size() || index==activeIndex) return;
        saveActive();BUILDER.reset();applyPlacement(index);
    }
    public static void unload(int index) {
        if(index<0 || index>=placements.size()) return;
        boolean wasActive=index==activeIndex;
        if(!wasActive) saveActive();
        placements.remove(index);BUILDER.reset();
        if(placements.isEmpty()){clearActive();return;}
        if(wasActive) applyPlacement(Math.min(index,placements.size()-1));
        else if(index<activeIndex) activeIndex--;
    }
    private static void clearActive() {
        blueprint=null;activeIndex=-1;targets=renderTargets=List.of();dimension="";
        origin=BlockPos.ORIGIN;rotation=0;layer=-1;missing=occupied=correct=unloaded=0;scanIndex=scanWait=0;
    }
    public static void refresh() {
        BUILDER.pause("Ready");targets=blueprint==null?List.of():blueprint.targets(origin,rotation,layer);
        visible=true;scanIndex=0;scanWait=0;
    }
    public static void toggleBuild() {
        if(BUILDER.active) BUILDER.pause("Paused by you");else BUILDER.start(MinecraftClient.getInstance());
    }
    public static void message(String text) {
        var p=MinecraftClient.getInstance().player;if(p!=null) p.sendMessage(Text.literal("[Blueprint Forge] "+text),false);
    }
    public static void materials() {
        BUILDER.pause("Paused: viewing materials");
        openLater(()->new MaterialsScreen(null));
    }
    public static void safe(java.util.concurrent.Callable<?> action) {
        try{action.call();}catch(Exception e){message(e.getMessage()==null?"Could not read blueprint.":e.getMessage());}
    }
    /** Counts a slice of the placement each tick so very large blueprints never cause a frame hitch. */
    private static void countStep(MinecraftClient mc) {
        if(!mc.world.getRegistryKey().getValue().toString().equals(dimension)){renderTargets=List.of();return;}
        if(scanWait>0){scanWait--;return;}
        if(scanIndex==0){scanList=targets;sMissing=sOccupied=sCorrect=sUnloaded=0;scanShown=new ArrayList<>();}
        int end=Math.min(scanList.size(),scanIndex+8192);
        for(;scanIndex<end;scanIndex++) {
            Blueprint.Target t=scanList.get(scanIndex);
            if(!mc.world.isChunkLoaded(t.pos())){sUnloaded++;continue;}
            var state=mc.world.getBlockState(t.pos());
            if(PlacementPlanner.matches(state,t.state()))sCorrect++;
            else {
                if(state.isReplaceable())sMissing++;else sOccupied++;
                if(scanShown.size()<4096 && t.pos().getSquaredDistance(mc.player.getPos())<96*96)scanShown.add(t);
            }
        }
        if(scanIndex>=scanList.size()) {
            missing=sMissing;occupied=sOccupied;correct=sCorrect;unloaded=sUnloaded;renderTargets=List.copyOf(scanShown);
            scanIndex=0;scanWait=10;
        }
    }
    /** Removes every loaded schematic. */
    public static void reset(){
        pendingScreen=null;BUILDER.reset();placements.clear();clearActive();
    }
}
