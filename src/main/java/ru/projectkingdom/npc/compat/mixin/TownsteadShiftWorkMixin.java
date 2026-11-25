package ru.projectkingdom.npc.compat.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.projectkingdom.npc.KingdomMcaShiftArbiter;

/** Stop native work through Behavior's real SRG bridges, retaining its stop cleanup. */
@Pseudo
@Mixin(targets = {
    "com.aetherianartificer.townstead.work.job.BlockInteractionWorkTask",
    "com.aetherianartificer.townstead.work.job.EntityInteractionWorkTask",
    "com.aetherianartificer.townstead.work.job.EntityDeliveryWorkTask",
    "com.aetherianartificer.townstead.work.producer.ProducerWorkTask",
    "com.aetherianartificer.townstead.work.producer.FinishCommittedProductionTask",
    "com.aetherianartificer.townstead.work.martial.MartialWorkTask",
    "com.aetherianartificer.townstead.hunger.HarvestWorkTask",
    "com.aetherianartificer.townstead.hunger.FishermanWorkTask",
    "com.aetherianartificer.townstead.hunger.RefuelTask",
    "com.aetherianartificer.townstead.shepherd.ShepherdDepositTask"
}, remap = false)
public abstract class TownsteadShiftWorkMixin {
    @Inject(method = "m_6114_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void kingdom$deferWorkStart(ServerLevel level, LivingEntity resident,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (KingdomMcaShiftArbiter.onShift(resident.getUUID())) cir.setReturnValue(false);
    }

    @Inject(method = "m_6737_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)Z",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void kingdom$deferRunningWork(ServerLevel level, LivingEntity resident, long time,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (KingdomMcaShiftArbiter.onShift(resident.getUUID())) cir.setReturnValue(false);
    }
}
