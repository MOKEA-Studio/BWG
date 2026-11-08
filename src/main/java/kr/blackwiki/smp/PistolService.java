package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Server-thread firearm transactions. Every projectile, reload, volley and cooldown is bounded. */
final class PistolService implements Listener,AutoCloseable {
    private record Reload(UUID gun,GunKind kind,int slot,long due){}
    private record Shot(Projectile bullet,UUID owner,GunKind kind,Location origin,long expiry,Volley volley){}
    private static final class Impact {
        final LivingEntity victim;final Projectile bullet;final long due;int pellets;
        Impact(LivingEntity victim,Projectile bullet,long due){this.victim=victim;this.bullet=bullet;this.due=due;this.pellets=1;}
    }
    private static final class Volley {
        final UUID owner;final Map<UUID,Impact> hits=new HashMap<>();final Set<UUID> settled=new HashSet<>();int remaining=5;
        Volley(UUID owner){this.owner=owner;}
    }
    private static final NamespacedKey BULLET=new NamespacedKey("blackwikismp","firearm-projectile");
    private final BlackWikiSMP p;
    private final Map<UUID,Reload> reloads=new HashMap<>();
    private final Map<UUID,Shot> shots=new HashMap<>();
    private final Set<Volley> volleys=new HashSet<>();
    private final Map<UUID,GunKind> applying=new HashMap<>();
    private final Map<UUID,Long> nextShot=new HashMap<>();
    private final Set<UUID> firing=new HashSet<>();
    private final BukkitTask task;
    private long clock;
    private boolean closed;
    PistolService(BlackWikiSMP p){this.p=p;task=Bukkit.getScheduler().runTaskTimer(p,this::tick,1,1);}
    private boolean enabled(){return !closed&&p.cfg().getBoolean("pistol.enabled",true);}
    private boolean base(Player a,boolean message){if(!enabled()){if(message)p.tell(a,"총기 사용이 꺼져 있습니다.");return false;}if(a.getWorld()!=p.wildWorld){if(message)p.tell(a,"총기의 발사·재장전은 약탈 야생 월드에서만 사용할 수 있습니다.");return false;}if(!a.isOnline()||a.isDead()||a.getGameMode()==GameMode.SPECTATOR||p.queue.waiting(a)){if(message)p.tell(a,"게임에 입장한 살아 있는 상태에서 이용하세요.");return false;}return true;}
    private boolean shooting(Player a,boolean message){if(!base(a,message))return false;if(a.getWorld()!=p.wildWorld||p.protection.protectedAt(a.getLocation())||p.outside(a.getLocation())){if(message)p.tell(a,"총기는 약탈 야생 월드에서만 발사할 수 있습니다.");return false;}return true;}
    private boolean target(Player a,LivingEntity victim){if(!shooting(a,false)||victim.getWorld()!=p.wildWorld||victim.getWorld()!=a.getWorld()||victim==a||victim.isDead()||!victim.isValid()||p.protection.protectedAt(victim.getLocation())||p.outside(victim.getLocation())||p.npcs.isNpc(victim))return false;return !(victim instanceof Player b&&(p.queue.waiting(b)||p.teams.friendly(a,b)||p.newbies.blocked(a,b)));}
    private boolean held(Player a,Reload r){var gun=PistolItems.gun(a.getInventory().getItemInMainHand());return a.getInventory().getHeldItemSlot()==r.slot&&gun!=null&&gun.id().equals(r.gun)&&gun.kind()==r.kind;}
    private void refresh(Player a){if(p.hud!=null)p.hud.refresh(a);}
    boolean reload(Player a){
        if(!base(a,true)||reloads.containsKey(a.getUniqueId()))return false;var gun=PistolItems.gun(a.getInventory().getItemInMainHand());if(gun==null){p.tell(a,"정품 총기를 주 손에 드세요.");return false;}
        try{GunRules.reload(gun.kind(),gun.loaded(),PistolInventory.count(a.getInventory().getContents(),gun.kind()));}catch(IllegalStateException ex){p.tell(a,ex.getMessage());return false;}
        reloads.put(a.getUniqueId(),new Reload(gun.id(),gun.kind(),a.getInventory().getHeldItemSlot(),clock+gun.kind().reloadTicks));a.playSound(a.getLocation(),Sound.BLOCK_IRON_TRAPDOOR_OPEN,SoundCategory.PLAYERS,.7f,1.2f);refresh(a);return true;
    }
    private void cancel(Player a){if(reloads.remove(a.getUniqueId())!=null)refresh(a);}
    private Projectile launch(Player a,GunKind kind,org.bukkit.util.Vector direction){
        Projectile bullet;
        if(kind==GunKind.SNIPER)bullet=a.launchProjectile(Arrow.class,direction,s->{s.setShooter(a);s.setGravity(false);s.setPersistent(false);s.setDamage(0);s.setCritical(false);s.setPierceLevel(0);s.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);});
        else bullet=a.launchProjectile(Snowball.class,direction,s->{s.setShooter(a);s.setGravity(false);s.setPersistent(false);s.setItem(new ItemStack(kind.ammoMaterial));});
        bullet.getPersistentDataContainer().set(BULLET,PersistentDataType.BYTE,(byte)1);return bullet;
    }
    boolean fire(Player a){
        if(!shooting(a,true)||!firing.add(a.getUniqueId()))return false;List<Projectile> launched=new ArrayList<>();
        try{
            var gun=PistolItems.gun(a.getInventory().getItemInMainHand());if(gun==null){p.tell(a,"총기 데이터가 올바르지 않습니다.");return false;}GunKind kind=gun.kind();if(reloads.containsKey(a.getUniqueId())||nextShot.getOrDefault(a.getUniqueId(),0L)>clock)return false;
            if(gun.loaded()==0){reload(a);return false;}if(PistolInventory.count(a.getInventory().getContents(),kind)==0){a.playSound(a.getLocation(),Sound.UI_BUTTON_CLICK,.5f,.6f);p.tell(a,kind.ammoName+"이 없습니다. 일반 재료는 사용할 수 없습니다.");refresh(a);return false;}
            if(shots.size()+kind.pellets>PistolRules.MAX_PROJECTILES){p.tell(a,"현재 탄환이 많습니다. 잠시 후 다시 발사하세요.");return false;}
            for(var direction:GunRules.directions(kind,a.getEyeLocation().getDirection())){Projectile bullet=launch(a,kind,direction);launched.add(bullet);if(!bullet.isValid()||bullet.isDead())return false;}
            // Third-party launch listeners can cancel or modify inventory; publish only after all pellets launch.
            var current=PistolItems.gun(a.getInventory().getItemInMainHand());if(current==null||!current.id().equals(gun.id())||current.kind()!=kind||!shooting(a,false)||reloads.containsKey(a.getUniqueId())||shots.size()+kind.pellets>PistolRules.MAX_PROJECTILES)return false;
            ItemStack[] before=a.getInventory().getContents();PistolInventory.Change change;
            try{change=PistolInventory.plan(before,a.getInventory().getHeldItemSlot(),false);}catch(IllegalStateException|IllegalArgumentException ex){return false;}
            try{a.getInventory().setContents(change.contents());}catch(RuntimeException ex){a.getInventory().setContents(before);throw ex;}
            Volley volley=kind==GunKind.SHOTGUN?new Volley(a.getUniqueId()):null;if(volley!=null)volleys.add(volley);
            for(var bullet:launched)shots.put(bullet.getUniqueId(),new Shot(bullet,a.getUniqueId(),kind,bullet.getLocation().clone(),clock+PistolRules.LIFETIME_TICKS,volley));nextShot.put(a.getUniqueId(),clock+kind.fireTicks);launched.clear();
            a.getWorld().playSound(a.getLocation(),Sound.ENTITY_FIREWORK_ROCKET_BLAST,SoundCategory.PLAYERS,.8f,kind==GunKind.SHOTGUN?.65f:1.4f);a.getWorld().playSound(a.getLocation(),Sound.ITEM_CROSSBOW_SHOOT,SoundCategory.PLAYERS,.8f,kind==GunKind.SNIPER?.6f:1.1f);refresh(a);
            if(change.contents()[a.getInventory().getHeldItemSlot()]==null){a.playSound(a.getLocation(),Sound.ENTITY_ITEM_BREAK,1,1);p.tell(a,kind.name+"의 내구도가 다해 파손되었습니다.");}
            else if(change.loaded()==0&&change.reserve()>0)reload(a);return true;
        }finally{for(var bullet:launched)bullet.remove();firing.remove(a.getUniqueId());}
    }
    private void resolve(Shot s){if(s.volley!=null)s.volley.remaining--;}
    private void tick(){
        clock++;if(!enabled()){reloads.clear();for(var s:shots.values())s.bullet.remove();shots.clear();volleys.clear();return;}
        Iterator<Map.Entry<UUID,Reload>> it=reloads.entrySet().iterator();
        while(it.hasNext()){
            var e=it.next();Player a=Bukkit.getPlayer(e.getKey());Reload r=e.getValue();
            if(a==null||!base(a,false)||!held(a,r)){it.remove();if(a!=null)refresh(a);continue;}
            if(clock<r.due)continue;it.remove();
            try{var change=PistolInventory.plan(a.getInventory().getContents(),r.slot,true);a.getInventory().setContents(change.contents());a.playSound(a.getLocation(),Sound.BLOCK_IRON_TRAPDOOR_CLOSE,SoundCategory.PLAYERS,.7f,1.3f);}catch(IllegalStateException|IllegalArgumentException ex){p.tell(a,"재장전 취소: 탄환이나 총기 상태가 변경되었습니다.");}refresh(a);
        }
        var bullets=shots.values().iterator();while(bullets.hasNext()){var s=bullets.next();if(!s.bullet.isValid()||s.bullet.isDead()||clock>=s.expiry||s.bullet.getWorld()!=s.origin.getWorld()||s.bullet.getLocation().distanceSquared(s.origin)>s.kind.range*s.kind.range){s.bullet.remove();resolve(s);bullets.remove();}}
        // One damage event per target/volley avoids vanilla hurt immunity swallowing simultaneous pellets.
        for(var v:List.copyOf(volleys)){
            for(var entry:List.copyOf(v.hits.entrySet())){Impact hit=entry.getValue();if(v.remaining>0&&clock<hit.due)continue;v.hits.remove(entry.getKey());v.settled.add(entry.getKey());Player a=Bukkit.getPlayer(v.owner);if(a!=null)damage(a,hit.victim,hit.bullet,GunKind.SHOTGUN,GunKind.SHOTGUN.damage*Math.min(5,hit.pellets)/5.0);}
            if(v.remaining==0&&v.hits.isEmpty())volleys.remove(v);
        }
        if(clock%100==0)nextShot.entrySet().removeIf(e->e.getValue()<=clock);
    }
    private void damage(Player a,LivingEntity victim,Projectile bullet,GunKind kind,double amount){
        if(!target(a,victim))return;UUID id=bullet.getUniqueId();GunKind previous=applying.put(id,kind);
        try{victim.damage(amount,DamageSource.builder(DamageType.THROWN).withCausingEntity(a).withDirectEntity(bullet).build());}finally{if(previous==null)applying.remove(id);else applying.put(id,previous);}
    }
    /** Cancel native arrow damage; only the authenticated impact above may apply a firearm hit. */
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void projectileDamage(EntityDamageEvent e){
        if(!(e.getDamageSource().getDirectEntity() instanceof Projectile bullet)||!bullet.getPersistentDataContainer().has(BULLET))return;GunKind kind=applying.get(bullet.getUniqueId());if(kind==null){e.setCancelled(true);return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void hit(ProjectileHitEvent e){
        Shot s=shots.remove(e.getEntity().getUniqueId());if(s==null)return;resolve(s);
        try{
            boolean denied=e.isCancelled();e.setCancelled(true);if(denied||!enabled()||clock>=s.expiry||e.getEntity().getWorld()!=s.origin.getWorld()||e.getEntity().getLocation().distanceSquared(s.origin)>s.kind.range*s.kind.range)return;
            Player a=Bukkit.getPlayer(s.owner);if(a==null||!(e.getHitEntity() instanceof LivingEntity victim)||!target(a,victim))return;
            if(s.volley==null)damage(a,victim,s.bullet,s.kind,s.kind.damage);
            else if(!s.volley.settled.contains(victim.getUniqueId())){Impact hit=s.volley.hits.get(victim.getUniqueId());if(hit==null)s.volley.hits.put(victim.getUniqueId(),new Impact(victim,s.bullet,clock+5));else hit.pellets++;}
        }finally{s.bullet.remove();}
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void melee(EntityDamageByEntityEvent e){if(e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK&&e.getDamager() instanceof Player a&&PistolItems.gun(a.getInventory().getItemInMainHand())!=null){e.setCancelled(true);reload(a);}}
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){
        Player a=e.getPlayer();var gun=PistolItems.gun(a.getInventory().getItemInMainHand());
        if(e.getHand()==EquipmentSlot.OFF_HAND){if(gun!=null&&PistolItems.ammo(e.getItem()))e.setCancelled(true);return;}
        if(!PistolItems.tagged(e.getItem())||e.getItem().getType()!=Material.CARROT_ON_A_STICK)return;boolean denied=e.getClickedBlock()!=null&&e.useInteractedBlock()==Event.Result.DENY;
        e.setCancelled(true);e.setUseItemInHand(Event.Result.DENY);if(denied)return;
        if(e.getAction()==Action.LEFT_CLICK_AIR||e.getAction()==Action.LEFT_CLICK_BLOCK)reload(a);
        else if(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK){if(gun!=null&&PistolItems.ammo(a.getInventory().getItemInOffHand(),gun.kind()))reload(a);else fire(a);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){var gun=PistolItems.gun(e.getItemDrop().getItemStack());if(gun==null)return;e.setCancelled(true);Player a=e.getPlayer();Bukkit.getScheduler().runTask(p,()->{var restored=PistolItems.gun(a.getInventory().getItemInMainHand());if(restored!=null&&restored.id().equals(gun.id()))reload(a);});}
    private void normalize(Player a){if(!a.isOnline())return;ItemStack[] items=a.getInventory().getContents();boolean changed=false;for(int i=0;i<items.length;i++){var g=PistolItems.gun(items[i]);if(g==null)continue;var m=(Damageable)items[i].getItemMeta();if(m.isUnbreakable()||!m.hasMaxDamage()||m.getMaxDamage()!=g.kind().durability){items[i]=PistolItems.loaded(items[i],g.loaded());changed=true;}}if(changed)a.getInventory().setContents(items);}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(p,()->normalize(e.getPlayer()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void held(PlayerItemHeldEvent e){cancel(e.getPlayer());Bukkit.getScheduler().runTask(p,()->normalize(e.getPlayer()));}
    @EventHandler public void swap(PlayerSwapHandItemsEvent e){cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){cancel(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){cancel(e.getEntity());}
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();reloads.remove(id);nextShot.remove(id);shots.values().removeIf(s->{if(s.owner.equals(id)){s.bullet.remove();resolve(s);return true;}return false;});volleys.removeIf(v->v.owner.equals(id));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ride(PlayerInteractEntityEvent e){if(PistolItems.tagged(e.getPlayer().getInventory().getItem(e.getHand())))e.setCancelled(true);}
    private static boolean any(ItemStack...items){return Arrays.stream(items).anyMatch(PistolItems::tagged);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void autoCraft(org.bukkit.event.block.CrafterCraftEvent e){if(e.getBlock().getState() instanceof org.bukkit.block.Crafter c&&any(c.getInventory().getContents()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void anvil(PrepareAnvilEvent e){if(any(e.getInventory().getItem(0),e.getInventory().getItem(1)))e.setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void grind(PrepareGrindstoneEvent e){if(any(e.getInventory().getItem(0),e.getInventory().getItem(1)))e.setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void smith(PrepareSmithingEvent e){if(any(e.getInventory().getItem(0),e.getInventory().getItem(1),e.getInventory().getItem(2)))e.setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void craft(PrepareItemCraftEvent e){if(any(e.getInventory().getMatrix()))e.getInventory().setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void takeResult(InventoryClickEvent e){var top=e.getView().getTopInventory();int slot=switch(top.getType()){case ANVIL,GRINDSTONE->2;case SMITHING->3;case CRAFTING,WORKBENCH->0;default->-1;};if(slot<0||e.getRawSlot()!=slot)return;boolean blocked=top instanceof CraftingInventory c?any(c.getMatrix()):any(Arrays.copyOfRange(top.getContents(),0,slot));if(blocked){e.setCancelled(true);top.setItem(slot,null);}}
    String hud(Player a){var gun=PistolItems.gun(a.getInventory().getItemInMainHand());if(gun==null)return "";Reload r=reloads.get(a.getUniqueId());String state=!enabled()?"사용 꺼짐":a.getWorld()!=p.wildWorld?"야생 전용":r!=null?"재장전 "+String.format(Locale.ROOT,"%.1f",Math.max(0,r.due-clock)/20.0)+"초":gun.loaded()+"/"+gun.kind().capacity;return gun.kind().name+": "+state+" · 탄환 "+PistolInventory.count(a.getInventory().getContents(),gun.kind())+"개 · 내구 "+(gun.kind().durability-gun.used());}
    void command(Player a,String[] args){if(args.length>0&&Set.of("재장전","reload").contains(args[0])){reload(a);return;}menu(a);}
    void menu(Player a){Gui.Menu m=p.gui.make(a,"총기 · 탄환 안내");int slot=11;for(var kind:GunKind.values()){m.inventory.setItem(slot,PistolItems.create(kind));slot+=2;}p.gui.button(m,20,Material.IRON_NUGGET,"손에 든 총기 재장전",b->{reload(b);menu(b);},"Q / 좌클릭 /총기 재장전","리볼버 2초 · 샷건 1.5초 · 저격총 2.5초","발사 1개 + 재장전 완료 때 추가 1개 소비");p.gui.button(m,24,Material.EMERALD,"상점 4페이지",b->p.shop.page(b,3),"각 총기와 전용 탄환 구매");m.inventory.setItem(31,Gui.item(Material.PAPER,hud(a).isBlank()?"총기를 주 손에 들어 상태를 확인하세요.":hud(a),"일반 재료·이름만 바꾼 아이템은 사용 불가","전용 탄환은 가방·보조 손에서 사용","야생 월드 전용 · 팀 PvP·신규 보호 적용","내구도 0이면 총기 파손 · 재장전으로 수리 불가"));if(BanService.operator(a))p.gui.button(m,40,Material.LEVER,"OP · 총기 사용: "+Gui.yes(enabled()),b->{if(!BanService.operator(b))return;p.cfg().set("pistol.enabled",!p.cfg().getBoolean("pistol.enabled",true));p.saveConfig();menu(b);},"기존 pistol.enabled 설정을 함께 사용");p.gui.show(a,m);}
    String status(){return (enabled()?"켜짐":"꺼짐")+" · 총기 3종 · 비행 "+shots.size()+" · 재장전 "+reloads.size();}
    @Override public void close(){if(closed)return;closed=true;task.cancel();reloads.clear();nextShot.clear();for(var s:shots.values())s.bullet.remove();shots.clear();volleys.clear();applying.clear();}
}
