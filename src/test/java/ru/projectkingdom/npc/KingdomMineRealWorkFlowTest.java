package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Reproductions for #93; not a substitute for in-world Forge/TFC acceptance. */
final class KingdomMineRealWorkFlowTest {
    @BeforeAll static void setup(){
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static KingdomNpcData.Worker worker(){
        return new KingdomNpcData().add(UUID.randomUUID(),"Actual miner","minecraft:overworld",0);
    }

    @Test void oreDeliveryPreservesExactNbtAgainstCopyReturningContainer(){
        var warehouse=new SimpleContainer(2){
            @Override public ItemStack getItem(int slot){return super.getItem(slot).copy();}
        };
        ItemStack in=new ItemStack(Items.COBBLESTONE,61);
        in.getOrCreateTag().putString("ore_origin","world/chunk/3");
        warehouse.setItem(0,in);
        ItemStack mined=in.copy();mined.setCount(8);
        assertEquals(8,KingdomMineDelivery.transfer(warehouse,mined));
        assertTrue(mined.isEmpty());
        assertEquals(64,warehouse.getItem(0).getCount());
        assertEquals(5,warehouse.getItem(1).getCount());
        assertEquals("world/chunk/3",warehouse.getItem(1).getTag().getString("ore_origin"));
    }

    @Test void rejectedAndFullWarehouseNeverDebitMiningCargo(){
        var rejecting=new SimpleContainer(1){
            @Override public void setItem(int slot,ItemStack stack){/* writes rejected */ }
            @Override public ItemStack getItem(int slot){return ItemStack.EMPTY;}
        };
        ItemStack load=new ItemStack(Items.DIAMOND,4);
        load.getOrCreateTag().putString("marker","mine-93");
        assertEquals(0,KingdomMineDelivery.transfer(rejecting,load));
        assertEquals(4,load.getCount());
        assertEquals("mine-93",load.getTag().getString("marker"));

        var full=new SimpleContainer(1);
        full.setItem(0,new ItemStack(Items.COBBLESTONE,64));
        assertEquals(0,KingdomMineDelivery.transfer(full,load));
        assertEquals(4,load.getCount());
    }

    @Test void noItemFabricationWhenDestinationIsSmallerThanOutput(){
        var small=new SimpleContainer(1){
            @Override public int getMaxStackSize(){return 2;}
        };
        var ore=new ItemStack(Items.DIAMOND,5);
        assertEquals(2,KingdomMineDelivery.transfer(small,ore));
        assertEquals(2,small.getItem(0).getCount());
        assertEquals(3,ore.getCount());
        assertEquals(0,KingdomMineDelivery.transfer(small,ore));
    }

    @Test void cargoMustFitExactlyThirtySixDistinctPersistentStacks() {
        var w=worker();
        for(int n=0;n<36;n++){
            var one=new ItemStack(Items.DIAMOND_SWORD);
            one.getOrCreateTag().putInt("ore_nbt",n);
            w.fieldInventory.add(one);
        }
        var additional=new ItemStack(Items.DIAMOND_SWORD);
        additional.getOrCreateTag().putInt("ore_nbt",36);
        assertFalse(KingdomMineDelivery.canCarry(w,List.of(additional)));
        assertFalse(KingdomMineDelivery.canCarry(w,List.of(w.fieldInventory.get(0).copy())));

        var saved=new KingdomNpcData();
        var old=saved.add(UUID.randomUUID(),"Stored miner","minecraft:overworld",0);
        for(var item:w.fieldInventory)old.fieldInventory.add(item.copy());
        var reloaded=KingdomNpcData.load(saved.save(new CompoundTag()));
        var copy=reloaded.all().iterator().next();
        assertEquals(36,copy.fieldInventory.size());
        for(int i=0;i<36;i++)
            assertEquals(i,copy.fieldInventory.get(i).getTag().getInt("ore_nbt"));
    }

    @Test void constructionSuppliesAndToolsNeverCountAsDeliveredOre(){
        var w=worker();
        w.fieldInventory.add(new ItemStack(Items.COBBLESTONE,20));
        w.fieldInventory.add(new ItemStack(Items.TORCH,12));
        w.fieldInventory.add(new ItemStack(Items.LADDER,8));
        w.fieldInventory.add(new ItemStack(Items.OAK_PLANKS,16));
        assertEquals(20,KingdomMineCargo.minedCount(w));
        assertTrue(KingdomMineCargo.isSupply(new ItemStack(Items.TORCH)));
        assertTrue(KingdomMineCargo.isSupply(new ItemStack(Items.LADDER)));
        assertTrue(KingdomMineCargo.isSupply(new ItemStack(Items.OAK_PLANKS)));
    }

    @Test void sevenBySevenRouteHasLaddersEveryTwoAndHorizonsEveryFive(){
        var shaft=new KingdomMineSettings.Shaft(0,6,0,6,80,"minecraft:overworld");
        var id=UUID.randomUUID();
        var settings=new KingdomMineSettings();
        assertTrue(settings.setShaft(id,shaft));
        assertTrue(settings.choose(id,KingdomMineSettings.Start.DOWN));
        assertTrue(settings.chooseLevels(id,3));
        assertTrue(KingdomMineCentralShaftPlan.valid(shaft,new BlockPos(3,80,7)));
        assertEquals(new BlockPos(3,80,7),KingdomMineCentralShaftPlan.entrance(shaft));
        var cells=KingdomMineCentralShaftPlan.cells(new BlockPos(3,80,7),shaft,3,
            KingdomMineSettings.Heading.NORTH);
        assertEquals(15,cells.stream().filter(c->c.approach().getY()>c.target().getY()).count());
        for(int depth=1;depth<=15;depth++){
            assertEquals(depth%2==0,KingdomMineStructurePlan.requiresLadder(depth));
            assertEquals(depth%5==0,KingdomMineStructurePlan.isLevel(depth));
        }
        var deck=KingdomMineStructurePlan.flooring(shaft,5,new BlockPos(3,80,7),
            3,KingdomMineSettings.Heading.NORTH);
        assertEquals(39,deck.size(),"Two-block perimeter with reserved next stair");
        settings.markStarted(id);
        assertFalse(settings.setShaft(id,new KingdomMineSettings.Shaft(0,7,0,7,80,"minecraft:overworld")));
        assertFalse(settings.chooseLevels(id,2));
        var serialized=settings.save(new CompoundTag());
        var stored=serialized.getList("Mines",10).getCompound(0);
        assertEquals("DOWN",stored.getString("Start"));
        assertEquals(3,stored.getInt("Levels"));
        assertTrue(stored.getBoolean("Started"));
    }

    @Test void realWorkUsesPerBlockShovelThenPickaxeAndLegalLoadedLand() throws Exception {
        assertEquals(KingdomMineTools.Tool.SHOVEL,
            KingdomMineTools.required(Blocks.DIRT.defaultBlockState()));
        assertEquals(KingdomMineTools.Tool.PICKAXE,
            KingdomMineTools.required(Blocks.STONE.defaultBlockState()));
        String work=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineWork.java"));
        assertTrue(work.contains("KingdomMineTools.required(level.getBlockState(target))"));
        assertTrue(work.contains("KingdomMineTools.equip(level,npc,worker,needed)"));
        assertTrue(work.contains("KingdomMineSupportSafety.inspect(level,target)"));
        assertTrue(work.contains("Block.getDrops(state,level,target"));
        assertTrue(work.contains("KingdomMineDelivery.canCarry(worker,drops)"));
        assertTrue(work.contains("KingdomMineDelivery.deliver(level,npc,worker)"));
        assertTrue(work.contains("KingdomTerritories.get(level.getServer()).canUse(level,pos,worker.employer)"));
        assertFalse(work.contains("NpcFieldWork.borrowTool(level,npc,worker)"));

        String site=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineSite.java"));
        assertFalse(site.contains("worker.hasWorkstation"));
        assertTrue(site.contains("EnterpriseCoreBlockEntity"));
        assertTrue(site.contains("KingdomMineCentralShaftPlan.entrance(area)"));
        String quickSetup=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomNpcQuickSetup.java"));
        assertTrue(quickSetup.contains("var mineInspection=KingdomMineSite.inspect(server,level,worker)"),
            "The Work button must not claim success when the mine entrance is blocked");
        assertTrue(quickSetup.contains("mineInspection.reason()"));
        String structures=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineStructureWork.java"));
        assertTrue(structures.contains("KingdomTerritories.get(level.getServer()).canUse(level,at,w.employer)"));
    }
}
