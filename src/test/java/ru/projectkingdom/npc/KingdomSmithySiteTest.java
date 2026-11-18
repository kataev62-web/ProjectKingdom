package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.enterprise.EnterpriseBuildingRegistration;
import ru.projectkingdom.enterprise.EnterpriseCoreBinding;
import ru.projectkingdom.enterprise.EnterpriseKind;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSmithySiteTest {
    private static final String DIM="minecraft:overworld";
    private final UUID owner=UUID.randomUUID(),firmId=UUID.randomUUID();
    private final BlockPos corePos=new BlockPos(12,64,12);
    private final KingdomEnterpriseData.Enterprise firm=
            new KingdomEnterpriseData.Enterprise(firmId,owner,"Кузница",0);
    private final EnterpriseCoreBinding core=
            new EnterpriseCoreBinding(firmId,owner,EnterpriseKind.SMITHY,
                    DIM,corePos.asLong(),true);
    private final EnterpriseBuildingRegistration.Room room=
            new EnterpriseBuildingRegistration.Room(firmId,DIM,corePos.asLong(),
                    10,63,10,20,70,20,1,80);

    private KingdomSmithySite.Status checked(EnterpriseCoreBinding binding,
                                              EnterpriseBuildingRegistration.Room area,
                                              String dimension){
        return KingdomSmithySite.registration(firmId,owner,firm,binding,area,dimension);
    }

    @Test void validRegisteredForgeIsAccepted(){
        assertEquals(KingdomSmithySite.Status.READY,checked(core,room,DIM));
        assertEquals(KingdomSmithySite.Status.READY,KingdomSmithySite.station(
                room,new BlockPos(15,65,15),true,true,true));
    }

    @Test void wrongCompanyOwnerCoreAndDimensionAreRejected(){
        assertEquals(KingdomSmithySite.Status.WRONG_COMPANY,
                KingdomSmithySite.registration(UUID.randomUUID(),owner,firm,core,room,DIM));
        assertEquals(KingdomSmithySite.Status.WRONG_OWNER,
                KingdomSmithySite.registration(firmId,UUID.randomUUID(),firm,core,room,DIM));
        assertEquals(KingdomSmithySite.Status.WRONG_DIMENSION,
                checked(core,room,"minecraft:the_nether"));
        assertEquals(KingdomSmithySite.Status.WRONG_CORE,checked(
                new EnterpriseCoreBinding(firmId,owner,EnterpriseKind.MINE,DIM,
                    corePos.asLong(),true),room,DIM));
        assertEquals(KingdomSmithySite.Status.WRONG_CORE,checked(core.suspended(),room,DIM));
        assertEquals(KingdomSmithySite.Status.NO_ROOM,checked(core,null,DIM));
        assertEquals(KingdomSmithySite.Status.ROOM_STALE,checked(core,
                new EnterpriseBuildingRegistration.Room(firmId,DIM,
                    new BlockPos(13,64,13).asLong(),10,63,10,20,70,20,0,80),DIM));
        assertEquals(KingdomSmithySite.Status.ROOM_STALE,checked(core,
                new EnterpriseBuildingRegistration.Room(UUID.randomUUID(),DIM,
                    corePos.asLong(),10,63,10,20,70,20,0,80),DIM));
    }

    @Test void boundaryChunkLandAndRealForgeAreSeparateFailures(){
        var forge=new BlockPos(15,65,15);
        assertEquals(KingdomSmithySite.Status.NO_FORGE_ASSIGNMENT,
                KingdomSmithySite.station(room,null,true,true,true));
        assertEquals(KingdomSmithySite.Status.FORGE_OUTSIDE,
                KingdomSmithySite.station(room,new BlockPos(25,65,15),true,true,true));
        assertEquals(KingdomSmithySite.Status.FORGE_UNLOADED,
                KingdomSmithySite.station(room,forge,false,false,false));
        assertEquals(KingdomSmithySite.Status.NO_ACCESS,
                KingdomSmithySite.station(room,forge,true,false,true));
        assertEquals(KingdomSmithySite.Status.NOT_FORGE,
                KingdomSmithySite.station(room,forge,true,true,false));
    }

    @Test void persistedRoomUsesSameIdentityAfterRestart() throws Exception {
        var tag=new CompoundTag();
        var list=new ListTag();
        var saved=new CompoundTag();
        saved.putUUID("Enterprise",room.enterprise());
        saved.putString("Dim",room.dimension());
        saved.putLong("Core",room.core());
        saved.putInt("MinX",room.minX());saved.putInt("MinY",room.minY());saved.putInt("MinZ",room.minZ());
        saved.putInt("MaxX",room.maxX());saved.putInt("MaxY",room.maxY());saved.putInt("MaxZ",room.maxZ());
        saved.putInt("Windows",room.windows());saved.putInt("Area",room.interiorArea());
        list.add(saved);tag.put("Rooms",list);
        Method restore=EnterpriseBuildingRegistration.class.getDeclaredMethod("load",CompoundTag.class);
        restore.setAccessible(true);
        var loaded=(EnterpriseBuildingRegistration)restore.invoke(null,tag);
        assertEquals(KingdomSmithySite.Status.READY,
                checked(core,loaded.room(firmId),DIM));
        var again=loaded.save(new CompoundTag());
        var persisted=again.getList("Rooms",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(corePos.asLong(),persisted.getLong("Core"));
        assertEquals(firmId,persisted.getUUID("Enterprise"));
    }

    private static net.minecraft.world.item.crafting.Recipe<?> recipe(String id){
        return (net.minecraft.world.item.crafting.Recipe<?>)java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.world.item.crafting.Recipe.class.getClassLoader(),
            new Class[]{net.minecraft.world.item.crafting.Recipe.class},
            (proxy,method,args)->{
                if("getId".equals(method.getName()))return new net.minecraft.resources.ResourceLocation(id);
                if("toString".equals(method.getName()))return id;
                return null;
            });
    }

    @Test void selectedNativeAnvilRecipeMustMatchAvailablePlan() throws Exception {
        Method choose=KingdomTfcSmithWork.class.getDeclaredMethod(
                "selectedRecipe",List.class,String.class);
        choose.setAccessible(true);
        var tool=recipe("tfc:anvil/steel_pickaxe_head");
        var weapon=recipe("tfc:anvil/steel_sword_blade");
        assertSame(tool,choose.invoke(null,List.of(tool,weapon),"tfc:anvil/steel_pickaxe_head"));
        assertSame(weapon,choose.invoke(null,List.of(tool,weapon),"tfc:anvil/steel_sword_blade"));
        assertNull(choose.invoke(null,List.of(tool,weapon),""));
        assertNull(choose.invoke(null,List.of(tool,weapon),"tfc:anvil/fake_item"));
        assertSame(tool,choose.invoke(null,List.of(tool),""));
    }

    @Test void nativeAnvilAndWarehouseMustRemainAuthoritative() throws Exception {
        String smith=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomTfcSmithWork.java"));
        assertTrue(smith.contains("KingdomSmithySite.inspect(level,w).ready()"));
        assertTrue(smith.contains("KingdomTerritories.get(server).usable(level,rawPos,w.employer,\"raw\")"));
        assertTrue(smith.contains("!land.canUse(level,anvilPos,w.employer)"));
        assertTrue(smith.contains("chest.removeItem(source,1)"));
        assertTrue(smith.contains("exact(dest.getStackInSlot(slot),unit)"));
        assertTrue(smith.contains("moveNative(forge,forgeInv,slot,anvil,anvilInv,0)"));
        assertTrue(smith.contains("deliverNative(finished,anvil,anvilInv)"));
        assertTrue(smith.contains("anvil.getClass().getMethod(\"work\""));
        assertTrue(smith.contains("getMethod(\"getAll\""));
        assertFalse(smith.contains("new ItemStack(Items.IRON_INGOT)"));
        assertEquals(6,KingdomTfcSmithWork.nextStep(13,0,-1,-1,-1,
                new String[]{"UPSET_LAST"}));
    }
}
