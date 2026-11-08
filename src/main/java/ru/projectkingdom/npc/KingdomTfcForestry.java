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
import net.minecraft.world.level.block.state.properties.Property;
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
    private static Method nativeLogging,nativeShouldLog,nativeFindLogs,nativeLoggingBlock,nativeBranchConnected;
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
            nativeFindLogs=helper.getMethod("findLogs",LevelAccessor.class,BlockPos.class);
            nativeLoggingBlock=helper.getMethod("isLoggingBlock",BlockState.class);
            nativeBranchConnected=Class.forName(
                "net.dries007.tfc.common.blocks.wood.BranchDirection"
            ).getMethod("connected",BlockPos.class,BlockPos.class);
            available=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC natural tree logging unavailable: "+ex);
        }
        return available;
    }
    /** Visible health check for user-facing work notifications. */
    public static boolean nativeAvailable(){return init();}

    /** Native TFC logging explicitly requires the AXES_THAT_LOG tag. */
    public static boolean usableAxe(ItemStack stack){
        return !stack.isEmpty()&&stack.is(TFC_AXES);
    }
    private static boolean axe(ItemStack stack){return usableAxe(stack);}
    /** Avoid choosing an adjacent branch that native TFC refuses to fell. */
    private static boolean validRoot(ServerLevel level,BlockPos root,ItemStack tool){
        if(!init()||!level.hasChunkAt(root))return false;
        try {
            return Boolean.TRUE.equals(nativeShouldLog.invoke(null,level,root,
                    level.getBlockState(root),tool));
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
    private static boolean treeLog(ServerLevel level,BlockPos at,String wood){
        if(!level.hasChunkAt(at))return false;
        BlockState state=level.getBlockState(at);
        return state.is(TFC_LOGS)
            &&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                .equals("tfc:wood/log/"+wood);
    }
    /** Both legacy claims and titled town plots are legal Kingdom working land.
     * A tree crossing into a foreign area still fails the entire preflight. */
    static boolean ownedHarvestLand(ServerLevel level,BlockPos at,UUID owner) {
        if(owner==null||!level.hasChunkAt(at))return false;
        var civic=KingdomCivicsData.get(level.getServer());
        if(civic.plot(level,at)!=null)
            return civic.allowed(owner,level.dimension().location().toString(),
                    at.getX(),at.getZ(),KingdomCivicsData.Action.BREAK);
        return KingdomTerritories.get(level.getServer()).canUse(level,at,owner);
    }
    private static boolean authorized(ServerLevel level,BlockPos at,
                                      UUID owner,KingdomForestData.Area area) {
        return area.inside(at) && ownedHarvestLand(level,at,owner);
    }
    /** Native TFC trees are *directionally* connected. A blanket 26-neighbour
     * check falsely rejected valid, densely planted trees and overhanging leaves.
     * The preflight traverses only BranchDirection-connected natural logs,
     * ensuring that ALL native reads remain in already-loaded chunks. We then
     * compare to TFC's own findLogs() before calling doLogging().
     */
    private record TreeCheck(List<BlockPos> logs,String blocker) {
        static TreeCheck blocked(String reason) {return new TreeCheck(List.of(),reason);}
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static boolean nativeConnection(BlockPos from,BlockPos to,BlockState state)
            throws ReflectiveOperationException {
        if(!Boolean.TRUE.equals(nativeLoggingBlock.invoke(null,state)))return false;
        for(Property<?> property:state.getProperties())
            if("branch_direction".equals(property.getName())){
                Object direction=state.getValue((Property)property);
                return Boolean.TRUE.equals(nativeBranchConnected.invoke(direction,from,to));
            }
        return false;
    }

    private static TreeCheck checkTree(ServerLevel level,BlockPos root,UUID owner,
                                       KingdomForestData.Area area,String species) {
        if(!init())return TreeCheck.blocked("TFC API рубки недоступен");
        if(!treeLog(level,root,species))
            return TreeCheck.blocked("Выбранная порода не совпадает с деревом");
        var queue=new ArrayDeque<BlockPos>();
        var seen=new LinkedHashSet<BlockPos>();
        queue.add(root.immutable());
        seen.add(root.immutable());
        try{
            while(!queue.isEmpty()){
                BlockPos p=queue.removeFirst();
                if(seen.size()>MAX_TREE)
                    return TreeCheck.blocked("Дерево больше безопасного лимита "+MAX_TREE+" брёвен");
                if(Math.abs(p.getX()-root.getX())>12||Math.abs(p.getZ()-root.getZ())>12
                    ||Math.abs(p.getY()-root.getY())>32)
                    return TreeCheck.blocked("Ветви уходят слишком далеко от ствола "+root.toShortString());
                if(!ownedHarvestLand(level,p,owner))
                    return TreeCheck.blocked("Бревно "+p.toShortString()+" вне земли с правом рубки");
                if(!treeLog(level,p,species))
                    return TreeCheck.blocked("Соединённое бревно "+p.toShortString()
                        +" имеет другую породу или тип");
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
                    for(int dz=-1;dz<=1;dz++){
                        if(dx==0&&dy==0&&dz==0)continue;
                        BlockPos next=p.offset(dx,dy,dz);
                        if(!level.hasChunkAt(next))
                            return TreeCheck.blocked("Чанк рядом с веткой "+next.toShortString()
                                +" не загружен");
                        if(!seen.contains(next)
                            &&nativeConnection(p,next,level.getBlockState(next))){
                            seen.add(next.immutable());
                            queue.add(next.immutable());
                        }
                    }
            }
            if(seen.size()<2)
                return TreeCheck.blocked("TFC не нашёл соединённых природных ветвей");
            Object result=nativeFindLogs.invoke(null,level,root);
            if(!(result instanceof List<?> actual)
                ||actual.size()!=seen.size()
                ||actual.stream().anyMatch(pos->!(pos instanceof BlockPos)
                    ||!seen.contains(pos)))
                return TreeCheck.blocked("Маршрут TFC не совпал с безопасной проверкой");
            // Use TFC's exact reverse-order list, never a blanket neighbouring-log list.
            return new TreeCheck(actual.stream().map(pos->(BlockPos)pos).toList(),null);
        }catch(ReflectiveOperationException|RuntimeException ex){
            return TreeCheck.blocked("Ошибка проверки направления ветвей TFC");
        }
    }

    private static List<BlockPos> safeTree(ServerLevel level,BlockPos root,UUID owner,
                                           KingdomForestData.Area area,String species) {
        return checkTree(level,root,owner,area,species).logs();
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
    /** Genuine fallen timber is collectable even when the employer felled the
     * tree by hand. The lumberjack may ONLY take matching TFC logs in its
     * registered, loaded and owned forest area. Never pick up another player's
     * explicitly owned items or another NPC's tagged felling drops.
     */
    static boolean eligibleGroundLog(String itemId,String species) {
        if(itemId==null||species==null||species.isBlank())return false;
        return ("tfc:wood/log/"+species).equals(itemId)
            || ("tfc:wood/stripped_log/"+species).equals(itemId)
            || ("tfc:wood/wood/"+species).equals(itemId);
    }
    private enum Recovery { NONE, WALKING, PICKED, FULL, NO_ROUTE }

    private static boolean recoverable(ServerLevel level,ItemEntity entity,
                                        KingdomNpcData.Worker worker,KingdomForestData.Area area) {
        if(entity==null || !entity.isAlive() || entity.getItem().isEmpty()
            || !level.hasChunkAt(entity.blockPosition())
            || !area.inside(entity.blockPosition())
            || !ownedHarvestLand(level,entity.blockPosition(),worker.employer))return false;
        var tag=entity.getPersistentData();
        if(tag.hasUUID(LOG_LOOT_OWNER))
            return worker.entityId.equals(tag.getUUID(LOG_LOOT_OWNER));
        var itemOwner=entity.getOwner();
        if(itemOwner!=null && !itemOwner.getUUID().equals(worker.employer))return false;
        String id=BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString();
        return eligibleGroundLog(id,wood(area.sapling()));
    }

    private static List<ItemEntity> fallenLogs(ServerLevel level,KingdomNpcData.Worker worker,
                                                KingdomForestData.Area area) {
        var bounds=new AABB(area.minX(),area.y()-2,area.minZ(),
            area.maxX()+1,area.y()+36,area.maxZ()+1);
        return level.getEntitiesOfClass(ItemEntity.class,bounds,
            entity->recoverable(level,entity,worker,area));
    }

    /** The diagnostic is read-only and never picks an item or force-loads a chunk. */
    public static boolean hasRecoverableLogs(ServerLevel level,KingdomNpcData.Worker worker,
                                              KingdomForestData.Area area) {
        return !fallenLogs(level,worker,area).isEmpty();
    }

    /** Navigate to the nearest allowed stack; never run the planting/harvest
     * navigation in the same tick while approaching a dropped stack.
     */
    private static Recovery recoverDrops(ServerLevel level,Villager npc,
                                         KingdomNpcData.Worker worker,KingdomForestData.Area area) {
        var drops=fallenLogs(level,worker,area);
        drops.sort(Comparator.comparingDouble(npc::distanceToSqr));
        for(ItemEntity item:drops){
            ItemStack stack=item.getItem();
            if(!NpcFieldWork.canFit(worker,List.of(stack)))return Recovery.FULL;
            if(npc.distanceToSqr(item)>8)
                return npc.getNavigation().moveTo(item,.8)
                    ?Recovery.WALKING:Recovery.NO_ROUTE;
            // The actual world ItemEntity is the only source of transferred
            // items. No duplicate representation or virtual goods are created.
            NpcFieldWork.put(worker,stack.copy());
            worker.fieldHarvested+=stack.getCount();
            item.discard();
            KingdomNpcData.get(level.getServer()).setDirty();
            return Recovery.PICKED;
        }
        return Recovery.NONE;
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
    /** Nearest native-compatible tree that also passes the *entire* land and
     * canopy safety preflight. Previously we always retried the nearest
     * candidate even when safeTree rejected it, starving all later trees.
     * No chunks are loaded or modified during this search.
     */
    static BlockPos nearestSafeTree(ServerLevel level,BlockPos observer,
                                   KingdomNpcData.Worker worker,KingdomForestData.Area area) {
        if(!init() || !axe(worker.fieldTool))return null;
        String species=wood(area.sapling());
        if(species.isBlank())return null;
        List<BlockPos> roots=new ArrayList<>();
        for(int x=area.minX();x<=area.maxX();x++)
            for(int z=area.minZ();z<=area.maxZ();z++)
                for(int y=area.y()-2;y<=area.y()+16;y++){
                    BlockPos pos=new BlockPos(x,y,z);
                    if(!level.hasChunkAt(pos)||!treeLog(level,pos,species)
                        ||!authorized(level,pos,worker.employer,area)
                        ||!validRoot(level,pos,worker.fieldTool))continue;
                    roots.add(pos);
                }
        roots.sort(Comparator.comparingDouble(p->p.distSqr(observer)));
        // Every candidate must pass its own bounded connected-tree preflight.
        for(BlockPos root:roots)
            if(!safeTree(level,root,worker.employer,area,species).isEmpty())
                return root;
        return null;
    }

    /** Read-only explanation for the owner; never claim that cutting is underway
     * just because the TFC API and an axe exist.
     */
    public static String harvestBlocker(ServerLevel level,KingdomNpcData.Worker worker,
                                         KingdomForestData.Area area) {
        String species=wood(area.sapling());
        if(species.isBlank())return "Выбери породу TFC в лесном участке";
        if(!level.hasChunkAt(area.center()))return "Лесной участок не загружен";
        boolean found=false,hasNativeRoot=false;
        String blocker=null;
        for(int x=area.minX();x<=area.maxX();x++)
            for(int z=area.minZ();z<=area.maxZ();z++)
                for(int y=area.y()-2;y<=area.y()+16;y++){
                    BlockPos pos=new BlockPos(x,y,z);
                    if(!level.hasChunkAt(pos)||!treeLog(level,pos,species))continue;
                    found=true;
                    if(!authorized(level,pos,worker.employer,area)){
                        if(blocker==null)blocker="Нет прав рубки у "+pos.toShortString();
                        continue;
                    }
                    if(!validRoot(level,pos,worker.fieldTool))continue;
                    hasNativeRoot=true;
                    var check=checkTree(level,pos,worker.employer,area,species);
                    if(!check.logs().isEmpty())return null;
                    if(blocker==null)blocker=check.blocker();
                }
        if(!found)return "Нет взрослого дерева выбранной породы на участке";
        if(blocker!=null)return blocker;
        if(!hasNativeRoot)
            return "TFC не разрешает рубить выбранный ствол: проверь форму дерева и топор";
        return "Нет дерева, проходящего проверку безопасности TFC";
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
            )return false;
        // Resolve vanilla or modded/TFC mutable inventories uniformly.
        Container raw=KingdomContainerAccess.resolve(level,source);
        Container finished=KingdomContainerAccess.resolve(level,dest);
        if(raw==null||finished==null)return false;
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
                // Collect genuine matching loose timber (including logs felled
                // by the employer) before either planting or cutting a new tree.
                var recovery=recoverDrops(level,npc,w,area);
                if(recovery==Recovery.PICKED)return true;
                if(recovery!=Recovery.NONE)return false;
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
                BlockPos best=nearestSafeTree(level,npc.blockPosition(),w,area);
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
