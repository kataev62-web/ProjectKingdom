package ru.projectkingdom.npc;
public class KingdomDesignEngineTest {
    public static void main(String[] args) {
        var d=KingdomDesignEngine.medievalHouse("test",11,13,2);
        if(d.blocks().isEmpty() || d.bill().values().stream().mapToInt(Integer::intValue).sum()!=d.blocks().size())throw new AssertionError("bill mismatch");
        if(!KingdomDesignEngine.footprintAllowed(d,0,0,(x,z)->x>=0&&x<11&&z>=0&&z<13))throw new AssertionError("allowed footprint rejected");
        if(KingdomDesignEngine.footprintAllowed(d,0,0,(x,z)->x<10))throw new AssertionError("partial claim accepted");
        if(d.blocks().stream().anyMatch(c->c.x()==5 && c.z()==0 && (c.y()==1 || c.y()==2)))throw new AssertionError("door blocked");
        System.out.println("OK: design, materials, claim boundaries, entrance; blocks="+d.blocks().size());
    }
}
