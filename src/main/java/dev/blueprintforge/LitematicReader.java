package dev.blueprintforge;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;

/** Converts independently decoded file data against the running Minecraft block registry. */
public final class LitematicReader {
    public record Result(Blueprint blueprint,int ignoredBlockEntities,int ignoredEntities) {}
    private LitematicReader() {}
    public static Result read(Path path)throws IOException {return resolve(LitematicData.read(path));}
    public static Result decode(NbtCompound root,String filename){return resolve(LitematicData.decode(root,filename));}
    private static Result resolve(LitematicData.Result data){
        List<Blueprint.Cell> cells=new ArrayList<>();
        Map<LitematicData.State,BlockState> states=new HashMap<>();
        for(var c:data.cells()) cells.add(new Blueprint.Cell(c.x(),c.y(),c.z(),states.computeIfAbsent(c.state(),LitematicReader::state)));
        return new Result(new Blueprint(data.name(),data.width(),data.height(),data.depth(),cells),data.ignoredBlockEntities(),data.ignoredEntities());
    }
    private static BlockState state(LitematicData.State tag){
        Identifier id=Identifier.of(tag.id());
        if(!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Unknown block "+id+". Install its mod or use a 1.21.1 schematic.");
        var block=Registries.BLOCK.get(id);BlockState result=block.getDefaultState();
        for(var entry:tag.properties().entrySet()){
            var property=block.getStateManager().getProperty(entry.getKey());
            if(property==null)throw new IllegalArgumentException("Unknown property "+id+"["+entry.getKey()+"]. Version conversion is not supported.");
            result=apply(result,property,entry.getValue());
        }
        return result;
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,Property<T> p,String value){
        return state.with(p,p.parse(value).orElseThrow(()->new IllegalArgumentException("Invalid block property: "+value)));
    }
}
