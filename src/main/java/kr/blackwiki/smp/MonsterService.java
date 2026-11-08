package kr.blackwiki.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

final class MonsterService implements Listener {
    private final BlackWikiSMP p;
    private final NamespacedKey level,baseName,visible,eligible,healthBonus,claimed;
    private final Map<UUID,LivingEntity> tracked=new LinkedHashMap<>();
    MonsterService(BlackWikiSMP p){this.p=p;level=new NamespacedKey(p,"mob-level");baseName=new NamespacedKey(p,"mob-original-name");visible=new NamespacedKey(p,"mob-original-visible");eligible=new NamespacedKey(p,"mob-reward-eligible");healthBonus=new NamespacedKey(p,"mob-level-health");claimed=new NamespacedKey(p,"mob-reward-claimed");}
    boolean applies(LivingEntity entity){return entity instanceof Enemy&&!Set.of(EntityType.ENDER_DRAGON,EntityType.WARDEN,EntityType.WITHER).contains(entity.getType())&&entity.getWorld()!=p.queue.world&&!p.safe(entity.getLocation())&&p.cfg().getBoolean("monsters.enabled",true);}
    boolean strong(EntityType type){return p.cfg().getStringList("monsters.strong-types").contains(type.name());}
    boolean strong(LivingEntity entity){var damage=entity.getAttribute(Attribute.ATTACK_DAMAGE);return strong(entity.getType())||damage!=null&&damage.getValue()>=p.cfg().getDouble("monsters.strong-attack-damage",6);}
    int level(LivingEntity entity){return entity.getPersistentDataContainer().getOrDefault(level,PersistentDataType.INTEGER,0);}
    int count(){return tracked.size();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawn(CreatureSpawnEvent e){if(applies(e.getEntity()))attach(e.getEntity(),e.getSpawnReason());}
    void attach(LivingEntity entity,CreatureSpawnEvent.SpawnReason reason){if(!applies(entity))return;var pd=entity.getPersistentDataContainer();int value=level(entity);if(value<1||value>50){value=ProgressRules.mobLevel(java.util.concurrent.ThreadLocalRandom.current().nextDouble(),p.cfg().getDouble("monsters.spawn-decay",.94));pd.set(level,PersistentDataType.INTEGER,value);}
        if(!pd.has(baseName,PersistentDataType.STRING)){pd.set(baseName,PersistentDataType.STRING,entity.customName()==null?"":GsonComponentSerializer.gson().serialize(entity.customName()));pd.set(visible,PersistentDataType.BYTE,(byte)(entity.isCustomNameVisible()?1:0));}
        if(!pd.has(eligible,PersistentDataType.BYTE))pd.set(eligible,PersistentDataType.BYTE,(byte)(reason!=CreatureSpawnEvent.SpawnReason.CUSTOM&&reason!=CreatureSpawnEvent.SpawnReason.SPAWNER_EGG&&reason!=CreatureSpawnEvent.SpawnReason.COMMAND?1:0));
        var health=entity.getAttribute(Attribute.MAX_HEALTH);if(health!=null&&health.getModifier(healthBonus)==null){double ratio=entity.getHealth()/Math.max(1,health.getValue()),amount=health.getValue()*(ProgressRules.healthFactor(value,p.cfg().getDouble("monsters.health-bonus-per-level",.04))-1);if(amount>0)health.addModifier(new AttributeModifier(healthBonus,amount,AttributeModifier.Operation.ADD_NUMBER));entity.setHealth(Math.max(.01,Math.min(health.getValue(),ratio*health.getValue())));}
        tracked.put(entity.getUniqueId(),entity);label(entity);
    }
    void label(LivingEntity entity){if(entity.isDead())return;int value=level(entity);if(value<1||value>50)return;var max=entity.getAttribute(Attribute.MAX_HEALTH);if(max==null)return;String original=entity.getPersistentDataContainer().getOrDefault(baseName,PersistentDataType.STRING,"");Component name=original.isEmpty()?Component.translatable(entity.getType().translationKey()):GsonComponentSerializer.gson().deserialize(original);
        entity.customName(Component.text("[Lv. "+value+"] ",value==50?NamedTextColor.GOLD:NamedTextColor.AQUA).append(name.colorIfAbsent(NamedTextColor.WHITE)).append(Component.text(" ❤ "+number(entity.getHealth())+"/"+number(max.getValue()),NamedTextColor.RED)));entity.setCustomNameVisible(true);
    }
    private static String number(double value){return String.format(Locale.ROOT,"%.1f",Math.max(0,value)).replaceAll("\\.0$","");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof LivingEntity entity&&tracked.containsKey(entity.getUniqueId()))Bukkit.getScheduler().runTask(p,()->{if(entity.isValid()&&!entity.isDead())label(entity);});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void heal(EntityRegainHealthEvent e){if(e.getEntity() instanceof LivingEntity entity&&tracked.containsKey(entity.getUniqueId()))Bukkit.getScheduler().runTask(p,()->{if(entity.isValid()&&!entity.isDead())label(entity);});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void death(EntityDeathEvent e){LivingEntity entity=e.getEntity();tracked.remove(entity.getUniqueId());if(e.isCancelled()||!applies(entity))return;int value=level(entity);Player killer=entity.getKiller();if(value<1||value>50||killer==null||p.queue.waiting(killer)||killer.getGameMode()!=GameMode.SURVIVAL&&killer.getGameMode()!=GameMode.ADVENTURE||!p.vaults.healthy(killer))return;
        if(entity.getPersistentDataContainer().getOrDefault(eligible,PersistentDataType.BYTE,(byte)0)==0)return;if(entity.getPersistentDataContainer().has(claimed,PersistentDataType.BYTE))return;long amount=ProgressRules.mobReward(value,strong(entity));if(amount>0){entity.getPersistentDataContainer().set(claimed,PersistentDataType.BYTE,(byte)1);p.rewards.credit(killer,amount);p.tell(killer,"Lv."+value+" 몬스터 처치 +"+amount+"코인");}}
    @EventHandler public void loaded(EntitiesLoadEvent e){for(Entity entity:e.getEntities())if(entity instanceof LivingEntity living&&applies(living))attach(living,CreatureSpawnEvent.SpawnReason.DEFAULT);}
    @EventHandler public void unloaded(EntitiesUnloadEvent e){for(Entity entity:e.getEntities())tracked.remove(entity.getUniqueId());}
    void refresh(){for(LivingEntity entity:tracked.values())if(entity.isValid())restore(entity);tracked.clear();for(World world:Bukkit.getWorlds())if(world!=p.queue.world)for(LivingEntity entity:world.getLivingEntities())if(applies(entity))attach(entity,CreatureSpawnEvent.SpawnReason.DEFAULT);else if(level(entity)>0)restore(entity);}
    void tick(){for(var it=tracked.entrySet().iterator();it.hasNext();){LivingEntity entity=it.next().getValue();if(!entity.isValid()||entity.isDead()){it.remove();continue;}if(!applies(entity)){restore(entity);it.remove();}else label(entity);}}
    private void restore(LivingEntity entity){var pd=entity.getPersistentDataContainer();String original=pd.get(baseName,PersistentDataType.STRING);if(original!=null)entity.customName(original.isEmpty()?null:GsonComponentSerializer.gson().deserialize(original));entity.setCustomNameVisible(pd.getOrDefault(visible,PersistentDataType.BYTE,(byte)0)==1);var attr=entity.getAttribute(Attribute.MAX_HEALTH);if(attr!=null&&attr.getModifier(healthBonus)!=null){double ratio=entity.getHealth()/attr.getValue();attr.removeModifier(healthBonus);if(!entity.isDead())entity.setHealth(Math.min(attr.getValue(),Math.max(.01,ratio*attr.getValue())));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void named(org.bukkit.event.player.PlayerInteractEntityEvent e){if(!(e.getRightClicked() instanceof LivingEntity living)||!tracked.containsKey(living.getUniqueId()))return;var hand=e.getPlayer().getInventory().getItem(e.getHand());if(hand.getType()!=Material.NAME_TAG||!hand.hasItemMeta()||!hand.getItemMeta().hasDisplayName())return;Component desired=hand.getItemMeta().displayName();Bukkit.getScheduler().runTask(p,()->{if(living.isValid()){living.getPersistentDataContainer().set(baseName,PersistentDataType.STRING,GsonComponentSerializer.gson().serialize(desired));label(living);}});}
    void close(){for(LivingEntity entity:tracked.values())if(entity.isValid())restore(entity);tracked.clear();}
}
