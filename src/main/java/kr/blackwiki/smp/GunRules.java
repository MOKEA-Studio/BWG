package kr.blackwiki.smp;

import org.bukkit.util.Vector;
import java.util.*;

final class GunRules {
    record Plan(int loaded,int reserve){}
    private static void check(GunKind kind,int loaded,int reserve){Objects.requireNonNull(kind);if(loaded<0||loaded>kind.capacity||reserve<0)throw new IllegalArgumentException("Invalid firearm state");}
    static Plan shot(GunKind kind,int loaded,int reserve){check(kind,loaded,reserve);if(loaded==0||reserve==0)throw new IllegalStateException("탄창 또는 전용 탄환이 비어 있습니다.");return new Plan(loaded-1,reserve-1);}
    static Plan reload(GunKind kind,int loaded,int reserve){check(kind,loaded,reserve);if(loaded==kind.capacity)throw new IllegalStateException("이미 "+kind.capacity+"발이 장전되어 있습니다.");if(reserve==0)throw new IllegalStateException(kind.ammoName+" 1개가 필요합니다.");return new Plan(kind.capacity,reserve-1);}
    static List<Vector> directions(GunKind kind,Vector aim){
        Vector forward=aim.clone();if(!Double.isFinite(forward.lengthSquared())||forward.lengthSquared()<1e-12)throw new IllegalArgumentException("Invalid aim");forward.normalize();
        if(kind.pellets==1)return List.of(forward.multiply(kind.speed));
        Vector right=forward.clone().crossProduct(new Vector(0,1,0));if(right.lengthSquared()<1e-8)right=new Vector(1,0,0);right.normalize();Vector up=right.clone().crossProduct(forward).normalize();
        double[][] offsets={{0,0},{-.15,.04},{.15,.04},{-.09,-.07},{.09,-.07}};List<Vector> rays=new ArrayList<>();
        for(double[] o:offsets)rays.add(forward.clone().add(right.clone().multiply(o[0])).add(up.clone().multiply(o[1])).normalize().multiply(kind.speed));return List.copyOf(rays);
    }
}
