package ru.projectkingdom.map;

/**
 * Pure GUI sizing for the Kingdom atlas. All numbers are Minecraft-scaled
 * pixels, so Java/Forge rendering code stays resolution independent.
 */
public final class KingdomAtlasLayout {
    public record Metrics(int width,int height,int left,int top,
                          int panelWidth,int panelHeight,
                          int navWidth,int inspectorWidth,
                          int gridX,int gridY,int cell,boolean compact) {
        public int mapWidth(){return cell*KingdomGraphicAtlasData.COLUMNS;}
        public int mapHeight(){return cell*KingdomGraphicAtlasData.ROWS;}
        public boolean fits(){
            return left>=0&&top>=0&&left+panelWidth<=width&&top+panelHeight<=height
                    && gridX>=left+navWidth
                    && gridX+mapWidth()<=left+panelWidth
                    && gridY+mapHeight()<top+panelHeight;
        }
    }
    private KingdomAtlasLayout() {}

    public static Metrics calculate(int width,int height) {
        if(width<240||height<210)throw new IllegalArgumentException(
                "Kingdom atlas requires at least a 240x210 scaled GUI area");
        int nav=width<520?98:width<800?124:146;
        int info=width<520?0:width<800?142:178;
        int available=(width-nav-info-38)/KingdomGraphicAtlasData.COLUMNS;
        int cell=Math.max(5,Math.min(22,Math.min(available,
                Math.max(5,(height-64)/KingdomGraphicAtlasData.ROWS))));
        int mapWidth=cell*KingdomGraphicAtlasData.COLUMNS;
        int mapHeight=cell*KingdomGraphicAtlasData.ROWS;
        int panelWidth=nav+mapWidth+info+22;
        int panelHeight=Math.min(height-8,
                Math.max(mapHeight+55,info>0?286:228));
        int left=Math.max(0,(width-panelWidth)/2);
        int top=Math.max(0,(height-panelHeight)/2);
        int gridX=left+nav+8,gridY=top+26;
        boolean compact=panelHeight<276||nav<112;
        return new Metrics(width,height,left,top,panelWidth,panelHeight,
                nav,info,gridX,gridY,cell,compact);
    }
}
