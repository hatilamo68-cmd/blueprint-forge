package dev.blueprintforge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Configuration menu with tabs along the top and "name ... value button" rows, like Litematica's config screens. */
public final class ConfigScreen extends ForgeScreen {
    public enum Tab {
        GENERIC("Generic"),BUILDER("Builder"),HOTKEYS("Hotkeys");
        final String label;
        Tab(String label){this.label=label;}
    }
    private final Tab tab;
    private final List<String> labels=new ArrayList<>();
    private int valueX;
    private ButtonWidget speedButton;

    public ConfigScreen(Screen parent,Tab tab){super("Blueprint Forge Configuration Menu",parent);this.tab=tab;}

    @Override protected void init(){
        int x=12;
        for(Tab t:Tab.values()){
            int w=textWidth(t.label)+24;
            ButtonWidget b=button(t.label,x,22,w,()->client.setScreen(new ConfigScreen(parent,t)));
            b.active=t!=tab;x+=w+4;
        }
        valueX=12+textWidth("Delay (ticks per block)")+24;
        labels.clear();
        SurvivalBuilder builder=BlueprintClient.BUILDER;
        switch(tab){
            case GENERIC->{
                toggle(0,"Schematic Rendering",()->BlueprintClient.visible,()->BlueprintClient.visible=!BlueprintClient.visible);
                toggle(1,"Info HUD Overlay",()->BlueprintClient.hud,()->BlueprintClient.hud=!BlueprintClient.hud);
            }
            case BUILDER->{
                labels.add("Placement Speed");
                speedButton=button("",valueX,rowY(0),150,()->{
                    builder.delay=builder.delay>=12?6:builder.delay>=6?SurvivalBuilder.MIN_DELAY:12;
                    Settings.save(builder);updateSpeed();
                });
                labels.add("Delay (ticks per block)");
                button("-",valueX,rowY(1),20,()->{builder.delay=Math.max(SurvivalBuilder.MIN_DELAY,builder.delay-(hasShiftDown()?10:1));Settings.save(builder);updateSpeed();});
                button("+",valueX+90,rowY(1),20,()->{builder.delay=Math.min(SurvivalBuilder.MAX_DELAY,builder.delay+(hasShiftDown()?10:1));Settings.save(builder);updateSpeed();});
                toggle(2,"Walking",()->builder.walking,()->{builder.pause("Walking setting changed");builder.walking=!builder.walking;});
                toggle(3,"Inventory Refill",()->builder.refill,()->builder.refill=!builder.refill);
                updateSpeed();
            }
            case HOTKEYS->{}
        }
        backButton();
    }
    private int rowY(int row){return 52+row*26;}
    private void updateSpeed(){
        if(speedButton!=null) speedButton.setMessage(Text.literal(SurvivalBuilder.speedName(BlueprintClient.BUILDER.delay)+" ("+BlueprintClient.BUILDER.interval()+" ticks)"));
    }
    private void toggle(int row,String name,BooleanSupplier get,Runnable flip){
        labels.add(name);
        ButtonWidget[] holder=new ButtonWidget[1];
        holder[0]=button("",valueX,rowY(row),150,()->{
            flip.run();Settings.save(BlueprintClient.BUILDER);holder[0].setMessage(onOff(get.getAsBoolean()));
        });
        holder[0].setMessage(onOff(get.getAsBoolean()));
    }
    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        switch(tab){
            case GENERIC->{
                draw.drawTextWithShadow(textRenderer,"Schematic Rendering",12,rowY(0)+6,WHITE);
                draw.drawTextWithShadow(textRenderer,"Info HUD Overlay",12,rowY(1)+6,WHITE);
                note(draw,rowY(2)+4,"Rendering draws outlines for blocks that are still missing (blue) or occupied by something else (red).",
                        "The HUD shows progress and builder status in the top-left corner.");
            }
            case BUILDER->{
                draw.drawTextWithShadow(textRenderer,"Placement Speed",12,rowY(0)+6,WHITE);
                draw.drawTextWithShadow(textRenderer,"Delay (ticks per block)",12,rowY(1)+6,WHITE);
                draw.drawTextWithShadow(textRenderer,BlueprintClient.BUILDER.delay+" ticks",valueX+26,rowY(1)+6,WHITE);
                draw.drawTextWithShadow(textRenderer,"Walking",12,rowY(2)+6,WHITE);
                draw.drawTextWithShadow(textRenderer,"Inventory Refill",12,rowY(3)+6,WHITE);
                note(draw,rowY(4)+4,"Careful = 12 ticks, Normal = 6, Fast = 4. Four ticks is the rate vanilla allows while holding right-click,",
                        "so the mod never places faster than that. 20 ticks = 1 second.",
                        "Faster building makes automation easier for a server to notice. No setting is ban-proof; only use this where",
                        "the server allows building automation. Walking and refill stay off unless you turn them on here.");
            }
            case HOTKEYS->{
                note(draw,rowY(0),"Open menu: G        Toggle schematic rendering: B        Start / pause builder: P",
                        "Rebind them in Options > Controls > Key Binds, under Blueprint Forge.",
                        "Moving with W A S D, jumping, sneaking or clicking the mouse pauses the builder.");
            }
        }
    }
    private void note(DrawContext draw,int y,String... lines){
        for(int i=0;i<lines.length;i++) draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(lines[i],width-24),12,y+i*12,GREY);
    }
}
