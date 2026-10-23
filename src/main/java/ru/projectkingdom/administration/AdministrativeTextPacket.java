package ru.projectkingdom.administration;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomCivicsData;

public record AdministrativeTextPacket(UUID townId,AdministrativeKind kind,String field,String value) {
    public static String validatedText(String input){
        String clean=input==null?"":input.trim();
        if(clean.isEmpty()||clean.length()>48||clean.indexOf('\u00a7')>=0||clean.chars().anyMatch(Character::isISOControl))return null;
        return clean;
    }
    private static String validatedField(String input){
        if(input==null||input.isBlank()||input.length()>24||!input.matches("[a-z_]+"))return null;return input;
    }
    private static String validatedCommandValue(String input){
        if(input==null||input.isBlank()||input.length()>64||input.indexOf('§')>=0||input.chars().anyMatch(Character::isISOControl))return null;
        return input.trim();
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
            var sender=context.getSender();String field=validatedField(packet.field);
            if(sender==null||packet.kind==null||field==null
                    ||!(sender.containerMenu instanceof AdministrativeCoreMenu menu)||!packet.townId.equals(menu.townId())
                    ||packet.kind!=menu.kind()||!menu.stillValid(sender))return;
            // A lawful sovereign may grant a national order from their own nation's town hall,
            // without acquiring municipal mayor powers. Service revalidates sovereign identity.
            boolean realmAward=field.equals("realm_award")&&packet.kind==AdministrativeKind.TOWN_HALL;
            if(!menu.manager()&&!realmAward)return;
            if(field.equals("appoint_role")||field.equals("revoke_role")||field.equals("appoint_mayor")){
                if(menu.kind()!=AdministrativeKind.TOWN_HALL)return;
                String command=validatedCommandValue(packet.value);if(command==null)return;
                KingdomCivicsData civics=KingdomCivicsData.get(sender.getServer());var town=civics.townById(packet.townId);if(town==null)return;
                boolean changed=false;
                if(field.equals("appoint_mayor")){
                    try{changed=civics.replaceMayorAuthorized(packet.townId,sender.getUUID(),UUID.fromString(command));}catch(IllegalArgumentException ignored){}
                }else{
                    int separator=command.indexOf('|');if(separator<1)return;
                    try{
                        UUID target=UUID.fromString(command.substring(0,separator));MunicipalRole role=MunicipalRole.valueOf(command.substring(separator+1));
                        if(role==MunicipalRole.MAYOR)return;
                        KingdomAdministrationData data=KingdomAdministrationData.get(sender.getServer());
                        if(field.equals("appoint_role")&&civics.isMember(packet.townId,target)
                                 &&ru.projectkingdom.social.KingdomSocialPolicy.mayHoldAdministrativeOffice(
                                     ru.projectkingdom.social.KingdomSocialData.get(sender.getServer()).rank(target)))
                             changed=data.appoint(packet.townId,sender.getUUID(),town.mayor(),target,role);
                        else if(field.equals("revoke_role"))changed=data.revoke(packet.townId,sender.getUUID(),town.mayor(),target,role);
                    }catch(IllegalArgumentException ignored){}
                }
                if(changed)KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,
                        field,sender.level().getGameTime());
                return;
            }
            if(field.equals("realm_award")){
                if(menu.kind()!=AdministrativeKind.TOWN_HALL)return;
                String raw=validatedCommandValue(packet.value);if(raw==null)return;
                try {
                    UUID target=UUID.fromString(raw);
                    boolean ok=ru.projectkingdom.social.KingdomCivicService.awardRealm(sender,
                            packet.townId,target);
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(ok
                        ?"[Project Kingdom] Государственный орден пожалован."
                        :"[Project Kingdom] Орден недоступен: проверяются власть правителя, гражданство и заслуги."));
                }catch(IllegalArgumentException ignored){}
                return;
            }
            if(field.equals("honor_grant")){
                if(menu.kind()!=AdministrativeKind.TOWN_HALL)return;
                String command=validatedCommandValue(packet.value);if(command==null)return;
                int divider=command.indexOf('|');if(divider<1)return;
                try {
                    UUID target=UUID.fromString(command.substring(0,divider));
                    var honor=ru.projectkingdom.social.KingdomCivicHonor.valueOf(command.substring(divider+1));
                    boolean ok=ru.projectkingdom.social.KingdomCivicService.award(sender,
                            packet.townId,target,honor);
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(ok
                        ?"[Project Kingdom] Городская награда вручена."
                        :"[Project Kingdom] Награда недоступна: проверьте заслуги, гражданство, полномочия и повтор."));
                }catch(IllegalArgumentException ignored){}
                return;
            }
            String text=validatedText(packet.value);if(text==null)return;
            KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,"text."+field+"="+text,sender.level().getGameTime());
        });context.setPacketHandled(true);
    }
}
