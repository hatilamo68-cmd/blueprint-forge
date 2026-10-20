package dev.blueprintforge.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.*;

/** Schematic Verifier: how much of the placement is correct, missing, or wrong, per block type. */
public final class VerifierScreen extends TableScreen {
    private record Row(Block block,String name,int correct,int missing,int wrongBlock,int wrongState) {
        int problems(){return missing+wrongBlock+wrongState;}
    }
    private List<Row> rows=List.of();
    private int correct,missing,wrongBlock,wrongState,unloaded;

    public VerifierScreen(Screen parent){super("Schematic Verifier",parent);}

    @Override protected void init(){
        layoutTable(48);
        backButton();
        button("Refresh",12+Math.max(60,textWidth("Back")+20)+8,height-26,90,this::compute);
        compute();
    }
    private void compute(){
        correct=missing=wrongBlock=wrongState=unloaded=0;
        Map<Block,int[]> map=new LinkedHashMap<>();
        if(client.world!=null && BlueprintClient.blueprint!=null
                && client.world.getRegistryKey().getValue().toString().equals(BlueprintClient.dimension)){
            for(var t:BlueprintClient.targets){
                Block wanted=t.state().getBlock();
                int[] c=map.computeIfAbsent(wanted,k->new int[4]);
                if(!client.world.isChunkLoaded(t.pos())){unloaded++;continue;}
                BlockState actual=client.world.getBlockState(t.pos());
                if(PlacementPlanner.matches(actual,t.state())){c[0]++;correct++;}
                else if(actual.isReplaceable()){c[1]++;missing++;}
                else if(actual.getBlock()!=wanted){c[2]++;wrongBlock++;}
                else {c[3]++;wrongState++;}
            }
        }
        List<Row> list=new ArrayList<>();
        map.forEach((block,c)->list.add(new Row(block,block.getName().getString(),c[0],c[1],c[2],c[3])));
        list.sort(Comparator.comparingInt(Row::problems).reversed().thenComparing(r->r.name().toLowerCase(Locale.ROOT)));
        rows=List.copyOf(list);scroll=0;
    }
    @Override protected int rows(){return rows.size();}
    private int colState(){return right-10;}
    private int colBlock(){return right-92;}
    private int colMissing(){return right-174;}
    private int colCorrect(){return right-256;}

    @Override protected void drawHeader(DrawContext draw,int x,int y,int w){
        draw.drawTextWithShadow(textRenderer,"Block",x+28,y,WHITE);
        number(draw,"Correct",colCorrect(),y,WHITE);number(draw,"Missing",colMissing(),y,WHITE);
        number(draw,"Wrong block",colBlock(),y,WHITE);number(draw,"Wrong state",colState(),y,WHITE);
    }
    @Override protected void drawRow(DrawContext draw,int i,int x,int y,int w,boolean hover){
        Row r=rows.get(i);
        var item=r.block().asItem();
        if(item!=Items.AIR) draw.drawItem(new ItemStack(item),x+6,y+3);
        draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(r.name(),Math.max(35,colCorrect()-x-70)),x+28,y+7,WHITE);
        number(draw,Integer.toString(r.correct()),colCorrect(),y+7,r.correct()>0?GREEN:GREY);
        number(draw,Integer.toString(r.missing()),colMissing(),y+7,r.missing()>0?0xFFFF7070:GREY);
        number(draw,Integer.toString(r.wrongBlock()),colBlock(),y+7,r.wrongBlock()>0?RED:GREY);
        number(draw,Integer.toString(r.wrongState()),colState(),y+7,r.wrongState()>0?ORANGE:GREY);
    }
    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        String name=BlueprintClient.blueprint==null?"No schematic loaded. Use Load Schematics first.":BlueprintClient.blueprint.name();
        draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(name+"   Correct "+correct+"   Missing "+missing+"   Wrong block "+wrongBlock
                +"   Wrong state "+wrongState+"   Unloaded "+unloaded,width-24),12,28,GREY);
        super.drawPanels(draw,mouseX,mouseY);
        if(rows.isEmpty()) draw.drawTextWithShadow(textRenderer,"Nothing to verify.",left+10,top+HEADER+10,GREY);
        draw.drawTextWithShadow(textRenderer,"Uses the active placement and layer filter. Block entities and derived states are not compared.",
                12+Math.max(60,textWidth("Back")+20)+108,height-20,GREY);
    }
}
