package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.enterprise.EnterpriseCoreMenu;
import ru.projectkingdom.npc.KingdomEnterpriseData;

public record RenameEnterprisePacket(UUID enterpriseId,String name) {
    public static String validatedName(String input){
        String clean=input==null?"":input.trim();
        if(clean.isEmpty()||clean.length()>48||clean.indexOf('\u00a7')>=0||clean.chars().anyMatch(Character::isISOControl))return null;
        return clean;
    }
    public static void encode(RenameEnterprisePacket packet,FriendlyByteBuf buffer){buffer.writeUUID(packet.enterpriseId);buffer.writeUtf(packet.name==null?"":packet.name,96);}
    public static RenameEnterprisePacket decode(FriendlyByteBuf buffer){return new RenameEnterprisePacket(buffer.readUUID(),buffer.readUtf(96));}
    public static void handle(RenameEnterprisePacket packet,Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context context=supplier.get();context.enqueueWork(()->{
            var sender=context.getSender();String clean=validatedName(packet.name);
            if(sender==null||clean==null||!(sender.containerMenu instanceof EnterpriseCoreMenu menu)
                    ||!packet.enterpriseId.equals(menu.enterpriseId())||!menu.stillValid(sender))return;
            KingdomEnterpriseData.get(sender.getServer()).rename(sender.getUUID(),packet.enterpriseId,clean);
        });context.setPacketHandled(true);
    }
}
