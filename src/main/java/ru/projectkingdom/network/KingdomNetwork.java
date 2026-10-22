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
    }
}
