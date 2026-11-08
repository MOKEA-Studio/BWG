package kr.blackwiki.smp;
import org.bukkit.Material;
import java.util.*;
/** Deterministic 100 x 100 garden. Coordinates are relative to the plaza floor. */
final class SpawnPlan {
    static final int VERSION=1,WIDTH=100;
    record Pos(int x,int y,int z){}
    record Voxel(int x,int y,int z,Material type,String data){}
    private final Map<Pos,Voxel> voxels=new LinkedHashMap<>();
    private void put(int x,int y,int z,Material type){if(x< -50||x>49||z< -50||z>49)throw new IllegalArgumentException("Spawn outside footprint");String data=type.name().endsWith("_LEAVES")?"minecraft:"+type.name().toLowerCase(Locale.ROOT)+"[persistent=true,distance=1]":null;voxels.put(new Pos(x,y,z),new Voxel(x,y,z,type,data));}
    static List<Voxel> build(){SpawnPlan p=new SpawnPlan();p.garden();return p.voxels.values().stream().sorted(Comparator.<Voxel>comparingInt(v->v.type==Material.WATER?1:0).thenComparingInt(Voxel::y).thenComparingInt(Voxel::x).thenComparingInt(Voxel::z)).toList();}
    private void garden(){
        for(int x=-50;x<50;x++)for(int z=-50;z<50;z++){
            if(x>=-42&&x<42&&z>=-42&&z<42)put(x,-3,z,Material.DEEPSLATE_BRICKS);
            if(x>=-46&&x<46&&z>=-46&&z<46)put(x,-2,z,Material.ANDESITE);
            if(x>=-49&&x<49&&z>=-49&&z<49)put(x,-1,z,Material.STONE_BRICKS);
            boolean frame=x< -46||x>45||z< -46||z>45;int radius=x*x+z*z;
            Material floor=frame?Material.POLISHED_DEEPSLATE:Math.abs(x)<=3||Math.abs(z)<=3?((x+z)%6==0?Material.CHISELED_STONE_BRICKS:Material.STONE_BRICKS):Math.abs(x)==4||Math.abs(z)==4?Material.SMOOTH_QUARTZ:Material.MOSS_BLOCK;
            if(radius<=100)floor=radius<16?Material.QUARTZ_BRICKS:radius>80?Material.POLISHED_BLACKSTONE:((x+z)&1)==0?Material.SMOOTH_QUARTZ:Material.CALCITE;
            put(x,0,z,floor);
            if(x==-50||x==49||z==-50||z==49){put(x,1,z,Material.STONE_BRICKS);put(x,2,z,Material.AZALEA_LEAVES);}
            if(!frame&&floor==Material.MOSS_BLOCK&&Math.floorMod(x*37+z*71,79)==0)put(x,1,z,((x+z)&1)==0?Material.PINK_TULIP:Material.WHITE_TULIP);
        }
        for(int x:new int[]{-25,25})for(int z:new int[]{-25,25})fountain(x,z);
        int[][] trees={{-38,-38},{-38,-12},{-12,-38},{12,-38},{38,-38},{38,-12},{38,12},{38,38},{12,38},{-12,38},{-38,38},{-38,12}};
        for(int i=0;i<trees.length;i++)tree(trees[i][0],trees[i][1],i%2==0);
        for(int x:new int[]{-8,8})for(int z:new int[]{-40,-20,20,40})lamp(x,z);
        for(int z:new int[]{-8,8})for(int x:new int[]{-40,-20,20,40})lamp(x,z);
        for(int x:new int[]{-13,13})for(int z:new int[]{-10,10})bench(x,z);
        pavilion();
        for(int x:new int[]{-43,42})for(int z:new int[]{-43,42}){put(x,1,z,Material.CHISELED_STONE_BRICKS);put(x,2,z,Material.SEA_LANTERN);put(x,3,z,Material.STONE_BRICK_SLAB);}
    }
    private void fountain(int cx,int cz){for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){int d=x*x+z*z;if(d>49)continue;put(cx+x,-1,cz+z,((x+z)&1)==0?Material.PRISMARINE_BRICKS:Material.SEA_LANTERN);if(d>=36){put(cx+x,0,cz+z,Material.QUARTZ_BRICKS);put(cx+x,1,cz+z,Material.SMOOTH_QUARTZ_SLAB);}else put(cx+x,0,cz+z,Material.WATER);}
        for(int y=1;y<=3;y++)put(cx,y,cz,Material.CHISELED_QUARTZ_BLOCK);put(cx,4,cz,Material.SEA_LANTERN);put(cx,5,cz,Material.WATER);
    }
    private void tree(int cx,int cz,boolean cherry){Material leaves=cherry?Material.CHERRY_LEAVES:Material.OAK_LEAVES;for(int y=4;y<=8;y++){int radius=y==8?1:y==7?2:3;for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(x*x+z*z<=radius*radius+1)put(cx+x,y,cz+z,leaves);}for(int y=1;y<=6;y++)put(cx,y,cz,cherry?Material.CHERRY_LOG:Material.OAK_LOG);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||Math.abs(z)==2)put(cx+x,1,cz+z,Material.FLOWERING_AZALEA_LEAVES);}
    private void lamp(int cx,int cz){put(cx,1,cz,Material.STONE_BRICK_WALL);for(int y=2;y<=3;y++)put(cx,y,cz,Material.DARK_OAK_FENCE);put(cx,4,cz,Material.LANTERN);}
    private void bench(int cx,int cz){for(int x=-2;x<=2;x++){put(cx+x,1,cz,Material.SPRUCE_SLAB);put(cx+x,1,cz+1,Material.SPRUCE_PLANKS);}put(cx-3,1,cz,Material.POTTED_FLOWERING_AZALEA_BUSH);put(cx+3,1,cz,Material.LANTERN);}
    private void pavilion(){for(int x:new int[]{-7,7})for(int z:new int[]{-7,7}){put(x,1,z,Material.CHISELED_STONE_BRICKS);for(int y=2;y<=5;y++)put(x,y,z,Material.STRIPPED_SPRUCE_LOG);}
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(Math.abs(x)==8||Math.abs(z)==8)put(x,6,z,Material.DARK_OAK_PLANKS);
        for(int y=7;y<=9;y++){int radius=9-(y-7)*2;for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)put(x,y,z,y==9?Material.WAXED_CUT_COPPER_SLAB:Material.WAXED_CUT_COPPER);}
        put(0,10,0,Material.SEA_LANTERN);put(0,11,0,Material.LIGHTNING_ROD);
        for(int x:new int[]{-6,6})for(int z:new int[]{-6,6}){put(x,6,z,Material.IRON_CHAIN);put(x,5,z,Material.IRON_CHAIN);put(x,4,z,Material.LANTERN);voxels.put(new Pos(x,4,z),new Voxel(x,4,z,Material.LANTERN,"minecraft:lantern[hanging=true]"));}
    }
}
