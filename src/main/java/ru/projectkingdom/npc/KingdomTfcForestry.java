package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.Container;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;

/**
 * Physical TFC forestry: owned plots, actual saplings, native TFC tree felling,
 * real loot and tools, natural growth, and existing Kingdom warehouse logistics.
 * All tree pieces are preflighted before invoking the native logging routine.
 */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomTfcForestry {
    private KingdomTfcForestry(){}
    private static final TagKey<Block> TFC_LOGS=TagKey.create(Registries.BLOCK,
        new ResourceLocation("tfc","logs_that_log"));
    private static final TagKey<Item> TFC_AXES=TagKey.create(Registries.ITEM,
        new ResourceLocation("tfc","axes_that_log"));
    private static final int MAX_TREE=160;
    private static boolean initialized,available;
    private static Method nativeLogging,nativeShouldLog;
    private static final String LOG_LOOT_OWNER="KingdomForestWorker";
    private record HarvestContext(ServerLevel level,UUID worker,List<ItemEntity> drops){}
    private static final ThreadLocal<HarvestContext> CURRENT=new ThreadLocal<>();

    @SubscribeEvent public static void recordDrop(EntityJoinLevelEvent event){
        var context=CURRENT.get();
        if(context!=null&&event.getLevel()==context.level()
                &&event.getEntity() instanceof ItemEntity item
                &&item.isAlive()){
            item.getPersistentData().putUUID(LOG_LOOT_OWNER,context.worker());
            context.drops().add(item);
        }
    }
    public static boolean validSapling(String id){
        if(id==null||!id.startsWith("tfc:wood/sapling/"))return false;
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.ITEM.containsKey(key))return false;
        Item item=BuiltInRegistries.ITEM.get(key);
        return item instanceof BlockItem block && block.getBlock() instanceof SaplingBlock
            &&BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString().equals(id);
    }
    public static String wood(String sapling){
        return validSapling(sapling)?sapling.substring("tfc:wood/sapling/".length()):"";
    }
    public static String logId(String sapling){
        String kind=wood(sapling);
        return kind.isBlank()?"":"tfc:wood/log/"+kind;
    }
    private static boolean init(){
        if(initialized)return available;
        initialized=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try{
            Class<?> helper=Class.forName("net.dries007.tfc.util.AxeLoggingHelper");
            nativeLogging=helper.getMethod("doLogging",LevelAccessor.class,BlockPos.class,
                net.minecraft.world.entity.player.Player.class,ItemStack.class);
            nativeShouldLog=helper.getMethod("shouldLog",LevelAccessor.class,BlockPos.class,
                BlockState.class,ItemStack.class);
            available=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC natural tree logging unavailable: "+ex);
        }
        return available;
    }
    private static boolean axe(ItemStack stack){
        return !stack.isEmpty()&&stack.is(TFC_AXES); // TFC stone axes may lack vanilla axe tag
    }
    private static boolean treeLog(ServerLevel level,BlockPos at,String wood){
        if(!level.hasChunkAt(at))return false;
        BlockState state=level.getBlockState(at);
        return state.is(TFC_LOGS)
            &&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                .equals("tfc:wood/log/"+wood);
    }
    private static boolean authorized(ServerLevel level,BlockPos at,
                                      UUID owner,KingdomForestData.Area area) {
        if(!level.hasChunkAt(at)||!area.inside(at)
            ||!KingdomTerritories.get(level.getServer()).canUse(level,at,owner))return false;
        var civic=KingdomCivicsData.get(level.getServer());
        return civic.plot(level,at)==null
            ||civic.allowed(owner,level.dimension().location().toString(),
                at.getX(),at.getZ(),KingdomCivicsData.Action.BREAK);
    }
    /**
     * 26-neighbour BFS is deliberately MORE conservative than the native
     * direction-dependent branch traversal. Entire connected wood cluster and
     * adjacent leaves must be on our actual land, loaded, inside selected plot.
     * Never run an unbounded TFC findLogs against unloaded terrain.
     */
    private static List<BlockPos> safeTree(ServerLevel level,BlockPos root,UUID owner,
                                           KingdomForestData.Area area,String species) {
        if(!treeLog(level,root,species))return List.of();
        var queue=new ArrayDeque<BlockPos>();
        var seen=new HashSet<BlockPos>();
        queue.add(root.immutable());
        while(!queue.isEmpty()){
            if(seen.size()>=MAX_TREE)return List.of();
            BlockPos p=queue.removeFirst();
            if(!seen.add(p))continue;
            if(Math.abs(p.getX()-root.getX())>12||Math.abs(p.getZ()-root.getZ())>12
                ||Math.abs(p.getY()-root.getY())>32
                ||!authorized(level,p,owner,area)
                ||!treeLog(level,p,species))return List.of();
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
                for(int dz=-1;dz<=1;dz++){
                    if(dx==0&&dy==0&&dz==0)continue;
                    var next=p.offset(dx,dy,dz);
                    if(!level.hasChunkAt(next))return List.of();
                    BlockState state=level.getBlockState(next);
                    // Protect the whole canopy, even where it overhangs plots.
                    if(state.is(BlockTags.LEAVES)
                        &&!authorized(level,next,owner,area))return List.of();
                    if(state.is(TFC_LOGS)){
                        if(!treeLog(level,next,species)||!authorized(level,next,owner,area))
                            return List.of();
                        if(!seen.contains(next))queue.add(next.immutable());
                    }
                }
        }
        return seen.size()<2?List.of():List.copyOf(seen);
    }
    private static boolean near(Villager npc,BlockPos at){
        return npc.distanceToSqr(at.getX()+.5,at.getY()+.5,at.getZ()+.5)<=6.25;
    }
    private static void walk(Villager npc,BlockPos at){
        npc.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.8);
    }
    private static boolean takeAxe(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                   Container raw,BlockPos source){
        if(axe(w.fieldTool))return true;
        if(!w.fieldTool.isEmpty())return false;
        if(!near(npc,source)){walk(npc,source);return false;}
        for(int i=0;i<raw.getContainerSize();i++){
            ItemStack stock=raw.getItem(i);
            if(axe(stock)&&raw.canTakeItem(raw,i,stock)){
                w.fieldTool=raw.removeItem(i,1);raw.setChanged();
                KingdomNpcData.get(level.getServer()).setDirty();return true;
            }
        }
        return false;
    }
    private static boolean isPlantingSpot(ServerLevel level,BlockPos location,
                                           KingdomForestData.Area area,UUID owner,Block block) {
        if(!authorized(level,location,owner,area)
           ||(KingdomCivicsData.get(level.getServer()).plot(level,location)!=null
               &&!KingdomCivicsData.get(level.getServer()).allowed(
                   owner,level.dimension().location().toString(),location.getX(),
                   location.getZ(),KingdomCivicsData.Action.BUILD))
           ||!level.hasChunkAt(location.below())
           ||!level.getBlockState(location).isAir()
           ||level.getMaxLocalRawBrightness(location.above())<9
           ||!block.defaultBlockState().canSurvive(level,location))return false;
        // At least four blocks between planted saplings / mature trunks.
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)
            for(int dy=-1;dy<=2;dy++){
                BlockPos p=location.offset(dx,dy,dz);
                if(!level.hasChunkAt(p))return false;
                var state=level.getBlockState(p);
                if(state.getBlock() instanceof SaplingBlock ||state.is(TFC_LOGS))return false;
            }
        return true;
    }
    private static boolean plant(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                  KingdomForestData.Area area,Container raw,BlockPos source){
        if(!area.replant()||!validSapling(area.sapling()))return false;
        var key=ResourceLocation.tryParse(area.sapling());
        Item seed=BuiltInRegistries.ITEM.get(key);
        Block block=((BlockItem)seed).getBlock();
        BlockPos target=null;
        // Plant only on approved natural soil. Space real tree trunks 4 blocks apart.
        for(int x=area.minX()+1;x<area.maxX() &&target==null;x+=4)
            for(int z=area.minZ()+1;z<area.maxZ();z+=4){
                var pos=new BlockPos(x,area.y()+1,z);
                if(isPlantingSpot(level,pos,area,w.employer,block)){target=pos;break;}
            }
        if(target==null)return false;
        int carried=-1;
        for(int i=0;i<w.fieldInventory.size();i++)
            if(w.fieldInventory.get(i).is(seed)){carried=i;break;}
        if(carried<0){
            if(!near(npc,source)){walk(npc,source);return false;}
            for(int slot=0;slot<raw.getContainerSize();slot++){
                ItemStack stack=raw.getItem(slot);
                if(!stack.is(seed)||!raw.canTakeItem(raw,slot,stack))continue;
                ItemStack taken=raw.removeItem(slot,1);
                if(taken.isEmpty())return false;
                NpcFieldWork.put(w,taken);
                raw.setChanged();KingdomNpcData.get(level.getServer()).setDirty();
                return true; // The NPC must physically carry its seed to the forest.
            }
            return false;
        }
        if(!near(npc,target)){walk(npc,target);return false;}
        if(!isPlantingSpot(level,target,area,w.employer,block))return false;
        ItemStack carriedStack=w.fieldInventory.get(carried);
        ItemStack consumed=carriedStack.split(1);
        if(carriedStack.isEmpty())w.fieldInventory.remove(carried);
        if(consumed.isEmpty())return false;
        if(!level.setBlock(target,block.defaultBlockState(),3)){
            NpcFieldWork.put(w,consumed);
            KingdomNpcData.get(level.getServer()).setDirty();return false;
        }
        // TFC's TickCounterBlockEntity controls growth from its own calendar.
        block.setPlacedBy(level,target,block.defaultBlockState(),npc,consumed);
        w.workUnits++;w.energy=Math.max(0,w.energy-1);
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
    private static boolean fell(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                KingdomForestData.Area area,BlockPos root) {
        if(!init()||!axe(w.fieldTool))return false;
        try{
            if(!Boolean.TRUE.equals(nativeShouldLog.invoke(null,level,root,
                level.getBlockState(root),w.fieldTool)))return false;
        }catch(ReflectiveOperationException|RuntimeException ex){
            available=false;return false;
        }
        List<BlockPos> tree=safeTree(level,root,w.employer,area,wood(area.sapling()));
        if(tree.isEmpty()||tree.size()>MAX_TREE
            ||!NpcFieldWork.canFit(w,List.of(new ItemStack(Items.OAK_LOG,
                Math.min(64,tree.size())))))return false;
        if(!near(npc,root)){walk(npc,root);return false;}
        // Tree and land can change during pathfinding.
        tree=safeTree(level,root,w.employer,area,wood(area.sapling()));
        if(tree.isEmpty())return false;
        var fake=FakePlayerFactory.get(level,new GameProfile(
            UUID.nameUUIDFromBytes("kingdom-tfc-forest".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "[KingdomLumberjack]"));
        fake.setItemInHand(InteractionHand.MAIN_HAND,w.fieldTool);
        var drops=new ArrayList<ItemEntity>();
        CURRENT.set(new HarvestContext(level,w.entityId,drops));
        try{
            nativeLogging.invoke(null,level,root,fake,w.fieldTool);
        }catch(ReflectiveOperationException|RuntimeException ex){
            available=false;
            System.err.println("[Kingdom] Native TFC tree felling unavailable: "+ex);
            return false;
        }finally{
            CURRENT.remove();
        }
        boolean changed=false;
        for(ItemEntity entity:drops){
            if(!entity.isAlive()||entity.getItem().isEmpty())continue;
            ItemStack stack=entity.getItem();
            if(!NpcFieldWork.canFit(w,List.of(stack)))continue; // Leave genuine drop on ground.
            NpcFieldWork.put(w,stack.copy());
            entity.discard();
            w.fieldHarvested+=stack.getCount();
            changed=true;
        }
        if(w.fieldTool.isEmpty()||w.fieldTool.getDamageValue()>=w.fieldTool.getMaxDamage())
            w.fieldTool=ItemStack.EMPTY;
        w.workUnits++;w.skillXp++;w.energy=Math.max(0,w.energy-3);
        KingdomNpcData.get(level.getServer()).setDirty();
        return changed || !treeLog(level,root,wood(area.sapling()));
    }
    /** Pick only real tree drops marked by this NPC's native logging action. */
    private static boolean recoverDrops(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                        KingdomForestData.Area area){
        var bounds=new AABB(area.minX(),area.y()-2,area.minZ(),
            area.maxX()+1,area.y()+36,area.maxZ()+1);
        for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,bounds,
                entity->entity.isAlive()&&entity.getPersistentData().hasUUID(LOG_LOOT_OWNER)
                    &&w.entityId.equals(entity.getPersistentData().getUUID(LOG_LOOT_OWNER)))){
            ItemStack stack=item.getItem();
            if(stack.isEmpty()||!NpcFieldWork.canFit(w,List.of(stack)))return false;
            if(npc.distanceToSqr(item)>8){
                npc.getNavigation().moveTo(item,.8);
                return false;
            }
            NpcFieldWork.put(w,stack.copy());
            w.fieldHarvested+=stack.getCount();
            item.discard();
            KingdomNpcData.get(level.getServer()).setDirty();
            return true;
        }
        return false;
    }
    private static boolean employedOnLumber(ServerLevel level,KingdomNpcData.Worker w){
        if(w.employer==null||!"lumberjack".equals(w.job)
            ||!KingdomNpcWorkday.employed(level.getServer(),w))return false;
        var firms=KingdomEnterpriseData.get(level.getServer());
        for(var contract:KingdomEnterprisePayrollData.get(level.getServer()).all()){
            if(!contract.active()||!contract.worker().equals(w.entityId)
                ||(!w.workplaceId.isBlank()&&!w.workplaceId.equals(
                    contract.enterprise().toString()))
                ||firms.kind(contract.enterprise())!=EnterpriseKind.LUMBER)continue;
            var firm=firms.find(contract.enterprise());var core=firms.core(contract.enterprise());
            if(firm!=null&&firm.owner().equals(w.employer)
                &&core!=null&&core.active()&&core.kind()==EnterpriseKind.LUMBER)
                return true;
        }
        return false;
    }
    public static BlockPos workSite(ServerLevel level,KingdomNpcData.Worker w){
        if(w.employer==null)return null;
        var firms=KingdomEnterpriseData.get(level.getServer());
        for(var contract:KingdomEnterprisePayrollData.get(level.getServer()).all()){
            if(!contract.active()||!contract.worker().equals(w.entityId)
                ||firms.kind(contract.enterprise())!=EnterpriseKind.LUMBER)continue;
            for(var area:KingdomForestData.get(level.getServer()).of(contract.enterprise())){
                if(area.enabled()&&area.dimension().equals(w.dimension)
                    &&level.hasChunkAt(area.center()))return area.center();
            }
        }
        return null;
    }
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        if(w==null||!employedOnLumber(level,w)||!init()
            ||!KingdomNpcDuty.autonomousOrder(w.order)
            ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
            ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
            ||!w.hasSource||!w.hasDestination
            ||!w.dimension.equals(level.dimension().location().toString()))return false;
        var firms=KingdomEnterpriseData.get(level.getServer());
        var land=KingdomTerritories.get(level.getServer());
        var source=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        var dest=new BlockPos(w.destX,w.destY,w.destZ);
        if(!level.hasChunkAt(source)||!level.hasChunkAt(dest)
            ||!land.usable(level,source,w.employer,"raw")
            ||!land.usable(level,dest,w.employer,"finished")
            ||!(level.getBlockEntity(source) instanceof Container raw)
            ||!(level.getBlockEntity(dest) instanceof Container finished))return false;
        // Deliver carried physical goods first, even when the desired stock is full.
        boolean hasGoods=w.fieldInventory.stream().anyMatch(stack->
            !stack.isEmpty()&&!validSapling(BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString()));
        if(hasGoods){
            boolean moved=NpcFieldWork.deposit(level,npc,w);
            if(moved||w.fieldInventory.stream().anyMatch(stack->
                !stack.isEmpty()&&!validSapling(BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString())))return moved;
        }
        for(var contract:KingdomEnterprisePayrollData.get(level.getServer()).all()){
            if(!contract.active()||!contract.worker().equals(w.entityId)
                ||firms.kind(contract.enterprise())!=EnterpriseKind.LUMBER)continue;
            for(var area:KingdomForestData.get(level.getServer()).of(contract.enterprise())){
                if(!area.enabled()||!area.dimension().equals(w.dimension)
                    ||!validSapling(area.sapling())
                    ||!level.hasChunkAt(area.center()))continue;
                String wood=wood(area.sapling());
                String log=logId(area.sapling());
                var stock=KingdomEnterpriseStock.inspect(level,contract.enterprise(),
                    log,EnterpriseBindingRole.FINISHED_STORAGE,dest);
                if(!stock.complete())continue;
                // Real leftovers stay as uniquely marked world drops until collected.
                if(recoverDrops(level,npc,w,area))return true;
                // Harvest first; otherwise perpetual planting starves woodcutting.
                var limits=new EnterpriseStockPolicy.Thresholds(area.minLogs(),area.maxLogs());
                boolean allowedHarvest=KingdomForestData.get(level.getServer()).harvestAllowed(
                    area.id(),limits,stock.physical());
                if(!allowedHarvest){
                    if(plant(level,npc,w,area,raw,source))return true;
                    continue;
                }
                if(!takeAxe(level,npc,w,raw,source)){
                    if(plant(level,npc,w,area,raw,source))return true;
                    continue;
                }
                BlockPos best=null;double closest=Double.MAX_VALUE;
                for(int x=area.minX();x<=area.maxX();x++)
                    for(int z=area.minZ();z<=area.maxZ();z++)
                        for(int y=area.y()-2;y<=area.y()+16;y++){
                            BlockPos p=new BlockPos(x,y,z);
                            if(!level.hasChunkAt(p)||!treeLog(level,p,wood))continue;
                            if(!authorized(level,p,w.employer,area))continue;
                            double distance=p.distSqr(npc.blockPosition());
                            if(distance<closest){closest=distance;best=p;}
                        }
                if(best!=null){
                    if(fell(level,npc,w,area,best))return true;
                    // An unharvestable adult tree is not a reason to plant forever.
                    continue;
                }
                if(plant(level,npc,w,area,raw,source))return true;
            }
        }
        return false;
    }
}
