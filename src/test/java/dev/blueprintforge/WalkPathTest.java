package dev.blueprintforge;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WalkPathTest {
    @Test void groundOnlyModeNeverChoosesAnAscentOrDescent(){
        var a=new WalkPath.Node(0,0,0);var up=new WalkPath.Node(1,1,0);var down=new WalkPath.Node(1,-1,0);
        assertTrue(WalkPath.find(grid(Set.of(a,up)),a,Set.of(up),8,100,false).isEmpty());
        assertTrue(WalkPath.find(grid(Set.of(a,down)),a,Set.of(down),8,100,false).isEmpty());
        var level=new WalkPath.Node(1,0,0);
        assertEquals(List.of(a,level),WalkPath.find(grid(Set.of(a,level)),a,Set.of(level),8,100,false));
    }
    private static WalkPath.Grid grid(Set<WalkPath.Node> walkable){
        return new WalkPath.Grid(){public boolean stand(WalkPath.Node p){return walkable.contains(p);}public boolean jumpClear(WalkPath.Node p){return true;}};
    }
    @Test void routesAroundAnObstacleWithoutDiagonalCornerCutting(){
        Set<WalkPath.Node> floor=new HashSet<>();for(int x=0;x<5;x++)for(int z=0;z<3;z++)floor.add(new WalkPath.Node(x,0,z));
        floor.remove(new WalkPath.Node(2,0,1));
        var start=new WalkPath.Node(0,0,1);var end=new WalkPath.Node(4,0,1);
        var path=WalkPath.find(grid(floor),start,Set.of(end),10,200);
        assertEquals(start,path.getFirst());assertEquals(end,path.getLast());assertFalse(path.contains(new WalkPath.Node(2,0,1)));
        for(int i=1;i<path.size();i++)assertEquals(1,Math.abs(path.get(i).x()-path.get(i-1).x())+Math.abs(path.get(i).z()-path.get(i-1).z()));
    }
    @Test void climbsOneBlockButDoesNotCrossAnUnsupportedGap(){
        var a=new WalkPath.Node(0,0,0);var b=new WalkPath.Node(1,1,0);var c=new WalkPath.Node(2,1,0);
        assertEquals(List.of(a,b,c),WalkPath.find(grid(Set.of(a,b,c)),a,Set.of(c),8,100));
        assertTrue(WalkPath.find(grid(Set.of(a,c)),a,Set.of(c),8,100).isEmpty());
    }
    @Test void willNotJumpUnderAnObstructedCeiling(){
        var a=new WalkPath.Node(0,0,0);var b=new WalkPath.Node(1,1,0);
        WalkPath.Grid low=new WalkPath.Grid(){public boolean stand(WalkPath.Node p){return p.equals(a)||p.equals(b);}public boolean jumpClear(WalkPath.Node p){return false;}};
        assertTrue(WalkPath.find(low,a,Set.of(b),8,100).isEmpty());
    }
    @Test void respectsRadiusAndSearchBudget(){
        Set<WalkPath.Node> floor=new HashSet<>();for(int x=0;x<=20;x++)floor.add(new WalkPath.Node(x,0,0));
        var start=new WalkPath.Node(0,0,0);var goal=new WalkPath.Node(20,0,0);
        assertTrue(WalkPath.find(grid(floor),start,Set.of(goal),4,100).isEmpty());
        assertTrue(WalkPath.find(grid(floor),start,Set.of(goal),32,2).isEmpty());
    }
}
