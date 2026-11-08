package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * The miner's workplace is a real mine ENTERPRISE plus its surveyed shaft.
 * A mine is not a crafting station: hasWorkstation is never a precondition.
 * This read-only inspection is shared by workday scheduling and dialogue.
 */
public final class KingdomMineSite {
    private KingdomMineSite() {}

    public record Inspection(boolean ready, BlockPos entrance, String reason) {}

    public static Inspection inspect(MinecraftServer server, ServerLevel level,
                                     KingdomNpcData.Worker worker) {
        if(worker==null || !"miner".equals(worker.job))
            return new Inspection(false,null,"Профессия шахтёра не назначена");
        if(worker.employer==null)
            return new Inspection(false,null,"Шахтёр не нанят");
        UUID mineId;
        try {mineId=UUID.fromString(worker.workplaceId);}
        catch(IllegalArgumentException | NullPointerException ex) {
            return new Inspection(false,null,"Привяжи шахтёра к предприятию «Шахта»");
        }
        var enterprises=KingdomEnterpriseData.get(server);
        var firm=enterprises.find(mineId);
        var core=enterprises.core(mineId);
        if(firm==null||core==null||!core.active()
            ||enterprises.kind(mineId)!=EnterpriseKind.MINE
            ||!worker.employer.equals(firm.owner()))
            return new Inspection(false,null,"Нет действующего предприятия «Шахта»");
        if(!worker.dimension.equals(level.dimension().location().toString())
            ||!core.dimension().equals(worker.dimension))
            return new Inspection(false,null,"Шахта находится в другом измерении");
        if(!worker.hasSource||!worker.hasDestination)
            return new Inspection(false,null,"Привяжи склады сырья и продукции шахты");
        var settings=KingdomMineSettings.get(server);
        BlockPos entrance;
        if(settings.start(mineId)==KingdomMineSettings.Start.DOWN){
            var area=settings.shaft(mineId);
            if(area==null||!area.dimension().equals(worker.dimension)
                ||!KingdomMineCentralShaftPlan.valid(area,BlockPos.of(core.packedPos())))
                return new Inspection(false,null,
                    "В блоке предприятия выбери «Сразу вниз» и выдели ствол 7×7–12×12");
            entrance=KingdomMineCentralShaftPlan.entrance(area);
        }else entrance=BlockPos.of(core.packedPos()).north();
        if(!level.hasChunkAt(entrance)||!level.hasChunkAt(entrance.below()))
            return new Inspection(false,entrance,"Загрузи участок входа в шахту");
        if(!level.getBlockState(entrance).isAir()
            ||!level.getBlockState(entrance.above()).isAir()
            ||level.getBlockState(entrance.below()).isAir())
            return new Inspection(false,entrance,
                "Освободи вход 1×2 с полом под ногами: "+entrance.toShortString());
        return new Inspection(true,entrance,"Рабочее место шахтёра готово");
    }
}
