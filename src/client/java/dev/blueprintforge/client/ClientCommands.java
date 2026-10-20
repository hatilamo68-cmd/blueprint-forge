package dev.blueprintforge.client;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import dev.blueprintforge.Blueprint;
import java.nio.file.*;
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class ClientCommands {
    @FunctionalInterface private interface Action {void run() throws Exception;}
    private ClientCommands(){}
    public static void register(){
        ClientCommandRegistrationCallback.EVENT.register((d,access)->{
            var root=literal("bf").executes(c->run(c,()->BlueprintClient.message("import <filename.litematic>, list, demo, here, move <x y z>, rotate <degrees>, layer <-1|n>, build, pause, stop, delay <4..200>, speed <careful|normal|fast>, menu, walk, refill, materials, status, clear")));
            for(String verb:List.of("import","load"))root.then(literal(verb).then(argument("file",StringArgumentType.greedyString()).suggests((context,builder)->{
                for(Path folder:BlueprintClient.folders()) if(Files.isDirectory(folder))try(var files=Files.list(folder)){
                    files.filter(Files::isRegularFile).map(p->p.getFileName().toString()).filter(s->s.endsWith(".litematic")||s.endsWith(".json"))
                            .filter(s->s.startsWith(builder.getRemaining())).limit(100).forEach(builder::suggest);
                }catch(Exception ignored){}return builder.buildFuture();
            }).executes(c->run(c,()->BlueprintClient.load(StringArgumentType.getString(c,"file").trim())))));
            root.then(literal("demo").executes(c->run(c,()->BlueprintClient.set(Blueprint.demo(),mc().player.getBlockPos().add(2,0,2)))));
            root.then(literal("here").executes(c->run(c,()->{require();BlueprintClient.origin=mc().player.getBlockPos();BlueprintClient.refresh();})));
            root.then(literal("move").then(argument("x",IntegerArgumentType.integer(-29999000,29999000))
                    .then(argument("y",IntegerArgumentType.integer(-2048,2048)).then(argument("z",IntegerArgumentType.integer(-29999000,29999000))
                    .executes(c->run(c,()->{require();BlueprintClient.origin=new BlockPos(IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z"));BlueprintClient.refresh();}))))));
            root.then(literal("rotate").then(argument("degrees",IntegerArgumentType.integer(0,270)).executes(c->run(c,()->{
                require();int n=IntegerArgumentType.getInteger(c,"degrees");if(n%90!=0)throw new IllegalArgumentException("Use 0, 90, 180 or 270.");BlueprintClient.rotation=n/90;BlueprintClient.refresh();}))));
            root.then(literal("layer").then(argument("index",IntegerArgumentType.integer(-1)).executes(c->run(c,()->{
                require();int n=IntegerArgumentType.getInteger(c,"index");if(n>=BlueprintClient.blueprint.height())throw new IllegalArgumentException("Layer exceeds blueprint height.");BlueprintClient.layer=n;BlueprintClient.refresh();}))));
            root.then(literal("build").executes(c->run(c,()->BlueprintClient.BUILDER.start(mc()))));
            root.then(literal("pause").executes(c->run(c,BlueprintClient::toggleBuild)));
            root.then(literal("stop").executes(c->run(c,()->BlueprintClient.BUILDER.pause("Stopped by you"))));
            root.then(literal("delay").then(argument("ticks",IntegerArgumentType.integer(SurvivalBuilder.MIN_DELAY,SurvivalBuilder.MAX_DELAY)).executes(c->run(c,()->{
                BlueprintClient.BUILDER.delay=IntegerArgumentType.getInteger(c,"ticks");Settings.save(BlueprintClient.BUILDER);
                BlueprintClient.message("Ticks between placements: "+BlueprintClient.BUILDER.delay+".");}))));
            for(String[] preset:new String[][]{{"careful","12"},{"normal","6"},{"fast",Integer.toString(SurvivalBuilder.MIN_DELAY)}})
                root.then(literal("speed").then(literal(preset[0]).executes(c->run(c,()->{
                    BlueprintClient.BUILDER.delay=Integer.parseInt(preset[1]);Settings.save(BlueprintClient.BUILDER);
                    BlueprintClient.message("Speed: "+preset[0]+" ("+preset[1]+" ticks between placements).");}))));
            root.then(literal("menu").executes(c->run(c,()->BlueprintClient.openLater(MainMenuScreen::new))));
            root.then(literal("walk").executes(c->run(c,()->{BlueprintClient.BUILDER.pause("Walking setting changed");BlueprintClient.BUILDER.walking=!BlueprintClient.BUILDER.walking;Settings.save(BlueprintClient.BUILDER);BlueprintClient.message("Walking: "+BlueprintClient.BUILDER.walking);})));
            root.then(literal("refill").executes(c->run(c,()->{BlueprintClient.BUILDER.refill=!BlueprintClient.BUILDER.refill;Settings.save(BlueprintClient.BUILDER);BlueprintClient.message("Inventory to hotbar refill: "+BlueprintClient.BUILDER.refill);})));
            root.then(literal("materials").executes(c->run(c,BlueprintClient::materials)));
            root.then(literal("status").executes(c->run(c,()->BlueprintClient.message(BlueprintClient.BUILDER.status+" | observed placements "+BlueprintClient.BUILDER.placed()))));
            root.then(literal("clear").executes(c->run(c,BlueprintClient::reset)));
            root.then(literal("list").executes(c->run(c,()->{
                Files.createDirectories(BlueprintClient.folder());
                for(Path folder:BlueprintClient.folders())if(Files.isDirectory(folder))try(var files=Files.list(folder)){
                    files.map(p->p.getFileName().toString()).filter(s->s.endsWith(".litematic")||s.endsWith(".json")).sorted().limit(100).forEach(BlueprintClient::message);
                }
                BlueprintClient.message("Import folder: "+BlueprintClient.folder());
            })));
            d.register(root);
        });
    }
    private static MinecraftClient mc(){return MinecraftClient.getInstance();}
    private static void require(){if(BlueprintClient.blueprint==null)throw new IllegalArgumentException("Import a blueprint first.");}
    private static int run(CommandContext<FabricClientCommandSource> c,Action action){
        try{action.run();return 1;}catch(Exception e){c.getSource().sendError(Text.literal(e.getMessage()==null?"Could not read blueprint.":e.getMessage()));return 0;}
    }
}
