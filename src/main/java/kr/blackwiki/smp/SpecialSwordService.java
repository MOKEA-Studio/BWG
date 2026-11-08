package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

final class SpecialSwordService implements Listener {
    private final BlackWikiSMP p;
    private record Hit(UUID attacker,MagicSwordItems.Kind kind,int tick){}
    private record Root(Location at,int until){}
    private final Map<UUID,Hit> hits=new HashMap<>();
    private final Map<UUID,Root> roots=new HashMap<>();
    private final NamespacedKey cooldown,rootModifier,enderCooldown;
    private final Map<String,Integer> stormHits=new HashMap<>();
    SpecialSwordService(BlackWikiSMP p){this.p=p;cooldown=new NamespacedKey(p,"angel-cooldown-until");rootModifier=new NamespacedKey(p,"angel-root");enderCooldown=new NamespacedKey(p,"ender-sword-cooldown-until");}
    boolean enemies(Player attacker,Player victim){return attacker!=victim&&!p.teams.same(attacker,victim)&&!p.queue.waiting(attacker)&&!p.queue.waiting(victim)&&!p.safe(attacker.getLocation())&&!p.safe(victim.getLocation());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent e){
        if(!(e.getEntity() instanceof Player victim)||e.getFinalDamage()<=0)return;hits.remove(victim.getUniqueId());
        if(!(e.getDamager() instanceof Player attacker)||e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK&&e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK||!enemies(attacker,victim))return;
        MagicSwordItems.Kind kind=MagicSwordItems.identify(attacker.getInventory().getItemInMainHand());if(kind==null)return;hits.put(victim.getUniqueId(),new Hit(attacker.getUniqueId(),kind,Bukkit.getCurrentTick()));if(kind==MagicSwordItems.Kind.ANGEL)angel(attacker,victim);else if(kind==MagicSwordItems.Kind.STORM)storm(attacker,victim);else if(kind==MagicSwordItems.Kind.ENDER)ender(attacker,victim);
    }
    /** Every 3rd direct hit on the same enemy: cosmetic lightning + 4 armor-ignoring magic damage. */
    static boolean stormProc(int hitNumber){return hitNumber>0&&hitNumber%3==0;}
    private void storm(Player attacker,Player victim){
        String key=attacker.getUniqueId()+":"+victim.getUniqueId();int n=stormHits.merge(key,1,Integer::sum);if(!stormProc(n))return;
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        Bukkit.getScheduler().runTask(p,()->{if(victim.isValid()&&!victim.isDead()&&enemies(attacker,victim))victim.damage(4,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MAGIC).withCausingEntity(attacker).withDirectEntity(attacker).build());});
        attacker.sendActionBar(BlackWikiSMP.text("천둥의 기증 · 방어 무시 4"));
    }
    private void ender(Player attacker,Player victim){
        long now=System.currentTimeMillis();if(attacker.getPersistentDataContainer().getOrDefault(enderCooldown,PersistentDataType.LONG,0L)>now||java.util.concurrent.ThreadLocalRandom.current().nextDouble()>=.25)return;
        attacker.getPersistentDataContainer().set(enderCooldown,PersistentDataType.LONG,now+10000);
        effect(victim,PotionEffectType.LEVITATION,20,0);effect(victim,PotionEffectType.DARKNESS,40,0);
        Location at=victim.getLocation().add(0,1,0);victim.getWorld().spawnParticle(Particle.REVERSE_PORTAL,at,40,.4,.6,.4,.05);victim.getWorld().spawnParticle(Particle.PORTAL,at,50,.5,.8,.5,.5);
        victim.getWorld().playSound(at,Sound.ENTITY_ENDER_DRAGON_FLAP,SoundCategory.PLAYERS,1f,.8f);attacker.sendActionBar(BlackWikiSMP.text("공허의 기증 · 재사용 10초"));
    }
    void playerKill(Player killer,Player victim,PlayerDeathEvent event){Hit hit=hits.remove(victim.getUniqueId());release(victim);if(hit==null||!hit.attacker.equals(killer.getUniqueId())||Bukkit.getCurrentTick()-hit.tick>2||event.getDamageSource().getDirectEntity()!=killer||!enemies(killer,victim))return;
        switch(hit.kind){case ORIUS->{var max=killer.getAttribute(Attribute.MAX_HEALTH);if(max!=null&&!killer.isDead())killer.setHealth(Math.min(max.getValue(),killer.getHealth()+4));effect(killer,PotionEffectType.SPEED,60,0);p.tell(killer,"혈투의 기증 · 체력 2칸 회복 / 신속 I 3초");}case DEMON->p.transfers.steal(killer,victim);case ENDER->{effect(killer,PotionEffectType.ABSORPTION,200,1);killer.getWorld().playSound(killer.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,SoundCategory.PLAYERS,1.2f,1f);killer.getWorld().spawnParticle(Particle.REVERSE_PORTAL,killer.getLocation().add(0,1,0),80,.8,.8,.8,.08);p.tell(killer,"드래곤의 포효 · 흡수 II 10초");}case ANGEL,EXECUTIONER,STORM->{}}
    }
    private static void effect(Player a,PotionEffectType type,int ticks,int amplifier){PotionEffect old=a.getPotionEffect(type);if(old==null||old.getAmplifier()<amplifier||old.getAmplifier()==amplifier&&!old.isInfinite()&&old.getDuration()<ticks)a.addPotionEffect(new PotionEffect(type,ticks,amplifier,false,true,true));}
    long cooldownLeft(Player a){return Math.max(0,a.getPersistentDataContainer().getOrDefault(cooldown,PersistentDataType.LONG,0L)-System.currentTimeMillis());}
    boolean angel(Player attacker,Player victim){if(!enemies(attacker,victim)||cooldownLeft(attacker)>0)return false;attacker.getPersistentDataContainer().set(cooldown,PersistentDataType.LONG,System.currentTimeMillis()+20000);if(!p.backups.running())attacker.saveData();
        roots.put(victim.getUniqueId(),new Root(victim.getLocation().clone(),Bukkit.getCurrentTick()+7));var movement=victim.getAttribute(Attribute.MOVEMENT_SPEED);if(movement!=null&&movement.getModifier(rootModifier)==null)movement.addTransientModifier(new AttributeModifier(rootModifier,-1,AttributeModifier.Operation.MULTIPLY_SCALAR_1));victim.setVelocity(new Vector());p.travel.cancel(victim,"천사의 별 검에 맞아 이동 준비가 취소되었습니다.");effect(attacker,PotionEffectType.RESISTANCE,40,1);attacker.playSound(attacker.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.5f,1.5f);return true;
    }
    boolean rooted(Player a){Root root=roots.get(a.getUniqueId());return root!=null&&root.until-Bukkit.getCurrentTick()>0;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(e instanceof PlayerTeleportEvent||!e.hasChangedPosition())return;Root root=roots.get(e.getPlayer().getUniqueId());if(root==null)return;if(root.until-Bukkit.getCurrentTick()<=0||root.at.getWorld()!=e.getTo().getWorld()){release(e.getPlayer());return;}Location to=root.at.clone();to.setYaw(e.getTo().getYaw());to.setPitch(e.getTo().getPitch());e.setTo(to);e.getPlayer().setVelocity(new Vector());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleported(PlayerTeleportEvent e){release(e.getPlayer());}
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent e){release(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){release(e.getPlayer());hits.remove(e.getPlayer().getUniqueId());hits.entrySet().removeIf(v->v.getValue().attacker.equals(e.getPlayer().getUniqueId()));String id=e.getPlayer().getUniqueId().toString();stormHits.keySet().removeIf(k->k.contains(id));}
    @EventHandler public void death(PlayerDeathEvent e){release(e.getEntity());}
    @EventHandler public void respawn(PlayerRespawnEvent e){release(e.getPlayer());}
    private void release(Player a){roots.remove(a.getUniqueId());var movement=a.getAttribute(Attribute.MOVEMENT_SPEED);if(movement!=null&&movement.getModifier(rootModifier)!=null)movement.removeModifier(rootModifier);}
    void tick(){for(UUID id:List.copyOf(roots.keySet())){Player a=Bukkit.getPlayer(id);if(a==null){roots.remove(id);continue;}if(!rooted(a)||a.isDead())release(a);else a.setVelocity(new Vector());}hits.entrySet().removeIf(e->Bukkit.getCurrentTick()-e.getValue().tick>40);}
    void close(){for(Player a:Bukkit.getOnlinePlayers())release(a);hits.clear();}
}
