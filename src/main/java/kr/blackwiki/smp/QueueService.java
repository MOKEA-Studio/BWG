package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import java.util.*;

final class QueueService implements Listener {
    final World world;
    private final BlackWikiSMP p;
    final Rules.Fifo fifo=new Rules.Fifo();
    private final Set<UUID> releasing=new HashSet<>();
    private final Map<UUID,Integer> displayed=new HashMap<>();
    private final Location stand;
    private int ticks;
    private final int originalMax;
    QueueService(BlackWikiSMP p){
        this.p=p;originalMax=Bukkit.getMaxPlayers();String name=p.cfg().getString("worlds.queue","BlackWikiSMPQueue");
        if(name.equals(p.spawnWorld.getName())||name.equals(p.wildWorld.getName()))throw new IllegalArgumentException("Queue world must be separate");
        World w=Bukkit.getWorld(name);if(w==null)w=Bukkit.createWorld(new WorldCreator(name).generator(new Empty()));
        world=Objects.requireNonNull(w);int y=p.cfg().getInt("queue.platform-y",100);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)world.getBlockAt(x,y-1,z).setType(Material.BARRIER,false);
        stand=new Location(world,0.5,y,0.5);world.setSpawnLocation(stand);
        world.setPVP(false);world.setTime(6000);world.setStorm(false);
        applyCapacity();
    }
    static final class Empty extends ChunkGenerator {
        @Override public ChunkData generateChunkData(World w,Random random,int x,int z,BiomeGrid grid){return createChunkData(w);}
        @Override public boolean shouldGenerateMobs(){return false;}
        @Override public boolean shouldGenerateStructures(){return false;}
    }
    int slots(){return p.cfg().getInt("queue.player-slots",50);}
    int capacity(){return p.cfg().getInt("queue.max-waiting",30);}
    void applyCapacity(){Bukkit.setMaxPlayers(slots()+(p.cfg().getBoolean("queue.enabled",true)?capacity():0));}
    int active(){return (int)Bukkit.getOnlinePlayers().stream().filter(a->!waiting(a)).count();}
    int size(){return fifo.size();}
    boolean waiting(Player a){return fifo.contains(a.getUniqueId());}
    int position(Player a){return fifo.position(a.getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST) public void login(PlayerLoginEvent e){
        if(e.getResult()!=PlayerLoginEvent.Result.ALLOWED&&e.getResult()!=PlayerLoginEvent.Result.KICK_FULL)return;
        boolean full=Bukkit.getOnlinePlayers().size()>=slots()+capacity();
        boolean gamesFull=active()>=slots()&&fifo.size()>=capacity();
        if(p.cfg().getBoolean("queue.enabled",true)&&!e.getPlayer().hasPermission("blackwikismp.queue.bypass")&&(full||gamesFull))e.disallow(PlayerLoginEvent.Result.KICK_FULL,"BlackWikiSMP: 서버와 대기열이 모두 가득 찼습니다.");
        else if(e.getResult()==PlayerLoginEvent.Result.KICK_FULL&&Bukkit.getOnlinePlayers().size()<slots()+(p.cfg().getBoolean("queue.enabled",true)?capacity():0))e.allow();
    }
    void join(Player a){
        if(!p.cfg().getBoolean("queue.enabled",true)||a.hasPermission("blackwikismp.queue.bypass")){recover(a);return;}
        if(active()<=slots()&&fifo.size()==0){recover(a);return;}
        enqueue(a,false);
    }
    private void recover(Player a){
        if(a.getWorld()==world){Location l=p.data.user(a).returnTo;if(l==null||l.getWorld()==world||p.outside(l))l=p.spawn();a.teleport(l);}
        if(!a.hasPlayedBefore())a.teleport(p.spawn());
        p.checkBorder(a);
    }
    void enqueue(Player a,boolean test){
        if(waiting(a))return;
        if(!test&&p.travel!=null&&p.travel.inCombat(a)){p.tell(a,"전투 중에는 대기열에 들어갈 수 없습니다.");return;}
        if(size()>=capacity()){a.kick(BlackWikiSMP.text("서버와 대기열이 가득 찼습니다."));return;}
        if(a.getWorld()!=world&&!p.outside(a.getLocation()))p.data.user(a).returnTo=a.getLocation().clone();
        p.data.tickTime();p.data.pauseSession(a);fifo.add(a.getUniqueId());p.data.changed();p.data.save(true);if(p.travel!=null)p.travel.cancel(a,"대기열 입장");
        a.closeInventory();if(!a.teleport(stand)){fifo.remove(a.getUniqueId());a.kick(BlackWikiSMP.text("대기열 이동 실패. 다시 접속하세요."));return;}
        p.tell(a,"대기열에 입장했습니다. 순서 "+position(a)+" / "+size());
        showPosition(a);
    }
    private void showPosition(Player a){displayed.put(a.getUniqueId(),position(a));a.showTitle(net.kyori.adventure.title.Title.title(BlackWikiSMP.text("현재 나의 대기열 숫자: "+position(a)),BlackWikiSMP.text("전체 대기 "+size()+"명 | 게임 "+active()+"/"+slots()),net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150),java.time.Duration.ofSeconds(11),java.time.Duration.ofMillis(300))));}
    void tick(){
        ticks++;fifo.list().stream().filter(id->Bukkit.getPlayer(id)==null).forEach(fifo::remove);
        while(size()>0&&(active()+releasing.size()<slots()||!p.cfg().getBoolean("queue.enabled",true))){
            UUID id=fifo.peek();Player a=Bukkit.getPlayer(id);if(a==null){fifo.remove(id);continue;}
            if(releasing.contains(id))break;
            releasing.add(id);Location target=p.data.user(a).returnTo;
            if(target==null||target.getWorld()==world||p.outside(target))target=p.spawn();
            final Location destination=target.clone();
            a.teleportAsync(destination).whenComplete((ok,ex)->p.sync(()->{
                releasing.remove(id);if(!a.isOnline()){fifo.remove(id);return;}
                if(ex!=null||!Boolean.TRUE.equals(ok)){p.tell(a,"입장 이동을 다시 시도합니다.");return;}
                fifo.remove(id);displayed.remove(id);p.data.startSession(a);a.clearTitle();p.tell(a,"대기열에서 입장했습니다!");
            }));break;
        }
        for(UUID id:fifo.list()) {Player a=Bukkit.getPlayer(id);if(a==null)continue;if(a.getWorld()!=world&&!releasing.contains(id))a.teleport(stand);if(p.hud!=null)p.hud.refresh(a);if(ticks%10==0||displayed.getOrDefault(id,0)!=position(a))showPosition(a);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void move(PlayerMoveEvent e){if(waiting(e.getPlayer())&&!releasing.contains(e.getPlayer().getUniqueId())&&e.hasChangedPosition()){Location to=stand.clone();to.setYaw(e.getTo().getYaw());to.setPitch(e.getTo().getPitch());e.setTo(to);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(waiting(e.getPlayer())&&!releasing.contains(e.getPlayer().getUniqueId())&&e.getTo().getWorld()!=world)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e){fifo.remove(e.getPlayer().getUniqueId());releasing.remove(e.getPlayer().getUniqueId());displayed.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST) public void command(PlayerCommandPreprocessEvent e){
        if(!waiting(e.getPlayer()))return;String root=e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);int colon=root.indexOf(':');if(colon>=0)root=root.substring(colon+1);
        if(BanService.operator(e.getPlayer())&&Set.of("벤","밴","smpban","디스코드","smpdiscord","최적화","플러그인상태","smp상태","smpstatus").contains(root))return;
        if(!Set.of("queue","대기열","메뉴","blacksmp","blackwikismp","smp","명령어","smpcommands").contains(root)){e.setCancelled(true);p.tell(e.getPlayer(),"대기 중에는 /대기열, /메뉴, /명령어 를 사용할 수 있습니다.");}
    }
    void shutdown(){for(UUID id:fifo.list()){Player a=Bukkit.getPlayer(id);if(a!=null){releasing.add(id);a.teleport(p.spawn());}}Bukkit.setMaxPlayers(originalMax);}
}
