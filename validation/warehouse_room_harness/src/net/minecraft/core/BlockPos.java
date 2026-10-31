package net.minecraft.core;
import java.util.*;
public record BlockPos(int x,int y,int z){
 public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
 public BlockPos immutable(){return this;} public double distSqr(BlockPos p){return (long)(x-p.x)*(x-p.x)+(long)(y-p.y)*(y-p.y)+(long)(z-p.z)*(z-p.z);}
 public BlockPos relative(Direction d){return new BlockPos(x+d.x,y,z+d.z);}
 public static Iterable<BlockPos> betweenClosed(BlockPos a,BlockPos b){var cells=new ArrayList<BlockPos>();for(int x=a.x;x<=b.x;x++)for(int y=a.y;y<=b.y;y++)for(int z=a.z;z<=b.z;z++)cells.add(new BlockPos(x,y,z));return cells;}
 public long asLong(){return ((long)x&0x3ffffff)<<38|((long)z&0x3ffffff)<<12|((long)y&0xfff);} public static BlockPos of(long n){return new BlockPos((int)(n>>38),(int)(n<<52>>52),(int)(n<<26>>38));}
}
