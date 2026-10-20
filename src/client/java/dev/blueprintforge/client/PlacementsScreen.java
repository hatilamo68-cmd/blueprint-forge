package dev.blueprintforge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.List;

/** Lists every loaded schematic placement: click a row to make it active, Configure to move or rotate it. */
public final class PlacementsScreen extends ForgeScreen {
    private static final int ROW=24,BW_REMOVE=56,BW_CONFIG=72,BW_SELECT=60;
    private int listX,listY,listW,listH,scroll,removeX,configX,selectX;

    public PlacementsScreen(Screen parent){super("Schematic Placements",parent);}

    @Override protected void init(){
        listX=12;listY=30;listW=width-24;listH=height-listY-34;
        removeX=listX+listW-6-BW_REMOVE;configX=removeX-4-BW_CONFIG;selectX=configX-4-BW_SELECT;
        backButton();
        int back=Math.max(60,textWidth("Back")+20),loadW=Math.max(120,textWidth("Load Schematics")+30);
        button("Load Schematics",12+back+8,height-26,loadW,()->client.setScreen(new LoadSchematicsScreen(this)));
        button("Unload All",12+back+8+loadW+8,height-26,Math.max(90,textWidth("Unload All")+30),()->{BlueprintClient.reset();scroll=0;});
    }
    private int visibleRows(){return Math.max(1,(listH-2)/ROW);}
    private void clampScroll(){scroll=Math.max(0,Math.min(scroll,Math.max(0,BlueprintClient.placements.size()-visibleRows())));}

    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        outline(draw,listX,listY,listW,listH,PANEL);
        List<Placement> list=BlueprintClient.placements;
        clampScroll();
        if(list.isEmpty()){
            draw.drawTextWithShadow(textRenderer,"No schematics are loaded. Use Load Schematics to add one.",listX+8,listY+8,GREY);
            return;
        }
        draw.enableScissor(listX+1,listY+1,listX+listW-1,listY+listH-1);
        int end=Math.min(list.size(),scroll+visibleRows());
        for(int i=scroll;i<end;i++){
            Placement p=list.get(i);int y=listY+1+(i-scroll)*ROW;boolean active=i==BlueprintClient.activeIndex;
            boolean hover=inside(mouseX,mouseY,listX+1,y,listW-2,ROW);
            rowBackground(draw,listX+1,y,listW-2,ROW,i,hover,active);
            int textMax=Math.max(40,selectX-listX-16);
            draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(p.name+(active?"  (active)":""),textMax),listX+8,y+3,active?CYAN:WHITE);
            var o=active?BlueprintClient.origin:p.origin;int rot=active?BlueprintClient.rotation:p.rotation,layer=active?BlueprintClient.layer:p.layer;
            draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth("Origin "+o.getX()+", "+o.getY()+", "+o.getZ()+"   Rotation "+rot*90
                    +"   Layer "+(layer<0?"ALL":layer)+"   Blocks "+p.blueprint.cells().size(),textMax),listX+8,y+13,GREY);
            int by=y+3;
            fakeButton(draw,selectX,by,BW_SELECT,18,active?"Active":"Select",inside(mouseX,mouseY,selectX,by,BW_SELECT,18),!active);
            fakeButton(draw,configX,by,BW_CONFIG,18,"Configure",inside(mouseX,mouseY,configX,by,BW_CONFIG,18),true);
            fakeButton(draw,removeX,by,BW_REMOVE,18,"Remove",inside(mouseX,mouseY,removeX,by,BW_REMOVE,18),true);
        }
        draw.disableScissor();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0 && inside(mx,my,listX,listY,listW,listH)){
            int i=scroll+(int)((my-listY-1)/ROW);
            if(i>=0 && i<BlueprintClient.placements.size()){
                int by=listY+1+(i-scroll)*ROW+3;
                if(inside(mx,my,removeX,by,BW_REMOVE,18)) BlueprintClient.unload(i);
                else if(inside(mx,my,configX,by,BW_CONFIG,18)) client.setScreen(new PlacementConfigScreen(this,i));
                else BlueprintClient.select(i);
            }
            return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        scroll-=(int)Math.signum(vertical)*2;clampScroll();return true;
    }
}
