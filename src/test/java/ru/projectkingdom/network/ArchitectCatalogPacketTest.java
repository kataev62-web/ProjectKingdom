package ru.projectkingdom.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomArchitectDesignRotation;
import ru.projectkingdom.npc.KingdomBuildingTypes;
import static org.junit.jupiter.api.Assertions.*;

/** The new native catalog must never invent material counts or lose block palette. */
class ArchitectCatalogPacketTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void catalogRoundtripCarriesGenuineBuildingPreviewAndMaterials() {
        var design=KingdomArchitectDesignRotation.rotate(
            KingdomBuildingTypes.design(KingdomBuildingTypes.Type.HOUSE),0);
        var token=UUID.randomUUID();
        var preview=ArchitectPreviewPacket.fromDesign(design,token,
            new BlockPos(0,64,0),"Жилой дом");
        assertNotNull(preview);
        var list=List.of(new ArchitectCatalogPacket.Entry(
            "b:HOUSE","Жилой дом",design.width(),design.height(),design.length(),design.blocks().size()));
        var materials=List.of(new ArchitectCatalogPacket.Material("minecraft:stone_bricks",125),
            new ArchitectCatalogPacket.Material("minecraft:oak_planks",75));
        var packet=new ArchitectCatalogPacket(token,list,0,preview,materials);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            ArchitectCatalogPacket.encode(packet,buf);
            var decoded=ArchitectCatalogPacket.decode(buf);
            assertEquals(packet.token(),decoded.token());
            assertEquals(packet.entries(),decoded.entries());
            assertEquals(packet.selected(),decoded.selected());
            assertEquals(packet.materials(),decoded.materials());
            assertEquals(packet.miniature().cells(),decoded.miniature().cells());
            assertEquals(packet.miniature().palette(),decoded.miniature().palette());
        } finally {
            buf.release();
        }
    }

    @Test void materialsLongerThanOldTwelveItemLimitArePreserved() {
        var design=KingdomArchitectDesignRotation.rotate(
            KingdomBuildingTypes.design(KingdomBuildingTypes.Type.HOUSE),0);
        var token=UUID.randomUUID();
        var preview=ArchitectPreviewPacket.fromDesign(design,token,
            new BlockPos(0,64,0),"Жилой дом");
        assertNotNull(preview);
        var materials=new java.util.ArrayList<ArchitectCatalogPacket.Material>();
        for(int i=0;i<28;i++)
            materials.add(new ArchitectCatalogPacket.Material("minecraft:stone_"+i,i+1));
        var packet=new ArchitectCatalogPacket(token,List.of(
            new ArchitectCatalogPacket.Entry("b:HOUSE","Жилой дом",
                design.width(),design.height(),design.length(),design.blocks().size())),
            0,preview,List.copyOf(materials));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            ArchitectCatalogPacket.encode(packet,buffer);
            var decoded=ArchitectCatalogPacket.decode(buffer);
            assertEquals(28,decoded.materials().size());
            assertEquals(materials,decoded.materials());
        } finally {buffer.release();}
    }

    @Test void actionsRoundtripMaintainServerTokenAndCopyName(){
        var packet=new ArchitectCatalogActionPacket(UUID.randomUUID(),
            ArchitectCatalogActionPacket.COPY,2,"moy_dom");
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            ArchitectCatalogActionPacket.encode(packet,buf);
            assertEquals(packet,ArchitectCatalogActionPacket.decode(buf));
        } finally {buf.release();}
    }
}
