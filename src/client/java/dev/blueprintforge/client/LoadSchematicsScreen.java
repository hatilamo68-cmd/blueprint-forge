package dev.blueprintforge.client;

import dev.blueprintforge.LitematicData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * File browser in the style of Litematica's "Load Schematics": folder navigation and a filter box on the left,
 * a details panel for the selected file on the right, Load / Back at the bottom.
 */
public final class LoadSchematicsScreen extends ForgeScreen {
    private record Entry(Path path,boolean dir,boolean up,String name,long size) {}
    private static final int ROW=20;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    /** null means the virtual top level that lists both schematic folders. */
    private static Path lastDir;

    private final List<Entry> entries=new ArrayList<>();
    private TextFieldWidget search;
    private ButtonWidget load;
    private Path dir=lastDir;
    private int selected=-1,scroll,listX,listY,listW,listH,infoX,infoW,messageX;
    private long lastClickTime;private int lastClickIndex=-1;
    private LitematicData.Meta meta;
    private String infoError="",error="";

    public LoadSchematicsScreen(Screen parent){super("Load Schematics",parent);}

    @Override protected void init(){
        listX=12;listY=48;
        infoW=width<460?0:Math.min(260,width/3);
        infoX=width-12-infoW;
        listW=(infoW==0?width-12:infoX-12)-listX;
        listH=height-listY-34;
        String text=search==null?"":search.getText();
        search=new TextFieldWidget(textRenderer,listX,22,Math.min(170,listW),20,Text.literal("Filter"));
        search.setMaxLength(100);search.setPlaceholder(Text.literal("Filter files"));search.setText(text);
        search.setChangedListener(value->refreshList());addDrawableChild(search);
        backButton();
        int back=Math.max(60,textWidth("Back")+20);
        load=button("Load Schematic",12+back+8,height-26,Math.max(120,textWidth("Load Schematic")+30),this::loadSelected);
        load.active=false;
        messageX=12+back+8+Math.max(120,textWidth("Load Schematic")+30)+12;
        refreshList();
    }
    private String rootLabel(Path root){
        return root.equals(BlueprintClient.folder())?"config/blueprint-forge/blueprints":"schematics";
    }
    private void refreshList(){
        entries.clear();selected=-1;scroll=0;meta=null;infoError="";
        String q=search==null?"":search.getText().strip().toLowerCase(Locale.ROOT);
        if(dir==null){
            for(Path root:BlueprintClient.folders()){
                try{Files.createDirectories(root);}catch(Exception ignored){}
                if(Files.isDirectory(root)) entries.add(new Entry(root,true,false,rootLabel(root),0));
            }
        } else {
            boolean isRoot=BlueprintClient.folders().contains(dir);
            entries.add(new Entry(isRoot?null:dir.getParent(),true,true,"..",0));
            List<Entry> found=new ArrayList<>();
            try(var files=Files.list(dir)){
                files.filter(p->!Files.isSymbolicLink(p)).forEach(p->{
                    String name=p.getFileName().toString(),lower=name.toLowerCase(Locale.ROOT);
                    boolean folder=Files.isDirectory(p);
                    if(!folder && !(lower.endsWith(".litematic") || lower.endsWith(".json"))) return;
                    if(!q.isEmpty() && !lower.contains(q)) return;
                    long size=0;try{if(!folder)size=Files.size(p);}catch(Exception ignored){}
                    found.add(new Entry(p,folder,false,name,size));
                });
            }catch(Exception e){error="Could not list folder: "+e.getMessage();}
            found.sort(Comparator.<Entry,Boolean>comparing(e->!e.dir()).thenComparing(e->e.name().toLowerCase(Locale.ROOT)));
            entries.addAll(found);
        }
        if(load!=null) load.active=false;
    }
    private int visibleRows(){return Math.max(1,(listH-2)/ROW);}
    private void clampScroll(){scroll=Math.max(0,Math.min(scroll,Math.max(0,entries.size()-visibleRows())));}

    private void select(int index){
        Entry e=entries.get(index);
        if(e.dir()){
            dir=e.path();lastDir=dir;error="";refreshList();return;
        }
        selected=index;meta=null;infoError="";
        if(load!=null) load.active=true;
        if(e.name().toLowerCase(Locale.ROOT).endsWith(".litematic")){
            try{meta=LitematicData.meta(e.path());}catch(Exception ex){infoError="Could not read header: "+(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());}
        }
    }
    private void loadSelected(){
        if(selected<0||selected>=entries.size()) return;
        Entry e=entries.get(selected);
        try{
            BlueprintClient.loadPath(e.path());
            client.setScreen(new PlacementsScreen(parent));
        }catch(Exception ex){
            error=ex.getMessage()==null?"Could not read blueprint.":ex.getMessage();
        }
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0 && inside(mx,my,listX,listY,listW,listH)){
            int i=scroll+(int)((my-listY-1)/ROW);
            if(i>=0 && i<entries.size()){
                long now=System.currentTimeMillis();
                boolean doubleClick=i==lastClickIndex && now-lastClickTime<400 && !entries.get(i).dir();
                lastClickIndex=i;lastClickTime=now;
                select(i);
                if(doubleClick && selected==i) loadSelected();
            }
            return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        scroll-=(int)Math.signum(vertical)*3;clampScroll();return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_PAGE_DOWN){scroll+=visibleRows();clampScroll();return true;}
        if(key==GLFW.GLFW_KEY_PAGE_UP){scroll-=visibleRows();clampScroll();return true;}
        return super.keyPressed(key,scan,modifiers);
    }

    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        String where=dir==null?"Folders":dir.toString();
        int pathX=listX+Math.min(170,listW)+10;
        draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth("Directory: "+where,Math.max(20,listX+listW-pathX)),pathX,28,GREY);
        outline(draw,listX,listY,listW,listH,PANEL);
        draw.enableScissor(listX+1,listY+1,listX+listW-1,listY+listH-1);
        int end=Math.min(entries.size(),scroll+visibleRows());
        for(int i=scroll;i<end;i++){
            Entry e=entries.get(i);int y=listY+1+(i-scroll)*ROW;
            boolean hover=inside(mouseX,mouseY,listX+1,y,listW-2,ROW);
            rowBackground(draw,listX+1,y,listW-2,ROW,i,hover,i==selected);
            draw.drawItem(new ItemStack(e.up()?Items.ARROW:e.dir()?Items.CHEST:Items.PAPER),listX+4,y+2);
            String label=e.up()?"../":e.dir()?e.name()+"/":e.name();
            draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(label,listW-28-(e.dir()?0:60)),listX+24,y+6,e.dir()?YELLOW:WHITE);
            if(!e.dir()){
                String size=e.size()<1024?e.size()+" B":e.size()<1048576?(e.size()/1024)+" KiB":String.format(Locale.ROOT,"%.1f MiB",e.size()/1048576.0);
                draw.drawTextWithShadow(textRenderer,size,listX+listW-8-textWidth(size),y+6,GREY);
            }
        }
        draw.disableScissor();
        if(entries.isEmpty()) draw.drawTextWithShadow(textRenderer,"No schematics found.",listX+8,listY+8,GREY);
        if(entries.size()>visibleRows()){
            int track=listH-2,thumb=Math.max(8,track*visibleRows()/entries.size());
            int thumbY=listY+1+(track-thumb)*scroll/Math.max(1,entries.size()-visibleRows());
            draw.fill(listX+listW-4,thumbY,listX+listW-1,thumbY+thumb,0xFFC0C0C0);
        }
        if(!error.isEmpty()) draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(error,Math.max(20,width-messageX-12)),messageX,height-20,RED);
        if(infoW>0) drawInfo(draw);
    }
    private void drawInfo(DrawContext draw){
        outline(draw,infoX,listY,infoW,listH,PANEL);
        int x=infoX+8,y=listY+8,max=infoW-16;
        if(selected<0||selected>=entries.size()){
            draw.drawTextWithShadow(textRenderer,"Select a schematic to see its details.",x,y,GREY);return;
        }
        Entry e=entries.get(selected);
        line(draw,"File",e.name(),x,y,max);y+=14;
        if(meta!=null){
            if(!meta.name().isBlank()){line(draw,"Name",meta.name(),x,y,max);y+=14;}
            if(!meta.author().isBlank()){line(draw,"Author",meta.author(),x,y,max);y+=14;}
            if(meta.created()>0){line(draw,"Created",DATE.format(Instant.ofEpochMilli(meta.created())),x,y,max);y+=14;}
            if(meta.modified()>0){line(draw,"Modified",DATE.format(Instant.ofEpochMilli(meta.modified())),x,y,max);y+=14;}
            line(draw,"Total blocks",Integer.toString(meta.totalBlocks()),x,y,max);y+=14;
            line(draw,"Total volume",Long.toString(meta.totalVolume()),x,y,max);y+=14;
            line(draw,"Enclosing size",meta.sizeX()+" x "+meta.sizeY()+" x "+meta.sizeZ(),x,y,max);y+=14;
            line(draw,"Regions",Integer.toString(meta.regions()),x,y,max);y+=14;
            line(draw,"Format version",Integer.toString(meta.version()),x,y,max);y+=14;
            if(!meta.description().isBlank()){line(draw,"Description",meta.description(),x,y,max);}
        } else if(!infoError.isEmpty()){
            draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(infoError,max),x,y,RED);
        } else if(e.name().toLowerCase(Locale.ROOT).endsWith(".json")){
            draw.drawTextWithShadow(textRenderer,"Blueprint Forge JSON blueprint",x,y,GREY);
        }
    }
    private void line(DrawContext draw,String key,String value,int x,int y,int max){
        String head=key+": ";
        draw.drawTextWithShadow(textRenderer,head,x,y,GREY);
        draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(value,Math.max(10,max-textWidth(head))),x+textWidth(head),y,WHITE);
    }
}
