package kr.blackwiki.smp;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

final class LampPlan {
    static final List<Material> COLUMN = List.of(Material.STONE_BRICK_WALL, Material.DARK_OAK_FENCE, Material.DARK_OAK_FENCE, Material.GLOWSTONE, Material.STONE_BRICK_SLAB);

    LampPlan() {
    }

    static List<Site> sites() {
        ArrayList<Site> list = new ArrayList<Site>();
        for (int x = -45; x <= 45; x += 10) {
            for (int z = -45; z <= 45; z += 10) {
                list.add(new Site(x, z));
            }
        }
        return List.copyOf(list);
    }

    record Site(int x, int z) {
    }
}

