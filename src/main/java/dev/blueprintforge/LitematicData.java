package dev.blueprintforge;

import net.minecraft.nbt.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Independent format reader; does not depend on Litematica or MaLiLib. */
public final class LitematicData {
    public record State(String id, Map<String,String> properties) {}
    public record Cell(int x,int y,int z,State state) {}
    public record Result(String name,int width,int height,int depth,List<Cell> cells,int ignoredBlockEntities,int ignoredEntities) {}
    private record Region(NbtCompound nbt, int x, int y, int z, int w, int h, int d) {}
    private static final Set<String> AIR=Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air","minecraft:structure_void");
    /** Header information shown in the schematic browser (read lazily, only for the selected file). */
    public record Meta(String name,String author,String description,long created,long modified,int totalBlocks,long totalVolume,
                       int sizeX,int sizeY,int sizeZ,int regions,int version) {}
    private LitematicData() {}
    public static Meta meta(Path path) throws IOException {
        if (Files.size(path)>16*1024*1024) throw new IllegalArgumentException("Compressed schematic exceeds 16 MiB.");
        NbtCompound root=NbtIo.readCompressed(path, NbtSizeTracker.of(64L*1024*1024));
        NbtCompound m=root.getCompound("Metadata"),size=m.getCompound("EnclosingSize");
        return new Meta(m.getString("Name"),m.getString("Author"),m.getString("Description"),m.getLong("TimeCreated"),m.getLong("TimeModified"),
                m.getInt("TotalBlocks"),m.getLong("TotalVolume"),size.getInt("x"),size.getInt("y"),size.getInt("z"),
                m.contains("RegionCount")?m.getInt("RegionCount"):root.getCompound("Regions").getKeys().size(),root.getInt("Version"));
    }
    public static Result read(Path path) throws IOException {
        if (Files.size(path)>16*1024*1024) throw new IllegalArgumentException("Compressed schematic exceeds 16 MiB.");
        return decode(NbtIo.readCompressed(path, NbtSizeTracker.of(64L*1024*1024)), path.getFileName().toString());
    }
    public static Result decode(NbtCompound root, String filename) {
        int version=root.getInt("Version");
        if (version<4 || version>7) throw new IllegalArgumentException("Supported .litematic versions: 4..7. Found " + version);
        NbtCompound all=root.getCompound("Regions");
        if (all.isEmpty() || all.getKeys().size()>256) throw new IllegalArgumentException("Invalid region count.");
        List<Region> regions=new ArrayList<>();
        int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        long total=0;
        for (String key:new TreeSet<>(all.getKeys())) {
            NbtCompound n=all.getCompound(key),p=n.getCompound("Position"),s=n.getCompound("Size");
            int sx=s.getInt("x"),sy=s.getInt("y"),sz=s.getInt("z");
            if (sx==0 || sy==0 || sz==0 || Math.abs((long)sx)>Blueprint.MAX_VOLUME
                    || Math.abs((long)sy)>Blueprint.MAX_VOLUME || Math.abs((long)sz)>Blueprint.MAX_VOLUME)
                throw new IllegalArgumentException("Invalid region dimensions.");
            int w=Math.abs(sx),h=Math.abs(sy),d=Math.abs(sz);
            long volume=(long)w*h*d;
            if (volume>Blueprint.MAX_VOLUME || (total+=volume)>Blueprint.MAX_VOLUME)
                throw new IllegalArgumentException("Total region volume exceeds " + Blueprint.MAX_VOLUME);
            int x=PackedPalette.regionMin(p.getInt("x"),sx),y=PackedPalette.regionMin(p.getInt("y"),sy),z=PackedPalette.regionMin(p.getInt("z"),sz);
            int ex=Math.addExact(x,w),ey=Math.addExact(y,h),ez=Math.addExact(z,d);
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,ex);maxY=Math.max(maxY,ey);maxZ=Math.max(maxZ,ez);
            regions.add(new Region(n,x,y,z,w,h,d));
        }
        long width=(long)maxX-minX,height=(long)maxY-minY,depth=(long)maxZ-minZ;
        if (width<1 || height<1 || depth<1 || width>Blueprint.MAX_VOLUME || height>Blueprint.MAX_VOLUME || depth>Blueprint.MAX_VOLUME
                || width*height>Blueprint.MAX_VOLUME || width*height*depth>Blueprint.MAX_VOLUME)
            throw new IllegalArgumentException("Combined schematic bounds exceed the volume limit.");
        Map<Geometry.Point,Cell> cells=new LinkedHashMap<>();
        int blockEntities=0,entities=0;
        for (Region r:regions) {
            NbtList paletteTag=r.nbt.getList("BlockStatePalette",NbtElement.COMPOUND_TYPE);
            int bits=PackedPalette.bits(paletteTag.size());
            List<State> palette=new ArrayList<>();
            for (int i=0;i<paletteTag.size();i++) palette.add(state(paletteTag.getCompound(i)));
            long[] data=r.nbt.getLongArray("BlockStates");
            long expected=((long)r.w*r.h*r.d*bits+63)/64;
            if (data.length<expected) throw new IllegalArgumentException("Truncated block-state data.");
            for (int y=0;y<r.h;y++) for (int z=0;z<r.d;z++) for (int x=0;x<r.w;x++) {
                int index=PackedPalette.get(data,bits,(y*r.d+z)*r.w+x);
                if (index>=palette.size()) throw new IllegalArgumentException("Palette index is out of bounds.");
                State state=palette.get(index);
                if (AIR.contains(state.id)) continue;
                int bx=r.x-minX+x,by=r.y-minY+y,bz=r.z-minZ+z;
                cells.put(new Geometry.Point(bx,by,bz),new Cell(bx,by,bz,state));
                if (cells.size()>Blueprint.MAX_BLOCKS) throw new IllegalArgumentException("Too many non-air blocks (limit " + Blueprint.MAX_BLOCKS + ").");
            }
            blockEntities+=r.nbt.getList("TileEntities",NbtElement.COMPOUND_TYPE).size();
            entities+=r.nbt.getList("Entities",NbtElement.COMPOUND_TYPE).size();
        }
        String name=filename.replaceFirst("(?i)\\.litematic$","");
        if (name.length()>64) name=name.substring(0,64);
        if(cells.isEmpty()) throw new IllegalArgumentException("Schematic has no non-air blocks.");
        return new Result(name,(int)width,(int)height,(int)depth,List.copyOf(cells.values()),blockEntities,entities);
    }
    private static State state(NbtCompound tag) {
        String id=tag.getString("Name");
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid block identifier: " + id);
        Map<String,String> properties=new LinkedHashMap<>();
        NbtCompound n=tag.getCompound("Properties");
        for(String key:n.getKeys()) properties.put(key,n.getString(key));
        return new State(id,Map.copyOf(properties));
    }
}
