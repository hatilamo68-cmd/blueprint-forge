package dev.blueprintforge;

import java.util.*;

/** Bounded A* for cardinal walking, one-block ascents and one-block descents. */
public final class WalkPath {
    public record Node(int x, int y, int z) {
        public Node add(int x, int y, int z) { return new Node(this.x+x, this.y+y, this.z+z); }
        int distance(Node b) {
            int horizontal=Math.abs(x-b.x)+Math.abs(z-b.z),vertical=Math.abs(y-b.y);
            return Math.max(horizontal,vertical)*10+vertical*5;
        }
    }
    public interface Grid {
        boolean stand(Node p);
        boolean jumpClear(Node from);
    }
    private record Entry(Node node, int g, int score) {}
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1}};
    private WalkPath() {}
    public static List<Node> find(Grid grid, Node start, Set<Node> goals, int radius, int budget) {
        return find(grid,start,goals,radius,budget,true);
    }
    public static List<Node> find(Grid grid, Node start, Set<Node> goals, int radius, int budget,boolean allowElevation) {
        if (goals.isEmpty()) return List.of();
        if (goals.contains(start)) return List.of(start);
        PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingInt(Entry::score));
        Map<Node,Integer> distance = new HashMap<>();
        Map<Node,Node> previous = new HashMap<>();
        distance.put(start,0); open.add(new Entry(start,0,heuristic(start,goals)));
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < budget) {
            Entry current = open.poll(); Node from = current.node;
            if (current.g != distance.getOrDefault(from,Integer.MAX_VALUE)) continue;
            if (goals.contains(from)) {
                LinkedList<Node> path = new LinkedList<>();
                for (Node p=from;p!=null;p=previous.get(p)) path.addFirst(p);
                return List.copyOf(path);
            }
            for (int[] d : DIRECTIONS) {
                Node next = from.add(d[0],0,d[1]);
                if (!grid.stand(next)) {
                    if(!allowElevation) continue;
                    if (grid.jumpClear(from) && grid.stand(next.add(0,1,0))) next = next.add(0,1,0);
                    else if (grid.stand(next.add(0,-1,0))) next = next.add(0,-1,0);
                    else continue;
                }
                if (Math.abs(next.x-start.x)>radius || Math.abs(next.z-start.z)>radius || Math.abs(next.y-start.y)>8) continue;
                int cost = current.g + 10 + Math.abs(next.y-from.y)*5;
                if (cost >= distance.getOrDefault(next,Integer.MAX_VALUE)) continue;
                distance.put(next,cost); previous.put(next,from);
                open.add(new Entry(next,cost,cost+heuristic(next,goals)));
            }
        }
        return List.of();
    }
    private static int heuristic(Node p, Set<Node> goals) {
        return goals.stream().mapToInt(p::distance).min().orElse(0);
    }
}
