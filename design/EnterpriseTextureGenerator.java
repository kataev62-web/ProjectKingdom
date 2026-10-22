import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Deterministic carved-wood enterprise cores with distinct profession emblems. */
public final class EnterpriseTextureGenerator {
    private EnterpriseTextureGenerator(){}
    public static void main(String[] args)throws Exception{
        Path out=args.length==0?Path.of("src/main/resources/assets/kingdomnpc/textures/block"):Path.of(args[0]);Files.createDirectories(out);
        Map<String,Integer> colors=new LinkedHashMap<>();colors.put("farm_core",0x6E9A45);colors.put("mine_core",0x59636D);
        colors.put("lumber_core",0x3E754B);colors.put("smithy_core",0x9A4938);colors.put("bakery_core",0xD49A45);
        colors.put("pottery_core",0xB96D49);colors.put("construction_core",0xC2A064);colors.put("courier_core",0x4D7FA1);
        char[] marks={'F','M','L','S','B','P','C','R'};int index=0;
        for(var entry:colors.entrySet())write(out.resolve(entry.getKey()+".png"),entry.getValue(),marks[index++]);
    }
    private static void write(Path file,int color,char mark)throws Exception{
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(0x3F291A));g.fillRect(0,0,16,16);g.setColor(new Color(0x76502E));g.fillRect(1,1,14,14);
        g.setColor(new Color(0xA97843));g.fillRect(2,2,12,2);g.fillRect(2,12,12,2);g.fillRect(2,4,2,8);g.fillRect(12,4,2,8);
        g.setColor(new Color(color));g.fillRect(4,4,8,8);g.setColor(new Color(0xF4DFB0));drawMark(g,mark);g.dispose();ImageIO.write(image,"PNG",file.toFile());
    }
    private static void drawMark(Graphics2D g,char mark){
        int[][] glyph=switch(mark){
            case 'F'->new int[][]{{0,0,4,1},{0,1,1,6},{0,3,3,1}};case 'M'->new int[][]{{0,0,1,7},{4,0,1,7},{1,1,1,2},{3,1,1,2},{2,2,1,2}};
            case 'L'->new int[][]{{0,0,1,7},{0,6,5,1}};case 'S'->new int[][]{{0,0,5,1},{0,1,1,2},{0,3,5,1},{4,4,1,2},{0,6,5,1}};
            case 'B'->new int[][]{{0,0,1,7},{0,0,4,1},{0,3,4,1},{0,6,4,1},{4,1,1,2},{4,4,1,2}};case 'P'->new int[][]{{0,0,1,7},{0,0,4,1},{0,3,4,1},{4,1,1,2}};
            case 'C'->new int[][]{{1,0,4,1},{0,1,1,5},{1,6,4,1}};default->new int[][]{{0,0,1,7},{0,0,4,1},{0,3,4,1},{4,1,1,2},{2,4,1,1},{3,5,1,2}};};
        for(int[] r:glyph)g.fillRect(6+r[0],5+r[1],r[2],r[3]);
    }
}
