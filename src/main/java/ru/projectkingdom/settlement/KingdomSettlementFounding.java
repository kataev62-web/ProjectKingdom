package ru.projectkingdom.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.administration.AdministrativeCoreBinding;
import ru.projectkingdom.administration.AdministrativeCoreBlockEntity;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationRegistry;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomTerritories;

/** Authoritative one-founder-one-town transaction at the actual charter block. */
public final class KingdomSettlementFounding {
    public enum Result {
        CREATED, BAD_NAME, NOT_OWNER, ALREADY_FOUNDER, NEED_FREE_LAND, TOO_CLOSE,
        OUTSIDE_WORLD, NOT_OVERWORLD, BROKEN_BLOCK, REGISTER_ERROR
    }
    private KingdomSettlementFounding(){}

    public static String cleanName(String name){
        if(name==null)return null;
        // Reject control characters before normalizing whitespace: a line break
        // must not be silently converted into an accepted settlement title.
        if(name.indexOf('§')>=0 || name.chars().anyMatch(Character::isISOControl))
            return null;
        String value=name.strip().replaceAll("\\s+"," ");
        if(value.isBlank()||value.length()>32)return null;
        return value;
    }
    public static Result create(ServerPlayer player,BlockPos pos,String name){
        if(player==null||pos==null||player.getServer()==null)return Result.BROKEN_BLOCK;
        var server=player.getServer();
        if(!server.isSameThread())return Result.REGISTER_ERROR;
        var level=player.serverLevel();
        String dimension=level.dimension().location().toString();
        if(!"minecraft:overworld".equals(dimension))return Result.NOT_OVERWORLD;
        if(!level.hasChunkAt(pos)||pos.distSqr(player.blockPosition())>64)return Result.BROKEN_BLOCK;
        var placed=level.getBlockEntity(pos);
        boolean legacy=placed instanceof KingdomSettlementCharterEntity charter
                &&level.getBlockState(pos).is(KingdomSettlementRegistry.CHARTER.get())
                &&!charter.founded();
        boolean hall=placed instanceof AdministrativeCoreBlockEntity core
                &&level.getBlockState(pos).is(KingdomAdministrationRegistry.TOWN_HALL.get())
                &&!core.bound();
        if(!legacy&&!hall)return Result.BROKEN_BLOCK;
        UUID placer=legacy?((KingdomSettlementCharterEntity)placed).placer()
                :((AdministrativeCoreBlockEntity)placed).placer();
        if(!player.getUUID().equals(placer))return Result.NOT_OWNER;
        String title=cleanName(name);
        if(title==null)return Result.BAD_NAME;
        var civic=KingdomCivicsData.get(server);
        var admin=KingdomAdministrationData.get(server);
        var oldTown=civic.townOf(player.getUUID());
        boolean recoverGhost=oldTown!=null
                &&player.getUUID().equals(oldTown.mayor())
                &&player.getUUID().equals(admin.founder(oldTown.id()));
        if(oldTown!=null&&!recoverGhost)return Result.ALREADY_FOUNDER;
        for(var town:civic.towns()){
            if(!town.id().equals(oldTown==null?null:oldTown.id())
                    &&player.getUUID().equals(admin.founder(town.id())))return Result.ALREADY_FOUNDER;
            if(!town.id().equals(oldTown==null?null:oldTown.id())
                    &&town.name().equalsIgnoreCase(title))return Result.BAD_NAME;
        }
        int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        BlockPos corner1=new BlockPos(cx<<4,pos.getY(),cz<<4);
        BlockPos corner2=new BlockPos((cx<<4)+15,pos.getY(),(cz<<4)+15);
        if(!level.getWorldBorder().isWithinBounds(corner1)
                ||!level.getWorldBorder().isWithinBounds(corner2))return Result.OUTSIDE_WORLD;
        var old=KingdomTerritories.get(server);
        if(civic.plot(level,pos)!=null||old.legacyClaimOwner(dimension,cx,cz)!=null)return Result.NEED_FREE_LAND;
        // Prevent founding directly against another city's settlement.
        for(var plot:civic.mapPlots(dimension))
            if(Math.abs((long)plot.x()-cx)<=3 && Math.abs((long)plot.z()-cz)<=3)
                return Result.TOO_CLOSE;
        // Repair only a truly EMPTY legacy ghost town. Check before reserving
        // the chunk in the legacy claim mirror to avoid orphan claims on refusal.
        if(recoverGhost && (!civic.townPlots(oldTown.id(),0,1).isEmpty()
                ||civic.townMembers(oldTown.id(),0,2).size()!=1))
            return Result.ALREADY_FOUNDER;
        // Legacy ownership mirror first, then the civic registry; reusing the
        // same authenticated UUID avoids anyone claiming an unowned stranger's land.
        if(!old.claimByChunk(dimension,cx,cz,player.getUUID()))return Result.NEED_FREE_LAND;
        KingdomCivicsData.Town founded;
        if(recoverGhost){
            if(!civic.bootstrapEmptyLegacyTown(player.getUUID(),title,dimension,cx,cz))
                return Result.ALREADY_FOUNDER;
            founded=civic.townById(oldTown.id());
        }else{
            founded=civic.foundTown(player.getUUID(),title);
            if(founded==null)return Result.REGISTER_ERROR;
            if(!civic.claim(player.getUUID(),dimension,cx,cz))
                throw new IllegalStateException("Settlement charter claim failed after title check");
        }
        if(!admin.ensureFounder(founded.id(),player.getUUID()))
            throw new IllegalStateException("Settlement charter founder register failed");
        if(hall){
            var binding=new AdministrativeCoreBinding(founded.id(),AdministrativeKind.TOWN_HALL,dimension,pos.asLong());
            if(!admin.activateCore(player.getUUID(),founded.mayor(),binding))
                throw new IllegalStateException("New town hall registration failed");
            ((AdministrativeCoreBlockEntity)placed).bind(founded.id(),player.getUUID(),AdministrativeKind.TOWN_HALL);
        }else ((KingdomSettlementCharterEntity)placed).establish(founded.id());
        KingdomSettlementRanks.get(server).initializeHamlet(founded.id());
        player.sendSystemMessage(Component.literal("[Kingdom] Основан хутор «"+title+
                "». Стартовая земля 16×16 зарегистрирована. Постройте здания и расширяйтесь через карту."));
        return Result.CREATED;
    }
    public static String notice(Result result){
        return switch(result){
            case CREATED -> "Поселение основано!";
            case BAD_NAME -> "Укажите уникальное имя до 32 символов без управляющих знаков.";
            case NOT_OWNER -> "Основать поселение может только игрок, поставивший блок.";
            case ALREADY_FOUNDER -> "Один игрок может основать только одно поселение и не должен состоять в другом.";
            case NEED_FREE_LAND -> "Земля уже занята или принадлежит другому городу.";
            case TOO_CLOSE -> "Слишком близко к другому городу. Отойдите на 4 чанка.";
            case OUTSIDE_WORLD -> "Чанк находится за границей мира.";
            case NOT_OVERWORLD -> "Поселение можно основать только в обычном мире.";
            case BROKEN_BLOCK -> "Ратуша не найдена, уже привязана или вы слишком далеко.";
            case REGISTER_ERROR -> "Не удалось зарегистрировать поселение. Проверьте сервер.";
        };
    }
}
