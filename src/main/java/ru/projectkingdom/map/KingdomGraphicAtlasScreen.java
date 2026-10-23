package ru.projectkingdom.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectkingdom.network.GraphicAtlasRequestPacket;
import ru.projectkingdom.network.OpenCityPlanFromAtlasPacket;
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
    private KingdomGraphicAtlasData.Layer layer = KingdomGraphicAtlasData.Layer.LAND;
    private KingdomGraphicAtlasData.Snapshot snapshot;
    private int selectedCol = -1, selectedRow = -1;
    private int left, top, cell, gridX, gridY, gridWidth, gridHeight, panelWidth, panelHeight;

    public KingdomGraphicAtlasScreen() {
        super(Component.literal("Атлас Project Kingdom"));
    }

    @Override protected void init() {
        cell = Math.max(7, Math.min(13, Math.min(
                (width - 180) / KingdomGraphicAtlasData.COLUMNS,
                (height - 65) / KingdomGraphicAtlasData.ROWS)));
        gridWidth = cell * KingdomGraphicAtlasData.COLUMNS;
        gridHeight = cell * KingdomGraphicAtlasData.ROWS;
        panelWidth = gridWidth + 156;
        panelHeight = Math.max(gridHeight + 55, Math.min(260, height - 8));
        left = Math.max(0, (width - panelWidth) / 2);
        top = Math.max(0, (height - panelHeight) / 2);
        gridX = left + 9;
        gridY = top + 26;
        int side = gridX + gridWidth + 10;
        int y = top + 30;

        addRenderableWidget(Button.builder(Component.literal("Земля"),
                button -> setLayer(KingdomGraphicAtlasData.Layer.LAND))
                .bounds(side, y, 45, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Нации"),
                button -> setLayer(KingdomGraphicAtlasData.Layer.POLITICAL))
                .bounds(side + 47, y, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Фирмы"),
                button -> setLayer(KingdomGraphicAtlasData.Layer.ENTERPRISES))
                .bounds(side + 97, y, 45, 18).build());

        addRenderableWidget(Button.builder(Component.literal("−"),
                button -> zoom(-1)).bounds(side, y + 24, 44, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                button -> zoom(1)).bounds(side + 48, y + 24, 44, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Центр"),
                button -> resetCenter()).bounds(side + 96, y + 24, 46, 18).build());

        addRenderableWidget(Button.builder(Component.literal("←"),
                button -> pan(-1,0)).bounds(side, y + 49, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal("↑"),
                button -> pan(0,-1)).bounds(side + 36, y + 49, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal("↓"),
                button -> pan(0,1)).bounds(side + 72, y + 49, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal("→"),
                button -> pan(1,0)).bounds(side + 108, y + 49, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal("План города"),
                button -> KingdomNetwork.CHANNEL.sendToServer(new OpenCityPlanFromAtlasPacket()))
                .bounds(side, top + panelHeight - 28, 83, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"),
                button -> onClose()).bounds(side + 86, top + panelHeight - 28, 56, 18).build());
        if (requestId == 0) resetCenter();
        else request();
    }

    private void resetCenter() {
        if (minecraft == null || minecraft.player == null) return;
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
    }
    @Override public void tick() {
        ticks++;
        if (ticks % 40 == 0) request();
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
        if (layer == KingdomGraphicAtlasData.Layer.ENTERPRISES) return mix(base, 0xFF9D9E88, 24);
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
        gui.fill(gridX - 2, gridY - 2, gridX + gridWidth + 2, gridY + gridHeight + 2, INK);
        gui.drawString(font, Component.literal("ATLAS  •  PROJECT KINGDOM"), left + 10, top + 9, INK, false);
        gui.drawString(font, Component.literal("X " + centerX + "  Z " + centerZ),
                gridX, top + panelHeight - 17, MUTED, false);
        gui.drawString(font, Component.literal("1 клетка = " + KingdomGraphicAtlasData.SCALES[scaleIndex] + " чанков"),
                gridX + gridWidth - 148, top + panelHeight - 17, MUTED, false);
        if (snapshot != null) renderTiles(gui);
        else gui.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, FREE);

        int sx = gridX + gridWidth + 11;
        int sy = gridY + 78;
        gui.drawString(font, Component.literal("Границы и владения"), sx, sy, INK, false);
        // Compact two-column legend keeps the owner inspector visible even
        // on 240px Minecraft GUI height at higher interface scaling.
        gui.fill(sx, sy + 14, sx + 8, sy + 22, OWN);
        gui.drawString(font, Component.literal("Свой"), sx + 10, sy + 14, MUTED, false);
        gui.fill(sx + 76, sy + 14, sx + 84, sy + 22, 0xFFC18D55);
        gui.drawString(font, Component.literal("Чужой"), sx + 86, sy + 14, MUTED, false);
        gui.fill(sx, sy + 27, sx + 8, sy + 35, PLAN);
        gui.drawString(font, Component.literal("План"), sx + 10, sy + 27, MUTED, false);
        gui.fill(sx + 76, sy + 27, sx + 84, sy + 35, FREE);
        gui.drawString(font, Component.literal("Свободно"), sx + 86, sy + 27, MUTED, false);

        int infoY = sy + 46;
        gui.drawString(font, Component.literal("Выбранный участок:"), sx, infoY, INK, false);
        if (snapshot != null && selectedCol >= 0 && selectedRow >= 0) {
            var tile = snapshot.tile(selectedCol, selectedRow);
            shortText(gui, tile.town().isEmpty() ? "Свободная земля" : tile.town(), sx, infoY + 12);
            String terrain = switch (KingdomAtlasTerrain.Type.values()[tile.terrain()]) {
                case UNKNOWN -> "Рельеф не исследован";
                case PLAIN -> "Равнина";
                case FOREST -> "Лес";
                case WATER -> "Вода";
                case MOUNTAIN -> "Горы";
                case ROAD -> "Дорога";
            };
            shortText(gui, tile.nation().isEmpty() ? terrain : tile.nation(), sx, infoY + 24);
            if (tile.enterprises() > 0) shortText(gui,
                    tile.business() + " (" + tile.enterprises() + ")", sx, infoY + 36);
        } else {
            shortText(gui, "Нажмите на область", sx, infoY + 12);
        }
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
            gui.renderTooltip(font, Component.literal("Чанк " + x + ", " + z
                    + " | " + terrain + " | " + (tile.town().isEmpty() ? "свободно" : tile.town())),
                    mouseX, mouseY);
        }
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
