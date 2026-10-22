package ru.projectkingdom.administration;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record AdministrativeTextPacket(UUID townId,AdministrativeKind kind,String field,String value) {
    public static String validatedText(String input){
        String clean=input==null?"":input.trim();
        if(clean.isEmpty()||clean.length()>48||clean.indexOf('\u00a7')>=0||clean.chars().anyMatch(Character::isISOControl))return null;
        return clean;
    }
    private static String validatedField(String input){
        if(input==null||input.isBlank()||input.length()>24||!input.matches("[a-z_]+"))return null;return input;
    }
    public static void encode(AdministrativeTextPacket packet,FriendlyByteBuf buffer){
        buffer.writeUUID(packet.townId);buffer.writeUtf(packet.kind==null?"":packet.kind.id(),32);
        buffer.writeUtf(packet.field==null?"":packet.field,32);buffer.writeUtf(packet.value==null?"":packet.value,96);
    }
    public static AdministrativeTextPacket decode(FriendlyByteBuf buffer){
        return new AdministrativeTextPacket(buffer.readUUID(),AdministrativeKind.byId(buffer.readUtf(32)).orElse(null),buffer.readUtf(32),buffer.readUtf(96));
    }
    public static void handle(AdministrativeTextPacket packet,Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context context=supplier.get();context.enqueueWork(()->{
            var sender=context.getSender();String text=validatedText(packet.value),field=validatedField(packet.field);
            if(sender==null||packet.kind==null||text==null||field==null
                    ||!(sender.containerMenu instanceof AdministrativeCoreMenu menu)||!packet.townId.equals(menu.townId())
                    ||packet.kind!=menu.kind()||!menu.manager()||!menu.stillValid(sender))return;
            KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,
                    "text."+field+"="+text,sender.level().getGameTime());
        });context.setPacketHandled(true);
    }
}
