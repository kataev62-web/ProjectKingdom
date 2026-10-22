package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Stage 1 physical NPC workshop: explicit 3x3 recipe, real source and destination,
 * bounded work time, minimum/maximum stock, real employee and optional local purchasing.
 * Not a simulation of TFC anvil/forge processes: those need separate adapters.
 */
public final class KingdomNpcWorkshop {
    private KingdomNpcWorkshop() {}
    private static final int WORK_RANGE=6;
    private static final int MIN_WORK_TICKS=200;

    /** Stand within 6 blocks of the worker's actual workbench; lay out ONE valid recipe
     * in the first nine slots of its RAW container (row-major), then click NPC menu slot 9.
     * Existing resources are not removed when the production plan is registered.
     */
    public static String configure(ServerPlayer owner, KingdomNpcData.Worker w){
        if(w==null||w.employer==null||!w.employer.equals(owner.getUUID())
                ||!w.hasSource||!w.hasDestination||!w.hasWorkstation)return "Назначь предприятие, оба склада и рабочий стол.";
        var e=KingdomEnterpriseData.get(owner.getServer()).owned(owner.getUUID());
        if(e==null)return "Предприятие не выбрано.";
        ServerLevel level=owner.serverLevel();
        if(!w.dimension.equals(level.dimension().location().toString()))return "Работник в другом измерении.";
        var desk=new BlockPos(w.workX,w.workY,w.workZ);
        if(desk.distSqr(owner.blockPosition())>36)return "Подойди к рабочему столу.";
        Container from=source(level,w,e.owner()),to=destination(level,w,e.owner());
        if(!bench(level,w,e.owner()) || from==null || to==null || from==to)return "Нет настоящего стола, складов или разрешения на участок.";
        var job=KingdomEnterprisePayrollData.get(owner.getServer()).contract(e.id(),w.entityId);
        if(job==null || !job.active())return "Сначала закрепи работника за выбранным предприятием.";
        var grid=layout(from,null);
        if(grid==null)return "В сундуке сырья должно быть минимум 9 ячеек.";
        var recipe=recipe(level,grid);if(recipe==null)return "Первые девять ячеек не образуют реальный рецепт верстака.";
        ItemStack output=recipe.assemble(grid,level.registryAccess());
        if(output.isEmpty() || output.getCount()<1 || output.getCount()>64 || money(output))
            return "Этот рецепт не поддержан или его выпуск нарушает денежный баланс.";
        List<String> ingredients=new ArrayList<>();
        for(int i=0;i<9;i++){
            var stack=grid.getItem(i);
            ingredients.add(stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        String target=BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
        var state=KingdomNpcWorkshopData.get(owner.getServer());
        state.configure(new KingdomNpcWorkshopData.Plan(w.entityId,e.id(),recipe.getId().toString(),target,
                ingredients,4,32,MIN_WORK_TICKS));
        return "План записан: "+target+" (склад 4–32, цикл 200 тиков). Реальные материалы обязательны.";
    }

    private static boolean money(ItemStack stack){
        return KingdomCurrency.value(stack)>0 || stack.is(KingdomCurrency.WALLET.get());
    }
    private static boolean bench(ServerLevel level,KingdomNpcData.Worker w,UUID owner){
        BlockPos p=new BlockPos(w.workX,w.workY,w.workZ);
        var t=KingdomTerritories.get(level.getServer());
        return level.hasChunkAt(p) && t.usable(level,p,owner,"workstation")
                && level.getBlockState(p).is(Blocks.CRAFTING_TABLE);
    }
    private static Container source(ServerLevel level,KingdomNpcData.Worker w,UUID owner){
        return box(level,new BlockPos(w.sourceX,w.sourceY,w.sourceZ),owner,"raw");
    }
    private static Container destination(ServerLevel level,KingdomNpcData.Worker w,UUID owner){
        return box(level,new BlockPos(w.destX,w.destY,w.destZ),owner,"finished");
    }
    private static Container box(ServerLevel level,BlockPos p,UUID owner,String role){
        if(!level.hasChunkAt(p) || !KingdomTerritories.get(level.getServer()).usable(level,p,owner,role))return null;
        BlockEntity e=level.getBlockEntity(p);return e instanceof Container c?c:null;
    }
    // Minecraft's own recipe lookup. No arbitrary item->item conversion tables.
    private static CraftingContainer layout(Container from,List<String> blueprint){
        if(from.getContainerSize()<9)return null;
        CraftingContainer grid=new TransientCraftingContainer(new AbstractContainerMenu(null,-1){
            @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
            @Override public boolean stillValid(Player p){return false;}
        },3,3);
        for(int i=0;i<9;i++){
            var stack=from.getItem(i);
            if(money(stack))return null;
            if(blueprint!=null){
                String needed=blueprint.get(i);
                if(needed.isBlank()){if(!stack.isEmpty())return null;}
                else if(stack.isEmpty() || !needed.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))return null;
            }
            var one=stack.copy();if(!one.isEmpty())one.setCount(1);
            grid.setItem(i,one);
        }
        return grid;
    }
    private static CraftingRecipe recipe(ServerLevel level,CraftingContainer grid){
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElse(null);
    }
    private static int count(Container c,String id){
        int n=0;
        for(int i=0;i<c.getContainerSize();i++){
            var stack=c.getItem(i);
            if(!stack.isEmpty()&&id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))n+=stack.getCount();
        }return n;
    }
    private static List<ItemStack> copy(Container c){
        var items=new ArrayList<ItemStack>();for(int i=0;i<c.getContainerSize();i++)items.add(c.getItem(i).copy());return items;
    }
    private static boolean insert(List<ItemStack> items,Container container,ItemStack item){
        ItemStack left=item.copy();if(left.isEmpty())return true;
        for(int i=0;i<items.size()&&!left.isEmpty();i++){
            var slot=items.get(i);
            if(!slot.isEmpty() && ItemStack.isSameItemSameTags(slot,left) && container.canPlaceItem(i,left)){
                int quantity=Math.min(left.getCount(),Math.max(0,Math.min(slot.getMaxStackSize(),container.getMaxStackSize())-slot.getCount()));
                slot.grow(quantity);left.shrink(quantity);
            }
        }
        for(int i=0;i<items.size()&&!left.isEmpty();i++){
            if(!items.get(i).isEmpty()||!container.canPlaceItem(i,left))continue;
            int quantity=Math.min(left.getCount(),Math.min(left.getMaxStackSize(),container.getMaxStackSize()));
            if(quantity<1)continue;
            var slot=left.copy();slot.setCount(quantity);items.set(i,slot);left.shrink(quantity);
        }
        return left.isEmpty();
    }
    /** Refill configured crafting slots only using REAL spare stock from slots >=9.
     * No consumption/output happens if a configured ingredient is unavailable.
     */
    private static boolean refill(Container raw,List<String> pattern){
        if(raw.getContainerSize()<9)return false;
        List<ItemStack> prepared=copy(raw);
        boolean changed=false;
        for(int i=0;i<9;i++){
            String item=pattern.get(i);
            if(item.isBlank()){
                if(!prepared.get(i).isEmpty())return false;
                continue;
            }
            if(!prepared.get(i).isEmpty()){
                if(!item.equals(BuiltInRegistries.ITEM.getKey(prepared.get(i).getItem()).toString()))return false;
                continue;
            }
            boolean found=false;
            for(int j=9;j<prepared.size();j++){
                var stack=prepared.get(j);
                if(stack.isEmpty()||!item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                        ||!raw.canTakeItem(raw,j,stack)||!raw.canPlaceItem(i,stack))continue;
                var one=stack.copy();one.setCount(1);stack.shrink(1);prepared.set(i,one);
                found=true;changed=true;break;
            }
            if(!found)return false; // No partial mutation: procurement must refill missing items first.
        }
        if(changed){for(int i=0;i<prepared.size();i++)raw.setItem(i,prepared.get(i));raw.setChanged();}
        return true;
    }
    /** Craft only after WORK TIME has elapsed. Validates inputs, recipe, byproducts and
     * destination capacity on copies before moving any physical inventory.
     */
    private static boolean complete(ServerLevel level,Container raw,Container output,KingdomNpcWorkshopData.Plan p){
        CraftingContainer grid=layout(raw,p.grid());if(grid==null)return false;
        CraftingRecipe r=recipe(level,grid);
        if(r==null||!r.getId().toString().equals(p.recipe()))return false;
        var made=r.assemble(grid,level.registryAccess());
        if(made.isEmpty()||!p.output().equals(BuiltInRegistries.ITEM.getKey(made.getItem()).toString()))return false;
        if(money(made))return false;
        var leftovers=r.getRemainingItems(grid);
        // A datapack recipe must never mint coins or wallets through container remainders.
        // The main output is checked above; remainder items need the same protection.
        for(ItemStack remainder:leftovers){
            if(!remainder.isEmpty() && money(remainder))return false;
        }
        List<ItemStack> input=copy(raw),dest=copy(output);
        for(int i=0;i<9;i++){
            ItemStack held=input.get(i);
            if(!held.isEmpty()){
                if(!raw.canTakeItem(raw,i,held))return false;
                held.shrink(1);
            }
        }
        for(int i=0;i<Math.min(9,leftovers.size());i++){
            ItemStack remainder=leftovers.get(i);
            if(!remainder.isEmpty() && !insert(input,raw,remainder))return false;
        }
        if(!insert(dest,output,made))return false;
        for(int i=0;i<input.size();i++)raw.setItem(i,input.get(i));
        for(int i=0;i<dest.size();i++)output.setItem(i,dest.get(i));
        raw.setChanged();output.setChanged();return true;
    }
    /** Main worker loop: runs only for actual nearby loaded villagers and on a low-frequency tick. */
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        var data=KingdomNpcWorkshopData.get(level.getServer());
        var p=data.plan(w.entityId);
        if(p==null)return false;
        var firm=KingdomEnterpriseData.get(level.getServer()).find(p.enterprise());
        var contract=KingdomEnterprisePayrollData.get(level.getServer()).contract(p.enterprise(),w.entityId);
        if(firm==null||!firm.owner().equals(w.employer) || contract==null || !contract.active()
                ||w.contractPaused||!"work".equals(w.order)||!"smith".equals(w.job)
                ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
                ||!w.dimension.equals(level.dimension().location().toString())||!w.hasWorkstation
                ||!w.hasSource||!w.hasDestination){data.clear(w.entityId);return false;}
        BlockPos station=new BlockPos(w.workX,w.workY,w.workZ);
        if(!bench(level,w,firm.owner()) || !level.hasChunkAt(station))return false;
        // A worker with earned debt visits the ACTUAL company cash desk, if loaded and
        // reasonably close. Funds never leave the employer until the villager arrives.
        long due=KingdomEnterprisePayrollData.get(level.getServer()).accrue(level.getServer(),p.enterprise(),w.entityId);
        var salaryDesk=KingdomEnterpriseData.get(level.getServer()).counter(p.enterprise());
        if(due>0 && firm.cash()>0 && salaryDesk!=null
                && salaryDesk.dimension().equals(w.dimension)&&level.hasChunkAt(salaryDesk.pos())
                && station.distSqr(salaryDesk.pos())<=1024
                && KingdomTerritories.get(level.getServer()).canUse(level,salaryDesk.pos(),firm.owner())
                && level.getBlockEntity(salaryDesk.pos()) instanceof Container) {
            if(npc.distanceToSqr(salaryDesk.pos().getX()+0.5,salaryDesk.pos().getY()+0.5,
                    salaryDesk.pos().getZ()+0.5)>9){
                npc.getNavigation().moveTo(salaryDesk.pos().getX()+0.5,
                    salaryDesk.pos().getY(),salaryDesk.pos().getZ()+0.5,0.75);
                return false;
            }
            KingdomEnterprisePayrollData.get(level.getServer()).claimNpc(level.getServer(),w);
        }
        if(station.distSqr(new BlockPos(w.sourceX,w.sourceY,w.sourceZ))>36 ||
           station.distSqr(new BlockPos(w.destX,w.destY,w.destZ))>36)return false;
        Container raw=source(level,w,firm.owner()),finished=destination(level,w,firm.owner());
        if(raw==null||finished==null||raw==finished)return false;
        if(npc.distanceToSqr(station.getX()+0.5,station.getY()+0.5,station.getZ()+0.5)>6.25){
            npc.getNavigation().moveTo(station.getX()+0.5,station.getY(),station.getZ()+0.5,0.75);return false;
        }
        int stocked=count(finished,p.output());
        if(stocked>=p.high()){data.setEnabled(w.entityId,false);data.clear(w.entityId);return false;}
        if(!data.enabled(w.entityId)){
            if(stocked>p.low())return false;
            data.setEnabled(w.entityId,true);
        }
        if(!refill(raw,p.grid())){
            data.clear(w.entityId);
            // The owner must fund their real company and make an offer available locally.
            procureLocal(level,npc,w,p,raw);
            return false;
        }
        CraftingContainer grid=layout(raw,p.grid());if(grid==null){data.clear(w.entityId);return false;}
        CraftingRecipe recipe=recipe(level,grid);
        if(recipe==null||!p.recipe().equals(recipe.getId().toString())){data.clear(w.entityId);return false;}
        ItemStack product=recipe.assemble(grid,level.registryAccess());
        if(product.isEmpty()||money(product)||!p.output().equals(BuiltInRegistries.ITEM.getKey(product.getItem()).toString())
                ||stocked>p.high()-product.getCount()){data.clear(w.entityId);return false;}
        long now=level.getGameTime();var stage=data.progress(w.entityId);
        if(stage==null){data.start(w.entityId,p.recipe(),now+p.duration());return false;}
        if(now<stage.readyAt())return false;
        if(!complete(level,raw,finished,p)){data.clear(w.entityId);return false;}
        // Advertisement names real, loaded workshop storage; no goods or money appear here.
        KingdomEnterpriseData.get(level.getServer()).publishWorkshop(level.getServer(),w.entityId,p.enterprise(),p.salePrice());
        data.clear(w.entityId);
        w.workUnits++;w.produced+=product.getCount();w.skillXp++;
        w.energy=Math.max(0,w.energy-1);
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
    /** Bounded local purchase. Buyer is a real employed NPC in reach of BOTH company counters.
     * This is counter pickup, NOT a long-distance courier service. Supplier must publish an offer.
     */
    private static void procureLocal(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
            KingdomNpcWorkshopData.Plan p,Container raw){
        var businesses=KingdomEnterpriseData.get(level.getServer());
        var buyerDesk=businesses.counter(p.enterprise());
        BlockPos supply=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(buyerDesk==null||!buyerDesk.pos().equals(supply)||
                !buyerDesk.dimension().equals(w.dimension))return;
        for(String item:p.grid()){
            if(item.isBlank())continue;
            int needed=0;for(String slot:p.grid())if(item.equals(slot))needed++;
            if(count(raw,item)>=Math.max(needed,4))continue;
            for(var offer:businesses.offers()){
                if(!offer.itemId().equals(item)||offer.seller().equals(p.enterprise()))continue;
                var sellerDesk=businesses.counter(offer.seller());
                if(sellerDesk==null||!sellerDesk.dimension().equals(w.dimension)||
                        sellerDesk.pos().distSqr(supply)>25)continue;
                if(businesses.buyForNpc(level,w.entityId,p.enterprise(),offer.id(),Math.min(8,Math.max(needed,4)-count(raw,item))))return;
            }
        }
    }
}
