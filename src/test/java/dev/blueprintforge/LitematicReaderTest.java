package dev.blueprintforge;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LitematicReaderTest {
    private static NbtCompound vec(int x,int y,int z){NbtCompound n=new NbtCompound();n.putInt("x",x);n.putInt("y",y);n.putInt("z",z);return n;}
    private static NbtCompound region(int x,int size,String block){
        NbtCompound r=new NbtCompound();r.put("Position",vec(x,0,0));r.put("Size",vec(size,1,1));
        NbtList palette=new NbtList();NbtCompound air=new NbtCompound();air.putString("Name","minecraft:air");palette.add(air);
        NbtCompound solid=new NbtCompound();solid.putString("Name",block);palette.add(solid);r.put("BlockStatePalette",palette);
        long bits=0;for(int i=0;i<Math.abs(size);i++)bits|=1L<<(i*2);r.putLongArray("BlockStates",new long[]{bits});return r;
    }
    private static NbtCompound fixture(){
        NbtCompound root=new NbtCompound();root.putInt("Version",6);NbtCompound regions=new NbtCompound();
        regions.put("negative",region(1,-2,"minecraft:stone"));regions.put("separate",region(4,1,"minecraft:oak_planks"));root.put("Regions",regions);return root;
    }
    @Test void importsMultipleRegionsAndNormalizesNegativeSizes(){
        var result=LitematicData.decode(fixture(),"house.litematic");
        assertEquals(5,result.width());assertEquals(3,result.cells().size());
        assertEquals("minecraft:stone",result.cells().getFirst().state().id());
        assertTrue(result.cells().stream().anyMatch(c->c.x()==4 && c.state().id().equals("minecraft:oak_planks")));
    }
    @Test void readsAnActualGzipNbtFile(@TempDir Path dir)throws Exception{
        Path file=dir.resolve("fixture.litematic");NbtIo.writeCompressed(fixture(),file);
        assertEquals(3,LitematicData.read(file).cells().size());
    }
    @Test void rejectsInvalidIdentifiersAndTruncatedData(){
        var root=fixture();root.getCompound("Regions").put("bad",region(5,1,"INVALID BLOCK"));
        final var unknown=root;assertThrows(IllegalArgumentException.class,()->LitematicData.decode(unknown,"bad.litematic"));
        root=fixture();root.getCompound("Regions").getCompound("negative").putLongArray("BlockStates",new long[]{});
        final var broken=root;assertThrows(IllegalArgumentException.class,()->LitematicData.decode(broken,"bad.litematic"));
    }
}
