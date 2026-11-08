package kr.blackwiki.smp;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

final class ProgressConfig {
    static void validate(FileConfiguration c){double decay=c.getDouble("monsters.spawn-decay",.94),bonus=c.getDouble("monsters.health-bonus-per-level",.04);if(!Double.isFinite(decay)||decay<=0||decay>=1||!Double.isFinite(bonus)||bonus<0||bonus>1)throw new IllegalArgumentException("monsters.spawn-decay: 0<값<1, health-bonus-per-level: 0~1");double strong=c.getDouble("monsters.strong-attack-damage",6);if(!Double.isFinite(strong)||strong<1||strong>1000)throw new IllegalArgumentException("Invalid strong monster damage threshold");for(String type:c.getStringList("monsters.strong-types"))try{EntityType.valueOf(type);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Unknown strong monster type: "+type);}
        int max=c.getInt("mining.max-level",100),base=c.getInt("mining.base-xp",100),growth=c.getInt("mining.xp-growth",50);if(max<2||max>10000||base<1||base>1000000||growth<0||growth>1000000)throw new IllegalArgumentException("Invalid mining level/XP curve");for(String key:new String[]{"block-xp","ore-xp","rare-ore-xp"}){int amount=c.getInt("mining."+key,1);if(amount<0||amount>1000000)throw new IllegalArgumentException("mining."+key+": 0~1000000");}}
}
