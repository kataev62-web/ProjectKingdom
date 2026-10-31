package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class TownHallAppointmentPickerTest {
    private static String src(String path)throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }
    @Test void candidateSnapshotsRoundTripWithKindNamesAndRoles(){
        UUID player=UUID.randomUUID(),npc=UUID.randomUUID();
        var candidates=List.of(
                new KingdomTownHallCandidates.Candidate(player,"Первый игрок",false,0,false),
                new KingdomTownHallCandidates.Candidate(npc,"Борис-кузнец",true,1<<MunicipalRole.TREASURER.ordinal(),false));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{
            KingdomTownHallCandidates.write(buffer,candidates);
            assertEquals(candidates,KingdomTownHallCandidates.read(buffer));
            assertEquals(0,buffer.readableBytes());
        }finally{buffer.release();}
    }
    @Test void snapshotHasHardLimitAndRejectsForgedCount(){
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{
            buffer.writeVarInt(KingdomTownHallCandidates.LIMIT+1);
            assertThrows(IllegalArgumentException.class,()->KingdomTownHallCandidates.read(buffer));
        }finally{buffer.release();}
    }
    @Test void screenDoesNotRequestManuallyEnteredUuid()throws Exception{
        String screen=src("administration/AdministrativeCoreScreen.java");
        assertFalse(screen.contains("roleTarget"));
        assertFalse(screen.contains("UUID гражданина"));
        assertTrue(screen.contains("officeShowNpc"));
        assertTrue(screen.contains("visibleCandidates()"));
        assertTrue(screen.contains("officeSelected"));
        assertTrue(screen.contains("sendSelectedOffice"));
        assertTrue(screen.contains("officeRoleName"));
    }
    @Test void serverMustRevalidateTownMembershipNpcAndRealAuthority()throws Exception{
        String packet=src("administration/AdministrativeTextPacket.java");
        String candidates=src("administration/KingdomTownHallCandidates.java");
        String menu=src("administration/AdministrativeCoreMenu.java");
        assertTrue(packet.contains("menu.stillValid(sender)"));
        assertTrue(packet.contains("AdministrativePermissionService.mayManage(sender.getUUID()"));
        assertTrue(packet.contains("KingdomTownHallCandidates.eligible(sender.getServer(),town.id(),target)"));
        assertTrue(packet.contains("menu.appointmentCandidates().stream().noneMatch"));
        assertTrue(packet.contains("role==MunicipalRole.MAYOR && !sender.getUUID().equals(admin.founder(town.id()))"));
        assertTrue(candidates.contains("civics.townMembers(townId,page,32)"));
        assertTrue(candidates.contains("npcLivesInTown(server,townId,worker)"));
        assertTrue(candidates.contains("worker.ageYears<18"));
        assertTrue(menu.contains("KingdomTownHallCandidates.write(buffer,appointmentCandidates)"));
        assertTrue(menu.contains("KingdomTownHallCandidates.read(buffer)"));
    }
    @Test void networkVersionAndBookRemainCompatible()throws Exception{
        assertTrue(src("network/KingdomNetwork.java").contains("PROTOCOL=\"11-voice3\""));
        String menu=src("administration/AdministrativeCoreMenu.java");
        assertTrue(menu.contains("MunicipalFoodView.write(buffer,foodView)"));
        assertTrue(menu.contains("KingdomTownHallCandidates.write(buffer,appointmentCandidates)"));
    }
}
