package dev.blueprintforge;

import org.junit.jupiter.api.Test;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class MaterialSummaryTest {
    @Test void reportsWholeBlueprintProgressAndAllInventoryStacks(){
        var summary=new MaterialSummary<String>();
        for(int i=0;i<96;i++)summary.position("stone",i<64);
        for(int i=0;i<8;i++)summary.position("planks",false);
        for(int i=0;i<6;i++)summary.position("glass",true);
        summary.inventory("stone",32);summary.inventory("stone",16);
        summary.inventory("planks",3);summary.inventory("glass",64);
        var rows=summary.rows().stream().collect(Collectors.toMap(MaterialSummary.Row::item,Function.identity()));
        assertEquals(3,rows.size(),"Completed materials must remain visible");
        assertEquals(new MaterialSummary.Row<>("stone",96,32,48,0),rows.get("stone"));
        assertEquals(new MaterialSummary.Row<>("planks",8,8,3,5),rows.get("planks"));
        assertEquals(new MaterialSummary.Row<>("glass",6,0,64,0),rows.get("glass"));
    }
    @Test void showsNeededMaterialsEvenWithAnEmptyInventory(){
        var summary=new MaterialSummary<String>();
        for(int i=0;i<130;i++)summary.position("stone",false);
        assertEquals(java.util.List.of(new MaterialSummary.Row<>("stone",130,130,0,130)),summary.rows());
    }
}
