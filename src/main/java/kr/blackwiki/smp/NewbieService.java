package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.util.*;
final class NewbieService implements Listener {
    private final BlackWikiSMP p;private final NamespacedKey eligible,played,owner;private final Map<UUID,Long> warned=new HashMap<>();
    NewbieService(BlackWikiSMP p){this.p=p;eligible=new NamespacedKey(p,"newbie-eligible");played=new NamespacedKey(p,"newbie-played-seconds");owner=new NamespacedKey(p,"pvp-source-owner");}
    void load(Player a,DataStore.User u){var pd=a.getPersistentDataContainer();u.newcomer=pd.has(eligible,PersistentDataType.BYTE)?pd.get(eligible,PersistentDataType.BYTE)==1:!a.hasPlayedBefore();u.newcomerSeconds=Math.max(0,pd.getOrDefault(played,PersistentDataType.LONG,0L));sync(a,u);}
    void sync(Player a,DataStore.User u){a.getPersistentDataContainer().set(eligible,PersistentDataType.BYTE,(byte)(u.newcomer?1:0));a.getPersistentDataContainer().set(played,PersistentDataType.LONG,u.newcomerSeconds);}
    long remaining(Player a){var u=p.data.user(a);return !p.cfg().getBoolean("newbie.enabled",true)||!u.newcomer?0:Math.max(0,p.cfg().getLong("newbie.duration-seconds",900)-u.newcomerSeconds);}
    boolean protectedPlayer(Player a){return remaining(a)>0;}
    private Player player(Entity entity){Player a=TravelService.attacker(entity);if(a!=null)return a;if(entity==null)return null;String value=entity.getPersistentDataContainer().get(owner,PersistentDataType.STRING);try{return value==null?null:Bukkit.getPlayer(UUID.fromString(value));}catch(IllegalArgumentException ex){return null;}}
    Player attacker(EntityDamageEvent e){Player a=player(e.getDamageSource().getCausingEntity());if(a==null&&e instanceof EntityDamageByEntityEvent hit)a=player(hit.getDamager());return a;}
    boolean blocked(Player a,Player b){return a!=null&&b!=null&&a!=b&&(protectedPlayer(a)||protectedPlayer(b));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hit(EntityDamageEvent e){if(!(e.getEntity() instanceof Player b))return;Player a=attacker(e);if(blocked(a,b)){e.setCancelled(true);if(warned.getOrDefault(a.getUniqueId(),0L)<System.currentTimeMillis()){warned.put(a.getUniqueId(),System.currentTimeMillis()+2000);p.tell(a,"신규 보호 중에는 서로 PvP할 수 없습니다. /신규보호");}}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(EntityPlaceEvent e){if(e.getPlayer()!=null)e.getEntity().getPersistentDataContainer().set(owner,PersistentDataType.STRING,e.getPlayer().getUniqueId().toString());}
    static boolean harmful(Collection<PotionEffect> effects){return effects.stream().anyMatch(effect->effect.getType().getCategory()==PotionEffectTypeCategory.HARMFUL);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent e){if(!harmful(e.getPotion().getEffects()))return;Player a=player(e.getPotion());if(a==null)return;for(LivingEntity entity:e.getAffectedEntities())if(entity instanceof Player b&&blocked(a,b))e.setIntensity(b,0);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){var cloud=e.getEntity();Player a=player(cloud);if(a==null&&cloud.getSource() instanceof Player source)a=source;if(a==null)return;List<PotionEffect> effects=new ArrayList<>(cloud.getCustomEffects());if(cloud.getBasePotionType()!=null)effects.addAll(cloud.getBasePotionType().getPotionEffects());if(!harmful(effects))return;Player source=a;e.getAffectedEntities().removeIf(entity->entity instanceof Player b&&blocked(source,b));}
    void giveUp(Player a){var u=p.data.user(a);u.newcomer=false;sync(a,u);a.saveData();p.tell(a,"신규 보호를 영구 포기했습니다.");open(a);}
    void open(Player a){if(p.queue.waiting(a))return;Gui.Menu m=p.gui.make(a,"신규 유저 보호");m.inventory.setItem(22,Gui.item(Material.SHIELD,"남은 보호: "+Rules.duration(remaining(a)),"실제 게임 입장 시간 기준 · 대기·오프라인 제외","플레이어끼리 피해 차단 · 환경·몬스터 피해는 적용"));if(protectedPlayer(a))p.gui.button(m,40,Material.BARRIER,"보호 영구 포기",b->p.gui.confirm(b,"보호 포기 · 되돌릴 수 없음",this::giveUp,this::open));p.gui.show(a,m);}
    @EventHandler public void quit(PlayerQuitEvent e){warned.remove(e.getPlayer().getUniqueId());}
}
