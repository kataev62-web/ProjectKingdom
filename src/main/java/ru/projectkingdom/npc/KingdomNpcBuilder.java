package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A builder walks to the site and consumes one real item per placed block.
 *  No unloaded chunks are generated and no material is created by the simulation.
 */
public final class KingdomNpcBuilder {
    private KingdomNpcBuilder() {}

    public static void tick(MinecraftServer server) {
        KingdomConstructionData sites=KingdomConstructionData.get(server);
        KingdomNpcData workers=KingdomNpcData.get(server);
        for(var site:sites.all()) {
            if(site.status()!=KingdomConstructionData.Status.WAITING_MATERIALS
                && site.status()!=KingdomConstructionData.Status.BUILDING)continue;
            var type=KingdomBuildingTypes.type(site.design());
            if(type==null)continue;
            var plan=KingdomBuildingTypes.design(type).blocks();
            int index=sites.progress(site.id());
            if(index>=plan.size())continue;
            ServerLevel level=null;
            for(var candidate:server.getAllLevels())
                if(candidate.dimension().location().toString().equals(site.dimension())){level=candidate;break;}
            if(level==null)continue;
            BlockPos lectern=new BlockPos(site.x()-2,site.y(),site.z()-2);
            if(!level.hasChunkAt(lectern)||!level.getBlockState(lectern).is(Blocks.LECTERN))continue;
            // A normal chest or barrel immediately beside the construction lectern is the supply depot.
            Container supplies=findSupplies(level,lectern);
            if(supplies==null)continue;
            Villager builder=null;
            for(var worker:workers.all()) {
                if (!site.owner().equals(worker.employer)
                        || !site.dimension().equals(worker.dimension)
                        || !"builder".equals(worker.job)
                        || !KingdomNpcDuty.mayExecute(worker.job, worker.order,
                            worker.autonomousIntent, KingdomNpcWorkday.employed(server, worker))) continue;
                var entity=level.getEntity(worker.entityId);
                if(entity instanceof Villager villager && villager.isAlive()
                    && villager.blockPosition().distSqr(lectern)<=48*48) {builder=villager;break;}
            }
            if(builder==null)continue;
            if(builder.isNoAi())builder.setNoAi(false);
            if(builder.distanceToSqr(lectern.getX()+0.5,lectern.getY()+0.5,lectern.getZ()+0.5)>16) {
                builder.getNavigation().moveTo(lectern.getX()+0.5,lectern.getY()+0.5,lectern.getZ()+0.5,0.8);
                continue;
            }
            builder.getNavigation().stop();
            var cell=plan.get(index);
            BlockPos target=new BlockPos(site.x()+cell.x(),site.y()+cell.y(),site.z()+cell.z());
            if(!level.hasChunkAt(target))continue;
            if(!KingdomConstructionAccess.canBuild(server,level,target,site.owner()))continue;
            ResourceLocation id=ResourceLocation.tryParse(cell.material());
            if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))continue;
            Block block=BuiltInRegistries.BLOCK.get(id);
            if(block==Blocks.AIR)continue;
            if(level.getBlockState(target).is(block)) {sites.advance(site.id(),1,plan.size());continue;}
            if(!level.getBlockState(target).isAir())continue;
            Item material=block.asItem();
            if(material==Items.AIR)continue;
            int slot=-1;
            for(int i=0;i<supplies.getContainerSize();i++) {
                ItemStack stack=supplies.getItem(i);
                if(!stack.isEmpty()&&stack.is(material)){slot=i;break;}
            }
            if(slot<0){sites.waiting(site.id());continue;}
            // Debit and place on the server thread. If placement fails, restore the item.
            ItemStack removed=supplies.removeItem(slot,1);
            if(removed.isEmpty())continue;
            if(level.setBlock(target,block.defaultBlockState(),3)) {
                supplies.setChanged();sites.advance(site.id(),1,plan.size());
            } else {
                ItemStack remainder=supplies.getItem(slot);
                if(remainder.isEmpty())supplies.setItem(slot,removed);
                else if(ItemStack.isSameItemSameTags(remainder,removed))remainder.grow(1);
                supplies.setChanged();
            }
        }
    }

    private static Container findSupplies(ServerLevel level,BlockPos lectern) {
        for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos at=lectern.relative(dir);
            if(!level.hasChunkAt(at))continue;
            BlockEntity entity=level.getBlockEntity(at);
            if(entity instanceof Container inventory)return inventory;
        }
        return null;
    }
}
