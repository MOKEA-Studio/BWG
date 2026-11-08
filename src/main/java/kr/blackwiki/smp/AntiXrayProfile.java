package kr.blackwiki.smp;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
/** Paper-native profiles; tile entities occur only in replacement-blocks, never as fake blocks. */
final class AntiXrayProfile {
 static final String ROOT="anticheat.anti-xray.";
 static final List<String> CONTAINERS;
 static {List<String> c=new ArrayList<>(List.of("chest","trapped_chest","ender_chest","barrel","spawner","shulker_box"));for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"))c.add(color+"_shulker_box");CONTAINERS=List.copyOf(c);}
 static List<String> hidden(World.Environment environment){if(environment==World.Environment.NETHER)return List.of("air","ancient_debris","nether_gold_ore","nether_quartz_ore");if(environment==World.Environment.THE_END)return List.of("air","end_stone","obsidian");return List.of("air","coal_ore","deepslate_coal_ore","copper_ore","deepslate_copper_ore","raw_copper_block","diamond_ore","deepslate_diamond_ore","emerald_ore","deepslate_emerald_ore","gold_ore","deepslate_gold_ore","raw_gold_block","iron_ore","deepslate_iron_ore","raw_iron_block","lapis_ore","deepslate_lapis_ore","redstone_ore","deepslate_redstone_ore");}
 static List<String> replacements(World.Environment environment){List<String> r=new ArrayList<>(CONTAINERS);r.addAll(environment==World.Environment.NETHER?List.of("netherrack","basalt","smooth_basalt","blackstone","soul_sand","soul_soil","gravel"):environment==World.Environment.THE_END?List.of("end_stone","obsidian","purpur_block","purpur_pillar","end_stone_bricks"):List.of("stone","deepslate","granite","andesite","diorite","tuff","calcite","dirt","gravel","clay","sandstone","red_sandstone","terracotta","amethyst_block","budding_amethyst","smooth_basalt"));return List.copyOf(new LinkedHashSet<>(r));}
 static int ceiling(int height){if(height<16||height>4096)throw new IllegalArgumentException("Invalid world height");return Math.multiplyExact(Math.floorDiv(height+15,16),16);}
 static void apply(YamlConfiguration y,World.Environment environment,int height){String q=ROOT;y.set(q+"enabled",true);y.set(q+"engine-mode",3);y.set(q+"max-block-height",ceiling(height));y.set(q+"update-radius",2);y.set(q+"lava-obscures",true);y.set(q+"use-permission",false);y.set(q+"hidden-blocks",hidden(environment));y.set(q+"replacement-blocks",replacements(environment));}
 static boolean matches(YamlConfiguration y,World.Environment environment,int height){YamlConfiguration desired=new YamlConfiguration();apply(desired,environment,height);for(String key:desired.getKeys(true))if(!desired.isConfigurationSection(key)&&!Objects.equals(desired.get(key),y.get(key)))return false;return true;}
}
