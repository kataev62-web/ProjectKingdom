package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import ru.projectkingdom.enterprise.EnterpriseBuildingRegistration;
import ru.projectkingdom.enterprise.EnterpriseCoreBinding;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Shared, read-only identity check for a smithy: payroll workplace UUID,
 * registered enterprise, active physical core, saved building and TFC forge.
 * Never guesses another company or automatically changes ownership.
 */
public final class KingdomSmithySite {
    private KingdomSmithySite(){}

    public enum Status {
        READY("Кузнец: здание и горн TFC привязаны"),
        NO_COMPANY("Кузнец: выбери предприятие «Кузница» и выполни настройку NPC"),
        WRONG_COMPANY("Кузнец: рабочее место привязано к неверному предприятию"),
        WRONG_OWNER("Кузнец: предприятие не принадлежит работодателю"),
        WRONG_CORE("Кузнец: ядро предприятия отсутствует или его привязка устарела"),
        WRONG_DIMENSION("Кузнец: ядро, здание и NPC должны быть в одной размерности"),
        CORE_UNLOADED("Кузнец: чанк с ядром кузницы не загружен"),
        CORE_MISMATCH("Кузнец: установленное ядро не соответствует предприятию"),
        NO_ROOM("Кузнец: здание этой кузницы не зарегистрировано; привяжи два угла через ядро"),
        ROOM_STALE("Кузнец: здание привязано к другому или старому ядру; зарегистрируй заново"),
        NO_FORGE_ASSIGNMENT("Кузнец: назначь горн TFC как рабочее место NPC"),
        FORGE_OUTSIDE("Кузнец: горн вне зарегистрированных границ кузницы"),
        FORGE_UNLOADED("Кузнец: чанк с горном не загружен"),
        NO_ACCESS("Кузнец: нет прав работодателя на участок кузницы"),
        NOT_FORGE("Кузнец: на рабочем месте нет настоящего горна TFC");
        private final String message;
        Status(String message){this.message=message;}
        public String message(){return message;}
        public boolean ready(){return this==READY;}
    }

    /**
     * Pure identity test. A room surviving a save/restart must still refer to
     * the EXACT active core of the SAME company, in the correct dimension.
     */
    public static Status registration(UUID company,UUID employer,
            KingdomEnterpriseData.Enterprise firm,EnterpriseCoreBinding core,
            EnterpriseBuildingRegistration.Room room,String dimension){
        if(company==null||firm==null||!company.equals(firm.id())
                )return Status.WRONG_COMPANY;
        if(employer==null||!employer.equals(firm.owner()))return Status.WRONG_OWNER;
        if(core==null||!core.active()||!company.equals(core.enterpriseId())
                ||core.kind()!=EnterpriseKind.SMITHY
                ||!employer.equals(core.owner()))return Status.WRONG_CORE;
        if(!core.dimension().equals(dimension))return Status.WRONG_DIMENSION;
        if(room==null)return Status.NO_ROOM;
        if(!company.equals(room.enterprise())||room.core()!=core.packedPos()
                ||!room.contains(BlockPos.of(core.packedPos())))return Status.ROOM_STALE;
        if(!room.dimension().equals(core.dimension()))return Status.WRONG_DIMENSION;
        return Status.READY;
    }

    public static Status station(EnterpriseBuildingRegistration.Room room,BlockPos forge,
                                 boolean loaded,boolean accessible,boolean nativeForge) {
        if(forge==null)return Status.NO_FORGE_ASSIGNMENT;
        if(room==null||!room.contains(forge))return Status.FORGE_OUTSIDE;
        if(!loaded)return Status.FORGE_UNLOADED;
        if(!accessible)return Status.NO_ACCESS;
        if(!nativeForge)return Status.NOT_FORGE;
        return Status.READY;
    }

    public static Status inspect(ServerLevel level,KingdomNpcData.Worker worker){
        if(level==null||worker==null||!"smith".equals(worker.job))return Status.WRONG_COMPANY;
        UUID company;
        try {company=UUID.fromString(worker.workplaceId);}
        catch(IllegalArgumentException|NullPointerException ex){return Status.NO_COMPANY;}
        var firms=KingdomEnterpriseData.get(level.getServer());
        var firm=firms.find(company);
        if(firm==null||firms.kind(company)!=EnterpriseKind.SMITHY)
            return Status.WRONG_COMPANY;
        var core=firms.core(company);
        var room=EnterpriseBuildingRegistration.get(level.getServer()).room(company);
        Status identity=registration(company,worker.employer,firm,core,room,
                level.dimension().location().toString());
        if(!identity.ready())return identity;
        if(!worker.dimension.equals(level.dimension().location().toString()))
            return Status.WRONG_DIMENSION;
        BlockPos corePos=BlockPos.of(core.packedPos());
        if(!level.hasChunkAt(corePos))return Status.CORE_UNLOADED;
        if(!(level.getBlockEntity(corePos) instanceof EnterpriseCoreBlockEntity block)
                ||!block.bound()||!company.equals(block.enterpriseId())
                ||!worker.employer.equals(block.owner())||block.kind()!=EnterpriseKind.SMITHY)
            return Status.CORE_MISMATCH;
        var territory=KingdomTerritories.get(level.getServer());
        if(!territory.canUse(level,corePos,worker.employer))return Status.NO_ACCESS;
        if(!worker.hasWorkstation)return Status.NO_FORGE_ASSIGNMENT;
        BlockPos forge=new BlockPos(worker.workX,worker.workY,worker.workZ);
        boolean loaded=room.contains(forge)&&level.hasChunkAt(forge);
        boolean access=loaded&&territory.canUse(level,forge,worker.employer);
        boolean nativeForge=access&&KingdomTfcSmithWork.isForge(level,worker)
                &&level.getBlockEntity(forge)!=null
                &&"net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity"
                    .equals(level.getBlockEntity(forge).getClass().getName());
        return station(room,forge,loaded,access,nativeForge);
    }
}
