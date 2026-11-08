package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
final class ArmorSetService implements Listener {
    private final BlackWikiSMP p;private final NamespacedKey health,knockback,slow,phoenix,immortal,immune,serumKey,serumHealth;private boolean reflecting;
    ArmorSetService(BlackWikiSMP p){this.p=p;health=key("guardian-health");knockback=key("guardian-knockback");slow=key("phoenix-slow");phoenix=key("phoenix-cooldown-until");immortal=key("immortal-cooldown-until");immune=key("immortal-immune-until");serumKey=key("giant-serum-uses");serumHealth=key("giant-serum-health");}
    private NamespacedKey key(String name){return new NamespacedKey(p,name);}
    private void modifier(Player a,Attribute attr,NamespacedKey key,double amount,AttributeModifier.Operation op){var value=a.getAttribute(attr);if(value==null)return;var old=value.getModifier(key);if(old!=null&&old.getAmount()==amount&&amount!=0)return;if(old!=null)value.removeModifier(key);if(amount!=0)value.addTransientModifier(new AttributeModifier(key,amount,op));}
    int serumUses(Player a){return Math.clamp(a.getPersistentDataContainer().getOrDefault(serumKey,PersistentDataType.INTEGER,0),0,3);}
    void setSerumUses(Player a,int count){a.getPersistentDataContainer().set(serumKey,PersistentDataType.INTEGER,count);update(a);}
    void update(Player a){ArmorSetItems.Kind kind=ArmorSetItems.equipped(a.getInventory());modifier(a,Attribute.MAX_HEALTH,health,kind==ArmorSetItems.Kind.GUARDIAN?2:0,AttributeModifier.Operation.ADD_NUMBER);modifier(a,Attribute.KNOCKBACK_RESISTANCE,knockback,kind==ArmorSetItems.Kind.GUARDIAN?.1:0,AttributeModifier.Operation.ADD_NUMBER);modifier(a,Attribute.MOVEMENT_SPEED,slow,kind==ArmorSetItems.Kind.PHOENIX?-.03:0,AttributeModifier.Operation.MULTIPLY_SCALAR_1);modifier(a,Attribute.MAX_HEALTH,serumHealth,serumUses(a)*2,AttributeModifier.Operation.ADD_NUMBER);double max=a.getAttribute(Attribute.MAX_HEALTH).getValue();if(!a.isDead()&&a.getHealth()>max)a.setHealth(max);}
    void tick(){for(Player a:Bukkit.getOnlinePlayers())update(a);}
    static boolean proc(double roll){return roll>=0&&roll<.15;}
    static void effect(Player a,PotionEffectType type,int ticks,int amplifier){PotionEffect old=a.getPotionEffect(type);if(old==null||old.getAmplifier()<amplifier||old.getAmplifier()==amplifier&&!old.isInfinite()&&old.getDuration()<ticks)a.addPotionEffect(new PotionEffect(type,ticks,amplifier,false,true,true));}
    boolean exceptional(EntityDamageEvent e){return e.getCause()==EntityDamageEvent.DamageCause.VOID||e.getCause()==EntityDamageEvent.DamageCause.KILL;}
    long cooldownLeft(Player a,ArmorSetItems.Kind kind){return Math.max(0,a.getPersistentDataContainer().getOrDefault(kind==ArmorSetItems.Kind.PHOENIX?phoenix:immortal,PersistentDataType.LONG,0L)-System.currentTimeMillis());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void rescue(EntityDamageEvent e){if(!(e.getEntity() instanceof Player a)||exceptional(e)||p.queue.waiting(a)||e.getFinalDamage()<=0)return;long now=System.currentTimeMillis();if(a.getPersistentDataContainer().getOrDefault(immune,PersistentDataType.LONG,0L)>now){e.setCancelled(true);return;}ArmorSetItems.Kind kind=ArmorSetItems.equipped(a.getInventory());if(kind!=ArmorSetItems.Kind.PHOENIX&&kind!=ArmorSetItems.Kind.IMMORTAL)return;double after=a.getHealth()-e.getFinalDamage();if(cooldownLeft(a,kind)>0||kind==ArmorSetItems.Kind.PHOENIX&&after>4||kind==ArmorSetItems.Kind.IMMORTAL&&after>0)return;
        if(kind==ArmorSetItems.Kind.PHOENIX){a.getPersistentDataContainer().set(phoenix,PersistentDataType.LONG,now+300000);if(after<=0){e.setCancelled(true);a.setHealth(Math.min(2,a.getAttribute(Attribute.MAX_HEALTH).getValue()));}effect(a,PotionEffectType.REGENERATION,60,1);p.tell(a,"불사조의 가호 · 재생 II / 구원 · 재사용 5분");}
        else{e.setCancelled(true);a.setHealth(1);a.getPersistentDataContainer().set(immortal,PersistentDataType.LONG,now+5000);a.getPersistentDataContainer().set(immune,PersistentDataType.LONG,now+3000);effect(a,PotionEffectType.SPEED,60,1);p.tell(a,"불멸자의 구원 · 무적·신속 II 3초 · 재사용 5초");}
        Player attacker=p.newbies.attacker(e);if(attacker!=null)p.travel.tag(attacker,a);a.playEffect(EntityEffect.TOTEM_RESURRECT);a.saveData();
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void reflect(EntityDamageByEntityEvent e){if(reflecting||e.getCause()==EntityDamageEvent.DamageCause.THORNS||e.getFinalDamage()<=0||!(e.getEntity() instanceof Player a)||ArmorSetItems.equipped(a.getInventory())!=ArmorSetItems.Kind.GUARDIAN||!proc(ThreadLocalRandom.current().nextDouble()))return;LivingEntity target=e.getDamager() instanceof LivingEntity living?living:e.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof LivingEntity living?living:null;if(target!=null&&target!=a)rebound(a,target);}
    void rebound(Player a,LivingEntity target){reflecting=true;try{target.damage(1.5,DamageSource.builder(DamageType.THORNS).withCausingEntity(a).withDirectEntity(a).build());}finally{reflecting=false;}}
    @EventHandler public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player a)Bukkit.getScheduler().runTask(p,()->{if(a.isOnline())update(a);});}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(p,()->update(e.getPlayer()));}
    @EventHandler public void respawn(PlayerRespawnEvent e){Bukkit.getScheduler().runTask(p,()->update(e.getPlayer()));}
    void close(){for(Player a:Bukkit.getOnlinePlayers()){modifier(a,Attribute.MAX_HEALTH,health,0,AttributeModifier.Operation.ADD_NUMBER);modifier(a,Attribute.KNOCKBACK_RESISTANCE,knockback,0,AttributeModifier.Operation.ADD_NUMBER);modifier(a,Attribute.MOVEMENT_SPEED,slow,0,AttributeModifier.Operation.MULTIPLY_SCALAR_1);modifier(a,Attribute.MAX_HEALTH,serumHealth,0,AttributeModifier.Operation.ADD_NUMBER);}}
}
