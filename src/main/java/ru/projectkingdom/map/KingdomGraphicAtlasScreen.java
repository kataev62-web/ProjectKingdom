package ru.projectkingdom.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectkingdom.network.GraphicAtlasRequestPacket;
import ru.projectkingdom.network.OpenCityPlanFromAtlasPacket;
import ru.projectkingdom.network.OpenLandMapFromAtlasPacket;
import ru.projectkingdom.network.OpenKingdomMapsHubPacket;
import ru.projectkingdom.network.KingdomNetwork;

/**
 * Parchment-style graphical atlas. Renders from an immutable, bounded S2C
 * viewport; map clicks change only local selection, never server ownership.
 */
public final class KingdomGraphicAtlasScreen extends Screen {
    private static final int FREE = 0xFFD1C6A6;
    private static final int OWN = 0xFF5486AC;
    private static final int PLAN = 0xFFE3BB58;
    private static final int INK = 0xFF39291B;
    private static final int MUTED = 0xFF826D50;
    private static final int[] TOWN_PALETTE = {
        0xFFA96155, 0xFF799D76, 0xFFA587C0, 0xFFC18D55, 0xFF729EAA,
        0xFFBD7D8F, 0xFF98A45C, 0xFF936FBD, 0xFF8EAA95, 0xFFB39A67
    };
    private int centerX, centerZ, scaleIndex, requestId, ticks;
    /** Following is disabled when manually panning, restored by the Centre button. */
    private boolean followPlayer = true;
    private KingdomGraphicAtlasData.Layer layer = KingdomGraphicAtlasData.Layer.LAND;
    private KingdomGraphicAtlasData.Snapshot snapshot;
    private int selectedCol = -1, selectedRow = -1;
    private KingdomAtlasInfrastructure.Marker selectedMarker;
    private Integer destinationChunkX, destinationChunkZ;
    private KingdomAtlasNavigator.Route route;
    private int lastRouteChunkX = Integer.MIN_VALUE, lastRouteChunkZ = Integer.MIN_VALUE;
    private int left, top, cell, gridX, gridY, gridWidth, gridHeight, panelWidth, panelHeight;
    private int navWidth, inspectorWidth;

    public KingdomGraphicAtlasScreen() {
        this(KingdomGraphicAtlasData.Layer.LAND);
    }

    public KingdomGraphicAtlasScreen(KingdomGraphicAtlasData.Layer initialLayer) {
        super(Component.literal("Карты Project Kingdom"));
        layer = initialLayer == null ? KingdomGraphicAtlasData.Layer.LAND : initialLayer;
    }

    /** MineColonies-inspired Kingdom layout: navigation left, world map, inspector right. */
    @Override protected void init() {
        navWidth = width < 385 ? 82 : 100;
        inspectorWidth = width < 385 ? 0 : width < 485 ? 116 : 152;
        int available = Math.max(5,(width - navWidth - inspectorWidth - 36)
                / KingdomGraphicAtlasData.COLUMNS);
        cell = Math.max(5,Math.min(12,Math.min(available,
                Math.max(5,(height - 65) / KingdomGraphicAtlasData.ROWS))));
        gridWidth = cell * KingdomGraphicAtlasData.COLUMNS;
        gridHeight = cell * KingdomGraphicAtlasData.ROWS;
        panelWidth = navWidth + gridWidth + inspectorWidth + 22;
        panelHeight = Math.min(height - 8, Math.max(208, gridHeight + 58));
        left = Math.max(0,(width - panelWidth) / 2);
        top = Math.max(0,(height - panelHeight) / 2);
        gridX = left + navWidth + 8;
        gridY = top + 26;
        int navX = left + 6, navButtonWidth = navWidth - 11;
        boolean compactControls = panelHeight < 218;
        KingdomGraphicAtlasData.Layer[] layers = KingdomGraphicAtlasData.Layer.values();
        int half = (navButtonWidth - 3) / 2;
        if (compactControls) {
            String[] labels = {"Мир", "Нации", "Фирмы", "Стройки"};
            for (int i=0;i<layers.length;i++) {
                var chosen=layers[i];
                int col=i%2,row=i/2;
                addRenderableWidget(Button.builder(Component.literal(labels[i]),
                        button -> setLayer(chosen)).bounds(
                        navX+col*(half+3),top+26+row*17,
                        col==0?half:navButtonWidth-half-3,16).build());
            }
        } else {
            String[] labels = {"Местность", "Государства", "Предприятия", "Стройки"};
            for (int i=0;i<layers.length;i++) {
                var chosen=layers[i];
                addRenderableWidget(Button.builder(Component.literal(labels[i]),
                        button -> setLayer(chosen))
                        .bounds(navX,top+27+i*19,navButtonWidth,17).build());
            }
        }
        int up=compactControls?66:108;
        int leftRight=compactControls?82:126;
        int down=compactControls?98:144;
        int zoom=compactControls?114:162;
        int centre=compactControls?130:180;
        int routeY=compactControls?146:198;
        addRenderableWidget(Button.builder(Component.literal("↑"),
                button -> pan(0,-1)).bounds(navX,top + up,navButtonWidth,16).build());
        addRenderableWidget(Button.builder(Component.literal("←"),
                button -> pan(-1,0)).bounds(navX,top + leftRight,half,16).build());
        addRenderableWidget(Button.builder(Component.literal("→"),
                button -> pan(1,0)).bounds(navX + half + 3,top + leftRight,
                    navButtonWidth - half - 3,16).build());
        addRenderableWidget(Button.builder(Component.literal("↓"),
                button -> pan(0,1)).bounds(navX,top + down,navButtonWidth,16).build());
        addRenderableWidget(Button.builder(Component.literal("−"),
                button -> zoom(-1)).bounds(navX,top + zoom,half,16).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                button -> zoom(1)).bounds(navX + half + 3,top + zoom,
                    navButtonWidth - half - 3,16).build());
        addRenderableWidget(Button.builder(Component.literal("Моё место"),
                button -> resetCenter()).bounds(navX,top + centre,navButtonWidth,16).build());
        addRenderableWidget(Button.builder(Component.literal("Маршрут"),
                button -> planSelectedRoute()).bounds(navX,top + routeY,navButtonWidth,16).build());

        // Selection remains informative. Only existing server menus may change
        // property rights or debit the real municipal treasury.
        if (inspectorWidth > 0) {
            int x = gridX + gridWidth + 7;
            int w = Math.max(44,inspectorWidth - 10);
            int halfWidth = (w - 4) / 2;
            addRenderableWidget(Button.builder(Component.literal("Границы"),
                    button -> KingdomNetwork.CHANNEL.sendToServer(new OpenCityPlanFromAtlasPacket()))
                    .bounds(x,top + panelHeight - 43,w,17).build());
            addRenderableWidget(Button.builder(Component.literal("Участки"),
                    button -> KingdomNetwork.CHANNEL.sendToServer(new OpenLandMapFromAtlasPacket()))
                    .bounds(x,top + panelHeight - 23,halfWidth,17).build());
            addRenderableWidget(Button.builder(Component.literal("Меню"),
                    button -> KingdomNetwork.CHANNEL.sendToServer(new OpenKingdomMapsHubPacket()))
                    .bounds(x + halfWidth + 4,top + panelHeight - 23,w - halfWidth - 4,17).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Меню"),
                    button -> KingdomNetwork.CHANNEL.sendToServer(new OpenKingdomMapsHubPacket()))
                    .bounds(navX,top + 6,navButtonWidth,17).build());
        }
        if (requestId == 0) resetCenter();
        else request();
    }

    private void resetCenter() {
        if (minecraft == null || minecraft.player == null) return;
        followPlayer = true;
        centerX = minecraft.player.chunkPosition().x;
        centerZ = minecraft.player.chunkPosition().z;
        request();
    }
    private void setLayer(KingdomGraphicAtlasData.Layer chosen) {
        layer = chosen;
        request();
    }
    private void zoom(int delta) {
        scaleIndex = Math.max(0, Math.min(KingdomGraphicAtlasData.SCALES.length - 1, scaleIndex + delta));
        request();
    }
    private void pan(int dx, int dz) {
        followPlayer = false;
        int displacement = KingdomGraphicAtlasData.SCALES[scaleIndex] * 5;
        centerX = Math.max(-16384, Math.min(16384, centerX + dx * displacement));
        centerZ = Math.max(-16384, Math.min(16384, centerZ + dz * displacement));
        request();
    }
    private void request() {
        if (minecraft == null || minecraft.player == null) return;
        // Never display an old country's territory under newly panned coordinates.
        if (snapshot != null && (snapshot.centerX() != centerX || snapshot.centerZ() != centerZ
                || snapshot.scale() != KingdomGraphicAtlasData.SCALES[scaleIndex]
                || snapshot.layer() != layer)) {
            snapshot = null;
            selectedCol = -1;
            selectedRow = -1;
            selectedMarker = null;
            route = null;
            // Keep the chosen goal across automatic recentering while walking.
        }
        requestId++;
        KingdomNetwork.CHANNEL.sendToServer(new GraphicAtlasRequestPacket(
                requestId, centerX, centerZ,
                KingdomGraphicAtlasData.SCALES[scaleIndex], layer.ordinal()));
    }
    public void accept(KingdomGraphicAtlasData.Snapshot reply) {
        if (reply.requestId() != requestId || reply.centerX() != centerX
                || reply.centerZ() != centerZ
                || reply.scale() != KingdomGraphicAtlasData.SCALES[scaleIndex]
                || reply.layer() != layer) return;
        snapshot = reply;
        recomputeRoute();
    }
    @Override public void tick() {
        ticks++;
        if (minecraft != null && minecraft.player != null
                && (minecraft.player.chunkPosition().x != lastRouteChunkX
                    || minecraft.player.chunkPosition().z != lastRouteChunkZ)) {
            recomputeRoute();
        }
        if (ticks % 40 != 0) return;
        if (followPlayer && minecraft != null && minecraft.player != null) {
            int threshold = 3 * KingdomGraphicAtlasData.SCALES[scaleIndex];
            int playerX = minecraft.player.chunkPosition().x;
            int playerZ = minecraft.player.chunkPosition().z;
            if (Math.abs((long)playerX - centerX) > threshold
                    || Math.abs((long)playerZ - centerZ) > threshold) {
                centerX = playerX;
                centerZ = playerZ;
            }
        }
        request();
    }

    private void planSelectedRoute() {
        if (minecraft == null || minecraft.player == null || snapshot == null) return;
        if (selectedMarker != null) {
            destinationChunkX = Math.floorDiv(selectedMarker.blockX(),16);
            destinationChunkZ = Math.floorDiv(selectedMarker.blockZ(),16);
        } else if (selectedCol >= 0 && selectedRow >= 0) {
            int scale = KingdomGraphicAtlasData.SCALES[scaleIndex];
            destinationChunkX = centerX - (KingdomGraphicAtlasData.COLUMNS/2)*scale
                    + selectedCol*scale + scale/2;
            destinationChunkZ = centerZ - (KingdomGraphicAtlasData.ROWS/2)*scale
                    + selectedRow*scale + scale/2;
        } else return;
        recomputeRoute();
    }

    private void recomputeRoute() {
        if (minecraft == null || minecraft.player == null || snapshot == null
                || destinationChunkX == null || destinationChunkZ == null) return;
        lastRouteChunkX = minecraft.player.chunkPosition().x;
        lastRouteChunkZ = minecraft.player.chunkPosition().z;
        route = KingdomAtlasNavigator.plan(snapshot,lastRouteChunkX,lastRouteChunkZ,
                destinationChunkX,destinationChunkZ);
    }

    private String routeHint() {
        if(route==null)return "Выберите объект и нажмите Путь";
        return switch(route.status()) {
            case ROUTE -> "Пешком ≈ " + route.estimatedBlocks() + " блоков";
            case ALREADY_THERE -> "Вы у пункта назначения";
            case ZOOM_IN -> "Для пути: масштаб 1 чанк";
            case OUTSIDE_MAP -> "Цель вне видимой области";
            case UNCHARTED -> "Маршрут не исследован";
            case BLOCKED -> "Связного прохода нет";
        };
    }

    private void renderRoute(GuiGraphics gui) {
        if(route==null || route.status()!=KingdomAtlasNavigator.Status.ROUTE) return;
        var steps=route.steps();
        for(int i=1;i<steps.size();i++) {
            var p=steps.get(i-1);var q=steps.get(i);
            int ax=gridX+p.column()*cell+cell/2, ay=gridY+p.row()*cell+cell/2;
            int bx=gridX+q.column()*cell+cell/2, by=gridY+q.row()*cell+cell/2;
            int x1=Math.min(ax,bx),x2=Math.max(ax,bx);
            int y1=Math.min(ay,by),y2=Math.max(ay,by);
            gui.fill(x1-1,y1-1,x2+2,y2+2,0xFF253F67);
            gui.fill(x1,y1,x2+1,y2+1,0xFFFCE27B);
        }
    }

    private static int mix(int left, int right, int percentage) {
        int inv = 100 - percentage;
        int r = ((((left >> 16) & 255) * inv) + (((right >> 16) & 255) * percentage)) / 100;
        int g = ((((left >> 8) & 255) * inv) + (((right >> 8) & 255) * percentage)) / 100;
        int b = (((left & 255) * inv) + ((right & 255) * percentage)) / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Terrain colors are read from real loaded TFC / Minecraft surface blocks. */
    private static int surfaceColor(KingdomGraphicAtlasData.Tile tile) {
        var types = KingdomAtlasTerrain.Type.values();
        int idx = tile.terrain();
        KingdomAtlasTerrain.Type type = idx >= 0 && idx < types.length
                ? types[idx] : KingdomAtlasTerrain.Type.UNKNOWN;
        int base = switch(type) {
            case UNKNOWN -> 0xFFE2D6B9;
            case PLAIN -> 0xFFC1CF96;
            case FOREST -> 0xFF719763;
            case WATER -> 0xFF82B5C2;
            case MOUNTAIN -> 0xFFA3A298;
            case ROAD -> 0xFFB39468;
        };
        // Simple hillshade: coarse elevation, not invented relief geometry.
        if (type == KingdomAtlasTerrain.Type.MOUNTAIN) {
            int shade = Math.max(0, Math.min(26, tile.elevation() / 5));
            return mix(base, 0xFF4E5048, shade);
        }
        return base;
    }

    private static int territoryColor(KingdomGraphicAtlasData.Tile tile,
                                      KingdomGraphicAtlasData.Layer layer) {
        int base = surfaceColor(tile);
        if (tile.planned() && layer == KingdomGraphicAtlasData.Layer.LAND)
            return mix(base, PLAN, 66);
        if (tile.claims() == 0) return base;
        if (tile.mixed()) return mix(base, 0xFFC08A6B, 70);
        if (layer == KingdomGraphicAtlasData.Layer.ENTERPRISES
                || layer == KingdomGraphicAtlasData.Layer.INFRASTRUCTURE)
            return mix(base, 0xFF9D9E88, 24);
        if (layer == KingdomGraphicAtlasData.Layer.LAND && tile.own()) return mix(base, OWN, 65);
        int seed = layer == KingdomGraphicAtlasData.Layer.POLITICAL
                    && !tile.nation().isEmpty() ? tile.nationColor() : tile.townColor();
        int paletteIndex = Math.floorMod(seed, TOWN_PALETTE.length);
        return mix(base, TOWN_PALETTE[paletteIndex], 66);
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        gui.fill(left, top, left + panelWidth, top + panelHeight, 0xFF39291B);
        gui.fill(left + 2, top + 2, left + panelWidth - 2, top + panelHeight - 2, 0xFFEAE0C8);
        // Guild-style navigation column and parchment map frame.
        gui.fill(left + 3,top + 3,left + navWidth + 3,top + panelHeight - 3,0xFF493A2A);
        gui.fill(left + 5,top + 5,left + navWidth + 1,top + panelHeight - 5,0xFF6B553B);
        gui.fill(gridX - 2, gridY - 2, gridX + gridWidth + 2, gridY + gridHeight + 2, INK);
        gui.drawString(font, Component.literal("KINGDOM  |  КАРТЫ"),
                gridX, top + 8, INK, false);
        // Make the currently selected category obvious at a glance.
        int activeTop = panelHeight < 218 ? top + 26 + (layer.ordinal() / 2) * 17
                : top + 27 + layer.ordinal() * 19;
        gui.fill(left + 3,activeTop,left + 6,activeTop + 16,0xFFFFD784);
        if (minecraft != null && minecraft.player != null) {
            gui.drawString(font, Component.literal("Вы: X " + minecraft.player.blockPosition().getX()
                    + "  Z " + minecraft.player.blockPosition().getZ()),
                    gridX, top + panelHeight - 17, INK, false);
        }
        gui.drawString(font, Component.literal("Масштаб: " + KingdomGraphicAtlasData.SCALES[scaleIndex] + " ч/кл"),
                gridX + Math.max(0,gridWidth - 100), top + panelHeight - 17, MUTED, false);
        if (snapshot != null) {
            renderTiles(gui);
            renderSurveyedRoads(gui);
            renderBuildingOutlines(gui);
            renderRoute(gui);
            renderInfrastructureMarkers(gui);
        } else gui.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, FREE);
        // The position and heading of THIS player are rendered every frame,
        // independently of the slower terrain/territory snapshot refresh.
        renderPlayerMarker(gui);

        renderKingdomInspector(gui);
        gui.drawString(font, font.plainSubstrByWidth(routeHint(), gridWidth - 4),
                gridX + 2, gridY + gridHeight + 8, INK, false);
        super.render(gui, mouseX, mouseY, partialTick);
        if (snapshot != null && mouseX >= gridX && mouseX < gridX + gridWidth
                && mouseY >= gridY && mouseY < gridY + gridHeight) {
            int col = (mouseX - gridX) / cell, row = (mouseY - gridY) / cell;
            var tile = snapshot.tile(col,row);
            int x = centerX - (KingdomGraphicAtlasData.COLUMNS / 2) * snapshot.scale()
                    + col * snapshot.scale();
            int z = centerZ - (KingdomGraphicAtlasData.ROWS / 2) * snapshot.scale()
                    + row * snapshot.scale();
            String terrain = switch (KingdomAtlasTerrain.Type.values()[tile.terrain()]) {
                case UNKNOWN -> "местность не загружена";
                case PLAIN -> "равнина";
                case FOREST -> "лес";
                case WATER -> "вода";
                case MOUNTAIN -> "горы";
                case ROAD -> "дорога";
            };
            var mark = infrastructureAt(mouseX, mouseY);
            if (mark != null) {
                gui.renderTooltip(font, Component.literal(
                        mark.label() + " • X " + mark.blockX() + " Z " + mark.blockZ()),
                        mouseX, mouseY);
            } else {
                gui.renderTooltip(font, Component.literal("Чанк " + x + ", " + z
                        + " | " + terrain + " | " + (tile.town().isEmpty() ? "свободно" : tile.town())),
                        mouseX, mouseY);
            }
        }
    }

    /** Right information panel, shared by all map tabs (no separate chest menus). */
    private void renderKingdomInspector(GuiGraphics gui) {
        if (inspectorWidth <= 0) {
            if (snapshot != null && selectedCol >= 0 && selectedRow >= 0) {
                var tile = snapshot.tile(selectedCol,selectedRow);
                gui.drawString(font,font.plainSubstrByWidth(
                        tile.town().isBlank() ? "Свободная земля" : tile.town(),
                        gridWidth - 4),gridX + 2,top + panelHeight - 29,INK,false);
            }
            return;
        }
        int x=gridX+gridWidth+7;
        int w=inspectorWidth-10;
        int bottom=top+panelHeight-48;
        gui.fill(x-3,top+27,x+w+3,bottom+2,0xFFE0D1B2);
        gui.drawString(font,Component.literal("СВЕДЕНИЯ"),x,top+33,INK,false);
        gui.fill(x,top+46,x+w,top+47,0xFFA7906D);

        int y=top+53;
        if(selectedMarker != null) {
            shortInfo(gui,selectedMarker.label(),x,y,w,INK);
            shortInfo(gui,markerTypeName(selectedMarker.kind()),x,y+13,w,MUTED);
            shortInfo(gui,"X "+selectedMarker.blockX(),x,y+27,w,MUTED);
            shortInfo(gui,"Z "+selectedMarker.blockZ(),x,y+40,w,MUTED);
        } else if(snapshot != null && selectedCol >= 0 && selectedRow >= 0) {
            var tile=snapshot.tile(selectedCol,selectedRow);
            shortInfo(gui,tile.town().isEmpty()?"Свободный участок":tile.town(),x,y,w,INK);
            if(!tile.nation().isEmpty())
                shortInfo(gui,tile.nation(),x,y+13,w,MUTED);
            String terrain=switch(KingdomAtlasTerrain.Type.values()[tile.terrain()]){
                case UNKNOWN -> "Не исследовано";
                case PLAIN -> "Равнина";
                case FOREST -> "Лес";
                case WATER -> "Вода";
                case MOUNTAIN -> "Горы";
                case ROAD -> "Дорога";
            };
            shortInfo(gui,terrain,x,y+27,w,MUTED);
            if(tile.enterprises()>0)
                shortInfo(gui,"Предприятий: "+tile.enterprises(),x,y+40,w,INK);
        } else {
            shortInfo(gui,"Выберите область",x,y,w,MUTED);
            shortInfo(gui,"или здание",x,y+13,w,MUTED);
        }

        int legendY=Math.min(bottom-46,top+127);
        if (legendY > y+45) {
            gui.drawString(font,Component.literal("УСЛОВНЫЕ ЗНАКИ"),x,legendY,INK,false);
            legendItem(gui,x,legendY+12,OWN,"Своя земля",w);
            legendItem(gui,x,legendY+24,0xFFC18D55,"Чужая земля",w);
            legendItem(gui,x,legendY+36,PLAN,"План расширения",w);
        }
    }

    private void shortInfo(GuiGraphics gui,String value,int x,int y,int maxWidth,int color){
        gui.drawString(font,font.plainSubstrByWidth(value,Math.max(12,maxWidth)),x,y,color,false);
    }
    private void legendItem(GuiGraphics gui,int x,int y,int color,String name,int w){
        gui.fill(x,y,x+7,y+7,color);
        shortInfo(gui,name,x+10,y,w-10,MUTED);
    }
    private static String markerTypeName(KingdomAtlasInfrastructure.Kind kind){
        return switch(kind){
            case BUILDING -> "Здание";
            case TOWN_HALL -> "Ратуша";
            case ADMINISTRATION -> "Администрация";
            case ENTERPRISE -> "Предприятие";
            case ROAD -> "Дорога";
            case BRIDGE -> "Мост";
        };
    }

    /** Each marker has real world coordinates provided by server registries. */
    private int markerScreenX(KingdomAtlasInfrastructure.Marker marker) {
        long westChunk = (long)centerX - (KingdomGraphicAtlasData.COLUMNS / 2L)
                * KingdomGraphicAtlasData.SCALES[scaleIndex];
        double offset = (marker.blockX() / 16.0 - westChunk)
                / KingdomGraphicAtlasData.SCALES[scaleIndex];
        return gridX + (int)Math.round(offset * cell);
    }

    private int markerScreenY(KingdomAtlasInfrastructure.Marker marker) {
        long northChunk = (long)centerZ - (KingdomGraphicAtlasData.ROWS / 2L)
                * KingdomGraphicAtlasData.SCALES[scaleIndex];
        double offset = (marker.blockZ() / 16.0 - northChunk)
                / KingdomGraphicAtlasData.SCALES[scaleIndex];
        return gridY + (int)Math.round(offset * cell);
    }

    private boolean markerVisible(KingdomAtlasInfrastructure.Marker marker) {
        if (layer == KingdomGraphicAtlasData.Layer.POLITICAL)
            return marker.kind() == KingdomAtlasInfrastructure.Kind.TOWN_HALL
                    || marker.kind() == KingdomAtlasInfrastructure.Kind.ADMINISTRATION;
        if (layer == KingdomGraphicAtlasData.Layer.ENTERPRISES)
            return marker.kind() == KingdomAtlasInfrastructure.Kind.ENTERPRISE;
        return true;
    }

    private int markerColor(KingdomAtlasInfrastructure.Marker marker) {
        return switch (marker.kind()) {
            case TOWN_HALL -> 0xFFFFE19A;
            case ADMINISTRATION -> 0xFF67D6D6;
            case ENTERPRISE -> 0xFFFAD447;
            case BUILDING -> 0xFFF2F2ED;
            case ROAD -> 0xFF9F6941;
            case BRIDGE -> 0xFFB5D6E1;
        };
    }

    /** Draw a coarse road network from genuinely surveyed dirt_path cells. */
    private void renderSurveyedRoads(GuiGraphics gui) {
        if (snapshot == null) return;
        int road = KingdomAtlasTerrain.Type.ROAD.ordinal();
        int paint = 0xFF9A673B, highlight = 0xFFEAC690;
        for (int row = 0; row < KingdomGraphicAtlasData.ROWS; row++) {
            for (int col = 0; col < KingdomGraphicAtlasData.COLUMNS; col++) {
                if (snapshot.tile(col,row).terrain() != road) continue;
                int px = gridX + col * cell + cell / 2;
                int py = gridY + row * cell + cell / 2;
                gui.fill(px - 2,py - 2,px + 3,py + 3,paint);
                gui.fill(px - 1,py - 1,px + 2,py + 2,highlight);
                if (col + 1 < KingdomGraphicAtlasData.COLUMNS
                        && snapshot.tile(col+1,row).terrain() == road) {
                    gui.fill(px,py-2,px+cell+1,py+3,paint);
                    gui.fill(px,py-1,px+cell+1,py+2,highlight);
                }
                if (row + 1 < KingdomGraphicAtlasData.ROWS
                        && snapshot.tile(col,row+1).terrain() == road) {
                    gui.fill(px-2,py-2,px+3,py+cell+1,paint);
                    gui.fill(px-1,py-1,px+2,py+cell+1,highlight);
                }
            }
        }
    }

    private void renderBuildingOutlines(GuiGraphics gui) {
        if(snapshot==null) return;
        int scale=KingdomGraphicAtlasData.SCALES[scaleIndex];
        long west=((long)centerX-(KingdomGraphicAtlasData.COLUMNS/2L)*scale)*16L;
        long north=((long)centerZ-(KingdomGraphicAtlasData.ROWS/2L)*scale)*16L;
        for(var outline:snapshot.outlines()) {
            int x1=gridX+(int)Math.floor(((long)outline.minX()-west)*cell/(16.0*scale));
            int z1=gridY+(int)Math.floor(((long)outline.minZ()-north)*cell/(16.0*scale));
            int x2=gridX+(int)Math.ceil(((long)outline.maxX()+1-west)*cell/(16.0*scale));
            int z2=gridY+(int)Math.ceil(((long)outline.maxZ()+1-north)*cell/(16.0*scale));
            if(x2<=gridX||z2<=gridY||x1>=gridX+gridWidth||z1>=gridY+gridHeight)continue;
            x1=Math.max(gridX,x1);z1=Math.max(gridY,z1);
            x2=Math.min(gridX+gridWidth,x2);z2=Math.min(gridY+gridHeight,z2);
            if(x2<=x1||z2<=z1)continue;
            int stroke=switch(outline.kind()) {
                case ROAD -> 0xFF8C5B35;
                case BRIDGE -> 0xFFDEF1ED;
                case TOWN_HALL -> 0xFFFFDC75;
                case BUILDING, ADMINISTRATION, ENTERPRISE -> 0xFFEFE2BF;
            };
            gui.fill(x1,z1,x2,z1+1,stroke);
            gui.fill(x1,z2-1,x2,z2,stroke);
            gui.fill(x1,z1,x1+1,z2,stroke);
            gui.fill(x2-1,z1,x2,z2,stroke);
        }
    }

    private void renderInfrastructureMarkers(GuiGraphics gui) {
        if (snapshot == null) return;
        for (var mark : snapshot.markers()) {
            if (!markerVisible(mark)) continue;
            int px = markerScreenX(mark), py = markerScreenY(mark);
            if (px < gridX + 3 || py < gridY + 3
                    || px >= gridX + gridWidth - 3 || py >= gridY + gridHeight - 3) continue;
            boolean chosen = mark.equals(selectedMarker);
            int dark = 0xFF352819;
            int tint = markerColor(mark);
            gui.fill(px - 3, py - 3, px + 4, py + 4, dark);
            gui.fill(px - 2, py - 2, px + 3, py + 3, tint);
            if (chosen) {
                gui.fill(px - 4, py - 4, px + 5, py - 3, 0xFFFFFFFF);
                gui.fill(px - 4, py + 4, px + 5, py + 5, 0xFFFFFFFF);
            }
        }
    }

    private KingdomAtlasInfrastructure.Marker infrastructureAt(double mouseX, double mouseY) {
        if (snapshot == null) return null;
        KingdomAtlasInfrastructure.Marker closest = null;
        double bestDistance = 36;
        for (var mark : snapshot.markers()) {
            if (!markerVisible(mark)) continue;
            int px = markerScreenX(mark), py = markerScreenY(mark);
            if (px < gridX + 3 || py < gridY + 3
                    || px >= gridX + gridWidth - 3 || py >= gridY + gridHeight - 3) continue;
            double dx = mouseX - px, dy = mouseY - py;
            double d = dx * dx + dy * dy;
            if (d < bestDistance) { bestDistance = d; closest = mark; }
        }
        return closest;
    }

    private void renderPlayerMarker(GuiGraphics gui) {
        if (minecraft == null || minecraft.player == null) return;
        var player = minecraft.player;
        var projection = KingdomAtlasPlayerMarker.project(
                player.getX(), player.getZ(), player.getYRot(), centerX, centerZ,
                KingdomGraphicAtlasData.SCALES[scaleIndex]);
        int x = gridX + (int)Math.round(projection.gridX() * cell);
        int y = gridY + (int)Math.round(projection.gridY() * cell);
        if (!projection.onMap()) {
            // A marker pinned to the rim signifies DIRECTION, not a false
            // world position. The actual X/Z coordinates remain in the footer.
            x = Math.max(gridX + 4, Math.min(gridX + gridWidth - 5, x));
            y = Math.max(gridY + 4, Math.min(gridY + gridHeight - 5, y));
        }
        // Gold/dark outline contrasts with both water and forest. Point the
        // nose in the player's actual Minecraft yaw direction.
        int outline = 0xFF392315;
        int white = 0xFFFFFFFF;
        int blue = projection.onMap() ? 0xFF1376D3 : 0xFFF4B534;
        gui.fill(x - 4, y - 4, x + 5, y + 5, outline);
        gui.fill(x - 3, y - 3, x + 4, y + 4, white);
        gui.fill(x - 2, y - 2, x + 3, y + 3, blue);
        for (int step = 3; step <= 8; step++) {
            int dx = (int)Math.round(projection.facingX() * step);
            int dy = (int)Math.round(projection.facingY() * step);
            gui.fill(x + dx - 1, y + dy - 1, x + dx + 2, y + dy + 2, outline);
            gui.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, blue);
        }
        // Fixed label: the map is always north-up, not player-rotated.
        gui.drawCenteredString(font, projection.onMap() ? "ВЫ" : "ВНЕ КАРТЫ",
                x, Math.max(gridY + 1, y - 17), outline);
    }

    private void shortText(GuiGraphics gui, String text, int x, int y) {
        if (text.length() > 23) text = text.substring(0, 22) + "…";
        gui.drawString(font, Component.literal(text), x, y, INK, false);
    }

    private void renderTiles(GuiGraphics gui) {
        for (int row = 0; row < KingdomGraphicAtlasData.ROWS; row++) {
            for (int col = 0; col < KingdomGraphicAtlasData.COLUMNS; col++) {
                var tile = snapshot.tile(col,row);
                int x = gridX + col * cell, y = gridY + row * cell;
                int fill = territoryColor(tile, layer);
                gui.fill(x, y, x + cell, y + cell, fill);
                // Separate adjacent sovereign parcels with crisp boundary lines.
                if (col == 0 || different(tile,snapshot.tile(col - 1,row)))
                    gui.fill(x, y, x + 1, y + cell, 0xFF59432F);
                if (row == 0 || different(tile,snapshot.tile(col,row - 1)))
                    gui.fill(x, y, x + cell, y + 1, 0xFF59432F);
                if (tile.enterprises() > 0) {
                    int m = Math.max(2, cell / 3);
                    gui.fill(x + (cell-m)/2, y + (cell-m)/2,
                            x + (cell+m)/2, y + (cell+m)/2, 0xFFEEC75C);
                }
                if (selectedCol == col && selectedRow == row) {
                    gui.fill(x, y, x + cell, y + 1, 0xFFFFFFFF);
                    gui.fill(x, y + cell - 1, x + cell, y + cell, 0xFFFFFFFF);
                    gui.fill(x, y, x + 1, y + cell, 0xFFFFFFFF);
                    gui.fill(x + cell - 1, y, x + cell, y + cell, 0xFFFFFFFF);
                }
            }
        }
        // Render a few readable settlement captions at their visible centroids.
        // Never fabricate villages for empty cells or reveal hidden ore.
        java.util.Map<String, int[]> captions = new java.util.LinkedHashMap<>();
        for (int row = 0; row < KingdomGraphicAtlasData.ROWS; row++) {
            for (int col = 0; col < KingdomGraphicAtlasData.COLUMNS; col++) {
                var tile = snapshot.tile(col,row);
                if (tile.claims() == 0 || tile.town().isBlank()
                        || "Несколько городов".equals(tile.town())) continue;
                String name = layer == KingdomGraphicAtlasData.Layer.POLITICAL
                        && !tile.nation().isBlank() ? tile.nation() : tile.town();
                int[] current = captions.computeIfAbsent(name, unused -> new int[3]);
                current[0] += col; current[1] += row; current[2]++;
            }
        }
        int shown = 0;
        java.util.List<int[]> drawn = new java.util.ArrayList<>();
        for (var caption : captions.entrySet()) {
            int[] area = caption.getValue();
            if (area[2] < 2 || shown >= 5) continue;
            int px = gridX + (area[0] * cell / area[2]) + cell / 2;
            int py = gridY + (area[1] * cell / area[2]) + cell / 2;
            boolean overlap = false;
            for (int[] old : drawn)
                if (Math.abs(px - old[0]) < 80 && Math.abs(py - old[1]) < 16) overlap = true;
            if (overlap) continue;
            String name = caption.getKey();
            if (name.length() > 22) name = name.substring(0, 21) + "…";
            int w = Math.min(gridWidth - 4, font.width(name) + 9);
            px = Math.max(gridX + w / 2 + 2, Math.min(gridX + gridWidth - w / 2 - 2, px));
            gui.fill(px - w / 2, py - 5, px + w / 2, py + 7, 0xD334281F);
            gui.drawCenteredString(font, name, px, py - 3, 0xFFFBEBD0);
            drawn.add(new int[]{px,py}); shown++;
        }
    }
    private boolean different(KingdomGraphicAtlasData.Tile a, KingdomGraphicAtlasData.Tile b) {
        if (a.claims() == 0 && b.claims() == 0) return false;
        if (a.claims() == 0 || b.claims() == 0) return true;
        return layer == KingdomGraphicAtlasData.Layer.POLITICAL
                ? a.nationColor() != b.nationColor()
                : a.townColor() != b.townColor();
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= gridX && mx < gridX + gridWidth
                && my >= gridY && my < gridY + gridHeight) {
            selectedMarker = infrastructureAt(mx, my);
            destinationChunkX = destinationChunkZ = null;
            route = null;
            selectedCol = ((int)mx - gridX) / cell;
            selectedRow = ((int)my - gridY) / cell;
            return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        zoom(delta > 0 ? -1 : 1);
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
