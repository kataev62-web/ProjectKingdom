package ru.projectkingdom.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomDesignEngine;

/** S2C immutable, size-bounded 3D blueprint preview; no world blocks altered. */
public record ArchitectPreviewPacket(UUID token, String title, BlockPos marker,
                                      BlockPos origin, int width, int height, int length,
                                      List<String> palette, List<Cell> cells) {
    public record Cell(int x,int y,int z,int state) {}
    private static final int MAX_CELLS=2400;
    private static final int MAX_PALETTE=512;

    public static ArchitectPreviewPacket fromDesign(KingdomDesignEngine.Design plan,
                                                     UUID token, BlockPos marker,String title) {
        if(plan==null || plan.blocks().isEmpty() || plan.width()>48
                || plan.length()>48 || plan.height()>48)return null;
        var occupied=new HashSet<BlockPos>();
        for(var cell:plan.blocks())occupied.add(new BlockPos(cell.x(),cell.y(),cell.z()));
        var shell=new ArrayList<KingdomDesignEngine.Cell>();
        for(var cell:plan.blocks()){
            var pos=new BlockPos(cell.x(),cell.y(),cell.z());
            boolean exposed=false;
            for(var direction:net.minecraft.core.Direction.values())
                if(!occupied.contains(pos.relative(direction))){exposed=true;break;}
            if(exposed)shell.add(cell);
        }
        int stride=Math.max(1,(shell.size()+MAX_CELLS-1)/MAX_CELLS);
        var palette=new ArrayList<String>();
        var index=new HashMap<String,Integer>();
        var cells=new ArrayList<Cell>();
        for(int i=0;i<shell.size();i+=stride){
            var at=shell.get(i);
            Integer id=index.get(at.material());
            if(id==null){
                if(palette.size()>=MAX_PALETTE)return null;
                id=palette.size();
                index.put(at.material(),id);
                palette.add(at.material());
            }
            cells.add(new Cell(at.x(),at.y(),at.z(),id));
        }
        return new ArchitectPreviewPacket(token,title,marker,marker.offset(2,0,2),
                plan.width(),plan.height(),plan.length(),List.copyOf(palette),List.copyOf(cells));
    }

    public static void encode(ArchitectPreviewPacket p,FriendlyByteBuf buf){
        buf.writeUUID(p.token);
        buf.writeUtf(p.title,96);
        buf.writeBlockPos(p.marker);
        buf.writeBlockPos(p.origin);
        buf.writeVarInt(p.width);
        buf.writeVarInt(p.height);
        buf.writeVarInt(p.length);
        buf.writeVarInt(p.palette.size());
        for(var value:p.palette)buf.writeUtf(value,240);
        buf.writeVarInt(p.cells.size());
        for(var c:p.cells){
            buf.writeVarInt(c.x());buf.writeVarInt(c.y());
            buf.writeVarInt(c.z());buf.writeVarInt(c.state());
        }
    }
    public static ArchitectPreviewPacket decode(FriendlyByteBuf buf){
        UUID token=buf.readUUID();String title=buf.readUtf(96);
        BlockPos marker=buf.readBlockPos(),origin=buf.readBlockPos();
        int width=buf.readVarInt(),height=buf.readVarInt(),length=buf.readVarInt();
        if(width<1 || width>48 || height<1 || height>48 || length<1 || length>48)
            throw new IllegalArgumentException("Invalid architect dimensions");
        int paletteCount=buf.readVarInt();
        if(paletteCount<1||paletteCount>MAX_PALETTE)
            throw new IllegalArgumentException("Invalid architect palette");
        var palette=new ArrayList<String>(paletteCount);
        for(int i=0;i<paletteCount;i++)palette.add(buf.readUtf(240));
        int cellCount=buf.readVarInt();
        if(cellCount<1||cellCount>MAX_CELLS)
            throw new IllegalArgumentException("Invalid architect preview size");
        var cells=new ArrayList<Cell>(cellCount);
        for(int i=0;i<cellCount;i++){
            int x=buf.readVarInt(),y=buf.readVarInt(),z=buf.readVarInt(),p=buf.readVarInt();
            if(x<0||x>=width||y<0||y>=height||z<0||z>=length||p<0||p>=paletteCount)
                throw new IllegalArgumentException("Invalid architect preview cell");
            cells.add(new Cell(x,y,z,p));
        }
        return new ArchitectPreviewPacket(token,title,marker,origin,width,height,length,
                List.copyOf(palette),List.copyOf(cells));
    }
    public static void handle(ArchitectPreviewPacket packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            ()->()->ru.projectkingdom.npc.client.KingdomArchitectClient.accept(packet)));
        ctx.setPacketHandled(true);
    }
}
