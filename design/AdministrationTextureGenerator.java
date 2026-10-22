import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class AdministrationTextureGenerator {
    private static final String[] IDS={"town_hall_core","treasury_core","bank_core","central_warehouse_core","city_market_core","land_office_core","court_core","prison_core","guard_administration_core"};
    private static final String[] CENTERS={"bell","chest","gold_ingot","barrel","emerald","map","iron_sword","iron_bars","shield"};
    private static final int[] ACCENTS={0xA33B32,0xD59B32,0x3D8D72,0x8C613C,0xB95778,0x5B8BB2,0xD5C5A0,0x77777F,0x3F618F};
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args.length==0?".":args[0]);BufferedImage sheet=new BufferedImage(16*3,16*3,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<IDS.length;i++){
            String id=IDS[i];BufferedImage image=texture(i);Path texture=root.resolve("src/main/resources/assets/kingdomnpc/textures/block/"+id+".png");
            Files.createDirectories(texture.getParent());ImageIO.write(image,"PNG",texture.toFile());sheet.getGraphics().drawImage(image,(i%3)*16,(i/3)*16,null);
            write(root.resolve("src/main/resources/assets/kingdomnpc/blockstates/"+id+".json"),"{\n  \"variants\": {\"\": {\"model\": \"kingdomnpc:block/"+id+"\"}}\n}\n");
            write(root.resolve("src/main/resources/assets/kingdomnpc/models/block/"+id+".json"),"{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\"all\": \"kingdomnpc:block/"+id+"\"}\n}\n");
            write(root.resolve("src/main/resources/assets/kingdomnpc/models/item/"+id+".json"),"{\n  \"parent\": \"kingdomnpc:block/"+id+"\"\n}\n");
            write(root.resolve("src/main/resources/data/kingdomnpc/recipes/"+id+".json"),recipe(id,CENTERS[i]));
        }
        Path contact=root.resolve("validation/administration_textures_contact_sheet.png");Files.createDirectories(contact.getParent());
        BufferedImage scaled=new BufferedImage(384,384,BufferedImage.TYPE_INT_ARGB);Graphics2D g=scaled.createGraphics();g.drawImage(sheet,0,0,384,384,null);g.dispose();ImageIO.write(scaled,"PNG",contact.toFile());
    }
    private static BufferedImage texture(int index){
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(0x3A2418));g.fillRect(0,0,16,16);g.setColor(new Color(0x68452A));g.fillRect(1,1,14,14);
        g.setColor(new Color(0x8B6238));for(int y=2;y<15;y+=4)g.fillRect(1,y,14,2);g.setColor(new Color(0x2A1B14));for(int y=4;y<15;y+=4)g.drawLine(1,y,14,y);
        g.setColor(new Color(0xD4B873));g.fillRect(3,3,10,10);g.setColor(new Color(0x432C1C));g.drawRect(3,3,9,9);
        g.setColor(new Color(ACCENTS[index]));symbol(g,index);g.dispose();return image;
    }
    private static void symbol(Graphics2D g,int i){switch(i){
        case 0->{g.fillRect(5,7,6,4);g.fillRect(4,5,2,3);g.fillRect(7,4,2,4);g.fillRect(10,5,2,3);}
        case 1->{g.fillRect(5,6,6,6);g.fillRect(4,7,8,2);g.setColor(new Color(0xF2CF55));g.fillRect(7,8,2,2);}
        case 2->{g.fillRect(4,5,8,2);g.fillRect(5,7,2,5);g.fillRect(9,7,2,5);g.fillRect(4,11,8,2);}
        case 3->{g.fillRect(4,5,8,7);g.setColor(new Color(0x432C1C));g.drawLine(4,8,11,8);g.drawLine(7,5,7,11);}
        case 4->{g.fillRect(4,5,8,3);g.fillRect(5,8,6,4);g.setColor(new Color(0xEEE0B2));g.fillRect(6,8,1,4);g.fillRect(9,8,1,4);}
        case 5->{g.fillRect(4,4,8,8);g.setColor(new Color(0xEEE0B2));g.drawLine(5,7,10,7);g.drawLine(8,5,8,10);g.drawLine(5,10,10,5);}
        case 6->{g.fillRect(7,4,2,7);g.fillRect(4,6,8,2);g.fillRect(4,10,3,2);g.fillRect(9,10,3,2);}
        case 7->{g.fillRect(4,4,2,9);g.fillRect(7,4,2,9);g.fillRect(10,4,2,9);g.fillRect(4,6,8,2);}
        case 8->{int[] xs={5,11,10,8,6,4};int[] ys={4,4,9,12,9,4};g.fillPolygon(xs,ys,6);g.setColor(new Color(0xEEE0B2));g.fillRect(7,6,2,4);}
        default->throw new IllegalArgumentException();}}
    private static String recipe(String id,String center){return "{\n  \"type\": \"minecraft:crafting_shaped\",\n  \"pattern\": [\"SWS\", \"WCW\", \"SWS\"],\n  \"key\": {\n    \"S\": {\"item\": \"minecraft:stone_bricks\"},\n    \"W\": {\"item\": \"minecraft:oak_planks\"},\n    \"C\": {\"item\": \"minecraft:"+center+"\"}\n  },\n  \"result\": {\"item\": \"kingdomnpc:"+id+"\"}\n}\n";}
    private static void write(Path path,String text)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,text,StandardCharsets.UTF_8);}
}
