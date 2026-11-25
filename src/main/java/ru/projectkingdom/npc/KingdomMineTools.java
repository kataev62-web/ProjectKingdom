package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.state.BlockState;

/** Physical shovel/pickaxe selection, without generating tools. */
public final class KingdomMineTools {
    private KingdomMineTools() {}

    public enum Tool { SHOVEL, PICKAXE, BLOCKED }

    public static Tool required(BlockState state) {
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(id.equals("minecraft:dirt")||id.equals("minecraft:grass_block")
            ||id.equals("minecraft:coarse_dirt")||id.equals("minecraft:rooted_dirt")
            ||id.equals("minecraft:gravel")||id.equals("minecraft:sand")
            ||id.equals("minecraft:red_sand")||id.equals("minecraft:clay")
            ||id.equals("minecraft:mud")||id.startsWith("tfc:dirt/")
            ||id.startsWith("tfc:grass/")||id.startsWith("tfc:clay/")
            ||id.startsWith("tfc:gravel/")||id.startsWith("tfc:sand/")
            ||id.startsWith("tfc:soil/"))return Tool.SHOVEL;
        return KingdomMineWork.naturalRock(id)?Tool.PICKAXE:Tool.BLOCKED;
    }

    public static boolean matches(ItemStack stack,Tool needed) {
        if(stack==null||stack.isEmpty())return false;
        return switch(needed) {
            case SHOVEL -> stack.getItem() instanceof ShovelItem
                || stack.is(ItemTags.SHOVELS);
            case PICKAXE -> stack.getItem() instanceof PickaxeItem
                || stack.is(ItemTags.PICKAXES);
            case BLOCKED -> false;
        };
    }

    /**
     * Changes the real equipment of the resident.
     *
     * <p>The tool is never copied: the one of the working view is moved into
     * the hand of the view and the old one goes into the cargo. A tool that
     * comes from the depot is only taken away when the real bag of the
     * resident really accepts it; otherwise it goes back into the depot.
     */
    public static boolean equip(ServerLevel level,Villager npc,
            KingdomNpcData.Worker worker,Tool needed) {
        if(needed==Tool.BLOCKED)return false;
        // The tool that the resident already carries is moved, never copied.
        if(selectInView(worker,needed))return true;
        if(worker.employer==null||!worker.hasSource)return false;
        BlockPos depot=new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
        if(!level.hasChunkAt(depot)
            ||!KingdomTerritories.get(level.getServer())
                .usable(level,depot,worker.employer,"raw"))return false;
        Container storage=KingdomContainerAccess.resolve(level,depot);
        if(storage==null)return false;
        int slot=-1;
        for(int i=0;i<storage.getContainerSize();i++){
            ItemStack stack=storage.getItem(i);
            if(matches(stack,needed)&&storage.canTakeItem(storage,i,stack)){
                slot=i;break;
            }
        }
        if(slot<0)return false;
        if(npc.distanceToSqr(depot.getX()+.5,depot.getY(),depot.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(depot.getX()+.5,depot.getY(),depot.getZ()+.5,.75);
            return false;
        }
        // The bag must have room before the depot is touched at all.
        Container personal=KingdomMcaAdapter.isResident(npc)
            ?KingdomResidentInventory.personal(npc):null;
        if(personal!=null&&!KingdomResidentInventory.hasRoom(personal,storage.getItem(slot)))
            return false;
        ItemStack selected=storage.removeItem(slot,1);
        if(!matches(selected,needed)){
            if(!selected.isEmpty())NpcFieldWork.put(worker,selected);
            storage.setChanged();
            return false;
        }
        // The bound work view owns changes until the lifecycle flush. Inserting
        // into the physical bag and syncing here would discard prior work in
        // this tick (including drops and the wear of the previous tool).
        if(!worker.fieldTool.isEmpty())NpcFieldWork.put(worker,worker.fieldTool);
        worker.fieldTool=selected;
        storage.setChanged();
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }

    /**
     * Moves the tool of the needed kind into the hand of the working view.
     *
     * <p>The old tool goes into the cargo, so that the resident keeps both
     * tools and no item is ever duplicated.
     */
    public static boolean selectInView(KingdomNpcData.Worker worker,Tool needed) {
        if(worker==null)return false;
        if(matches(worker.fieldTool,needed))return true;
        for(int i=0;i<worker.fieldInventory.size();i++){
            ItemStack candidate=worker.fieldInventory.get(i);
            if(candidate==null||candidate.isEmpty())continue;
            if(!matches(candidate,needed))continue;
            ItemStack selected=worker.fieldInventory.remove(i);
            if(!worker.fieldTool.isEmpty())worker.fieldInventory.add(worker.fieldTool);
            worker.fieldTool=selected;
            return true;
        }
        return false;
    }
}
