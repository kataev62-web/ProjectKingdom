package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stage 3 municipal nutrition planning. Read-only demand derived from the
 * *same physical* central warehouse forecast used by Stage 2 procurement.
 * A plan is never a purchase, shipment, edible item or debit instruction.
 */
public final class KingdomMunicipalDietPlan {
    private KingdomMunicipalDietPlan() {}

    public record Demand(boolean known, long groupTarget, List<Integer> stock,
                         List<Long> missing, List<Integer> priority) {
        public Demand {
            stock=List.copyOf(stock);
            missing=List.copyOf(missing);
            priority=List.copyOf(priority);
            if(stock.size()!=KingdomNpcNutrition.COUNT
                    ||missing.size()!=KingdomNpcNutrition.COUNT
                    ||priority.size()!=KingdomNpcNutrition.COUNT)
                throw new IllegalArgumentException("Five TFC diet groups are required");
        }
        public int greatestDeficit() {
            if(!known)return -1;
            int selected=0;
            for(int i=1;i<missing.size();i++)
                if(missing.get(i)>missing.get(selected))selected=i;
            return missing.get(selected)==0?-1:selected;
        }
    }
    public record Candidate(String itemId,long unitCopper,int[] nutrients,int available,
                            long expiry) {
        public Candidate {
            nutrients=nutrients.clone();
            if(nutrients.length!=KingdomNpcNutrition.COUNT || unitCopper<=0
                    ||available<1 || itemId==null||itemId.isBlank())
                throw new IllegalArgumentException("Invalid physical market offer");
        }
        @Override public int[] nutrients(){return nutrients.clone();}
    }

    /**
     * A balanced plan aims for 1/5 of the required edible portions from EACH
     * nutrient group. Mixed TFC meals legitimately cover several categories.
     * Unknown stock is UNKNOWN, never fabricated as zero or missing stock.
     */
    public static Demand calculate(KingdomMunicipalFoodReserves.Forecast forecast) {
        List<Integer> stock=forecast==null
                ?List.of(0,0,0,0,0):forecast.groups();
        boolean known=forecast!=null && forecast.complete();
        long total=known?forecast.target():0;
        long each=known?(total/KingdomNpcNutrition.COUNT
                +(total%KingdomNpcNutrition.COUNT==0?0:1)):0;
        List<Long> deficits=new ArrayList<>();
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<KingdomNpcNutrition.COUNT;i++){
            deficits.add(known?Math.max(0L,each-Math.max(0,stock.get(i))):0L);
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingLong(deficits::get).reversed()
                .thenComparingInt(Integer::intValue));
        return new Demand(known,each,stock,deficits,order);
    }

    /** Read-only rank: largest missing dietary group wins; cost is tie-breaker. */
    public static long nutritionValue(Demand need,int[] nutrients) {
        if(need==null||!need.known||nutrients==null
                ||nutrients.length!=KingdomNpcNutrition.COUNT)return 0;
        long score=0;
        for(int i=0;i<KingdomNpcNutrition.COUNT;i++){
            if(nutrients[i]<=0)continue;
            long missing=need.missing().get(i);
            if(missing>0)
                score+=Math.min(1_000_000L,missing)*Math.min(100,nutrients[i]);
        }
        return score;
    }

    /** Pure comparison; never prefers an unknown or physically unavailable lot. */
    public static List<Candidate> prioritize(Demand need,List<Candidate> offered,int limit) {
        if(need==null||!need.known||offered==null||limit<=0)return List.of();
        return offered.stream().filter(c->c!=null&&c.available()>0)
            .sorted(Comparator.<Candidate>comparingLong(
                c->nutritionValue(need,c.nutrients())).reversed()
                .thenComparingLong(Candidate::unitCopper)
                .thenComparingLong(Candidate::expiry)
                .thenComparing(Candidate::itemId))
            .limit(Math.min(limit,16)).toList();
    }

    public static String groupName(int group) {
        return switch(group) {
            case 0 -> "Зерновые";
            case 1 -> "Фрукты";
            case 2 -> "Овощи";
            case 3 -> "Белки";
            case 4 -> "Молочные";
            default -> "Неизвестно";
        };
    }
}
