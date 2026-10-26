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
        int chunks=0, completed=0;
        for(var p:civics.mapPlots(dimension))if(townId.equals(p.town()))chunks++;
        // Only real FINISHED projects inside the same city's actual territorial chunks.
        for(var project:buildings.all()){
            if(project.status()!=KingdomConstructionData.Status.COMPLETED
                    ||!dimension.equals(project.dimension()))continue;
            int cx=project.x()>>4,cz=project.z()>>4;
            if(civics.mapPlots(dimension).stream()
                    .anyMatch(p->p.x()==cx&&p.z()==cz&&townId.equals(p.town())))completed++;
        }
        return rank(chunks,completed);
    }
}
