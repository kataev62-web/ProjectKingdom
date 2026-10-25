package ru.projectkingdom.npc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Two world clicks with the existing building_blueprint copy a building to the
 * server-owned workshop library. No WorldEdit dependency or client-supplied NBT.
 * The source world is never edited, and inventories/entities are never copied.
 */
public final class KingdomStructureCopy {
    private KingdomStructureCopy() {}
    private record Selection(String name,BlockPos office,String dimension,BlockPos first,long expires) {}
    private static final Map<UUID,Selection> ACTIVE=new HashMap<>();
    private static final long EXPIRY_TICKS=20*60*10;

    public static void begin(ServerPlayer player,BlockPos office,String name){
        if(!KingdomSchematicLibrary.validName(name)
                || !KingdomArchitectSiteAccess.mayUse(player,office)) {
            say(player,"Неправильное имя или контора недоступна.");return;
        }
        ACTIVE.put(player.getUUID(),new Selection(name,office.immutable(),
            player.level().dimension().location().toString(),null,
            player.serverLevel().getGameTime()+EXPIRY_TICKS));
        say(player,"Копирование «"+name+"»: возьми строительный чертёж в руку. "
            +"Нажми ПКМ по ПЕРВОМУ углу здания, затем ПКМ по ПРОТИВОПОЛОЖНОМУ углу. "
            +"Оба угла включаются. Постройка не изменится.");
    }

    public static void cancel(UUID player){ACTIVE.remove(player);}

    public static boolean hasSelection(UUID id){return ACTIVE.containsKey(id);}

    public static void handle(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        var session=ACTIVE.get(player.getUUID());
        if(session==null)return;
        if(!event.getItemStack().is(KingdomConstructionRegistry.BUILDING_BLUEPRINT.get()))
            return; // Other interactive items still work during the survey.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if(session.expires()<player.serverLevel().getGameTime()
                || !session.dimension().equals(player.level().dimension().location().toString())){
            ACTIVE.remove(player.getUUID());say(player,"Копирование истекло. Начни снова.");
            return;
        }
        BlockPos clicked=event.getPos().immutable();
        if(!player.serverLevel().hasChunkAt(clicked)
                || player.distanceToSqr(clicked.getX()+.5,clicked.getY()+.5,
                    clicked.getZ()+.5)>64) {
            say(player,"Угол должен быть в загруженной области не дальше 8 блоков.");return;
        }
        if(session.first()==null){
            ACTIVE.put(player.getUUID(),new Selection(session.name(),session.office(),
                session.dimension(),clicked,session.expires()));
            say(player,"Первый угол: "+clicked.toShortString()
                +". Теперь отметь противоположный угол тем же строительным чертежом.");
            return;
        }
        try {
            var result=copy(player,session.name(),session.first(),clicked);
            ACTIVE.remove(player.getUUID());
            say(player,"Схема «"+result.name()+"» сохранена в строительной конторе: "
                +result.copied()+" блоков. "
                +(result.skipped()>0 ? "Пропущено несовместимых блоков: "+result.skipped()
                    +" (например, двери/кровати — установи вручную). " : "")
                +(result.emptyNbt()>0 ? "Содержимое "+result.emptyNbt()
                    +" контейнеров/механизмов не копируется. " : "")
                +"Возвращайся к конторе, новая схема уже в каталоге.");
        }catch(IOException | IllegalArgumentException ex){
            say(player,"Копирование не выполнено: "+ex.getMessage()
                +". Исправь ошибку и повторно нажми второй угол или переоткрой архитектора.");
        }
    }

    public record CopyResult(String name,int copied,int skipped,int emptyNbt) {}

    /** Validate all positions before writing anything to disk. */
    static CopyResult copy(ServerPlayer player,String name,BlockPos p1,BlockPos p2)
            throws IOException {
        if(!KingdomSchematicLibrary.validName(name))
            throw new IOException("Используй латиницу, цифры и _ для имени файла.");
        int minX=Math.min(p1.getX(),p2.getX()),maxX=Math.max(p1.getX(),p2.getX());
        int minY=Math.min(p1.getY(),p2.getY()),maxY=Math.max(p1.getY(),p2.getY());
        int minZ=Math.min(p1.getZ(),p2.getZ()),maxZ=Math.max(p1.getZ(),p2.getZ());
        long width=(long)maxX-minX+1,height=(long)maxY-minY+1,length=(long)maxZ-minZ+1;
        if(width>48||height>48||length>48||width*height*length>65_536)
            throw new IOException("Максимальный объём: 48×48×48 и 65 536 блоков. "
                +"Большое здание нужно копировать по секциям.");
        ServerLevel level=player.serverLevel();
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            BlockPos at=new BlockPos(x,minY,z);
            if(!level.hasChunkAt(at))throw new IOException("Часть постройки не загружена.");
            if(!player.hasPermissions(2) && !KingdomConstructionAccess.canBuild(
                    player.getServer(),level,at,player.getUUID()))
                throw new IOException("Нет права на копирование: участок "+x+", "+z
                    +". Разрешены свои и доверенные строительные участки.");
        }
        int volume=(int)(width*height*length);
        Map<String,Integer> palette=new HashMap<>();
        CompoundTag paletteTag=new CompoundTag();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(volume);
        int solid=0,blockEntities=0,skipped=0;
        for(int y=minY;y<=maxY;y++)
            for(int z=minZ;z<=maxZ;z++)
                for(int x=minX;x<=maxX;x++){
                    BlockPos at=new BlockPos(x,y,z);
                    var state=level.getBlockState(at);
                    // A copied building may contain doors, beds, unsupported
                    // fluids or blocks with no legal survival ItemStack.
                    // Keep the buildable shell and report EACH omitted block.
                    if(!state.isAir() && !KingdomBlockStateCodec.buildable(state)){
                        skipped++;
                        state=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                    }
                    if(!state.isAir()) {
                        solid++;
                        if(level.getBlockEntity(at)!=null)blockEntities++;
                    }
                    String spec=KingdomArchitectDesignRotation.encode(state);
                    Integer index=palette.get(spec);
                    if(index==null){
                        index=palette.size();
                        if(index>=4096)throw new IOException("Слишком много разновидностей блоков.");
                        palette.put(spec,index);
                        paletteTag.putInt(spec,index);
                    }
                    int v=index;
                    do {
                        int part=v&127;v>>>=7;
                        bytes.write(v==0?part:part|128);
                    }while(v!=0);
                }
        if(solid==0)throw new IOException("В выбранной области нет построек.");
        var root=new CompoundTag();
        root.putInt("Version",2);
        root.putInt("DataVersion",3465); // Minecraft Java 1.20.1
        root.putShort("Width",(short)width);
        root.putShort("Height",(short)height);
        root.putShort("Length",(short)length);
        root.putInt("PaletteMax",palette.size());
        root.put("Palette",paletteTag);
        root.putByteArray("BlockData",bytes.toByteArray());
        var metadata=new CompoundTag();
        metadata.putString("KingdomCopyOwner",player.getUUID().toString());
        metadata.putInt("KingdomCopyBlockEntitiesWithoutNbt",blockEntities);
        metadata.putInt("KingdomSkippedUnsupported",skipped);
        root.put("Metadata",metadata);
        Path directory=player.getServer().getWorldPath(LevelResource.ROOT)
            .resolve("data").resolve("kingdom_schematics");
        Files.createDirectories(directory);
        Path target=directory.resolve(name+".schem");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Имя «"+name+"» уже занято. Выбери другое.");
        Path temp=Files.createTempFile(directory,"kingdom-copy-", ".tmp");
        try {
            try(var output=Files.newOutputStream(temp)){
                NbtIo.writeCompressed(root,output);
            }
            // Run the exact importer before making the file visible.
            KingdomSchematicLibrary.readDesign(temp,name);
            Files.move(temp,target); // no overwrite of user/admin's existing file.
        }finally {
            Files.deleteIfExists(temp);
        }
        return new CopyResult(name,solid,skipped,blockEntities);
    }

    private static void say(ServerPlayer player,String text){
        player.sendSystemMessage(Component.literal("[Архитектор] "+text));
    }
}
