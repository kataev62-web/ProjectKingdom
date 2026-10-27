package ru.projectkingdom.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.administration.AdministrativeActionPacket;
import ru.projectkingdom.administration.AdministrativeTextPacket;
import ru.projectkingdom.settlement.KingdomSettlementFoundPacket;

public final class KingdomNetwork {
    private static final String PROTOCOL="10";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KingdomNpcMod.MOD_ID,"main"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private static boolean registered;
    private KingdomNetwork(){}
    public static void register(){
        if(registered)return;registered=true;
        CHANNEL.registerMessage(0,RenameEnterprisePacket.class,RenameEnterprisePacket::encode,
                RenameEnterprisePacket::decode,RenameEnterprisePacket::handle);
        CHANNEL.registerMessage(1,AdministrativeActionPacket.class,AdministrativeActionPacket::encode,
                AdministrativeActionPacket::decode,AdministrativeActionPacket::handle);
        CHANNEL.registerMessage(2,AdministrativeTextPacket.class,AdministrativeTextPacket::encode,
                AdministrativeTextPacket::decode,AdministrativeTextPacket::handle);
        CHANNEL.registerMessage(3,OpenLandMapFromBookPacket.class,OpenLandMapFromBookPacket::encode,
                OpenLandMapFromBookPacket::decode,OpenLandMapFromBookPacket::handle);
        CHANNEL.registerMessage(4,OpenCityExpansionMapPacket.class,OpenCityExpansionMapPacket::encode,
                OpenCityExpansionMapPacket::decode,OpenCityExpansionMapPacket::handle);
        CHANNEL.registerMessage(5,OpenWorldAtlasPacket.class,OpenWorldAtlasPacket::encode,
                OpenWorldAtlasPacket::decode,OpenWorldAtlasPacket::handle);
        CHANNEL.registerMessage(6,GraphicAtlasRequestPacket.class,GraphicAtlasRequestPacket::encode,
                GraphicAtlasRequestPacket::decode,GraphicAtlasRequestPacket::handle);
        CHANNEL.registerMessage(7,GraphicAtlasResponsePacket.class,GraphicAtlasResponsePacket::encode,
                GraphicAtlasResponsePacket::decode,GraphicAtlasResponsePacket::handle);
        CHANNEL.registerMessage(8,OpenGraphicAtlasPacket.class,OpenGraphicAtlasPacket::encode,
                OpenGraphicAtlasPacket::decode,OpenGraphicAtlasPacket::handle);
        CHANNEL.registerMessage(9,OpenCityPlanFromAtlasPacket.class,OpenCityPlanFromAtlasPacket::encode,
                OpenCityPlanFromAtlasPacket::decode,OpenCityPlanFromAtlasPacket::handle);
        CHANNEL.registerMessage(10,ArchitectPreviewPacket.class,ArchitectPreviewPacket::encode,
                ArchitectPreviewPacket::decode,ArchitectPreviewPacket::handle);
        CHANNEL.registerMessage(11,ArchitectDecisionPacket.class,ArchitectDecisionPacket::encode,
                ArchitectDecisionPacket::decode,ArchitectDecisionPacket::handle);
        CHANNEL.registerMessage(12,ArchitectResultPacket.class,ArchitectResultPacket::encode,
                ArchitectResultPacket::decode,ArchitectResultPacket::handle);
        CHANNEL.registerMessage(13,ArchitectCatalogPacket.class,ArchitectCatalogPacket::encode,
                ArchitectCatalogPacket::decode,ArchitectCatalogPacket::handle);
        CHANNEL.registerMessage(14,ArchitectCatalogActionPacket.class,ArchitectCatalogActionPacket::encode,
                ArchitectCatalogActionPacket::decode,ArchitectCatalogActionPacket::handle);
        CHANNEL.registerMessage(17,KingdomAdminDashboardPacket.class,
                KingdomAdminDashboardPacket::encode,KingdomAdminDashboardPacket::decode,
                KingdomAdminDashboardPacket::handle);
        CHANNEL.registerMessage(18,KingdomAdminActionPacket.class,
                KingdomAdminActionPacket::encode,KingdomAdminActionPacket::decode,
                KingdomAdminActionPacket::handle);
        CHANNEL.registerMessage(20,OpenKingdomMapsFromBookPacket.class,OpenKingdomMapsFromBookPacket::encode,
                OpenKingdomMapsFromBookPacket::decode,OpenKingdomMapsFromBookPacket::handle);
        CHANNEL.registerMessage(21,OpenKingdomMapsHubPacket.class,OpenKingdomMapsHubPacket::encode,
                OpenKingdomMapsHubPacket::decode,OpenKingdomMapsHubPacket::handle);
        CHANNEL.registerMessage(22,KingdomFemaleOutfitPacket.class,KingdomFemaleOutfitPacket::encode,
                KingdomFemaleOutfitPacket::decode,KingdomFemaleOutfitPacket::handle);
        CHANNEL.registerMessage(31,OpenLandMapFromAtlasPacket.class,OpenLandMapFromAtlasPacket::encode,
                OpenLandMapFromAtlasPacket::decode,OpenLandMapFromAtlasPacket::handle);
        CHANNEL.registerMessage(40,NpcVoiceRequestPacket.class,NpcVoiceRequestPacket::encode,
                NpcVoiceRequestPacket::decode,NpcVoiceRequestPacket::handle);
        CHANNEL.registerMessage(41,NpcSpeechPacket.class,NpcSpeechPacket::encode,
                NpcSpeechPacket::decode,NpcSpeechPacket::handle);
        CHANNEL.registerMessage(52,KingdomSettlementFoundPacket.class,KingdomSettlementFoundPacket::encode,
                KingdomSettlementFoundPacket::decode,KingdomSettlementFoundPacket::handle);
        CHANNEL.registerMessage(53,ToggleCityBordersPacket.class,ToggleCityBordersPacket::encode,
                ToggleCityBordersPacket::decode,ToggleCityBordersPacket::handle);
    }

    public static void sendNpcSpeech(net.minecraft.server.level.ServerPlayer player,NpcSpeechPacket packet){
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),packet);
    }
}
