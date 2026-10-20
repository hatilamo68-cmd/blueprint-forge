package dev.blueprintforge.client;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.Properties;

/** Remembers the builder options between sessions (config/blueprint-forge/settings.properties). */
public final class Settings {
    private Settings(){}
    private static Path file(){return FabricLoader.getInstance().getConfigDir().resolve("blueprint-forge/settings.properties");}
    public static void load(SurvivalBuilder builder){
        try{
            Path f=file();
            if(!Files.isRegularFile(f)) return;
            Properties p=new Properties();
            try(var in=Files.newInputStream(f)){p.load(in);}
            builder.walking=Boolean.parseBoolean(p.getProperty("walking","false"));
            builder.refill=Boolean.parseBoolean(p.getProperty("refill","false"));
            builder.delay=Math.max(SurvivalBuilder.MIN_DELAY,Math.min(SurvivalBuilder.MAX_DELAY,Integer.parseInt(p.getProperty("delay","6").trim())));
            BlueprintClient.visible=Boolean.parseBoolean(p.getProperty("preview","true"));
            BlueprintClient.hud=Boolean.parseBoolean(p.getProperty("hud","true"));
        }catch(Exception ignored){}
    }
    public static void save(SurvivalBuilder builder){
        try{
            Files.createDirectories(file().getParent());
            Properties p=new Properties();
            p.setProperty("walking",Boolean.toString(builder.walking));
            p.setProperty("refill",Boolean.toString(builder.refill));
            p.setProperty("delay",Integer.toString(builder.delay));
            p.setProperty("preview",Boolean.toString(BlueprintClient.visible));
            p.setProperty("hud",Boolean.toString(BlueprintClient.hud));
            try(var out=Files.newOutputStream(file())){p.store(out,"Blueprint Forge settings");}
        }catch(Exception ignored){}
    }
}
