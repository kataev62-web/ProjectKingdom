package ru.projectkingdom.settlement;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomConstructionData;

/** Authenticated stepwise promotion from the currently active town hall. */
public final class KingdomTownHallPromotion {
    public enum Result {
        PROMOTED("Уровень поселения повышен!"),
        NOT_AUTHORIZED("Повышать статус может только основатель или мэр."),
        UNKNOWN_TOWN("Поселение больше не существует."),
        NO_HALL("Сначала нужна действующая ратуша."),
        MAX_RANK("Поселение уже имеет статус города."),
        NOT_READY("Недостаточно земли, завершённых зданий или жителей. Проверьте требования в ратуше."),
        INVALID_STATE("Не удалось обновить статус. Откройте ратушу повторно.");
        private final String message;
        Result(String message){this.message=message;}
        public String message(){return message;}
    }
    private KingdomTownHallPromotion(){}

    public static Result promote(ServerPlayer player,UUID townId){
        if(player==null||townId==null||player.getServer()==null)return Result.INVALID_STATE;
        if(!player.getServer().isSameThread())return Result.INVALID_STATE;
        var civics=KingdomCivicsData.get(player.getServer());
        var town=civics.townById(townId);
        if(town==null)return Result.UNKNOWN_TOWN;
        var admin=KingdomAdministrationData.get(player.getServer());
        if(!player.getUUID().equals(admin.founder(townId))
                &&!player.getUUID().equals(town.mayor()))return Result.NOT_AUTHORIZED;
        var hall=admin.core(townId,AdministrativeKind.TOWN_HALL);
        if(hall==null||!hall.dimension().equals(player.serverLevel().dimension().location().toString())
                ||!player.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.of(hall.packedPos())))
            return Result.NO_HALL;
        var pos=net.minecraft.core.BlockPos.of(hall.packedPos());
        if(!player.serverLevel().getBlockState(pos).is(
                ru.projectkingdom.administration.KingdomAdministrationRegistry.TOWN_HALL.get())
                ||!(player.serverLevel().getBlockEntity(pos) instanceof
                    ru.projectkingdom.administration.AdministrativeCoreBlockEntity core)
                ||!core.bound()||!townId.equals(core.townId()))return Result.NO_HALL;
        var progress=KingdomSettlementGrowth.inspect(civics,KingdomConstructionData.get(player.getServer()),
                townId,player.serverLevel().dimension().location().toString(),player.getServer());
        var ranks=KingdomSettlementRanks.get(player.getServer());
        var current=ranks.current(townId,progress.eligible());
        if(current==KingdomSettlementGrowth.Rank.CITY)return Result.MAX_RANK;
        var next=KingdomSettlementGrowth.Rank.values()[current.ordinal()+1];
        if(progress.eligible().ordinal()<next.ordinal())return Result.NOT_READY;
        if(!ranks.promote(townId,current,next))return Result.INVALID_STATE;
        admin.record(townId,player.getUUID(),null,"Поселение повышено до "+next.title(),player.level().getGameTime());
        return Result.PROMOTED;
    }
}
