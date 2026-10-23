package ru.projectkingdom.administration;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomTaxData;
import net.minecraft.network.chat.Component;

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
            if(field.equals("pay_taxes")){
                if(packet.kind!=AdministrativeKind.TREASURY)return;
                var taxes=KingdomTaxData.get(sender.getServer());
                long owed=taxes.owedToTown(sender.getServer(),sender.getUUID(),packet.townId);
                String text=packet.value==null?"":packet.value.trim();
                long amount;
                if(text.equals("all"))amount=owed;
                else {
                    if(!text.matches("[0-9]{1,7}")){
                        sender.sendSystemMessage(Component.literal("[Казначейство] Укажите число медных монет от 1 до суммы долга."));
                        return;
                    }
                    try{amount=Long.parseLong(text);}catch(NumberFormatException ex){return;}
                }
                var result=taxes.payAtTreasury(sender,packet.townId,amount);
                sender.sendSystemMessage(Component.literal("[Казначейство] "+KingdomTaxData.paymentMessage(result)));
                if(result==KingdomTaxData.PaymentResult.SUCCESS)sender.closeContainer();
                return;
            }
            if(!menu.manager())return;
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
                        if(field.equals("appoint_role")&&civics.isMember(packet.townId,target))changed=data.appoint(packet.townId,sender.getUUID(),town.mayor(),target,role);
                        else if(field.equals("revoke_role"))changed=data.revoke(packet.townId,sender.getUUID(),town.mayor(),target,role);
                    }catch(IllegalArgumentException ignored){}
                }
                if(changed)KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,
                        field,sender.level().getGameTime());
                return;
            }
            String text=validatedText(packet.value);if(text==null)return;
            KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,"text."+field+"="+text,sender.level().getGameTime());
        });context.setPacketHandled(true);
    }
}
