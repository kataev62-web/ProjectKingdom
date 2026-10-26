package ru.projectkingdom.npc.voice.client;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Client hint only; the server repeats every entity and range check. */
public final class NpcVoiceTargeting {
    static final double MAX_DISTANCE=8.0D;
    private NpcVoiceTargeting() {}

    public static Optional<UUID> target(Minecraft minecraft){
        Entity camera=minecraft.getCameraEntity();
        if(camera==null)return Optional.empty();
        Vec3 start=camera.getEyePosition();
        Vec3 direction=camera.getViewVector(1.0F);
        Vec3 end=start.add(direction.scale(MAX_DISTANCE));
        var bounds=camera.getBoundingBox().expandTowards(direction.scale(MAX_DISTANCE)).inflate(1.0D);
        EntityHitResult hit=ProjectileUtil.getEntityHitResult(camera,start,end,bounds,
                entity->entity instanceof Villager&&!entity.isSpectator(),MAX_DISTANCE*MAX_DISTANCE);
        return hit!=null&&hit.getEntity() instanceof Villager
                ?Optional.of(hit.getEntity().getUUID()):Optional.empty();
    }
}
