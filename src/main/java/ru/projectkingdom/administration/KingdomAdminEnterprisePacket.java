package ru.projectkingdom.administration;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

/** Never trusts client-supplied ownership or municipality. Two-step permissioned close. */
public record KingdomAdminEnterprisePacket(int action,UUID enterprise) {
    public static final int ARM=1,CONFIRM=2,REFRESH=3;
    public static void encode(KingdomAdminEnterprisePacket p,FriendlyByteBuf out){
        out.writeVarInt(p.action);
        out.writeBoolean(p.enterprise!=null);
        if(p.enterprise!=null)out.writeUUID(p.enterprise);
    }
    public static KingdomAdminEnterprisePacket decode(FriendlyByteBuf in){
        int action=in.readVarInt();
        return new KingdomAdminEnterprisePacket(action,in.readBoolean()?in.readUUID():null);
    }
    public static void handle(KingdomAdminEnterprisePacket p,Supplier<NetworkEvent.Context> supplied){
        var ctx=supplied.get();
        ctx.enqueueWork(()->{
            var actor=ctx.getSender();
            if(actor==null||!actor.hasPermissions(2)
                    ||!(actor.containerMenu instanceof KingdomAdminEnterpriseMenu menu)
                    ||!menu.stillValid(actor))return;
            if(p.action==REFRESH){KingdomAdminEnterpriseMenu.open(actor);return;}
            if(p.enterprise==null||!menu.contains(p.enterprise))return;
            if(p.action==ARM){
                var current=KingdomAdminEnterpriseService.location(actor.getServer(),p.enterprise);
                if(current==null){actor.sendSystemMessage(Component.literal("[Kingdom] Предприятие больше недоступно."));return;}
                menu.arm(p.enterprise,System.currentTimeMillis());
                actor.sendSystemMessage(Component.literal("[Kingdom] Подтвердите закрытие «"+
                        current.name()+"» в течение 20 секунд. Остатки и долги будут проверены."));
            }else if(p.action==CONFIRM && menu.consumeArm(p.enterprise,System.currentTimeMillis())){
                var result=KingdomAdminEnterpriseService.close(actor,p.enterprise);
                if(result==null){
                    actor.sendSystemMessage(Component.literal("[Kingdom] Не найдено предприятие в городе или государстве."));
                    return;
                }
                actor.sendSystemMessage(Component.literal("[Kingdom] "+
                        ru.projectkingdom.npc.KingdomEnterpriseData.closeMessage(result.status())));
                if(result.status()==ru.projectkingdom.npc.KingdomEnterpriseData.ClosureStatus.CLOSED)
                    KingdomAdminEnterpriseMenu.open(actor);
            }
        });
        ctx.setPacketHandled(true);
    }
}
