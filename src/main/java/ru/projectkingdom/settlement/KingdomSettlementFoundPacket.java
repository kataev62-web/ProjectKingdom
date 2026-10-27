package ru.projectkingdom.settlement;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

public record KingdomSettlementFoundPacket(BlockPos pos,String name) {
    public static void encode(KingdomSettlementFoundPacket packet,FriendlyByteBuf out){
        out.writeBlockPos(packet.pos);
        out.writeUtf(packet.name==null?"":packet.name,40);
    }
    public static KingdomSettlementFoundPacket decode(FriendlyByteBuf in){
        return new KingdomSettlementFoundPacket(in.readBlockPos(),in.readUtf(40));
    }
    public static void handle(KingdomSettlementFoundPacket packet,Supplier<NetworkEvent.Context> contextSupplier){
        var context=contextSupplier.get();
        context.enqueueWork(()->{
            var player=context.getSender();
            if(player==null||!(player.containerMenu instanceof KingdomSettlementMenu menu)
                    ||!menu.pos().equals(packet.pos)||!menu.stillValid(player))return;
            var result=KingdomSettlementFounding.create(player,packet.pos,packet.name);
            player.sendSystemMessage(Component.literal("[Kingdom] "+KingdomSettlementFounding.notice(result)));
            if(result==KingdomSettlementFounding.Result.CREATED){
                var entity=player.serverLevel().getBlockEntity(packet.pos);
                if(entity instanceof ru.projectkingdom.administration.AdministrativeCoreBlockEntity hall)
                    ru.projectkingdom.administration.AdministrativeCoreMenu.open(player,hall);
                else if(entity instanceof KingdomSettlementCharterEntity core)
                    KingdomSettlementMenu.open(player,core);
            }
        });
        context.setPacketHandled(true);
    }
}
