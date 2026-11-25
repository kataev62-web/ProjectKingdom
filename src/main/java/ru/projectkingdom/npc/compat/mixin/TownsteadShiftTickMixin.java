package ru.projectkingdom.npc.compat.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.projectkingdom.npc.KingdomMcaShiftArbiter;
import ru.projectkingdom.npc.KingdomResidentRestController;
import ru.projectkingdom.npc.compat.TownsteadNpcCompatibility;

/** Defer Townstead's extra server tick only while Kingdom owns this resident.
 * MCA family/core logic and Townstead HEAD fatigue/collapse safety stay active. */
@Pseudo
@Mixin(targets = "com.aetherianartificer.townstead.tick.VillagerServerTickDispatcher", remap = false)
public abstract class TownsteadShiftTickMixin {
    @Inject(method = "tick(Lforge/net/conczin/mca/entity/VillagerEntityMCA;)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void kingdom$deferAutonomousTick(@Coerce Object resident, CallbackInfo ci) {
        if (!(resident instanceof Villager villager)
                || !(KingdomMcaShiftArbiter.onShift(villager.getUUID())
                    || KingdomResidentRestController.requiresRestControl(villager))) return;
        if (TownsteadNpcCompatibility.mustRest(villager)) {
            KingdomMcaShiftArbiter.release(villager);
            return;
        }
        TownsteadNpcCompatibility.recordDeferral();
        ci.cancel();
    }
}
