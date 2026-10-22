package ru.projectkingdom.administration;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record AdministrativeActionPacket(UUID townId,AdministrativeKind kind,int action) {
    public static final int BIND_TREASURY=110,BIND_BANK=111,BIND_WAREHOUSE=112,BIND_MARKET=113,BIND_PRISON_CELL=114;
    public static void encode(AdministrativeActionPacket packet,FriendlyByteBuf buffer){
        buffer.writeUUID(packet.townId);buffer.writeUtf(packet.kind==null?"":packet.kind.id(),32);buffer.writeInt(packet.action);
    }
    public static AdministrativeActionPacket decode(FriendlyByteBuf buffer){
        return new AdministrativeActionPacket(buffer.readUUID(),AdministrativeKind.byId(buffer.readUtf(32)).orElse(null),buffer.readInt());
    }
    public static void handle(AdministrativeActionPacket packet,Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context context=supplier.get();context.enqueueWork(()->{
            var sender=context.getSender();
            if(sender==null||packet.kind==null||!(sender.containerMenu instanceof AdministrativeCoreMenu menu)
                    ||!packet.townId.equals(menu.townId())||packet.kind!=menu.kind()||!menu.manager()||!menu.stillValid(sender))return;
            AdministrativeBindingRole role=switch(packet.action){
                case BIND_TREASURY->AdministrativeBindingRole.TREASURY_VAULT;
                case BIND_BANK->AdministrativeBindingRole.BANK_VAULT;
                case BIND_WAREHOUSE->AdministrativeBindingRole.WAREHOUSE_STORAGE;
                case BIND_MARKET->AdministrativeBindingRole.MARKET_STORAGE;
                case BIND_PRISON_CELL->AdministrativeBindingRole.PRISON_CELL;
                default->null;
            };
            if(role!=null)AdministrativeAccessService.begin(sender,packet.townId,packet.kind,role);
        });context.setPacketHandled(true);
    }
}
