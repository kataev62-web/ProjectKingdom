package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomBankData;
import ru.projectkingdom.npc.KingdomLandReservations;
import ru.projectkingdom.npc.KingdomTaxData;
import ru.projectkingdom.npc.KingdomCurrency;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.settlement.KingdomSettlementGrowth;
import ru.projectkingdom.settlement.KingdomSettlementRanks;

public final class AdministrativeCoreMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final BlockPos corePos;
    private final UUID townId,founder,mayor;
    private final AdministrativeKind kind;
    private final String townName;
    private final boolean manager,publicAccess,member,canJoin;
    private final long bankAccount,bankReserve,treasuryBalance,treasuryReserve,historicalTreasury,taxDebt,walletCoins;
    private final long legacyBank,legacyTown,legacyPersonal;
    private final String vaultRoomInfo;
    private final long vaultCopper;
    private final List<String> pageRows;
    private final List<String> warehouseRooms;
    private final MunicipalFoodView foodView;
    private final List<KingdomTownHallCandidates.Candidate> appointmentCandidates;
    private final int rankIndex;
    private final boolean canPromote;
    private final String growthSummary,growthRequirements;

    public AdministrativeCoreMenu(int id,Inventory inventory,FriendlyByteBuf buffer){
        this(id,inventory,buffer.readBlockPos(),buffer.readUUID(),AdministrativeKind.byId(buffer.readUtf(32)).orElseThrow(),
                buffer.readUtf(48),buffer.readUUID(),buffer.readUUID(),buffer.readBoolean(),buffer.readBoolean(),
                buffer.readLong(),buffer.readLong(),buffer.readLong(),buffer.readLong(),buffer.readLong(),readRows(buffer),buffer.readBoolean(),buffer.readBoolean(),buffer.readLong(),buffer.readLong(),
                buffer.readLong(),buffer.readLong(),buffer.readLong(),buffer.readUtf(96),buffer.readLong(),readWarehouseRows(buffer),
                MunicipalFoodView.read(buffer),KingdomTownHallCandidates.read(buffer),
                 buffer.readVarInt(),buffer.readBoolean(),buffer.readUtf(160),buffer.readUtf(160));
    }
    private AdministrativeCoreMenu(int id,Inventory inventory,BlockPos corePos,UUID townId,AdministrativeKind kind,
                                   String townName,UUID founder,UUID mayor,boolean manager,boolean publicAccess){
        this(id,inventory,corePos,townId,kind,townName,founder,mayor,manager,publicAccess,0,0,0,0,0,List.of(),false,false,0,0,0,0,0,"",0,List.of(),MunicipalFoodView.empty(),List.of(),0,false,"","");
    }
    private AdministrativeCoreMenu(int id,Inventory inventory,BlockPos corePos,UUID townId,AdministrativeKind kind,
                                   String townName,UUID founder,UUID mayor,boolean manager,boolean publicAccess,
                                   long bankAccount,long bankReserve,long treasuryBalance,long treasuryReserve,long historicalTreasury,
                                   List<String> pageRows,boolean member,boolean canJoin,long taxDebt,long walletCoins,
                                   long legacyBank,long legacyTown,long legacyPersonal,String vaultRoomInfo,long vaultCopper,
                                   List<String> warehouseRooms,MunicipalFoodView foodView,
                                    List<KingdomTownHallCandidates.Candidate> appointmentCandidates,
                                     int rankIndex,boolean canPromote,String growthSummary,String growthRequirements){
        super(KingdomAdministrationRegistry.CORE_MENU.get(),id);this.inventory=inventory;this.corePos=corePos;this.townId=townId;
        this.kind=kind;this.townName=townName;this.founder=founder;this.mayor=mayor;this.manager=manager;this.publicAccess=publicAccess;
        this.bankAccount=bankAccount;this.bankReserve=bankReserve;this.treasuryBalance=treasuryBalance;this.treasuryReserve=treasuryReserve;this.historicalTreasury=historicalTreasury;
        this.pageRows=List.copyOf(pageRows);this.member=member;this.canJoin=canJoin;this.taxDebt=taxDebt;this.walletCoins=walletCoins;
        this.legacyBank=legacyBank;this.legacyTown=legacyTown;this.legacyPersonal=legacyPersonal;
        this.vaultRoomInfo=vaultRoomInfo;this.vaultCopper=vaultCopper;
        this.warehouseRooms=List.copyOf(warehouseRooms);
        this.foodView=foodView;
        this.appointmentCandidates=List.copyOf(appointmentCandidates);
        this.rankIndex=Math.max(0,Math.min(3,rankIndex));
        this.canPromote=canPromote;
        this.growthSummary=growthSummary;this.growthRequirements=growthRequirements;
    }
    private static List<String> readWarehouseRows(FriendlyByteBuf buffer){
        int n=buffer.readVarInt();
        if(n<0||n>KingdomCentralWarehouseRooms.MAX_ROOMS)throw new IllegalArgumentException("Warehouse rooms limit");
        List<String> rows=new ArrayList<>();
        for(int i=0;i<n;i++)rows.add(buffer.readUtf(96));
        return List.copyOf(rows);
    }
    private static List<String> readRows(FriendlyByteBuf buffer){int count=Math.min(8,Math.max(0,buffer.readVarInt()));List<String> rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add(buffer.readUtf(96));return List.copyOf(rows);}
    public static void open(ServerPlayer player,AdministrativeCoreBlockEntity core){
        open(player,core,"");
    }
    public static void open(ServerPlayer player,AdministrativeCoreBlockEntity core,String notice){
        if(core==null||!core.bound())return;
        KingdomCivicsData civics=KingdomCivicsData.get(player.getServer());var town=civics.townById(core.townId());if(town==null)return;
        boolean publicAccess=AdministrativePermissionService.mayUsePublic(core.kind());
        if(!publicAccess&&!civics.isMember(town.id(),player.getUUID())){
            player.sendSystemMessage(Component.literal("[Project Kingdom] Эта городская служба недоступна посторонним."));return;
        }
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());UUID founder=data.founder(town.id());if(founder==null)return;
        boolean manager=AdministrativePermissionService.mayManage(player.getUUID(),founder,town.mayor(),data.roles(town.id(),player.getUUID()),core.kind());
        boolean member=civics.isMember(town.id(),player.getUUID());
        boolean canJoin=core.kind()==AdministrativeKind.TOWN_HALL && civics.townOf(player.getUUID())==null;
        long account=KingdomBankData.get(player.getServer()).balance(player.getUUID());long bankReserve=data.bankReserve(town.id());
        long treasuryReserve=data.treasuryReserve(town.id()),historical=data.historicalTreasuryLiability(town.id());
        long taxDebt=core.kind()==AdministrativeKind.TREASURY
                ?KingdomTaxData.get(player.getServer()).owedToTown(player.getServer(),player.getUUID(),town.id()):0;
        long walletCoins=(core.kind()==AdministrativeKind.TREASURY || core.kind()==AdministrativeKind.BANK)
                ?KingdomCurrency.balance(player):0;
        long legacyBank=manager?data.legacyBankLiability(town.id()):0L;
        long legacyTown=manager?data.legacyTreasuryLiability(town.id()):0L;
        long legacyPersonal=KingdomBankData.get(player.getServer()).legacyBalance(player.getUUID());
        var vaultRoom=KingdomBankVaultRooms.get(player.getServer()).room(town.id());
        String roomInfo=vaultRoom==null?"Комната не размечена (старый сейф)"
                :"Комната "+(vaultRoom.max().getX()-vaultRoom.min().getX()+1)+"×"
                    +(vaultRoom.max().getY()-vaultRoom.min().getY()+1)+"×"
                    +(vaultRoom.max().getZ()-vaultRoom.min().getZ()+1);
        List<String> warehouseRooms=new ArrayList<>();
        if(core.kind()==AdministrativeKind.CENTRAL_WAREHOUSE){
            var registered=KingdomCentralWarehouseRooms.get(player.getServer());
            for(var room:registered.rooms(town.id())){
                var stock=registered.inspect(player.serverLevel(),room);
                String status=stock.loaded()?"шт. "+stock.itemCount():"не загружена";
                if(room.kind()==KingdomCentralWarehouseRooms.Kind.COLD){
                    status+=stock.cellarControllerPresent()?" | станция найдена (проверь)":" | нет проверенного охлаждения";
                }
                String text=room.kind().title()+" "+room.dimensions()+" | "+status;
                warehouseRooms.add(text.length()>94?text.substring(0,94):text);
            }
        }
        long physicalCoins=core.kind()==AdministrativeKind.BANK&&manager
                ?ru.projectkingdom.npc.KingdomPhysicalCash.value(
                    ru.projectkingdom.npc.KingdomPhysicalCash.townVault(player,town.id(),AdministrativeBindingRole.BANK_VAULT)):-1L;
        List<String> rows=new ArrayList<>();
        if(core.kind()==AdministrativeKind.TOWN_HALL && manager){
            for(var booking:KingdomLandReservations.get(player.getServer())
                    .reservedForTown(town.id(),System.currentTimeMillis())) {
                if(rows.size()>=6)break;
                rows.add("Бронь: "+booking.buyerName()+" "+booking.x()+","+booking.z()
                        +" — "+booking.price()+" мед. ["+booking.state()+"]");
            }
        }
        if(core.kind()==AdministrativeKind.TOWN_HALL && (manager || member))
            for(UUID resident:civics.townMembers(town.id(),0,6)) {
                if(rows.size()>=8)break;
                rows.add("Житель: "+resident.toString().substring(0,8));
            }
        if(core.kind()==AdministrativeKind.LAND_OFFICE)for(KingdomCivicsData.Plot plot:civics.townPlots(town.id(),0,6))
            rows.add(plot.dimension()+" "+plot.x()+", "+plot.z()+" "+plot.zone().name());
        if(core.kind()==AdministrativeKind.COURT)for(JusticeCase item:data.justiceCases(town.id(),0,6))rows.add(item.id().toString().substring(0,8)+" "+item.status().name()+" "+item.summary());
        if(core.kind()==AdministrativeKind.PRISON)for(PrisonRecord item:data.prisonRecords(town.id(),0,6))rows.add(item.prisoner().toString().substring(0,8)+" "+(item.active()?"заключён":"освобождён"));
        if(core.kind()==AdministrativeKind.GUARD_ADMINISTRATION){
            for(GuardRecord item:data.guards(town.id(),0,3))rows.add("Страж: "+item.name());
            for(KingdomAdministrationData.WarrantEntry item:data.wantedEntries(town.id(),0,3))rows.add("Розыск: "+item.target().toString().substring(0,8));
        }
        List<KingdomTownHallCandidates.Candidate> appointmentCandidates=
                core.kind()==AdministrativeKind.TOWN_HALL&&manager
                        ?KingdomTownHallCandidates.snapshot(player.getServer(),town.id())
                        :List.of();
        MunicipalFoodView foodView=(core.kind()==AdministrativeKind.CENTRAL_WAREHOUSE
                || core.kind()==AdministrativeKind.BANK || core.kind()==AdministrativeKind.TOWN_HALL)
                ?MunicipalFoodView.snapshot(player,town.id(),notice)
                :MunicipalFoodView.empty();
        var growth=core.kind()==AdministrativeKind.TOWN_HALL
                ?KingdomSettlementGrowth.inspect(civics,KingdomConstructionData.get(player.getServer()),
                    town.id(),player.serverLevel().dimension().location().toString(),player.getServer())
                :null;
        var rank=growth==null?KingdomSettlementGrowth.Rank.HAMLET
                :KingdomSettlementRanks.get(player.getServer()).current(town.id(),growth.eligible());
        int rankIndex=rank.ordinal();
        boolean canPromote=growth!=null&&rankIndex+1<KingdomSettlementGrowth.Rank.values().length
                &&growth.eligible().ordinal()>rankIndex;
        String growthSummary=growth==null?"":growth.summary();
        String growthRequirements=growth==null||rankIndex==3?"Максимальный уровень"
                :growth.requirements(KingdomSettlementGrowth.Rank.values()[rankIndex+1]);
        NetworkHooks.openScreen(player,new MenuProvider(){
            @Override public Component getDisplayName(){return Component.literal(core.kind().russianName());}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player ignored){
                return new AdministrativeCoreMenu(id,inv,core.getBlockPos(),town.id(),core.kind(),town.name(),founder,town.mayor(),manager,publicAccess,
                        account,manager?bankReserve:0L,manager?town.treasury():0L,manager?treasuryReserve:0L,
                        manager?historical:0L,rows,member,canJoin,taxDebt,walletCoins,
                        legacyBank,legacyTown,legacyPersonal,roomInfo,physicalCoins,warehouseRooms,foodView,
                         appointmentCandidates,rankIndex,canPromote,growthSummary,growthRequirements);
            }
        },buffer->{buffer.writeBlockPos(core.getBlockPos());buffer.writeUUID(town.id());buffer.writeUtf(core.kind().id(),32);
            buffer.writeUtf(town.name(),48);buffer.writeUUID(founder);buffer.writeUUID(town.mayor());buffer.writeBoolean(manager);buffer.writeBoolean(publicAccess);
            buffer.writeLong(account);buffer.writeLong(manager?bankReserve:0L);buffer.writeLong(manager?town.treasury():0L);
            buffer.writeLong(manager?treasuryReserve:0L);buffer.writeLong(manager?historical:0L);
            buffer.writeVarInt(rows.size());for(String row:rows)buffer.writeUtf(row,96);
            buffer.writeBoolean(member);buffer.writeBoolean(canJoin);
            buffer.writeLong(taxDebt);buffer.writeLong(walletCoins);
            buffer.writeLong(legacyBank);buffer.writeLong(legacyTown);buffer.writeLong(legacyPersonal);
            buffer.writeUtf(roomInfo,96);buffer.writeLong(physicalCoins);
            buffer.writeVarInt(warehouseRooms.size());
            for(String row:warehouseRooms)buffer.writeUtf(row,96);
            MunicipalFoodView.write(buffer,foodView);
            KingdomTownHallCandidates.write(buffer,appointmentCandidates);
            buffer.writeVarInt(rankIndex);buffer.writeBoolean(canPromote);
            buffer.writeUtf(growthSummary,160);buffer.writeUtf(growthRequirements,160);});
    }
    public BlockPos corePos(){return corePos;}public UUID townId(){return townId;}public AdministrativeKind kind(){return kind;}public String townName(){return townName;}
    public UUID founder(){return founder;}public UUID mayor(){return mayor;}public boolean manager(){return manager;}public boolean publicAccess(){return publicAccess;}
    public long bankAccount(){return bankAccount;}public long bankReserve(){return bankReserve;}public long treasuryBalance(){return treasuryBalance;}
    public long treasuryReserve(){return treasuryReserve;}public long historicalTreasury(){return historicalTreasury;}
    public List<String> pageRows(){return pageRows;}
    public List<KingdomTownHallCandidates.Candidate> appointmentCandidates(){return appointmentCandidates;}
    public boolean member(){return member;}
    public boolean canJoin(){return canJoin;}
    public long taxDebt(){return taxDebt;}
    public long walletCoins(){return walletCoins;}
    public long legacyBank(){return legacyBank;}
    public long legacyTown(){return legacyTown;}
    public long legacyPersonal(){return legacyPersonal;}
    public String vaultRoomInfo(){return vaultRoomInfo;}
    public long vaultCopper(){return vaultCopper;}
    public List<String> warehouseRooms(){return warehouseRooms;}
    public MunicipalFoodView foodView(){return foodView;}
    public int rankIndex(){return rankIndex;}
    public String rankTitle(){return KingdomSettlementGrowth.Rank.values()[rankIndex].title();}
    public String nextRankTitle(){return rankIndex>=3?"":KingdomSettlementGrowth.Rank.values()[rankIndex+1].title();}
    public boolean canPromote(){return canPromote;}
    public String growthSummary(){return growthSummary;}
    public String growthRequirements(){return growthRequirements;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){
        if(player.level()!=inventory.player.level()||corePos.distSqr(player.blockPosition())>64||!player.level().hasChunkAt(corePos))return false;
        if(!(player.level().getBlockEntity(corePos) instanceof AdministrativeCoreBlockEntity core)
                ||!townId.equals(core.townId())||kind!=core.kind())return false;
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());
        AdministrativeCoreBinding active=data.core(townId,kind);
        return active!=null&&active.dimension().equals(player.level().dimension().location().toString())&&active.packedPos()==corePos.asLong();
    }
}
