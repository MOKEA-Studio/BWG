package kr.blackwiki.smp;
import org.bukkit.Material;
import java.util.*;
final class FoodBundles {
 record Food(String id,String name,Material material,int units,long price){}
 static final List<Food> ALL=List.of(new Food("food_bread_10","빵 10개",Material.BREAD,10,100),new Food("food_apple_10","사과 10개",Material.APPLE,10,70),new Food("food_pork_10","익힌 돼지고기 10개",Material.COOKED_PORKCHOP,10,120),new Food("food_steak_10","스테이크 10개",Material.COOKED_BEEF,10,120),new Food("food_golden_apple_5","황금사과 5개",Material.GOLDEN_APPLE,5,500),new Food("food_enchanted_apple_5","마법부여 황금사과 5개",Material.ENCHANTED_GOLDEN_APPLE,5,5000));
 static int units(String id){return ALL.stream().filter(f->f.id.equals(id)).mapToInt(Food::units).findFirst().orElse(1);}
 static int delivered(String id,int bundles){if(bundles<1||bundles>64)throw new IllegalArgumentException("수량은 1~64묶음입니다.");return Math.multiplyExact(units(id),bundles);}
}
