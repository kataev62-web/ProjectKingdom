package ru.projectkingdom.npc.compat.mixin;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.projectkingdom.npc.KingdomMcaShiftArbiter;
import ru.projectkingdom.npc.KingdomResidentRestController;

/** Keep native schedules intact while a movement lease or native fatigue rest is active. */
@Mixin(value = Brain.class, remap = false)
public abstract class KingdomBrainScheduleMixin {
    @Inject(method = {"updateActivityFromSchedule(JJ)V", "m_21862_(JJ)V"},
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void kingdom$keepExclusiveActivity(long dayTime, long gameTime, CallbackInfo ci) {
        Brain<?> brain = (Brain<?>)(Object)this;
        if (brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE) || brain.isActive(Activity.RAID)) return;
        if (KingdomMcaShiftArbiter.shouldDeferScheduleUpdate(brain)
                || KingdomResidentRestController.shouldKeepRestActivity(brain)) ci.cancel();
    }
}
