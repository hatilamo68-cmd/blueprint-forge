package dev.blueprintforge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** A bordered, scrollable table with a header strip, like Litematica's material list and verifier lists. */
public abstract class TableScreen extends ForgeScreen {
    protected static final int ROW=22,HEADER=16;
    protected int left,right,top,bottom,scroll;

    protected TableScreen(String title,Screen parent){super(title,parent);}

    protected abstract int rows();
    protected abstract void drawHeader(DrawContext draw,int x,int y,int w);
    protected abstract void drawRow(DrawContext draw,int index,int x,int y,int w,boolean hover);

    protected void layoutTable(int topY){left=12;right=width-12;top=topY;bottom=height-34;}
    protected int visibleRows(){return Math.max(1,(bottom-top-HEADER-2)/ROW);}
    protected void clampScroll(){scroll=Math.max(0,Math.min(scroll,Math.max(0,rows()-visibleRows())));}

    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        outline(draw,left,top,right-left,bottom-top,PANEL);
        draw.fill(left+1,top+1,right-1,top+HEADER,0x60FFFFFF);
        drawHeader(draw,left,top+4,right-left);
        clampScroll();
        int bodyTop=top+HEADER+1;
        draw.enableScissor(left+1,bodyTop,right-1,bottom-1);
        int end=Math.min(rows(),scroll+visibleRows());
        for(int i=scroll;i<end;i++){
            int y=bodyTop+(i-scroll)*ROW;
            boolean hover=inside(mouseX,mouseY,left+1,y,right-left-2,ROW);
            rowBackground(draw,left+1,y,right-left-2,ROW,i,hover,false);
            drawRow(draw,i,left,y,right-left,hover);
        }
        draw.disableScissor();
        if(rows()>visibleRows()){
            int track=bottom-bodyTop-1,thumb=Math.max(8,track*visibleRows()/rows());
            int thumbY=bodyTop+(track-thumb)*scroll/Math.max(1,rows()-visibleRows());
            draw.fill(right-4,thumbY,right-1,thumbY+thumb,0xFFC0C0C0);
        }
    }
    protected void number(DrawContext draw,String value,int rightEdge,int y,int colour){
        draw.drawTextWithShadow(textRenderer,value,rightEdge-textRenderer.getWidth(value),y,colour);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        scroll-=(int)Math.signum(vertical)*3;clampScroll();return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_PAGE_DOWN){scroll+=visibleRows();clampScroll();return true;}
        if(key==GLFW.GLFW_KEY_PAGE_UP){scroll-=visibleRows();clampScroll();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
