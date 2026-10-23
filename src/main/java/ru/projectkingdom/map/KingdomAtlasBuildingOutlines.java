package ru.projectkingdom.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.npc.KingdomConstructionPlans;

/**
 * Footprints of COMPLETED Kingdom construction designs.
 * The outline reflects the registered blueprint, not a fresh block-by-block
 * integrity inspection. Only projects intersecting the viewport are resolved.
 */
public final class KingdomAtlasBuildingOutlines {
    public static final int MAX_OUTLINES = 48;
    public static final int MAX_DESIGN_CELLS = 150000;
    public record Outline(KingdomAtlasInfrastructure.Kind kind,
                          int minX,int minZ,int maxX,int maxZ) {
        public Outline {
            if (kind == null || minX>maxX || minZ>maxZ
                    || (long)maxX-minX>256 || (long)maxZ-minZ>256)
                throw new IllegalArgumentException("Invalid building footprint");
        }
    }
    public record Source(String dimension, Outline outline) {}
    private KingdomAtlasBuildingOutlines() {}

    public static List<Outline> forPlayer(ServerPlayer player, int cx, int cz, int scale) {
        var server=player.getServer();
        String dimension=player.serverLevel().dimension().location().toString();
        long minChunkX=(long)cx-KingdomGraphicAtlasData.COLUMNS/2L*scale;
        long minChunkZ=(long)cz-KingdomGraphicAtlasData.ROWS/2L*scale;
        long maxChunkX=minChunkX+KingdomGraphicAtlasData.COLUMNS*(long)scale;
        long maxChunkZ=minChunkZ+KingdomGraphicAtlasData.ROWS*(long)scale;
        List<Source> sources=new ArrayList<>();
        var candidates=new ArrayList<KingdomConstructionData.Project>();
        for(var project:KingdomConstructionData.get(server).all()) {
            if(project.status()!=KingdomConstructionData.Status.COMPLETED
                    || !dimension.equals(project.dimension()))continue;
            long originX=Math.floorDiv(project.x(),16), originZ=Math.floorDiv(project.z(),16);
            // Designs may extend to neighboring chunks; broad pre-filter only.
            if(originX<minChunkX-17 || originX>=maxChunkX+17
                    || originZ<minChunkZ-17 || originZ>=maxChunkZ+17)continue;
            candidates.add(project);
        }
        // Resolving large schematic geometry is much more expensive than
        // filtering registry coordinates. Resolve only closest 96 candidates.
        candidates.sort(Comparator.comparingLong(project->{
            long dx=(long)project.x()-(long)cx*16L;
            long dz=(long)project.z()-(long)cz*16L;
            return dx*dx+dz*dz;
        }));
        for(int i=0;i<Math.min(candidates.size(),MAX_OUTLINES*2);i++){
            var project=candidates.get(i);
            var design=KingdomConstructionPlans.resolve(server,project.design());
            if (design==null || design.blocks().isEmpty()
                    || design.blocks().size()>MAX_DESIGN_CELLS)continue;
            int minX=Integer.MAX_VALUE, minZ=Integer.MAX_VALUE;
            int maxX=Integer.MIN_VALUE, maxZ=Integer.MIN_VALUE;
            for(var cell:design.blocks()) {
                minX=Math.min(minX,cell.x());minZ=Math.min(minZ,cell.z());
                maxX=Math.max(maxX,cell.x());maxZ=Math.max(maxZ,cell.z());
            }
            long ax=(long)project.x()+minX, az=(long)project.z()+minZ;
            long bx=(long)project.x()+maxX, bz=(long)project.z()+maxZ;
            if(ax<Integer.MIN_VALUE||az<Integer.MIN_VALUE||bx>Integer.MAX_VALUE||bz>Integer.MAX_VALUE)
                continue;
            String title=KingdomConstructionPlans.title(project.design());
            var kind=KingdomAtlasInfrastructure.designKind(project.design(),title);
            try {
                sources.add(new Source(dimension,new Outline(kind,(int)ax,(int)az,(int)bx,(int)bz)));
            } catch (IllegalArgumentException ignored) { }
        }
        return select(sources,dimension,cx,cz,scale);
    }

    public static List<Outline> select(Collection<Source> sources,String dimension,
                                       int centerX,int centerZ,int scale) {
        if(!KingdomGraphicAtlasData.supportedScale(scale)||dimension==null)
            throw new IllegalArgumentException("Bad viewport");
        long minX=((long)centerX-(KingdomGraphicAtlasData.COLUMNS/2L)*scale)*16L;
        long minZ=((long)centerZ-(KingdomGraphicAtlasData.ROWS/2L)*scale)*16L;
        long maxX=minX+(long)KingdomGraphicAtlasData.COLUMNS*scale*16L;
        long maxZ=minZ+(long)KingdomGraphicAtlasData.ROWS*scale*16L;
        List<Outline> visible=new ArrayList<>();
        for(var source:sources){
            if(!dimension.equals(source.dimension()))continue;
            var o=source.outline();
            if((long)o.maxX()<minX||(long)o.maxZ()<minZ||o.minX()>=maxX||o.minZ()>=maxZ)
                continue;
            visible.add(o);
        }
        visible.sort(Comparator.comparingLong(o->{
            long dx=((long)o.minX()+o.maxX())/2L-(long)centerX*16L;
            long dz=((long)o.minZ()+o.maxZ())/2L-(long)centerZ*16L;
            return dx*dx+dz*dz;
        }));
        if(visible.size()>MAX_OUTLINES)return List.copyOf(visible.subList(0,MAX_OUTLINES));
        return List.copyOf(visible);
    }
}
