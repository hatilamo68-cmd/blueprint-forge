package dev.blueprintforge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Shared look for every Blueprint Forge screen, laid out the way Litematica's menus are: the title sits in the top-left
 * corner, content lives in bordered dark panels, rows alternate in shade and highlight on hover, and a Back button sits
 * bottom-left. Panels are drawn from {@link #renderBackground} so they appear under the widgets and are never covered by
 * the vanilla background that {@code Screen.render} draws first.
 */
public abstract class ForgeScreen extends Screen {
    protected static final int WHITE=0xFFFFFFFF,GREY=0xFFB0B0B0,GREEN=0xFF55FF55,RED=0xFFFF5555,YELLOW=0xFFFFFF55,CYAN=0xFF63E3FF,ORANGE=0xFFFFB078;
    protected static final int ROW_A=0x30FFFFFF,ROW_B=0x20FFFFFF,ROW_HOVER=0x70FFFFFF,ROW_SELECTED=0x50FFFFFF,PANEL=0xA0000000,BORDER=0xFFFFFFFF;
    protected final Screen parent;

    protected ForgeScreen(String title,Screen parent){super(Text.literal(title));this.parent=parent;}

    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}

    /** Draws the title and any panels. Subclasses call super first. */
    @Override public void renderBackground(DrawContext draw,int mouseX,int mouseY,float delta){
        super.renderBackground(draw,mouseX,mouseY,delta);
        draw.drawTextWithShadow(textRenderer,title.getString(),10,6,WHITE);
        drawPanels(draw,mouseX,mouseY);
    }
    /** Panels and list rows that sit underneath the widgets. */
    protected void drawPanels(DrawContext draw,int mouseX,int mouseY){}

    protected void outline(DrawContext draw,int x,int y,int w,int h,int fill){
        draw.fill(x,y,x+w,y+h,fill);
        draw.fill(x,y,x+w,y+1,BORDER);draw.fill(x,y+h-1,x+w,y+h,BORDER);
        draw.fill(x,y,x+1,y+h,BORDER);draw.fill(x+w-1,y,x+w,y+h,BORDER);
    }
    protected void rowBackground(DrawContext draw,int x,int y,int w,int h,int index,boolean hover,boolean selected){
        draw.fill(x,y,x+w,y+h,hover?ROW_HOVER:selected?ROW_SELECTED:index%2==0?ROW_A:ROW_B);
    }
    /** A button drawn by hand, for use inside scrolling lists where real widgets cannot be placed. */
    protected void fakeButton(DrawContext draw,int x,int y,int w,int h,String label,boolean hover,boolean enabled){
        draw.fill(x,y,x+w,y+h,0xFFA0A0A0);
        draw.fill(x+1,y+1,x+w-1,y+h-1,!enabled?0xFF2A2A2A:hover?0xFF5A6A8A:0xFF404040);
        String text=textRenderer.trimToWidth(label,w-4);
        draw.drawTextWithShadow(textRenderer,text,x+(w-textRenderer.getWidth(text))/2,y+(h-8)/2,enabled?WHITE:0xFF707070);
    }
    protected static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x && mx<x+w && my>=y && my<y+h;}
    protected ButtonWidget button(String label,int x,int y,int w,Runnable action){
        ButtonWidget b=ButtonWidget.builder(Text.literal(label),press->action.run()).dimensions(x,y,w,20).build();
        addDrawableChild(b);return b;
    }
    protected ButtonWidget backButton(){
        return button("Back",12,height-26,Math.max(60,textRenderer.getWidth("Back")+20),this::close);
    }
    protected int textWidth(String s){return textRenderer.getWidth(s);}
    /** Runs an action that may throw and reports problems in chat, like the commands do. */
    protected static void attempt(java.util.concurrent.Callable<?> action){BlueprintClient.safe(action);}
    protected static String yesNo(boolean on){return on?"ON":"OFF";}
    protected static Text onOff(boolean on){return Text.literal(yesNo(on)).formatted(on?Formatting.GREEN:Formatting.RED);}
}
