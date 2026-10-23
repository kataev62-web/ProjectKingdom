package ru.projectkingdom.social;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import ru.projectkingdom.npc.KingdomCivicsData;

/** Trusted call sites only: no packet for arbitrary reputation awards. */
public final class KingdomReputationEvents {
    private KingdomReputationEvents() {}

    private static void award(MinecraftServer server,UUID person,UUID town,
                              KingdomReputationPolicy.Event event,long copper) {
        if(server==null||person==null||town==null)return;
        long day=Math.max(0,server.overworld().getDayTime()/24000L);
        KingdomReputationData.get(server).recordVerified(town,person,event,UUID.randomUUID(),copper,day);
    }
    public static void taxSettled(MinecraftServer server,UUID person,UUID paidTown,long copper) {
        award(server,person,paidTown,KingdomReputationPolicy.Event.TAX_PAYMENT,copper);
    }
    public static void earnedCityWage(MinecraftServer server,UUID person,UUID payingTown,long copper) {
        award(server,person,payingTown,KingdomReputationPolicy.Event.EARNED_WAGE,copper);
    }
    public static void earnedEnterpriseWage(MinecraftServer server,UUID person,long copper) {
        if(server==null)return;
        var town=KingdomCivicsData.get(server).townOf(person);
        if(town!=null)award(server,person,town.id(),KingdomReputationPolicy.Event.EARNED_WAGE,copper);
    }
    public static void landPurchased(MinecraftServer server,UUID person,UUID town,long copper) {
        award(server,person,town,KingdomReputationPolicy.Event.LAND_PURCHASE,copper);
    }
}
