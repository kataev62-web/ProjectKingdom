package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * First conservative MineColonies-style shaft executor for Kingdom.
 * A real MINE enterprise anchors a bounded 1x2 horizontal gallery.
 * Only naturally mineable rock/ore is removed, one block per work tick.
 * No simulated goods, remote block breaking, chunk loads or forced tunnelling.
 * Vertical shafts and structural supports remain a separate safety feature.
 * The second level uses a descending walkable stair, not a vertical drop.
 * Torches are installed from physical stock at predeclared gallery checkpoints.
 */
public final class KingdomMineWork {
    private KingdomMineWork() {}
    private static final double REACH_SQR = 6.25;

    private static void status(ServerLevel level,KingdomNpcData.Worker worker,String next) {
        if(!next.equals(worker.mineStatus)){
            worker.mineStatus=next;
            KingdomNpcData.get(level.getServer()).setDirty();
        }
    }


    static boolean naturalRock(String id) {
        return "minecraft:stone".equals(id) || "minecraft:deepslate".equals(id)
            || "minecraft:tuff".equals(id) || "minecraft:andesite".equals(id)
            || "minecraft:diorite".equals(id) || "minecraft:granite".equals(id)
            || id.startsWith("minecraft:") && id.endsWith("_ore")
            || id.startsWith("tfc:rock/raw/")
            || KingdomTfcFieldRules.ore(id);
    }

    /** Natural surface dirt often covers the first step of a TFC shaft.
     * It must be removable before the miner can reach stone beneath it.
     */
    static boolean surfaceSoil(String id) {
        return "minecraft:dirt".equals(id) || "minecraft:grass_block".equals(id)
            || "minecraft:coarse_dirt".equals(id)
            || id.startsWith("tfc:soil/dirt/")
            || id.startsWith("tfc:soil/grass/")
            || id.startsWith("tfc:soil/grass_path/")
            || id.startsWith("tfc:soil/coarse_dirt/");
    }

    static boolean safeToBreak(ServerLevel level, BlockPos pos,
                                       KingdomNpcData.Worker worker) {
        if (!level.hasChunkAt(pos) || pos.getY() <= level.getMinBuildHeight() + 1
            || pos.getY() >= level.getMaxBuildHeight() - 3
            || worker.employer==null
            || !KingdomTfcForestry.ownedHarvestLand(level,pos,worker.employer))
            return false;
        BlockState state=level.getBlockState(pos);
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean rock=naturalRock(id);
        boolean surface=surfaceSoil(id);
        if ((!rock&&!surface) || (rock&&!state.is(BlockTags.MINEABLE_WITH_PICKAXE))
            || !state.getFluidState().isEmpty() || level.getBlockEntity(pos)!=null
            || state.getDestroySpeed(level,pos)<0) return false;
        if (worker.fieldTool.isEmpty()
            || !(worker.fieldTool.is(ItemTags.PICKAXES)
                 || worker.fieldTool.getItem() instanceof PickaxeItem)
            || (rock && !worker.fieldTool.isCorrectToolForDrops(state))) return false;
        // Never deliberately open a liquid pocket or let loose gravel/sand fall.
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbor=pos.relative(direction);
            if (!level.hasChunkAt(neighbor)
                || !level.getFluidState(neighbor).isEmpty()) return false;
        }
        BlockPos above=pos.above();
        return !(level.getBlockState(above).getBlock() instanceof FallingBlock);
    }

    /**
     * Finds the first unexcavated gallery cell. The previous cell must be open
     * and supported by a floor; the NPC must physically approach that cell.
     */
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker) {
        if (!"miner".equals(worker.job))return false;
        if(worker.employer==null){status(level,worker,"Нет работодателя шахты");return false;}
        if(worker.contractPaused){status(level,worker,"Шахтёр: работа остановлена из-за задолженности");return false;}
        if(!KingdomNpcWorkday.employed(level.getServer(),worker)){
            status(level,worker,"Шахтёр: нет действующего трудового договора");return false;
        }
        if(!worker.hasSource||!worker.hasDestination){
            status(level,worker,"Шахтёр: назначь склады сырья и продукции");return false;
        }
        if(!worker.dimension.equals(level.dimension().location().toString())){
            status(level,worker,"Шахтёр: неправильное измерение");return false;
        }
        if(!"work".equals(worker.order)&&!"wander".equals(worker.order)){
            status(level,worker,"Шахтёр: включи приказ Работать");return false;
        }

        UUID id;
        try { id=UUID.fromString(worker.workplaceId); }
        catch (IllegalArgumentException error) { status(level,worker,"Шахта не назначена");return false; }
        var businesses=KingdomEnterpriseData.get(level.getServer());
        var mine=businesses.find(id);
        var binding=businesses.core(id);
        if (mine==null || binding==null || !binding.active()
            || businesses.kind(id)!=EnterpriseKind.MINE
            || !mine.owner().equals(worker.employer)
            || !binding.dimension().equals(worker.dimension)) {status(level,worker,"Нет действующей шахты");return false;}
        BlockPos core=BlockPos.of(binding.packedPos());
        if (!level.hasChunkAt(core)){
            status(level,worker,"Шахтёр: чанк ядра шахты не загружен");return false;
        }
        int carried=KingdomMineCargo.minedCount(worker);
        // Construction stock is not finished ore. Deposit on cargo threshold,
        // and let the shared physical route bring the miner back to the depot.
        if (carried >= 64) {
            status(level,worker,"Доставка добычи на склад");
            return NpcFieldWork.deposit(level,npc,worker);
        }
        if (!NpcFieldWork.borrowTool(level,npc,worker)) {
            status(level,worker,"Шахтёр: идёт за киркой или кирки нет на складе");return false;
        }
        if(!(worker.fieldTool.is(ItemTags.PICKAXES)
                || worker.fieldTool.getItem() instanceof PickaxeItem)){
            status(level,worker,"Шахтёр: в руках не кирка; убери другой инструмент");return false;
        }

        var settings=KingdomMineSettings.get(level.getServer());
        var start=settings.start(id);
        var area=settings.shaft(id);
        boolean central=start==KingdomMineSettings.Start.DOWN;
        if(central && (area==null
                ||!area.dimension().equals(worker.dimension)
                ||!KingdomMineCentralShaftPlan.valid(area,core))) {
            status(level,worker,"Выдели центральный ствол 5×5–12×12 через ПКМ в меню NPC");
            return false;
        }
        // Forward keeps the legacy northern entrance; a central shaft can face
        // any cardinal side, provided the chosen area touches the core.
        BlockPos entrance=central?KingdomMineCentralShaftPlan.entrance(area,core):core.north();
        if (entrance==null || !level.hasChunkAt(entrance)
            || !level.getBlockState(entrance).isAir()
            || !level.getBlockState(entrance.above()).isAir()) {
            status(level,worker,"Освободи вход 1×2 между ядром и выделенным стволом");return false;
        }
        var anchors=central?java.util.List.<BlockPos>of()
            :KingdomMineLevelPlan.anchors(core,start);
        var excavation=central?KingdomMineCentralShaftPlan.cells(core,area)
            :KingdomMineLevelPlan.cells(core,start);
        for (var cell : excavation) {
            BlockPos floorCell=cell.target();
            BlockPos previous=cell.approach();
            if (!level.hasChunkAt(floorCell) || !level.hasChunkAt(previous)
                || !level.hasChunkAt(floorCell.below())
                || !level.hasChunkAt(previous.above())){
                status(level,worker,"Шахтёр: не загружен участок "+floorCell.toShortString());return false;
            }
            // Only horizontal moves or exactly one downward step are allowed.
            int deltaY=previous.getY()-floorCell.getY();
            if(deltaY<0 || deltaY>1 || previous.distManhattan(floorCell)!=1+deltaY){
                status(level,worker,"Шахтёр: ошибка геометрии маршрута "+floorCell.toShortString());return false;
            }
            // Do not enter a void, cave opening or flooded unsupported cell.
            if (level.getBlockState(floorCell.below()).isAir()
                || !level.getFluidState(floorCell.below()).isEmpty()){
                status(level,worker,"Шахтёр: нет устойчивого пола под "+floorCell.toShortString());return false;
            }
            if (!KingdomMineMaintenance.passable(level.getBlockState(previous))
                || !level.getBlockState(previous.above()).isAir()
                || level.getBlockState(previous.below()).isAir()
                || !level.getFluidState(previous).isEmpty()){
                status(level,worker,"Шахтёр: подход к выработке заблокирован у "+previous.toShortString());
                return false;
            }
            for (int height=0;height<2;height++) {
                BlockPos target=floorCell.above(height);
                if (KingdomMineMaintenance.passable(level.getBlockState(target))) {
                    if (!level.getFluidState(target).isEmpty()){
                        status(level,worker,"Шахтёр: обнаружена жидкость у "+target.toShortString());return false;
                    }
                    continue;
                }
                var supportSafety=KingdomMineSupportSafety.inspect(level,target);
                if(supportSafety==KingdomMineSupportSafety.Result.UNLOADED) {
                    status(level,worker,"Нужен загруженный участок шахты");
                    return false;
                }
                if(supportSafety==KingdomMineSupportSafety.Result.NEEDS_HORIZONTAL_BEAM) {
                    // Prepare physical support BEFORE removing collapse-risk rock,
                    // from the last accessible gallery cell. Fail closed if the
                    // current cavity cannot accommodate a native TFC frame.
                    var frame=KingdomMineSupportPlan.emergency(previous,floorCell);
                    if(frame!=null){
                        var preparation=KingdomMineSupportExcavation.tick(level,npc,worker,frame);
                        if(preparation==KingdomMineSupportExcavation.Result.EXCAVATED){
                            settings.markStarted(id);
                            status(level,worker,"Готовит безопасное крепление перед проходкой");
                            return true;
                        }
                        if(preparation==KingdomMineSupportExcavation.Result.WALKING){
                            status(level,worker,"Идёт укреплять опасный участок");
                            return false;
                        }
                        if(preparation==KingdomMineSupportExcavation.Result.CLEAR){
                            var support=KingdomMineSupportBuilder.tick(level,npc,worker,frame);
                            if(support==KingdomMineSupportBuilder.Result.SUPPLIED){
                                status(level,worker,"Взял балки TFC со склада, несёт к креплению");
                                return false;
                            }
                            if(support==KingdomMineSupportBuilder.Result.PLACED){
                                settings.markStarted(id);
                                status(level,worker,"Укрепляет участок перед добычей");
                                return true;
                            }
                            if(support==KingdomMineSupportBuilder.Result.WALKING){
                                status(level,worker,"Несёт крепления к опасному участку");
                                return false;
                            }
                            if(support==KingdomMineSupportBuilder.Result.NO_STOCK){
                                status(level,worker,"Нужны 8 балок TFC одной породы");
                                return false;
                            }
                            if(support==KingdomMineSupportBuilder.Result.INACCESSIBLE){
                                status(level,worker,"Нет доступа к месту установки креплений");
                                return false;
                            }
                            if(support==KingdomMineSupportBuilder.Result.NEED_CLEAR_POCKETS){
                                status(level,worker,"Крепление: нужны безопасные карманы под стойки");
                                return false;
                            }
                            if(support==KingdomMineSupportBuilder.Result.FAILED_NATIVE_PLACEMENT){
                                status(level,worker,"TFC отклонил установку опоры: проходка запрещена");
                                return false;
                            }
                            if(support==KingdomMineSupportBuilder.Result.COMPLETE
                                    &&KingdomMineSupportSafety.inspect(level,target)
                                        ==KingdomMineSupportSafety.Result.SAFE)
                                return false;
                        }
                    }
                    status(level,worker,"Опасная порода у "+target.toShortString()
                        +": "+(frame==null?"нет схемы опоры":
                            KingdomMineSupportExcavation.blocker(level,worker,frame)));
                    return false;
                }
                if (!safeToBreak(level,target,worker)) {
                    String material=BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString();
                    status(level,worker,"Шахтёр: нельзя копать "+material+" у "+target.toShortString()
                        +" (территория, инструмент или опасная порода)");
                    return false;
                }
                if (npc.distanceToSqr(previous.getX()+0.5,
                    previous.getY(),previous.getZ()+0.5)>REACH_SQR) {
                    if(!npc.getNavigation().moveTo(previous.getX()+0.5,
                        previous.getY(),previous.getZ()+0.5,0.75))
                        status(level,worker,"Нет пути к "+previous.toShortString());
                    else status(level,worker,"Идёт к месту добычи");
                    return false;
                }
                BlockState state=level.getBlockState(target);
                var drops=Block.getDrops(state,level,target,level.getBlockEntity(target),
                    npc,worker.fieldTool);
                if (!NpcFieldWork.canFit(worker,drops)) {status(level,worker,"Сумка переполнена");return false;}
                if (!level.setBlock(target,Blocks.AIR.defaultBlockState(),3)){
                    status(level,worker,"Шахтёр: Minecraft отклонил разрушение "+target.toShortString());
                    return false;
                }
                settings.markStarted(id);
                for (ItemStack stack:drops) NpcFieldWork.put(worker,stack);
                worker.fieldTool.hurt(1,level.random,null);
                if (worker.fieldTool.isEmpty() || worker.fieldTool.getDamageValue()
                    >=worker.fieldTool.getMaxDamage()) worker.fieldTool=ItemStack.EMPTY;
                status(level,worker,"Добывает породу на глубине Y="+target.getY());
                worker.fieldHarvested+=drops.stream().mapToInt(ItemStack::getCount).sum();
                worker.skillXp++;
                worker.energy=Math.max(0,worker.energy-1);
                KingdomNpcData.get(level.getServer()).setDirty();
                return true;
            }
            // Every completed shaft landing requires a REAL warehouse torch
            // before excavating the connected room or descending further.
            int depth=central?area.surfaceY()-floorCell.getY():0;
            if(central && previous.getY()==floorCell.getY()+1
                && (depth==3||depth==6||depth==10)) {
                var lighting=KingdomMineMaintenance.lightAtCheckpoint(
                    level,npc,worker,floorCell);
                if(lighting==KingdomMineMaintenance.Result.PLACED) {
                    settings.markStarted(id);
                    status(level,worker,"Осветил центральный ствол на глубине "+depth);
                    return true;
                }
                if(lighting!=KingdomMineMaintenance.Result.READY) {
                    status(level,worker,switch(lighting) {
                        case NO_SUPPLIES -> "Нужны факелы для ствола шахты";
                        case WALKING_TO_SUPPLIES -> "Идёт за факелами для ствола";
                        case WALKING_TO_SITE -> "Несёт факел на нижний уровень";
                        default -> "Не удаётся осветить нижний уровень";
                    });
                    return false;
                }
            }
            // Build a real TFC support frame every five cells. The native
            // TFC item controls column/link creation. On any failure we
            // stop rather than allowing unsupported excavation.
            BlockPos galleryAnchor=null;
            for(BlockPos candidate:anchors)
                if(candidate.getY()==floorCell.getY())galleryAnchor=candidate;
            if(central && (depth==3||depth==6||depth==10)
                && KingdomMineCentralShaftPlan.beyondGallery(area,core,floorCell))
                galleryAnchor=KingdomMineCentralShaftPlan.galleryAnchor(area,core,floorCell.getY());
            if(galleryAnchor!=null) {
                var galleryDirection=central
                    ?KingdomMineCentralShaftPlan.entryDirection(area,core)
                    :net.minecraft.core.Direction.NORTH;
                for(var frame:KingdomMineSupportPlan.frames(galleryAnchor,galleryDirection)) {
                    if(frame.walkway().equals(floorCell)) {
                        var prepare=KingdomMineSupportExcavation.tick(level,npc,worker,frame);
                        if(prepare==KingdomMineSupportExcavation.Result.EXCAVATED) {
                            settings.markStarted(id);
                            status(level,worker,"Расчищает место для опор TFC");
                            return true;
                        }
                        if(prepare==KingdomMineSupportExcavation.Result.WALKING) {
                            status(level,worker,"Идёт к месту крепления");
                            return false;
                        }
                        if(prepare==KingdomMineSupportExcavation.Result.UNSAFE) {
                            status(level,worker,"Небезопасно расширять штрек: нужны опоры или свободная порода");
                            return false;
                        }
                        var build=KingdomMineSupportBuilder.tick(level,npc,worker,frame);
                        if(build==KingdomMineSupportBuilder.Result.SUPPLIED) {
                            status(level,worker,"Получил настоящие балки TFC на складе");
                            return false;
                        }
                        if(build==KingdomMineSupportBuilder.Result.PLACED) {
                            settings.markStarted(id);
                            status(level,worker,"Укрепляет шахту настоящими балками TFC");
                            return true;
                        }
                        if(build!=KingdomMineSupportBuilder.Result.COMPLETE) {
                            status(level,worker,switch(build) {
                                case WALKING -> "Несёт балки TFC к месту крепления";
                                case NO_STOCK -> "Нужны балки TFC (минимум 8 одинаковых) на складе";
                                case NEED_CLEAR_POCKETS -> "Расчисти боковые карманы 3×3 для креплений";
                                case INACCESSIBLE -> "Нет прав или чанк креплений не загружен";
                                default -> "TFC не установил опору: проверь место и материалы";
                            });
                            return false;
                        }
                    }
                }
            }
            // Light a fully excavated section before starting the next cell.
            // Stock and navigation are real: no torches = no further advance.
            BlockPos lightAnchor=galleryAnchor;
            if(KingdomMineMaintenance.lightPoint(lightAnchor,floorCell)) {
                var lighting=KingdomMineMaintenance.light(level,npc,worker,lightAnchor,floorCell);
                if(lighting==KingdomMineMaintenance.Result.PLACED) {
                    settings.markStarted(id);
                    status(level,worker,"Установил освещение шахты");
                    return true;
                }
                if(lighting!=KingdomMineMaintenance.Result.READY) {
                    status(level,worker,switch(lighting){
                        case NO_SUPPLIES -> "Нужны факелы на складе сырья";
                        case WALKING_TO_SUPPLIES -> "Идёт за факелами";
                        case WALKING_TO_SITE -> "Несёт факелы в шахту";
                        default -> "Не удаётся осветить выработку";
                    });
                    return false;
                }
            }
        }
        if(KingdomMineCargo.minedCount(worker)>0) {
            status(level,worker,"Все выработки пройдены. Возвращает последнюю добычу на склад");
            return NpcFieldWork.deposit(level,npc,worker);
        }
        status(level,worker,"Все доступные выработки пройдены; руда доставлена");
        return false;
    }
}
