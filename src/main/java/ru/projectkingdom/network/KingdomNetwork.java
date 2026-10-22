package ru.projectkingdom.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.projectkingdom.npc.KingdomNpcMod;

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
    }
}
