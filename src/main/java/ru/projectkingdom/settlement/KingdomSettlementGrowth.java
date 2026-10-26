package ru.projectkingdom.settlement;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.npc.KingdomEnterpriseData;

/**
 * Population and real separate buildings determine the grade of settlement.
 * The initial tavern is sufficient for a HAMLET, not for VILLAGE or CITY.
 * No extra title, currency, blocks, or terrain are minted on promotion.
 */
public final class KingdomSettlementGrowth {
    public enum Rank {
        HAMLET("Хутор"), VILLAGE("Деревня"), SETTLEMENT("Посёлок"), CITY("Город");
        private final String title;
        Rank(String title){this.title=title;}
        public String title(){return title;}
    }
    private KingdomSettlementGrowth(){}

    /** Legacy structural preview for existing tests; live game uses population gate. */
    public static Rank rank(int ownedChunks,int completedBuildings){
        if(ownedChunks>=32&&completedBuildings>=12)return Rank.CITY;
        if(ownedChunks>=12&&completedBuildings>=5)return Rank.SETTLEMENT;
        if(ownedChunks>=4&&completedBuildings>=2)return Rank.VILLAGE;
        return Rank.HAMLET;
    }

    /** Number of projects here is deliberately EXCLUSIVE of the starting tavern. */
    public static Rank rank(int chunks,int separateCompletedBuildings,int population,
                            boolean activeTavern){
        if(!activeTavern)return Rank.HAMLET;
        if(chunks>=32&&separateCompletedBuildings>=11&&population>=20)return Rank.CITY;
        if(chunks>=12&&separateCompletedBuildings>=4&&population>=8)return Rank.SETTLEMENT;
        if(chunks>=4&&separateCompletedBuildings>=1&&population>=3)return Rank.VILLAGE;
        return Rank.HAMLET;
    }

    public static Rank forTown(KingdomCivicsData civics,KingdomConstructionData buildings,
                               UUID townId,String dimension,MinecraftServer server){
        var town=civics.townById(townId);
        if(town==null||server==null)return Rank.HAMLET;
        Set<Long> owned=new HashSet<>();
        for(var plot:civics.mapPlots(dimension))
            if(townId.equals(plot.town()))
                owned.add(((long)plot.x()<<32)|(plot.z()&0xffffffffL));
        var company=KingdomEnterpriseData.get(server);
        BlockPos tavern=null;
        for(var firm:company.all()){
            if(company.kind(firm.id())!=EnterpriseKind.TAVERN)continue;
            var bound=company.core(firm.id());
            if(bound==null||!bound.active()||!dimension.equals(bound.dimension()))continue;
            BlockPos core=BlockPos.of(bound.packedPos());
            long key=((long)(core.getX()>>4)<<32)|((core.getZ()>>4)&0xffffffffL);
            if(!owned.contains(key))continue;
            if(!civics.isMember(townId,firm.owner()))continue;
            tavern=core;break;
        }
        int independent=0;
        var land=ru.projectkingdom.property.KingdomRentalsData.get(server);
        var inn=tavern==null?null:land.atBuilding(dimension,tavern);
        for(var project:buildings.all()){
            if(project.status()!=KingdomConstructionData.Status.COMPLETED
                    ||!dimension.equals(project.dimension()))continue;
            int cx=project.x()>>4,cz=project.z()>>4;
            if(!owned.contains(((long)cx<<32)|(cz&0xffffffffL)))continue;
            BlockPos site=new BlockPos(project.x(),project.y(),project.z());
            // Not a second house if the player simply built a second floor
            // inside the same tavern footprint.
            if(tavern!=null&&(site.distSqr(tavern)<144
                    ||inn!=null&&inn.bounds().contains(site)))continue;
            independent++;
        }
        int population=KingdomTavernImmigration.countResidents(server,townId);
        return rank(owned.size(),independent,population,tavern!=null);
    }

    /** Pure structural fallback for old tool integrations without a server. */
    @Deprecated public static Rank forTown(KingdomCivicsData civics,
            KingdomConstructionData buildings,UUID townId,String dimension){
        var town=civics.townById(townId);
        if(town==null)return Rank.HAMLET;
        Set<Long> owned=new HashSet<>();
        for(var plot:civics.mapPlots(dimension))if(townId.equals(plot.town()))
            owned.add(((long)plot.x()<<32)|(plot.z()&0xffffffffL));
        int completed=0;
        for(var project:buildings.all())if(project.status()==KingdomConstructionData.Status.COMPLETED
                &&dimension.equals(project.dimension())
                &&owned.contains(((long)(project.x()>>4)<<32)|((project.z()>>4)&0xffffffffL)))
            completed++;
        return rank(owned.size(),completed);
    }
}
