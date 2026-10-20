package dev.blueprintforge;

import java.util.*;

/** Inventory counts and blueprint progress are kept separate, including completed materials. */
public final class MaterialSummary<T> {
    public record Row<T>(T item,int total,int remaining,int have,int need) {}
    private final Map<T,Integer> totals=new LinkedHashMap<>(),remaining=new HashMap<>(),inventory=new HashMap<>();
    public void position(T item,boolean complete) {
        totals.merge(item,1,Integer::sum);
        if(!complete) remaining.merge(item,1,Integer::sum);
    }
    public void inventory(T item,int count) {
        if(count>0) inventory.merge(item,count,Integer::sum);
    }
    public List<Row<T>> rows() {
        List<Row<T>> result=new ArrayList<>();
        totals.forEach((item,total)->{
            int left=remaining.getOrDefault(item,0),have=inventory.getOrDefault(item,0);
            result.add(new Row<>(item,total,left,have,Math.max(0,left-have)));
        });
        return List.copyOf(result);
    }
}
