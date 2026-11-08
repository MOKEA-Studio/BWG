package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.damage.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import java.util.*;
final class ExecutionerService implements Listener {
    private final BlackWikiSMP p;private final Set<UUID> fixed=new HashSet<>();
    ExecutionerService(BlackWikiSMP p){this.p=p;}
    static boolean threshold(double health,double max){return max>0&&health>=max*.5;}
    boolean applying(Player a){return fixed.contains(a.getUniqueId());}
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player a)||!applying(a))return;e.setDamage(3);for(EntityDamageEvent.DamageModifier modifier:EntityDamageEvent.DamageModifier.values())if(modifier!=EntityDamageEvent.DamageModifier.BASE&&e.isApplicable(modifier))e.setDamage(modifier,0);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent e){if(!(e.getEntity() instanceof Player victim)||!(e.getDamager() instanceof Player a)||applying(victim)||e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK||e.getFinalDamage()<=0||MagicSwordItems.identify(a.getInventory().getItemInMainHand())!=MagicSwordItems.Kind.EXECUTIONER||!p.swords.enemies(a,victim)||p.newbies.blocked(a,victim)||!threshold(victim.getHealth(),victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()))return;
        Bukkit.getScheduler().runTask(p,()->{if(!a.isOnline()||!victim.isOnline()||a.isDead()||victim.isDead()||!p.swords.enemies(a,victim)||p.newbies.blocked(a,victim)||a.getWorld()!=victim.getWorld()||a.getLocation().distanceSquared(victim.getLocation())>100)return;strike(a,victim);});
    }
    void strike(Player a,Player victim){int old=victim.getNoDamageTicks();fixed.add(victim.getUniqueId());try{victim.setNoDamageTicks(0);victim.damage(3,DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(a).withDirectEntity(a).build());}finally{fixed.remove(victim.getUniqueId());if(!victim.isDead())victim.setNoDamageTicks(Math.max(old,victim.getNoDamageTicks()));}}
}
