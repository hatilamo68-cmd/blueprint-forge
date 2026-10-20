package dev.blueprintforge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/** Move, rotate and layer-filter one placement. Hold Shift on the +/- buttons to move by 10 blocks. */
public final class PlacementConfigScreen extends ForgeScreen {
    private final int index;
    private final TextFieldWidget[] fields=new TextFieldWidget[3];
    private ButtonWidget rotation,layer;
    private String message="";

    public PlacementConfigScreen(Screen parent,int index){
        super("Configure Schematic Placement",parent);
        this.index=index;
        BlueprintClient.select(index);
    }
    @Override protected void init(){
        String[] axes={"X","Y","Z"};
        int x=12,label=64;
        for(int a=0;a<3;a++){
            final int axis=a;int row=34+a*24;
            button("-",x+label,row,20,()->nudge(axis,-1));
            fields[a]=new TextFieldWidget(textRenderer,x+label+24,row,84,20,Text.literal(axes[a]));
            fields[a].setMaxLength(12);addDrawableChild(fields[a]);
            button("+",x+label+112,row,20,()->nudge(axis,1));
        }
        button("Apply Coordinates",x+label+140,34,Math.max(130,textWidth("Apply Coordinates")+24),this::applyFields);
        button("Move To My Position",x+label+140,58,Math.max(130,textWidth("Move To My Position")+24),()->{
            if(client.player!=null){BlueprintClient.origin=client.player.getBlockPos();BlueprintClient.refresh();syncFields();message="";}
        });
        rotation=button("",x,114,180,()->{
            BlueprintClient.rotation=(BlueprintClient.rotation+1)%4;BlueprintClient.refresh();updateLabels();
        });
        button("<",x,142,20,()->stepLayer(-1));
        layer=button("",x+24,142,156,()->{BlueprintClient.layer=-1;BlueprintClient.refresh();updateLabels();});
        button(">",x+184,142,20,()->stepLayer(1));
        button("Remove Placement",x,176,180,()->{BlueprintClient.unload(index);close();});
        backButton();
        syncFields();updateLabels();
    }
    private void nudge(int axis,int direction){
        int step=hasShiftDown()?10:1;BlockPos o=BlueprintClient.origin;
        BlueprintClient.origin=axis==0?o.add(direction*step,0,0):axis==1?o.add(0,direction*step,0):o.add(0,0,direction*step);
        BlueprintClient.refresh();syncFields();message="";
    }
    private void applyFields(){
        try{
            int x=Integer.parseInt(fields[0].getText().strip()),y=Integer.parseInt(fields[1].getText().strip()),z=Integer.parseInt(fields[2].getText().strip());
            BlueprintClient.origin=new BlockPos(x,y,z);BlueprintClient.refresh();message="";
        }catch(NumberFormatException e){message="Coordinates must be whole numbers.";}
    }
    private void stepLayer(int direction){
        int height=BlueprintClient.blueprint.height();
        int next=BlueprintClient.layer+direction;
        if(next>=height) next=-1;else if(next<-1) next=height-1;
        BlueprintClient.layer=next;BlueprintClient.refresh();updateLabels();
    }
    private void syncFields(){
        BlockPos o=BlueprintClient.origin;
        fields[0].setText(Integer.toString(o.getX()));fields[1].setText(Integer.toString(o.getY()));fields[2].setText(Integer.toString(o.getZ()));
    }
    private void updateLabels(){
        rotation.setMessage(Text.literal("Rotation: "+BlueprintClient.rotation*90+" degrees"));
        layer.setMessage(Text.literal(BlueprintClient.layer<0?"Layer: ALL (click to reset)":"Layer: "+BlueprintClient.layer+" of "+(BlueprintClient.blueprint.height()-1)));
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER){applyFields();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        String[] axes={"Origin X","Origin Y","Origin Z"};
        for(int a=0;a<3;a++) draw.drawTextWithShadow(textRenderer,axes[a],12,40+a*24,WHITE);
        draw.drawTextWithShadow(textRenderer,"Schematic: "+BlueprintClient.blueprint.name(),12,100,CYAN);
        var b=BlueprintClient.blueprint;
        draw.drawTextWithShadow(textRenderer,"Size "+b.width()+" x "+b.height()+" x "+b.depth()+"   Blocks "+b.cells().size(),12,206,GREY);
        draw.drawTextWithShadow(textRenderer,"The origin is the minimum corner of the schematic. Shift + click +/- moves by 10.",12,220,GREY);
        if(!message.isEmpty()) draw.drawTextWithShadow(textRenderer,message,12,234,RED);
    }
}
