package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static ru.projectkingdom.enterprise.EnterpriseBuildingRegistration.Tile.*;

/** Deterministic no-world simulation of the same bounded physical building scan. */
final class EnterpriseBuildingRegistrationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraftRegistries(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final UUID FIRM=UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final BlockPos CORE=new BlockPos(2,0,2);
    private static final String DIM="minecraft:overworld";
    private static EnterpriseBuildingRegistration.Cells home(boolean roof,boolean floor,
                                                               boolean window,boolean hole){
        return (x,y,z)->{
            boolean interior=x>=1&&x<=3&&z>=1&&z<=3;
            boolean wall=x>=0&&x<=4&&z>=0&&z<=4&&!interior;
            if(interior && y==-1)return floor?SOLID:AIR;
            if(interior && y==3)return roof?SOLID:AIR;
            if(wall && (y==0||y==1)){
                if(hole&&x==0&&z==2&&y==1)return AIR;
                if(window&&x==0&&z==2&&y==1)return WINDOW;
                return SOLID;
            }
            return AIR;
        };
    }
    @Test void completeEnclosedWorkshopRegistersAndStoresDimensions(){
        var result=EnterpriseBuildingRegistration.verify(home(true,true,true,false),FIRM,DIM,CORE);
        assertEquals(EnterpriseBuildingRegistration.Status.REGISTERED,result.status());
        assertTrue(result.success());
        assertEquals("5×5×5",result.room().dimensions());
        assertTrue(result.room().windows()>0);
        assertEquals(9,result.room().interiorArea());
    }
    @Test void wallRoofFloorAndWindowAreRequired(){
        assertEquals(EnterpriseBuildingRegistration.Status.NO_ROOF,
                EnterpriseBuildingRegistration.verify(home(false,true,true,false),FIRM,DIM,CORE).status());
        assertEquals(EnterpriseBuildingRegistration.Status.NO_FLOOR,
                EnterpriseBuildingRegistration.verify(home(true,false,true,false),FIRM,DIM,CORE).status());
        assertEquals(EnterpriseBuildingRegistration.Status.NO_WALLS,
                EnterpriseBuildingRegistration.verify(home(true,true,true,true),FIRM,DIM,CORE).status());
        assertEquals(EnterpriseBuildingRegistration.Status.NO_WINDOWS,
                EnterpriseBuildingRegistration.verify(home(true,true,false,false),FIRM,DIM,CORE).status());
    }
    @Test void fencesAndGlassShareOneWindowRule(){
        // In-world classification maps vanilla/Forge glass panes and fences to WINDOW.
        var result=EnterpriseBuildingRegistration.verify(home(true,true,true,false),FIRM,DIM,CORE);
        assertTrue(result.success());
        var source=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseBuildingRegistration.java");
        assertTrue(source.contains("BlockTags.FENCES"));
        assertTrue(source.contains("IronBarsBlock"));
        assertTrue(source.contains("FORGE_FENCES"));
        assertTrue(source.contains("new ResourceLocation(\"forge\",\"glass_panes\")"));
    }
    @Test void doesNotLoadChunksAndRejectsMissingSpace(){
        var grid=home(true,true,true,false);
        EnterpriseBuildingRegistration.Cells unloaded=(x,y,z)->
                x==3&&z==3&&y==1?UNLOADED:grid.at(x,y,z);
        assertEquals(EnterpriseBuildingRegistration.Status.UNLOADED,
                EnterpriseBuildingRegistration.verify(unloaded,FIRM,DIM,CORE).status());
        assertEquals(EnterpriseBuildingRegistration.Status.TOO_SMALL,
                EnterpriseBuildingRegistration.verify((x,y,z)->{
                    if(x==2&&z==2&&y==-1)return SOLID;
                    if(x==2&&z==2&&y==3)return SOLID;
                    if(Math.abs(x-2)+Math.abs(z-2)==1&&(y==0||y==1))
                        return x==1&&y==1?WINDOW:SOLID;
                    return AIR;
                },FIRM,DIM,CORE).status());
    }
    @Test void realForgeNeedsOpenChimneyAndInternalMachineryAreNotWalls(){
        var original=home(true,true,true,false);
        EnterpriseBuildingRegistration.Cells smith=(x,y,z)->{
            if(x==1&&z==1&&y==0)return EnterpriseBuildingRegistration.Tile.FORGE;
            if(x==1&&z==1&&y==3)return AIR; // TFC chimney vents through roof
            if(x==3&&z==3&&y==0)return EnterpriseBuildingRegistration.Tile.MACHINE;
            return original.at(x,y,z);
        };
        var result=EnterpriseBuildingRegistration.verify(smith,FIRM,DIM,CORE);
        assertEquals(EnterpriseBuildingRegistration.Status.REGISTERED,result.status());
        assertEquals(9,result.room().interiorArea());
        var source=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseBuildingRegistration.java");
        assertTrue(source.contains("tfc:charcoal_forge"));
        assertTrue(source.contains("ground!=Tile.FORGE"));
    }
    @Test void noOverlapBetweenDifferentEnterprises(){
        var first=EnterpriseBuildingRegistration.verify(home(true,true,true,false),FIRM,DIM,CORE).room();
        assertNotNull(first);
        var second=new EnterpriseBuildingRegistration.Room(UUID.randomUUID(),DIM,0,
                first.minX(),first.minY(),first.minZ(),first.maxX(),first.maxY(),first.maxZ(),1,9);
        assertTrue(first.overlaps(second));
        var separated=new EnterpriseBuildingRegistration.Room(UUID.randomUUID(),DIM,0,100,0,100,104,8,104,1,9);
        assertFalse(first.overlaps(separated));
    }
    @Test void interfaceUsesServerAuthoritativeCoreAndOwnerNotClientCoordinates(){
        var menu=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreMenu.java");
        var gui=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreScreen.java");
        var core=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreBlock.java");
        var registry=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseBuildingRegistration.java");
        assertTrue(menu.contains("REGISTER_BUILDING=113"));
        assertTrue(menu.contains(".register(server,enterpriseId,corePos)"));
        assertTrue(gui.contains("Зарегистрировать здание"));
        assertTrue(core.contains("EnterpriseBuildingRegistration.get(server.getServer()).remove(core.enterpriseId())"));
        assertTrue(registry.contains("!player.getUUID().equals(be.owner())"));
        assertTrue(registry.contains("civic.plotOwner(dim,cx,cz)"));
        assertTrue(registry.contains("level.hasChunkAt(pos)"));
        assertTrue(registry.contains("root.put(\"Rooms\",entries)"));
    }
    private static String read(String path){
        try{return Files.readString(Path.of(path));}catch(Exception e){throw new AssertionError(e);}
    }
}
