package ru.projectkingdom.map;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.PriorityQueue;

/**
 * Local, read-only WALKING ROUTE ESTIMATE within the currently surveyed map.
 * Uses a small 4-neighbor weighted graph; does not validate block-level
 * collision, elevation transitions, doors, fences or NPC navigation.
 * Unknown and water cells are never treated as roads.
 */
public final class KingdomAtlasNavigator {
    public enum Status { ROUTE, ALREADY_THERE, UNCHARTED, BLOCKED, OUTSIDE_MAP, ZOOM_IN }
    public record Step(int column, int row) {}
    public record Route(Status status, List<Step> steps, int estimatedBlocks) {
        public Route {
            steps = List.copyOf(steps);
        }
    }
    private record Node(int index, int score) {}
    private KingdomAtlasNavigator() {}

    public static Route plan(KingdomGraphicAtlasData.Snapshot map,
                             int startChunkX, int startChunkZ,
                             int finishChunkX, int finishChunkZ) {
        if (map == null) throw new IllegalArgumentException("Map required");
        if (map.scale() != 1) return fail(Status.ZOOM_IN);
        int startX = map.centerX() - KingdomGraphicAtlasData.COLUMNS / 2;
        int startZ = map.centerZ() - KingdomGraphicAtlasData.ROWS / 2;
        long sx = (long)startChunkX - startX, sz = (long)startChunkZ - startZ;
        long ex = (long)finishChunkX - startX, ez = (long)finishChunkZ - startZ;
        if (!inside(sx,sz) || !inside(ex,ez)) return fail(Status.OUTSIDE_MAP);
        int start = (int)sz * KingdomGraphicAtlasData.COLUMNS + (int)sx;
        int end = (int)ez * KingdomGraphicAtlasData.COLUMNS + (int)ex;
        int[] costs = new int[KingdomGraphicAtlasData.MAX_CELLS];
        for (int i=0;i<costs.length;i++) costs[i]=cost(map.tiles().get(i).terrain());
        if (costs[start] < 0 || costs[end] < 0) return fail(Status.UNCHARTED);
        if (start==end) return new Route(Status.ALREADY_THERE,
                List.of(new Step((int)sx,(int)sz)),0);

        int[] dist = new int[costs.length], from = new int[costs.length];
        Arrays.fill(dist,Integer.MAX_VALUE);
        Arrays.fill(from,-1);
        var queue=new PriorityQueue<Node>((a,b)->Integer.compare(a.score(),b.score()));
        dist[start]=0;queue.add(new Node(start,0));
        while(!queue.isEmpty()){
            Node next=queue.remove();
            if (next.score()!=dist[next.index()]) continue;
            if (next.index()==end) break;
            int col=next.index()%KingdomGraphicAtlasData.COLUMNS;
            int row=next.index()/KingdomGraphicAtlasData.COLUMNS;
            int[] x={col+1,col-1,col,col};
            int[] z={row,row,row+1,row-1};
            for(int i=0;i<4;i++){
                if(!inside(x[i],z[i]))continue;
                int at=z[i]*KingdomGraphicAtlasData.COLUMNS+x[i];
                if(costs[at]<0)continue;
                int score=dist[next.index()]+costs[at];
                if(score<dist[at]) {
                    dist[at]=score;from[at]=next.index();
                    queue.add(new Node(at,score));
                }
            }
        }
        if(dist[end]==Integer.MAX_VALUE) return fail(Status.BLOCKED);
        List<Step> route=new ArrayList<>();
        for(int at=end;at!=-1;at=from[at]) {
            route.add(new Step(at%KingdomGraphicAtlasData.COLUMNS,
                    at/KingdomGraphicAtlasData.COLUMNS));
            if(at==start)break;
        }
        Collections.reverse(route);
        return new Route(Status.ROUTE,route,(route.size()-1)*16);
    }

    private static boolean inside(long x,long z) {
        return x>=0 && x<KingdomGraphicAtlasData.COLUMNS
                && z>=0 && z<KingdomGraphicAtlasData.ROWS;
    }

    private static int cost(int terrain) {
        var types=KingdomAtlasTerrain.Type.values();
        if(terrain<0||terrain>=types.length)return -1;
        return switch(types[terrain]) {
            case ROAD -> 1;
            case PLAIN -> 3;
            case FOREST -> 6;
            case MOUNTAIN -> 9;
            case WATER,UNKNOWN -> -1;
        };
    }
    private static Route fail(Status status) { return new Route(status,List.of(),0); }
}
