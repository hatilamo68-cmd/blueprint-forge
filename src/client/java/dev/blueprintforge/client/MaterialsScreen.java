package dev.blueprintforge.client;

import dev.blueprintforge.Blueprint;
import dev.blueprintforge.MaterialSummary;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import java.util.*;

/** Material list with Litematica's columns: Item, Total, Missing, Available. Searchable, sortable and refreshable. */
public final class MaterialsScreen extends TableScreen {
    private enum Sort{MISSING("Missing"),TOTAL("Total"),NAME("Name");final String label;Sort(String l){label=l;}}
    private TextFieldWidget search;
    private ButtonWidget scopeButton,sortButton,hideButton;
    private List<MaterialSummary.Row<Item>> rows=List.of(),filtered=List.of();
    private boolean selectedLayer,hideComplete;
    private Sort sort=Sort.MISSING;
    private int ticks,unsupported,unloaded,occupied;
    private List<Blueprint.Target> whole;

    public MaterialsScreen(Screen parent){super("Material List",parent);}

    @Override protected void init(){
        layoutTable(48);
        if(BlueprintClient.blueprint!=null && whole==null)
            whole=BlueprintClient.layer<0?BlueprintClient.targets:BlueprintClient.blueprint.targets(BlueprintClient.origin,BlueprintClient.rotation,-1);
        String text=search==null?"":search.getText();
        search=new TextFieldWidget(textRenderer,left,22,150,20,Text.literal("Search materials"));
        search.setMaxLength(128);search.setPlaceholder(Text.literal("Search name or ID"));search.setText(text);
        search.setChangedListener(value->{scroll=0;filter();});addDrawableChild(search);
        int x=left+158;
        scopeButton=button(selectedLayer?"Scope: Selected layer":"Scope: Whole schematic",x,22,150,()->{selectedLayer=!selectedLayer;scroll=0;updateLabels();refresh();});
        x+=154;
        sortButton=button("Sort: "+sort.label,x,22,100,()->{sort=Sort.values()[(sort.ordinal()+1)%Sort.values().length];scroll=0;updateLabels();refresh();});
        x+=104;
        hideButton=button("Hide complete: "+yesNo(hideComplete),x,22,130,()->{hideComplete=!hideComplete;scroll=0;updateLabels();filter();});
        backButton();
        button("Refresh List",12+Math.max(60,textWidth("Back")+20)+8,height-26,100,this::refresh);
        refresh();
    }
    private void updateLabels(){
        scopeButton.setMessage(Text.literal(selectedLayer?"Scope: Selected layer":"Scope: Whole schematic"));
        sortButton.setMessage(Text.literal("Sort: "+sort.label));
        hideButton.setMessage(Text.literal("Hide complete: "+yesNo(hideComplete)));
    }
    private void refresh(){
        MaterialSummary<Item> summary=new MaterialSummary<>();unsupported=unloaded=occupied=0;
        if(client.player!=null && client.world!=null && BlueprintClient.blueprint!=null && whole!=null){
            boolean sameDimension=client.world.getRegistryKey().getValue().toString().equals(BlueprintClient.dimension);
            var targets=selectedLayer?BlueprintClient.targets:whole;
            for(var target:targets){
                Item item=PlacementPlanner.item(target);
                if(item==Items.AIR){unsupported++;continue;}
                boolean loaded=sameDimension && client.world.isChunkLoaded(target.pos());
                boolean complete=loaded && PlacementPlanner.matches(client.world.getBlockState(target.pos()),target.state());
                if(!loaded)unloaded++;
                else if(!complete && !client.world.getBlockState(target.pos()).isReplaceable())occupied++;
                summary.position(item,complete);
            }
            for(int i=0;i<36;i++){
                ItemStack stack=client.player.getInventory().getStack(i);
                if(!stack.isEmpty())summary.inventory(stack.getItem(),stack.getCount());
            }
        }
        Comparator<MaterialSummary.Row<Item>> byName=Comparator.comparing((MaterialSummary.Row<Item> r)->r.item().getName().getString(),String.CASE_INSENSITIVE_ORDER);
        Comparator<MaterialSummary.Row<Item>> order=switch(sort){
            case MISSING->Comparator.<MaterialSummary.Row<Item>>comparingInt(MaterialSummary.Row::remaining).reversed().thenComparing(byName);
            case TOTAL->Comparator.<MaterialSummary.Row<Item>>comparingInt(MaterialSummary.Row::total).reversed().thenComparing(byName);
            case NAME->byName;
        };
        rows=summary.rows().stream().sorted(order).toList();
        filter();
    }
    private void filter(){
        String query=search==null?"":search.getText().strip().toLowerCase(Locale.ROOT);
        filtered=rows.stream().filter(r->!hideComplete || r.remaining()>0)
                .filter(r->r.item().getName().getString().toLowerCase(Locale.ROOT).contains(query)
                        || Registries.ITEM.getId(r.item()).toString().contains(query)).toList();
        clampScroll();
    }
    @Override public void tick(){if(++ticks%40==0)refresh();}
    @Override protected int rows(){return filtered.size();}

    private int colAvailable(){return right-10;}
    private int colMissing(){return right-90;}
    private int colTotal(){return right-170;}

    @Override protected void drawHeader(DrawContext draw,int x,int y,int w){
        draw.drawTextWithShadow(textRenderer,"Item",x+28,y,WHITE);
        number(draw,"Total",colTotal(),y,WHITE);number(draw,"Missing",colMissing(),y,WHITE);number(draw,"Available",colAvailable(),y,WHITE);
    }
    @Override protected void drawRow(DrawContext draw,int i,int x,int y,int w,boolean hover){
        var row=filtered.get(i);
        draw.drawItem(new ItemStack(row.item()),x+6,y+3);
        draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(row.item().getName().getString(),Math.max(35,colTotal()-x-68)),x+28,y+7,WHITE);
        boolean short_=row.have()<row.remaining();
        number(draw,Integer.toString(row.total()),colTotal(),y+7,WHITE);
        number(draw,Integer.toString(row.remaining()),colMissing(),y+7,row.remaining()>0?0xFFFF7070:GREEN);
        number(draw,Integer.toString(row.have()),colAvailable(),y+7,row.remaining()==0?GREY:short_?RED:GREEN);
    }
    @Override protected void drawPanels(DrawContext draw,int mouseX,int mouseY){
        super.drawPanels(draw,mouseX,mouseY);
        String name=BlueprintClient.blueprint==null?"No schematic loaded. Use Load Schematics first.":BlueprintClient.blueprint.name();
        draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(name,Math.max(50,width-24-textWidth("Back")-250)),12+Math.max(60,textWidth("Back")+20)+120,height-20,GREY);
        if(filtered.isEmpty()) draw.drawTextWithShadow(textRenderer,rows.isEmpty()?"No material rows for this scope.":"No matching materials.",left+10,top+HEADER+10,GREY);
        String foot="Unloaded "+unloaded+"   Occupied "+occupied+"   No item "+unsupported+"   Counts are block positions";
        draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(foot,Math.max(40,right-(12+Math.max(60,textWidth("Back")+20)+120)-8)),
                Math.max(12+Math.max(60,textWidth("Back")+20)+120,right-textWidth(foot)),height-32,GREY);
    }
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        super.render(draw,mouseX,mouseY,delta);
        int bodyTop=top+HEADER+1,end=Math.min(filtered.size(),scroll+visibleRows());
        for(int i=scroll;i<end;i++){
            int y=bodyTop+(i-scroll)*ROW;
            if(mouseX>=left && mouseX<colTotal()-40 && mouseY>=y && mouseY<y+ROW){
                var row=filtered.get(i);
                draw.drawTooltip(textRenderer,Text.literal(row.item().getName().getString()+" - "+Registries.ITEM.getId(row.item())),mouseX,mouseY);break;
            }
        }
    }
}
