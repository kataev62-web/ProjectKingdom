package ru.projectkingdom.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** S2C: server-owned blueprint catalog and one genuine exterior 3D miniature. */
public record ArchitectCatalogPacket(UUID token, List<Entry> entries, int selected,
                                     ArchitectPreviewPacket miniature, List<Material> materials) {
    public record Entry(String key, String title, int width, int height, int length, int blocks) {}
    public record Material(String blockId, int count) {}
    public static void encode(ArchitectCatalogPacket packet,FriendlyByteBuf buf){
        buf.writeUUID(packet.token);
        buf.writeVarInt(packet.entries.size());
        for(var e:packet.entries){
            buf.writeUtf(e.key,72);buf.writeUtf(e.title,96);
            buf.writeVarInt(e.width);buf.writeVarInt(e.height);buf.writeVarInt(e.length);buf.writeVarInt(e.blocks);
        }
        buf.writeVarInt(packet.selected);
        ArchitectPreviewPacket.encode(packet.miniature,buf);
        buf.writeVarInt(packet.materials.size());
        for(var material:packet.materials){
            buf.writeUtf(material.blockId,150);buf.writeVarInt(material.count);
        }
    }
    public static ArchitectCatalogPacket decode(FriendlyByteBuf buf){
        UUID token=buf.readUUID();
        int n=buf.readVarInt();
        if(n<1||n>80)throw new IllegalArgumentException("Blueprint catalog too large");
        var entries=new ArrayList<Entry>();
        for(int i=0;i<n;i++){
            var e=new Entry(buf.readUtf(72),buf.readUtf(96),
                buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt());
            if(e.width<1||e.width>48||e.height<1||e.height>48
                    ||e.length<1||e.length>48||e.blocks<1||e.blocks>65_536)
                throw new IllegalArgumentException("Invalid blueprint catalog entry");
            entries.add(e);
        }
        int selected=buf.readVarInt();
        if(selected<0||selected>=n)throw new IllegalArgumentException("Bad blueprint index");
        var model=ArchitectPreviewPacket.decode(buf);
        int matCount=buf.readVarInt();
        if(matCount<0||matCount>512)throw new IllegalArgumentException("Invalid material count");
        var materials=new ArrayList<Material>();
        for(int i=0;i<matCount;i++){
            String id=buf.readUtf(150);int count=buf.readVarInt();
            if(count<1||count>65_536)throw new IllegalArgumentException("Invalid material amount");
            materials.add(new Material(id,count));
        }
        return new ArchitectCatalogPacket(token,List.copyOf(entries),selected,model,List.copyOf(materials));
    }
    public static void handle(ArchitectCatalogPacket packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            ()->()->ru.projectkingdom.npc.client.KingdomArchitectCatalogScreen.receive(packet)));
        ctx.setPacketHandled(true);
    }
}
