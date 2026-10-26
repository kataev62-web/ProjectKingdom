package ru.projectkingdom.settlement;

import java.util.UUID;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomConstructionData;

/** Honorary settlement stage based on truly claimed land and completed buildings. */
public final class KingdomSettlementGrowth {
    public enum Rank {
        HAMLET("Хутор"), VILLAGE("Деревня"), SETTLEMENT("Посёлок"), CITY("Город");
        private final String title;
        Rank(String title){this.title=title;}
        public String title(){return title;}
    }
    private KingdomSettlementGrowth(){}
    public static Rank rank(int ownedChunks,int completedBuildings){
        if(ownedChunks>=32&&completedBuildings>=12)return Rank.CITY;
        if(ownedChunks>=12&&completedBuildings>=5)return Rank.SETTLEMENT;
        if(ownedChunks>=4&&completedBuildings>=2)return Rank.VILLAGE;
        return Rank.HAMLET;
    }
    public static Rank forTown(KingdomCivicsData civics,KingdomConstructionData buildings,UUID townId,String dimension){
        var town=civics.townById(townId);
        if(town==null)return Rank.HAMLET;
        java.util.Set<Long> owned=new java.util.HashSet<>();
        for(var plot:civics.mapPlots(dimension))
            if(townId.equals(plot.town()))
                owned.add(((long)plot.x()<<32)|(plot.z()&0xffffffffL));
        int completed=0;
        // Only actual completed projects in the city's titled footprint count.
        for(var project:buildings.all()) {
            if(project.status()!=KingdomConstructionData.Status.COMPLETED
                    ||!dimension.equals(project.dimension()))continue;
            int cx=project.x()>>4,cz=project.z()>>4;
            if(owned.contains(((long)cx<<32)|(cz&0xffffffffL)))completed++;
        }
        return rank(owned.size(),completed);
    }
}
