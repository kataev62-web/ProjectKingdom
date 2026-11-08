package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** S2C only. The visual survey never authorizes land, contents, copying or payments. */
public record KingdomVolumeSelectionPacket(int mode,BlockPos first,boolean active,int depth) {
    public KingdomVolumeSelectionPacket(int mode,BlockPos first,boolean active){this(mode,first,active,15);}
    public static final int WAREHOUSE=1,ENTERPRISE=2,COPY=3,MINE=4;
    public KingdomVolumeSelectionPacket {
        if(mode<1||mode>4||depth<1||depth>64)throw new IllegalArgumentException("survey mode/depth");
    }
    public static void encode(KingdomVolumeSelectionPacket p,FriendlyByteBuf b){
        b.writeVarInt(p.mode());b.writeBoolean(p.active());
        b.writeBoolean(p.first()!=null);
        if(p.first()!=null)b.writeBlockPos(p.first());
        b.writeVarInt(p.depth());
    }
    public static KingdomVolumeSelectionPacket decode(FriendlyByteBuf b){
        int mode=b.readVarInt();boolean active=b.readBoolean();
        BlockPos first=b.readBoolean()?b.readBlockPos():null;
        return new KingdomVolumeSelectionPacket(mode,first,active,b.readVarInt());
    }
    public static void handle(KingdomVolumeSelectionPacket packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        if(ctx.getDirection()==NetworkDirection.PLAY_TO_CLIENT)
            ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->ru.projectkingdom.npc.client.KingdomVolumeSelectionOverlay.accept(packet)));
        ctx.setPacketHandled(true);
    }
    public static void send(net.minecraft.server.level.ServerPlayer player,int mode,BlockPos first,boolean active){
        KingdomNetwork.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),
            new KingdomVolumeSelectionPacket(mode,first,active));
    }
    public static void send(net.minecraft.server.level.ServerPlayer player,
            int mode,BlockPos first,boolean active,int depth){
        KingdomNetwork.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),
            new KingdomVolumeSelectionPacket(mode,first,active,depth));
    }
}
