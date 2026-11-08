package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class TravelService implements Listener {
    record Combat(UUID opponent,long until){}
    record Request(UUID from,Location anchor,long expires){}
    private final BlackWikiSMP p;
    private final Map<UUID,Combat> combat=new HashMap<>();
    private final Map<UUID,BukkitTask> warmups=new HashMap<>();
    private final Map<UUID,UUID> operations=new HashMap<>();
    private final Map<UUID,Request> requests=new HashMap<>();
    private final Map<String,Long> kills=new HashMap<>();
    private int generating;
    TravelService(BlackWikiSMP p){this.p=p;}
    boolean inCombat(Player a){return combatLeft(a)>0;}
    long combatLeft(Player a){Combat c=combat.get(a.getUniqueId());return c==null?0:Math.max(0,(c.until-System.currentTimeMillis()+999)/1000);}
    void tick(){long now=System.currentTimeMillis();combat.entrySet().removeIf(e->e.getValue().until<now);requests.entrySet().removeIf(e->e.getValue().expires<now);kills.entrySet().removeIf(e->e.getValue()<now);}
    static Player attacker(Entity e){if(e instanceof Player a)return a;if(e instanceof Projectile q&&q.getShooter() instanceof Player a)return a;if(e instanceof TNTPrimed t&&t.getSource() instanceof Player a)return a;if(e instanceof Tameable t&&t.getOwner() instanceof Player a)return a;return null;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageEvent e){if(e.getEntity() instanceof Player a&&e.getFinalDamage()>0)cancel(a,"피해를 받아 이동이 취소되었습니다.");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void combat(EntityDamageByEntityEvent e){if(e.getFinalDamage()>0&&e.getEntity() instanceof Player b){Player a=attacker(e.getDamager());if(a!=null)tag(a,b);}}
    void tag(Player a,Player b){if(a==b||p.queue.waiting(a)||p.queue.waiting(b))return;long until=System.currentTimeMillis()+p.cfg().getLong("combat.tag-seconds",30)*1000;combat.put(a.getUniqueId(),new Combat(b.getUniqueId(),until));combat.put(b.getUniqueId(),new Combat(a.getUniqueId(),until));cancel(a,"전투 시작으로 이동이 취소되었습니다.");cancel(b,"전투 시작으로 이동이 취소되었습니다.");}
    @EventHandler(priority=EventPriority.LOWEST) public void logout(PlayerQuitEvent e){Player a=e.getPlayer();if(inCombat(a)&&p.cfg().getBoolean("combat.logout-death",true)&&!a.isDead()){a.setHealth(0);p.getLogger().info(a.getName()+" died after logging out during combat");}combat.remove(a.getUniqueId());cancel(a,"");requests.remove(a.getUniqueId());requests.entrySet().removeIf(v->v.getValue().from.equals(a.getUniqueId()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void death(PlayerDeathEvent e){
        Player victim=e.getEntity();p.data.user(victim).deaths++;Combat c=combat.remove(victim.getUniqueId());cancel(victim,"");
        Player killer=victim.getKiller();if(killer==null&&c!=null&&c.until>System.currentTimeMillis())killer=Bukkit.getPlayer(c.opponent);
        if(killer!=null&&killer!=victim&&!p.teams.same(killer,victim)&&!p.safe(victim.getLocation())&&!p.queue.waiting(victim)){
            String pair=killer.getUniqueId()+":"+victim.getUniqueId();long now=System.currentTimeMillis();
            if(kills.getOrDefault(pair,0L)<=now){p.data.user(killer).kills++;kills.put(pair,now+p.cfg().getLong("combat.same-victim-kill-cooldown-seconds",300)*1000);p.swords.playerKill(killer,victim,e);p.events.reward(killer,victim);p.tell(killer,"플레이어 킬 +1");}else p.tell(killer,"같은 상대 반복 킬은 잠시 랭킹에 반영되지 않습니다.");
        }p.data.changed();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){
        Player a=e.getPlayer();if(inCombat(a)&&(e.getCause()==PlayerTeleportEvent.TeleportCause.ENDER_PEARL||e.getCause()==PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT)&&p.safe(e.getTo())){e.setCancelled(true);p.tell(a,"전투 중에는 스폰 보호구역으로 이동할 수 없습니다.");}
        p.sync(()->Bukkit.getScheduler().runTask(p,()->{if(a.isOnline())p.checkBorder(a);}));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e){
        if(!inCombat(e.getPlayer())||e.getPlayer().hasPermission("blackwikismp.admin"))return;
        String root=e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);int colon=root.indexOf(':');if(colon>=0)root=root.substring(colon+1);
        if(Set.of("spawn","스폰","로비","wilderness","야생","rtp","home","홈","sethome","홈설정","tpa","tpahere","tpaaccept","tpa수락","tp","teleport","warp","back","suicide","kill").contains(root)){e.setCancelled(true);p.tell(e.getPlayer(),"전투 중 이동·도주 명령어를 사용할 수 없습니다. "+combatLeft(e.getPlayer())+"초");}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void moved(PlayerMoveEvent e){if(!operations.containsKey(e.getPlayer().getUniqueId()))return;if(e.hasChangedPosition()&&(e.getFrom().getWorld()!=e.getTo().getWorld()||e.getFrom().distanceSquared(e.getTo())>0.0001))cancel(e.getPlayer(),"움직여서 이동이 취소되었습니다.");}
    boolean allowed(Player a){if(p.queue.waiting(a)){p.tell(a,"대기열 입장 후 이용하세요.");return false;}if(inCombat(a)){p.tell(a,"전투가 끝난 뒤 이용하세요. "+combatLeft(a)+"초");return false;}if(a.isDead()||a.isInsideVehicle()||!a.getPassengers().isEmpty()){p.tell(a,"살아 있고 탑승하지 않은 상태에서 이용하세요.");return false;}return true;}
    void go(Player a,Location target){go(a,target,target!=null&&p.safe(target)?10:5);}
    void go(Player a,Location target,int seconds){if(target==null){p.tell(a,"목적지가 없습니다.");return;}Location at=target.clone();begin(a,seconds,()->CompletableFuture.completedFuture(at),()->{},true);}
    void warm(Player a,Supplier<CompletableFuture<Location>> target,Runnable success){begin(a,5,target,success,true);}
    void waitAction(Player a,int seconds,Runnable success){begin(a,seconds,()->CompletableFuture.completedFuture(null),success,false);}
    private void begin(Player a,int seconds,Supplier<CompletableFuture<Location>> target,Runnable success,boolean teleport){
        if(!allowed(a))return;if(operations.containsKey(a.getUniqueId())){p.tell(a,"이미 이동을 준비 중입니다.");return;}UUID token=UUID.randomUUID();operations.put(a.getUniqueId(),token);Location origin=a.getLocation().clone();long due=System.nanoTime()+seconds*1_000_000_000L;p.tell(a,seconds+"초 대기 · 움직이거나 피해를 받으면 취소됩니다.");
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(p,()->{
            if(!a.isOnline()||!token.equals(operations.get(a.getUniqueId()))){cancel(a,"");return;}
            if(a.isDead()||a.getWorld()!=origin.getWorld()||a.getLocation().distanceSquared(origin)>.0001||inCombat(a)||p.queue.waiting(a)){cancel(a,"이동이 취소되었습니다.");return;}
            long left=Math.max(0,(due-System.nanoTime()+999999999L)/1_000_000_000L);p.hud.notice(a,"이동·저장까지 "+left+"초 · 움직이면 취소");if(left>0)return;
            BukkitTask self=warmups.remove(a.getUniqueId());if(self!=null)self.cancel();p.hud.clearNotice(a);
            if(!teleport){operations.remove(a.getUniqueId());success.run();return;}
            CompletableFuture<Location> f;try{f=target.get();}catch(Exception ex){cancel(a,"안전한 위치를 찾지 못했습니다.");return;}
            f.orTimeout(30,java.util.concurrent.TimeUnit.SECONDS).whenComplete((l,ex)->p.sync(()->{
                if(!token.equals(operations.get(a.getUniqueId())))return;
                if(ex!=null||l==null||Bukkit.getWorld(l.getWorld().getUID())!=l.getWorld()||l.getWorld()==p.queue.world||p.outside(l)||l.getWorld()==p.wildWorld&&p.resets.busy()){cancel(a,"안전한 목적지가 없습니다.");return;}
                if(!a.isOnline()||a.isDead()||inCombat(a)||p.queue.waiting(a)||a.getWorld()!=origin.getWorld()||a.getLocation().distanceSquared(origin)>.0001){cancel(a,"이동이 취소되었습니다.");return;}
                operations.remove(a.getUniqueId());a.teleportAsync(l).whenComplete((ok,error)->p.sync(()->{if(error==null&&Boolean.TRUE.equals(ok)&&a.isOnline()){success.run();p.tell(a,"이동했습니다.");}else if(a.isOnline())p.tell(a,"이동에 실패했습니다.");}));
            }));
        },1,5);warmups.put(a.getUniqueId(),task);
    }
    void cancel(Player a,String message){UUID id=a.getUniqueId();boolean was=operations.remove(id)!=null;BukkitTask task=warmups.remove(id);if(task!=null)task.cancel();if(was&&p.hud!=null)p.hud.clearNotice(a);if(was&&!message.isBlank()&&a.isOnline())p.tell(a,message);}
    void wild(Player a){if(p.resets.busy()){p.tell(a,"야생 초기화 중입니다.");return;}long cd=p.cfg().getLong("teleport.rtp-cooldown-seconds",60)*1000;long remain=p.data.user(a).rtpAt+cd-System.currentTimeMillis();if(remain>0){p.tell(a,"야생 이동 재사용까지 "+((remain+999)/1000)+"초");return;}warm(a,()->random(0),()->{p.data.user(a).rtpAt=System.currentTimeMillis();p.data.changed();});}
    private CompletableFuture<Location> random(int attempt){
        if(p.resets.busy())return CompletableFuture.completedFuture(null);
        if(attempt>=p.cfg().getInt("teleport.rtp-attempts",16))return CompletableFuture.completedFuture(null);
        if(generating>=p.cfg().getInt("teleport.rtp-max-concurrent",2))return CompletableFuture.failedFuture(new IllegalStateException("RTP busy"));
        generating++;int max=p.cfg().getInt("teleport.rtp-max-radius",9000),min=p.cfg().getInt("teleport.rtp-min-radius",600);java.util.concurrent.ThreadLocalRandom r=java.util.concurrent.ThreadLocalRandom.current();int dx,dz;do{dx=r.nextInt(-max,max+1);dz=r.nextInt(-max,max+1);}while(Math.max(Math.abs(dx),Math.abs(dz))<min);
        final int x=(int)p.cfg().getDouble("border.center-x")+dx,z=(int)p.cfg().getDouble("border.center-z")+dz;
        World source=p.wildWorld;CompletableFuture<Location> result=new CompletableFuture<>();source.getChunkAtAsync(x>>4,z>>4,true).whenComplete((chunk,error)->p.sync(()->{
            generating--;if(p.resets.busy()||p.wildWorld!=source){result.complete(null);return;}if(error!=null){result.completeExceptionally(error);return;}
            int y=p.wildWorld.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES);Location l=new Location(p.wildWorld,x+0.5,y+1,z+0.5);
            Material ground=p.wildWorld.getBlockAt(x,y,z).getType();boolean danger=Set.of(Material.LAVA,Material.WATER,Material.MAGMA_BLOCK,Material.CACTUS,Material.POWDER_SNOW,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.FIRE,Material.SWEET_BERRY_BUSH).contains(ground);
            if(!danger&&ground.isSolid()&&p.wildWorld.getBlockAt(x,y+1,z).isPassable()&&p.wildWorld.getBlockAt(x,y+2,z).isPassable()&&!p.outside(l))result.complete(l);
            else random(attempt+1).whenComplete((next,ex)->{if(ex!=null)result.completeExceptionally(ex);else result.complete(next);});
        }));return result;
    }
    void request(Player a,Player target){if(!allowed(a))return;if(target==null||target==a||p.queue.waiting(target)||!p.data.user(target).tpa){p.tell(a,"이 플레이어는 이동 요청을 받을 수 없습니다.");return;}requests.put(target.getUniqueId(),new Request(a.getUniqueId(),target.getLocation().clone(),System.currentTimeMillis()+p.cfg().getLong("teleport.request-seconds",60)*1000));p.tell(a,"이동 요청을 보냈습니다.");p.tell(target,a.getName()+"님이 이동을 요청했습니다. /tpa수락 또는 /tpa거절");p.gui.tpa(target,a.getUniqueId());}
    void accept(Player a){Request req=requests.remove(a.getUniqueId());if(req==null||req.expires<System.currentTimeMillis()){p.tell(a,"유효한 이동 요청이 없습니다.");return;}Player from=Bukkit.getPlayer(req.from);if(from==null||!allowed(a)){p.tell(a,"요청을 수락할 수 없습니다.");return;}go(from,a.getLocation());}
    void deny(Player a){requests.remove(a.getUniqueId());p.tell(a,"이동 요청을 거절했습니다.");}
    void setHome(Player a){p.homes.set(a,"기본");}
    int generating(){return generating;}
    void clearCombat(Player a){combat.remove(a.getUniqueId());}
    void prepareReset(){close();requests.clear();}
    void close(){for(BukkitTask t:warmups.values())t.cancel();warmups.clear();operations.clear();}
}
