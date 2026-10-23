package ru.projectkingdom.social;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomCurrency;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomNpcData;
import ru.projectkingdom.npc.KingdomPayrollData;
import ru.projectkingdom.npc.KingdomTaxData;

/** Strict town-hall promotion: achievements -> physical purse debit -> treasury reserve -> rank. */
public final class KingdomSocialPromotionService {
    public record Preview(int level, long balanceCopper, long taxDebt,
                          long enterprises, long workers, long plots, boolean hasJob,
                          boolean ready) {
        public static Preview empty() { return new Preview(1,0,0,0,0,0,false,false); }
        public void write(FriendlyByteBuf out) {
            out.writeVarInt(level);out.writeLong(balanceCopper);out.writeLong(taxDebt);
            out.writeLong(enterprises);out.writeLong(workers);out.writeLong(plots);
            out.writeBoolean(hasJob);out.writeBoolean(ready);
        }
        public static Preview read(FriendlyByteBuf in) {
            return new Preview(in.readVarInt(),in.readLong(),in.readLong(),in.readLong(),
                    in.readLong(),in.readLong(),in.readBoolean(),in.readBoolean());
        }
        public KingdomSocialRank rank() { return KingdomSocialRank.fromLevel(level); }
        public KingdomSocialPromotionRules.Requirements requirements() {
            return KingdomSocialPromotionRules.forCurrent(rank());
        }
    }

    private KingdomSocialPromotionService() {}

    public static Preview preview(ServerPlayer player, UUID townId) {
        if (player == null || townId == null) return Preview.empty();
        var server = player.getServer();
        var civics = KingdomCivicsData.get(server);
        if (!civics.isMember(townId,player.getUUID())) return Preview.empty();
        UUID person = player.getUUID();
        var rank = KingdomSocialData.get(server).rank(person);
        long firms = KingdomEnterpriseData.get(server).ownedAll(person).size();
        long hired = KingdomNpcData.get(server).all().stream()
                .filter(w -> person.equals(w.employer)).count();
        long plots = civics.purchasedPlotOwnerCounts().getOrDefault(person,0L);
        boolean employed = KingdomPayrollData.get(server).contract(person) != null;
        long debt = KingdomTaxData.get(server).owed(server,person);
        long balance = KingdomCurrency.balance(player);
        var progress = new KingdomSocialPromotionRules.Progress(firms,hired,plots,employed,debt,balance);
        return new Preview(rank.level(),balance,debt,firms,hired,plots,employed,
                KingdomSocialPromotionRules.evaluate(rank,progress).ready());
    }

    public static boolean promote(ServerPlayer player, UUID townId) {
        if (player == null || townId == null) return false;
        var server = player.getServer();
        var civics = KingdomCivicsData.get(server);
        var town = civics.townById(townId);
        if (town == null || !civics.isMember(townId,player.getUUID())) return false;
        // The packet handler ALSO validates the currently open, nearby active town hall.
        Preview snapshot = preview(player,townId);
        var rule = snapshot.requirements();
        if (!snapshot.ready() || rule == null) return false;
        long amount = rule.feeCopper();
        var admin = KingdomAdministrationData.get(server);
        if (town.treasury() > Long.MAX_VALUE - amount
                || admin.treasuryReserve(townId) > Long.MAX_VALUE - amount) return false;
        if (!KingdomCurrency.debit(player,amount)) return false;
        if (!civics.depositTownTo(townId,player.getUUID(),amount)) {
            if (!KingdomCurrency.credit(player,amount))
                throw new IllegalStateException("Social fee refund to physical purse failed");
            return false;
        }
        if (!KingdomSocialData.get(server).grantVerified(player.getUUID(),rule.target())) {
            if (!civics.payTownObligation(townId,amount) || !KingdomCurrency.credit(player,amount))
                throw new IllegalStateException("Social promotion refund failed after treasury deposit");
            return false;
        }
        admin.record(townId,player.getUUID(),null,"social.promote."+rule.target().level(),
                player.level().getGameTime());
        return true;
    }
}
