package ru.projectkingdom.map;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A REAL filled Minecraft map that can be held in either hand.
 *
 * Only maps bearing the Kingdom tag are painted; vanilla maps are untouched.
 * The vanilla renderer draws the map paper, pixels and player direction marker
 * in first person. Registry-owned boundaries, buildings and a selected route
 * are rendered onto a locked map palette rather than a fake screen overlay.
 */
public final class KingdomHeldMapService {
    public static final String ITEM_TAG = "KingdomHeldAtlas";
    private static final String OWNER_TAG = "KingdomHeldAtlasOwner";
    private static final String DIM_TAG = "KingdomHeldAtlasDimension";
    private static final String WAYPOINT_TAG = "KingdomNavigationWaypoint";
    private static final int SIZE = 128;
    private static final int MAP_SCALE = 2; // four physical blocks per map pixel
    private static final int STEP = 1 << MAP_SCALE;
    private static final int MAX_PER_TICK = 2;
    private static final byte PARCHMENT = shade(MapColor.SAND);
    private static final byte GREEN = shade(MapColor.GRASS);
    private static final byte FOREST = shade(MapColor.COLOR_GREEN);
    private static final byte WATER = shade(MapColor.WATER);
    private static final byte MOUNTAIN = shade(MapColor.STONE);
    private static final byte ROAD = shade(MapColor.COLOR_BROWN);
    private static final byte OWN = shade(MapColor.COLOR_BLUE);
    private static final byte FOREIGN = shade(MapColor.COLOR_RED);
    private static final byte BORDER = shade(MapColor.COLOR_BLACK);
    private static final byte PLAN = shade(MapColor.COLOR_YELLOW);
    private static final byte BUILDING = shade(MapColor.COLOR_LIGHT_GRAY);
    private static final byte WORKSHOP = shade(MapColor.GOLD);
    private static final byte ROUTE = shade(MapColor.COLOR_CYAN);

    private KingdomHeldMapService() {}
    private static byte shade(MapColor color) {
        return color.getPackedId(MapColor.Brightness.NORMAL);
    }

    public static boolean isKingdomMap(ItemStack stack) {
        return stack.is(Items.FILLED_MAP) && stack.hasTag()
                && stack.getTag().getBoolean(ITEM_TAG);
    }

    /** One paper is consumed; issuing a map cannot mint paper from nothing. */
    public static void issue(ServerPlayer player) {
        if (player.getServer() == null) return;
        if (hasAtlas(player)) {
            player.sendSystemMessage(Component.literal(
                    "[Kingdom] Карта уже есть в инвентаре. Возьмите её в руку."));
            return;
        }
        if (!player.isCreative() && !consumePaper(player)) {
            player.sendSystemMessage(Component.literal(
                    "[Kingdom] Для изготовления карты Kingdom требуется 1 лист бумаги."));
            return;
        }
        var level = player.serverLevel();
        ItemStack result = MapItem.create(level,player.blockPosition().getX(),
                player.blockPosition().getZ(),(byte)MAP_SCALE,true,false);
        Integer id = MapItem.getMapId(result);
        if (id == null) return;
        MapItemSavedData data = MapItem.getSavedData(result,level);
        if (data == null) return;
        level.setMapData(MapItem.makeKey(id),data.locked());
        result.getOrCreateTag().putBoolean(ITEM_TAG,true);
        result.getOrCreateTag().putUUID(OWNER_TAG,player.getUUID());
        result.getOrCreateTag().putString(DIM_TAG,level.dimension().location().toString());
        result.setHoverName(Component.literal("Карта Kingdom — местность и навигация"));
        paint(player,result);
        if (!player.getInventory().add(result)) player.drop(result,false);
        player.sendSystemMessage(Component.literal(
                "[Kingdom] Карта изготовлена. Возьмите её в руку: дороги, города и путь будут прямо на бумаге."));
    }

    private static boolean hasAtlas(ServerPlayer player) {
        for (int i=0;i<player.getInventory().getContainerSize();i++)
            if (isKingdomMap(player.getInventory().getItem(i))) return true;
        return isKingdomMap(player.getOffhandItem());
    }
    private static boolean consumePaper(ServerPlayer player) {
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            ItemStack stack=player.getInventory().getItem(i);
            if(stack.is(Items.PAPER) && !stack.isEmpty()){
                stack.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    public static void selectDestination(ServerPlayer player,int chunkX,int chunkZ) {
        if (Math.abs((long)chunkX)>16384 || Math.abs((long)chunkZ)>16384) return;
        var waypoint=new net.minecraft.nbt.CompoundTag();
        waypoint.putString("Dim",player.serverLevel().dimension().location().toString());
        waypoint.putInt("ChunkX",chunkX);
        waypoint.putInt("ChunkZ",chunkZ);
        player.getPersistentData().put(WAYPOINT_TAG,waypoint);
    }

    /** Single 40-tick pass for held maps only, no background chunk generation. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount%40!=0) return;
        ItemStack main=player.getMainHandItem(),off=player.getOffhandItem();
        if(isKingdomMap(main))paint(player,main);
        if(off!=main && isKingdomMap(off))paint(player,off);
    }

    private static byte surface(KingdomGraphicAtlasData.Tile tile) {
        var t=KingdomAtlasTerrain.Type.values();
        int index=tile.terrain();
        var type=index>=0&&index<t.length?t[index]:KingdomAtlasTerrain.Type.UNKNOWN;
        byte terrain=switch(type){
            case FOREST -> FOREST;
            case WATER -> WATER;
            case MOUNTAIN -> MOUNTAIN;
            case ROAD -> ROAD;
            case PLAIN -> GREEN;
            case UNKNOWN -> PARCHMENT;
        };
        if(tile.planned()) return PLAN;
        if(tile.claims()>0) return tile.own()?OWN:FOREIGN;
        return terrain;
    }

    private static int pixel(int worldCoord,int mapCenter) {
        return Math.floorDiv(worldCoord-mapCenter,STEP)+64;
    }
    private static void dot(byte[] colors,int x,int z,int radius,byte color){
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){
            int px=x+dx,pz=z+dz;
            if(px>=0 && px<SIZE && pz>=0 && pz<SIZE)colors[pz*SIZE+px]=color;
        }
    }
    private static void line(byte[] colors,int x0,int z0,int x1,int z1,byte color) {
        int dx=Math.abs(x1-x0),dz=Math.abs(z1-z0);
        int sx=x0<x1?1:-1,sz=z0<z1?1:-1,err=dx-dz;
        for(int n=0;n<512;n++){
            dot(colors,x0,z0,0,color);
            if(x0==x1 && z0==z1)break;
            int twice=2*err;
            if(twice>-dz){err-=dz;x0+=sx;}
            if(twice<dx){err+=dx;z0+=sz;}
        }
    }

    private static void paint(ServerPlayer player,ItemStack stack) {
        if (!isKingdomMap(stack) || !stack.getTag().hasUUID(OWNER_TAG)
                || !player.getUUID().equals(stack.getTag().getUUID(OWNER_TAG))
                || !player.serverLevel().dimension().location().toString()
                    .equals(stack.getTag().getString(DIM_TAG))) return;
        MapItemSavedData data=MapItem.getSavedData(stack,player.serverLevel());
        if(data==null || !data.locked) return;
        int cx=data.centerX,cz=data.centerZ;
        // Snapshot is exactly the known surveyed and owned region. Unknown
        // chunks remain parchment, not invented navigable paths.
        var snapshot=KingdomGraphicAtlasData.fromPlayer(player,0,
                Math.floorDiv(cx,16),Math.floorDiv(cz,16),1,
                KingdomGraphicAtlasData.Layer.LAND);
        byte[] pixels=new byte[SIZE*SIZE];
        Arrays.fill(pixels,PARCHMENT);
        for(int row=0;row<KingdomGraphicAtlasData.ROWS;row++)for(int col=0;col<KingdomGraphicAtlasData.COLUMNS;col++){
            var tile=snapshot.tile(col,row);
            int tx=snapshot.centerX()-KingdomGraphicAtlasData.COLUMNS/2+col;
            int tz=snapshot.centerZ()-KingdomGraphicAtlasData.ROWS/2+row;
            int px=pixel(tx*16,cx),pz=pixel(tz*16,cz);
            byte fill=surface(tile);
            for(int dz=0;dz<16/STEP;dz++)for(int dx=0;dx<16/STEP;dx++)
                dot(pixels,px+dx,pz+dz,0,fill);
            if(tile.claims()>0){
                // Draw strong borders only where a different owner appears.
                var west=col==0?null:snapshot.tile(col-1,row);
                var north=row==0?null:snapshot.tile(col,row-1);
                if(west==null || west.claims()==0 || west.townColor()!=tile.townColor())
                    line(pixels,px,pz,px,pz+16/STEP-1,BORDER);
                if(north==null || north.claims()==0 || north.townColor()!=tile.townColor())
                    line(pixels,px,pz,px+16/STEP-1,pz,BORDER);
            }
        }
        // Registered buildings/bridges/roads: trace their real blueprint
        // footprints. Bounded to 48 outlines by the shared atlas.
        for(var shape:snapshot.outlines()){
            int x0=pixel(shape.minX(),cx),z0=pixel(shape.minZ(),cz);
            int x1=pixel(shape.maxX(),cx),z1=pixel(shape.maxZ(),cz);
            byte shade=switch(shape.kind()){
                case ROAD -> ROAD;
                case BRIDGE -> BUILDING;
                case ENTERPRISE -> WORKSHOP;
                default -> BORDER;
            };
            line(pixels,x0,z0,x1,z0,shade);
            line(pixels,x0,z1,x1,z1,shade);
            line(pixels,x0,z0,x0,z1,shade);
            line(pixels,x1,z0,x1,z1,shade);
        }
        for(var marker:snapshot.markers()){
            byte color=switch(marker.kind()){
                case ENTERPRISE -> WORKSHOP;
                case ROAD -> ROAD;
                case BRIDGE -> BUILDING;
                default -> BORDER;
            };
            dot(pixels,pixel(marker.blockX(),cx),pixel(marker.blockZ(),cz),1,color);
        }
        var goal=player.getPersistentData().getCompound(WAYPOINT_TAG);
        if(player.serverLevel().dimension().location().toString().equals(goal.getString("Dim"))){
            int targetX=goal.getInt("ChunkX"),targetZ=goal.getInt("ChunkZ");
            var route=KingdomAtlasNavigator.plan(snapshot,
                    player.chunkPosition().x,player.chunkPosition().z,targetX,targetZ);
            if(route.status()==KingdomAtlasNavigator.Status.ROUTE){
                List<KingdomAtlasNavigator.Step> path=route.steps();
                for(int i=1;i<path.size();i++){
                    var a=path.get(i-1);var b=path.get(i);
                    int ax=snapshot.centerX()-KingdomGraphicAtlasData.COLUMNS/2+a.column();
                    int az=snapshot.centerZ()-KingdomGraphicAtlasData.ROWS/2+a.row();
                    int bx=snapshot.centerX()-KingdomGraphicAtlasData.COLUMNS/2+b.column();
                    int bz=snapshot.centerZ()-KingdomGraphicAtlasData.ROWS/2+b.row();
                    line(pixels,pixel(ax*16+8,cx),pixel(az*16+8,cz),
                            pixel(bx*16+8,cx),pixel(bz*16+8,cz),ROUTE);
                }
            }
            MapItemSavedData.addTargetDecoration(stack,
                    new BlockPos(targetX*16+8,64,targetZ*16+8),
                    "kingdom_navigation_target",MapDecoration.Type.RED_X);
        }
        for(int index=0;index<pixels.length;index++)
            if(data.colors[index]!=pixels[index])
                data.setColor(index%SIZE,index/SIZE,pixels[index]);
    }
}
