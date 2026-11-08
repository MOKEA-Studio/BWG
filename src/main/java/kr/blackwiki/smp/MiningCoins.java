package kr.blackwiki.smp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

final class MiningCoins {
    static final Map<Material, Long> DEFAULTS;

    private MiningCoins() {
    }

    static Material canonical(Material type) {
        return type.name().startsWith("DEEPSLATE_") && type.name().endsWith("_ORE") ? Material.valueOf((String)type.name().substring("DEEPSLATE_".length())) : type;
    }

    static String label(Material type) {
        return switch (MiningCoins.canonical(type)) {
            case Material.COAL_ORE -> "석탄 광석";
            case Material.COPPER_ORE -> "구리 광석";
            case Material.NETHER_GOLD_ORE -> "네더 금 광석";
            case Material.NETHER_QUARTZ_ORE -> "네더 석영 광석";
            case Material.IRON_ORE -> "철 광석";
            case Material.REDSTONE_ORE -> "레드스톤 광석";
            case Material.LAPIS_ORE -> "청금석 광석";
            case Material.GOLD_ORE -> "금 광석";
            case Material.DIAMOND_ORE -> "다이아몬드 광석";
            case Material.EMERALD_ORE -> "에메랄드 광석";
            case Material.ANCIENT_DEBRIS -> "고대 잔해";
            default -> "일반 블록";
        };
    }

    static long reward(Material type, FileConfiguration c) {
        if (!c.getBoolean("mining.coins.enabled", true) || !type.isBlock() || type.isAir() || Set.of(Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN, Material.BARRIER, Material.BEDROCK, Material.LIGHT, Material.STRUCTURE_VOID).contains(type)) {
            return 0L;
        }
        return MiningCoins.price(type, c);
    }

    static long price(Material type, FileConfiguration c) {
        if (!c.getBoolean("mining.coins.enabled", true)) {
            return 0L;
        }
        Material key = MiningCoins.canonical(type);
        Long ore = DEFAULTS.get(key);
        return ore == null ? c.getLong("mining.coins.block", 2L) : c.getLong("mining.coins.ores." + key.name(), ore.longValue());
    }

    static void validate(FileConfiguration c) {
        ArrayList<String> paths = new ArrayList<String>();
        paths.add("mining.coins.block");
        for (Material material : DEFAULTS.keySet()) {
            paths.add("mining.coins.ores." + material.name());
        }
        for (String string : paths) {
            Number n;
            Object value;
            if (!c.isSet(string) || (value = c.get(string)) instanceof Number && Double.isFinite((n = (Number)value).doubleValue()) && n.doubleValue() == (double)n.longValue() && n.longValue() >= 0L && n.longValue() <= 1000000L) continue;
            throw new IllegalArgumentException(string + ": 정수 0~1000000");
        }
        ConfigurationSection ores = c.getConfigurationSection("mining.coins.ores");
        if (ores != null) {
            for (String key : ores.getKeys(false)) {
                try {
                    if (DEFAULTS.containsKey(Material.valueOf((String)key))) continue;
                    throw new IllegalArgumentException();
                }
                catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Unknown mining coin ore: " + key);
                }
            }
        }
    }

    static {
        LinkedHashMap<Material, Long> values = new LinkedHashMap<Material, Long>();
        values.put(Material.COAL_ORE, 4L);
        values.put(Material.COPPER_ORE, 5L);
        values.put(Material.NETHER_GOLD_ORE, 6L);
        values.put(Material.NETHER_QUARTZ_ORE, 6L);
        values.put(Material.IRON_ORE, 8L);
        values.put(Material.REDSTONE_ORE, 10L);
        values.put(Material.LAPIS_ORE, 12L);
        values.put(Material.GOLD_ORE, 16L);
        values.put(Material.DIAMOND_ORE, 40L);
        values.put(Material.EMERALD_ORE, 60L);
        values.put(Material.ANCIENT_DEBRIS, 100L);
        DEFAULTS = Collections.unmodifiableMap(values);
    }
}

