package ru.projectkingdom.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.administration.AdministrativeActionPacket;
import ru.projectkingdom.administration.AdministrativeTextPacket;

public final class KingdomNetwork {
    private static final String PROTOCOL="1";
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
    }
}
