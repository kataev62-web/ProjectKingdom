package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Survival-mode building, one real inventory block for each newly placed plan block. */
public final class KingdomSiteBuilder {
    private KingdomSiteBuilder() {}
    public static String place(ServerPlayer p,KingdomBuildingTypes.Type type) {
        BlockPos marker=findLectern(p);
        if(marker==null)return "Поставь кафедру (lectern) и стой не дальше 4 блоков от неё.";
        var db=KingdomConstructionData.get(p.getServer());
        String dimension=p.level().dimension().location().toString();
        if(db.atMarker(dimension,marker.getX(),marker.getY(),marker.getZ())!=null)return "На этой кафедре уже есть стройка.";
        var design=KingdomBuildingTypes.design(type);
        var claims=KingdomTerritories.get(p.getServer());
        var civics=KingdomCivicsData.get(p.getServer());
        if (design.blocks().isEmpty()) return "У проекта нет строительных блоков.";
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for (var cell:design.blocks()) {
            int x=marker.getX()+2+cell.x(),y=marker.getY()+cell.y(),z=marker.getZ()+2+cell.z();
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
        }
        if (db.overlaps(dimension,minX,minY,minZ,maxX,maxY,maxZ))
            return "Здесь уже зарегистрирована другая постройка. Выбери свободное место.";
        for(var cell:design.blocks()) {
            BlockPos target=marker.offset(2+cell.x(),cell.y(),2+cell.z());
            if(target.getY()<p.serverLevel().getMinBuildHeight()||target.getY()>=p.serverLevel().getMaxBuildHeight())return "Проект выходит за высоту мира.";
            if(!KingdomConstructionAccess.canBuild(p.getServer(),p.serverLevel(),target,p.getUUID()))return "Нет права строительства: блок "+target.toShortString()+", чанк "+(target.getX()>>4)+", "+(target.getZ()>>4)+". Проверь собственность или разрешение работника.";
            if(!p.serverLevel().hasChunkAt(target))return "Сначала прогрузи все чанки строительной площадки.";
            Block b=BuiltInRegistries.BLOCK.get(new ResourceLocation(cell.material()));
            if(b==Blocks.AIR)return "Неизвестный материал в проекте: "+cell.material();
            var existing=p.serverLevel().getBlockState(target);
            if(!existing.isAir()&&!existing.is(b))return "Очисти площадку: препятствие в "+target.toShortString();
        }
        var project=db.add(p.getUUID(),dimension,type.name(),marker.getX()+2,marker.getY(),marker.getZ()+2);
        return "Площадка "+type.title+" создана. Открой кафедру ПКМ. ID: "+project.id();
    }
    public static BlockPos findLectern(ServerPlayer p) {
        BlockPos base=p.blockPosition();BlockPos closest=null;double dist=Double.MAX_VALUE;
        for(int dx=-4;dx<=4;dx++)for(int dy=-2;dy<=2;dy++)for(int dz=-4;dz<=4;dz++){
            BlockPos pos=base.offset(dx,dy,dz);
            if(!p.serverLevel().getBlockState(pos).is(Blocks.LECTERN))continue;
            double d=pos.distSqr(base);if(d<dist){dist=d;closest=pos;}
        }return closest;
    }
    public static String build(ServerPlayer p,KingdomConstructionData.Project project) {
        if(project==null||!project.owner().equals(p.getUUID()))return "Нет прав на стройку.";
        if(!project.dimension().equals(p.level().dimension().location().toString()))return "Постройка находится в другом измерении.";
        var type=KingdomBuildingTypes.type(project.design());if(type==null)return "Старый проект: автоматическая стройка не поддерживается.";
        var plan=KingdomBuildingTypes.design(type).blocks();var data=KingdomConstructionData.get(p.getServer());
        int index=data.progress(project.id());if(index>=plan.size())return "Здание уже построено.";
        BlockPos marker=new BlockPos(project.x()-2,project.y(),project.z()-2);
        if(!p.serverLevel().getBlockState(marker).is(Blocks.LECTERN))return "Строительная кафедра удалена.";
        if(p.distanceToSqr(marker.getX()+0.5,marker.getY()+0.5,marker.getZ()+0.5)>144)return "Подойди к стройке (не дальше 12 блоков).";
        var claims=KingdomTerritories.get(p.getServer());var civics=KingdomCivicsData.get(p.getServer());
        int placed=0;
        for(;index<plan.size()&&placed<32;index++){
            var cell=plan.get(index);BlockPos target=new BlockPos(project.x()+cell.x(),project.y()+cell.y(),project.z()+cell.z());
            if(!p.serverLevel().hasChunkAt(target))break;
            if(!KingdomConstructionAccess.canBuild(p.getServer(),p.serverLevel(),target,p.getUUID()))break;
            Block block=BuiltInRegistries.BLOCK.get(new ResourceLocation(cell.material()));
            if(block==Blocks.AIR)break;
            if(p.serverLevel().getBlockState(target).is(block)){data.advance(project.id(),1,plan.size());continue;}
            if(!p.serverLevel().getBlockState(target).isAir())break;
            Item item=block.asItem();if(item==net.minecraft.world.item.Items.AIR)break;
            int inventorySlot=-1;
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                ItemStack stack=p.getInventory().getItem(i);
                if(stack.is(item)&&!stack.isEmpty()){inventorySlot=i;break;}
            }
            if(inventorySlot<0)break;
            // Inventory is debited only when the placement is possible. Server-side only.
            var stack=p.getInventory().getItem(inventorySlot);
            stack.shrink(1);
            if(!p.serverLevel().setBlock(target,block.defaultBlockState(),3)){
                stack.grow(1);break;
            }
            data.advance(project.id(),1,plan.size());placed++;
        }
        p.getInventory().setChanged();
        int progress=data.progress(project.id());
        return (progress==plan.size()?"Строительство завершено! ":"Строительство: ")+progress+"/"+plan.size()+" блоков (установлено сейчас "+placed+"). Если остановилось — проверь материалы, препятствия и права на землю.";
    }
}
