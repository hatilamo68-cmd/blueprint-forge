package dev.blueprintforge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Main menu, modelled on Litematica's: one column of equally wide buttons, one per tool screen. */
public final class MainMenuScreen extends ForgeScreen {
    private ButtonWidget render,start;
    public MainMenuScreen(){super("Blueprint Forge Main Menu",null);}

    @Override protected void init(){
        String[] labels={"Load Schematics","Schematic Placements","Material List","Schematic Verifier","Configuration Menu"};
        int w=0;for(String s:labels) w=Math.max(w,textWidth(s));
        w+=40;
        int x=12,y=26;
        button(labels[0],x,y,w,()->client.setScreen(new LoadSchematicsScreen(this)));y+=22;
        button(labels[1],x,y,w,()->client.setScreen(new PlacementsScreen(this)));y+=22;
        button(labels[2],x,y,w,()->{BlueprintClient.BUILDER.pause("Paused: viewing materials");client.setScreen(new MaterialsScreen(this));});y+=22;
        button(labels[3],x,y,w,()->client.setScreen(new VerifierScreen(this)));y+=22;
        button(labels[4],x,y,w,()->client.setScreen(new ConfigScreen(this,ConfigScreen.Tab.GENERIC)));y+=32;
        render=button("Schematic Rendering: "+yesNo(BlueprintClient.visible),x,y,w,()->{
            BlueprintClient.visible=!BlueprintClient.visible;Settings.save(BlueprintClient.BUILDER);
            render.setMessage(Text.literal("Schematic Rendering: "+yesNo(BlueprintClient.visible)));
        });y+=22;
        start=button(BlueprintClient.BUILDER.active?"Builder: Pause":"Builder: Start",x,y,w,()->{
            attempt(()->{BlueprintClient.toggleBuild();return null;});close();
        });y+=22;
        button("Done",x,y,w,this::close);
        start.active=BlueprintClient.blueprint!=null;
    }
    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        int x=Math.max(260,12+textWidth("Schematic Rendering: OFF")+60);
        if(x+120>width) return;
        draw.drawTextWithShadow(textRenderer,"Loaded: "+(BlueprintClient.blueprint==null?"none":BlueprintClient.blueprint.name()),x,30,WHITE);
        if(BlueprintClient.blueprint!=null){
            var b=BlueprintClient.blueprint;
            draw.drawTextWithShadow(textRenderer,"Size: "+b.width()+" x "+b.height()+" x "+b.depth()+"   Blocks: "+b.cells().size(),x,44,GREY);
            draw.drawTextWithShadow(textRenderer,"Origin: "+BlueprintClient.origin.getX()+", "+BlueprintClient.origin.getY()+", "+BlueprintClient.origin.getZ()
                    +"   Rotation: "+BlueprintClient.rotation*90,x,58,GREY);
            draw.drawTextWithShadow(textRenderer,"Correct "+BlueprintClient.correct+"   Missing "+BlueprintClient.missing+"   Wrong "+BlueprintClient.occupied,x,72,GREY);
        }
        draw.drawTextWithShadow(textRenderer,"Status: "+BlueprintClient.BUILDER.status,x,92,CYAN);
        draw.drawTextWithShadow(textRenderer,"Speed: "+SurvivalBuilder.speedName(BlueprintClient.BUILDER.delay)+" ("+BlueprintClient.BUILDER.interval()+" ticks per block)",x,106,GREY);
        draw.drawTextWithShadow(textRenderer,"Hotkeys: G menu, B preview, P start/pause (rebind in Controls)",x,126,GREY);
    }
}
