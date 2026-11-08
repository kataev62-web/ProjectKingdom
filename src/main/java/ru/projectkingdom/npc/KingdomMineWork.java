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

    static boolean safeToBreak(ServerLevel level, BlockPos pos,
                                       KingdomNpcData.Worker worker) {
        if (!level.hasChunkAt(pos) || pos.getY() <= level.getMinBuildHeight() + 1
            || pos.getY() >= level.getMaxBuildHeight() - 3
            || !KingdomTerritories.get(level.getServer()).canUse(level,pos,worker.employer))
            return false;
        BlockState state=level.getBlockState(pos);
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        var needed=KingdomMineTools.required(state);
        if (needed==KingdomMineTools.Tool.BLOCKED
            || !state.getFluidState().isEmpty() || level.getBlockEntity(pos)!=null
            || state.getDestroySpeed(level,pos)<0) return false;
        if (!KingdomMineTools.matches(worker.fieldTool,needed)
            || !worker.fieldTool.isCorrectToolForDrops(state)) return false;
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
        if (!"miner".equals(worker.job) || worker.employer==null
            || worker.contractPaused || !KingdomNpcWorkday.employed(level.getServer(),worker)
            || !worker.hasSource || !worker.hasDestination
            || !worker.dimension.equals(level.dimension().location().toString())
            || !"work".equals(worker.order) && !"wander".equals(worker.order))
            return false;

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
        if (!level.hasChunkAt(core)) return false;
        int carried=KingdomMineCargo.minedCount(worker);
        // Construction stock is not finished ore. Deposit on cargo threshold,
        // and let the shared physical route bring the miner back to the depot.
        if (carried >= 64) {
            status(level,worker,"Доставка добычи на склад");
            return NpcFieldWork.deposit(level,npc,worker);
        }
        // Central shaft selects shovel or pickaxe from the actual target block.
        // Requiring a pickaxe first would block a soil-only start with a shovel.
        if(KingdomMineSettings.get(level.getServer()).start(id)
                ==KingdomMineSettings.Start.FORWARD
            &&!NpcFieldWork.borrowTool(level,npc,worker)) {
            status(level,worker,"Нужна исправная кирка на складе сырья");
            return false;
        }

        var settings=KingdomMineSettings.get(level.getServer());
        var start=settings.start(id);
        var area=settings.shaft(id);
        boolean central=start==KingdomMineSettings.Start.DOWN;
        BlockPos entrance=central && area!=null
            ?KingdomMineCentralShaftPlan.entrance(area):core.north();
        if(!level.hasChunkAt(entrance)
            ||!level.getBlockState(entrance).isAir()
            ||!level.getBlockState(entrance.above()).isAir()
            ||level.getBlockState(entrance.below()).isAir()) {
            status(level,worker,"Нужен свободный вход 1×2 с опорой под ногами: "
                +entrance.toShortString());
            return false;
        }
        if(central && (area==null
                ||!area.dimension().equals(worker.dimension)
                ||!KingdomMineCentralShaftPlan.valid(area,core))) {
            status(level,worker,"Выдели ствол 7×7–12×12 через блок предприятия «Шахта»");
            return false;
        }
        var anchors=central?java.util.List.<BlockPos>of()
            :KingdomMineLevelPlan.anchors(core,start);
        int requestedLevels=settings.levels(id);
        var heading=settings.heading(id);
        var excavation=central?KingdomMineCentralShaftPlan.cells(
            core,area,requestedLevels,heading):KingdomMineLevelPlan.cells(core,start);
        for (var cell : excavation) {
            BlockPos floorCell=cell.target();
            BlockPos previous=cell.approach();
            if (!level.hasChunkAt(floorCell) || !level.hasChunkAt(previous)
                || !level.hasChunkAt(floorCell.below())
                || !level.hasChunkAt(previous.above())) return false;
            // Only horizontal moves or exactly one downward step are allowed.
            int deltaY=previous.getY()-floorCell.getY();
            if(deltaY<0 || deltaY>1 || previous.distManhattan(floorCell)!=1+deltaY)
                return false;
            // Do not enter a void, cave opening or flooded unsupported cell.
            if (level.getBlockState(floorCell.below()).isAir()
                || !level.getFluidState(floorCell.below()).isEmpty()) return false;
            if (!KingdomMineMaintenance.passable(level.getBlockState(previous))
                || !level.getBlockState(previous.above()).isAir()
                || level.getBlockState(previous.below()).isAir()
                || !level.getFluidState(previous).isEmpty()) return false;
            // Finish the central shaft and ladder route FIRST. Once an inner
            // chamber is clear, build its two-wide wooden deck BEFORE extending
            // that level's outward ore gallery.
            if(central) {
                int currentDepth=area.surfaceY()-floorCell.getY();
                if(KingdomMineStructurePlan.isLevel(currentDepth)
                    && currentDepth<=requestedLevels*KingdomMineSettings.LEVEL_INTERVAL
                    && floorCell.equals(KingdomMineCentralShaftPlan.galleryEnd(
                        area,currentDepth,heading).relative(
                            heading.direction().getOpposite(),
                            KingdomMineCentralShaftPlan.GALLERY_LENGTH-1))) {
                    var deck=KingdomMineStructureWork.floorAt(
                        level,npc,worker,area,currentDepth,core,requestedLevels,heading);
                    if(deck==KingdomMineStructureWork.Result.BUILT) {
                        settings.markStarted(id);
                        status(level,worker,"Строит настил горизонта "+currentDepth);
                        return true;
                    }
                    if(deck!=KingdomMineStructureWork.Result.READY) {
                        status(level,worker,switch(deck) {
                            case NEED_MATERIAL -> "Нужны доски для настила";
                            case NEED_TOOL -> "Нужна лопата или кирка для настила";
                            case WALKING -> "Идёт укладывать настил";
                            default -> "Нельзя безопасно завершить настил";
                        });
                        return false;
                    }
                }
            }
            for (int height=0;height<2;height++) {
                BlockPos target=floorCell.above(height);
                if (KingdomMineMaintenance.passable(level.getBlockState(target))) {
                    if (!level.getFluidState(target).isEmpty()) return false;
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
                    status(level,worker,"Опасная порода: нет безопасного места для установки опор TFC");
                    return false;
                }
                var needed=KingdomMineTools.required(level.getBlockState(target));
                if(needed==KingdomMineTools.Tool.BLOCKED){
                    status(level,worker,"Неподходящий блок для проходки");
                    return false;
                }
                if(!KingdomMineTools.equip(level,npc,worker,needed)){
                    status(level,worker,needed==KingdomMineTools.Tool.SHOVEL
                        ?"Нужна лопата для грунта на складе сырья"
                        :"Нужна кирка для камня на складе сырья");
                    return false;
                }
                if (!safeToBreak(level,target,worker)) {status(level,worker,"Проходка остановлена: опасная или защищённая порода");return false;}
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
                if (!level.setBlock(target,Blocks.AIR.defaultBlockState(),3)) return false;
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
            // At each two-block descent checkpoint build a real ladder, then
            // lay the three-block perimeter deck after its level is excavated.
            int depth=central?area.surfaceY()-floorCell.getY():0;
            if(central && deltaY==1 && KingdomMineStructurePlan.requiresLadder(depth)){
                var built=KingdomMineStructureWork.ladderAt(level,npc,worker,area,floorCell);
                if(built==KingdomMineStructureWork.Result.BUILT){
                    settings.markStarted(id);
                    status(level,worker,"Устанавливает лестницу на глубине "+depth);
                    return true;
                }
                if(built!=KingdomMineStructureWork.Result.READY){
                    status(level,worker,built==KingdomMineStructureWork.Result.NEED_MATERIAL
                        ?"Нужны лестницы на складе сырья"
                        :"Ожидает безопасную установку лестницы");
                    return false;
                }
            }
            // Ladder occupies the descent cell, so no floor torch is placed
            // in that same block. Gallery lighting remains independent.
            // Build a real TFC support frame every five cells. The native
            // TFC item controls column/link creation. On any failure we
            // stop rather than allowing unsupported excavation.
            BlockPos galleryAnchor=null;
            for(BlockPos candidate:anchors)
                if(candidate.getY()==floorCell.getY())galleryAnchor=candidate;
            if(central && KingdomMineStructurePlan.isLevel(depth)
                && depth<=requestedLevels*KingdomMineSettings.LEVEL_INTERVAL)
                galleryAnchor=KingdomMineCentralShaftPlan.galleryEnd(
                    area,depth,heading).relative(
                        heading.direction().getOpposite(),
                        KingdomMineCentralShaftPlan.GALLERY_LENGTH+1);
            if(galleryAnchor!=null) {
                for(var frame:central
                    ?KingdomMineSupportPlan.frames(galleryAnchor,
                        heading.direction(),KingdomMineCentralShaftPlan.GALLERY_LENGTH)
                    :KingdomMineSupportPlan.frames(galleryAnchor)) {
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
                            // This is a planned reinforcement checkpoint,
                            // not an unsafe target. Real collapse-risk targets
                            // were handled by inspect() before breaking.
                            // Do not deadlock a safe gallery merely because
                            // optional post pockets cannot be cut.
                            status(level,worker,"Карманы опор недоступны; продолжает безопасную проходку");
                            continue;
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
                            // Only real collapse hazards mandate TFC supports.
                            // A scheduled but physically impossible frame is
                            // optional until the next block actually requires it.
                            if(build==KingdomMineSupportBuilder.Result.NEED_CLEAR_POCKETS
                                ||build==KingdomMineSupportBuilder.Result.FAILED_NATIVE_PLACEMENT
                                ||build==KingdomMineSupportBuilder.Result.INACCESSIBLE
                                ||build==KingdomMineSupportBuilder.Result.NO_STOCK) {
                                status(level,worker,"Крепление пока недоступно; продолжает безопасную добычу");
                                continue;
                            }
                            status(level,worker,"Несёт балки TFC к месту крепления");
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
