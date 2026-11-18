package ru.projectkingdom.npc;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;

/** Cross-PR contracts, not a substitute for TFC world execution. */
class KingdomIntegrationProfessionsSmokeTest {
    private static final String[] JOBS={"lumberjack","miner","breeder"};
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void sharedSchedulerPermitsOnlyActiveAssignedDaytimeWork(){
        for(String job:JOBS){
            assertTrue(KingdomNpcDuty.hasPhysicalExecutor(job),job);
            assertEquals(KingdomNpcGoalSelector.Goal.WORK,goal(true,true,true));
            assertTrue(KingdomNpcDuty.mayExecute(job,"work","CONTINUE_ASSIGNED_JOB",true),job);
            assertFalse(KingdomNpcDuty.mayExecute(job,"work","CONTINUE_ASSIGNED_JOB",false),job);
            assertFalse(KingdomNpcDuty.mayExecute(job,"work","SEEK_SHELTER",true),job);
            assertNotEquals(KingdomNpcGoalSelector.Goal.WORK,goal(false,true,true));
            assertNotEquals(KingdomNpcGoalSelector.Goal.WORK,goal(true,false,true));
            assertNotEquals(KingdomNpcGoalSelector.Goal.WORK,goal(true,true,false));
        }
    }
    private static KingdomNpcGoalSelector.Goal goal(boolean contract,boolean site,boolean day){
        return KingdomNpcGoalSelector.choose(new KingdomNpcGoalSelector.Context(true,contract,site,day,true,80,80));
    }
    @Test void pausedDebtBlocksEveryProfessionBeforeAnyWorldAccess(){
        var w=new KingdomNpcData.Worker(1,java.util.UUID.randomUUID(),"Test","minecraft:overworld",0);
        w.contractPaused=true;
        for(String job:JOBS){
            w.job=job;
            assertFalse(KingdomNpcWorkday.employed(null,w),job);
            assertFalse(KingdomNpcWorkday.hasAssignment(null,null,w),job);
        }
    }
    @Test void professionSequenceSurvivesSavedDataRoundTripsWithoutLosingCargo(){
        var data=new KingdomNpcData();
        var w=data.add(java.util.UUID.randomUUID(),"Test","minecraft:overworld",0);
        w.employer=java.util.UUID.randomUUID();w.contractPaused=true;w.unpaidDays=3;
        w.fieldTool=new ItemStack(Items.IRON_PICKAXE);
        var logs=new ItemStack(Items.OAK_LOG,13);logs.getOrCreateTag().putString("cargo","original");
        w.fieldInventory.add(logs);w.mineStatus="Ожидание креплений";
        var id=w.entityId;var employer=w.employer;
        for(String job:JOBS){
            w.job=job;
            data=KingdomNpcData.load(data.save(new CompoundTag()));
            w=data.all().iterator().next();
            assertEquals(job,w.job);assertEquals(id,w.entityId);assertEquals(employer,w.employer);
            assertTrue(w.contractPaused);assertEquals(3,w.unpaidDays);
            assertTrue(w.fieldTool.is(Items.IRON_PICKAXE));
            assertEquals(13,w.fieldInventory.get(0).getCount());
            assertEquals("original",w.fieldInventory.get(0).getTag().getString("cargo"));
            assertEquals("Ожидание креплений",w.mineStatus);
        }
    }
    @Test void partiallyLoadedPenFeedsCommonAssignmentGateWithoutLoadingChunks(){
        int[] point=KingdomRanchLoadedArea.loadedPoint(15,16,0,0,(x,z)->x==1&&z==0);
        assertArrayEquals(new int[]{16,0},point);
        assertEquals(KingdomNpcGoalSelector.Goal.WORK,goal(true,point!=null,true));
        point=KingdomRanchLoadedArea.loadedPoint(15,16,0,0,(x,z)->false);
        assertNull(point);
        assertNotEquals(KingdomNpcGoalSelector.Goal.WORK,goal(true,point!=null,true));
    }
}
