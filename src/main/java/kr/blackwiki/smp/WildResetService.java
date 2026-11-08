package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/** Rename-only dimension reset, with a durable journal and rollback before startup world load. */
final class WildResetService implements Listener {
    private final BlackWikiSMP p;
    private final Path journal,container,archives;
    private final String name;
    private long generation;
    private boolean busy,locked;
    private String last="아직 초기화하지 않았습니다.";
    private BukkitTask waiting;
    private static final String MARKER=".blackwikismp-reset";
    WildResetService(BlackWikiSMP p,String name)throws Exception{
        this.p=p;this.name=name;container=Bukkit.getWorldContainer().toPath().toRealPath();archives=container.resolve("backups/BlackWikiSMP/wild-resets");journal=p.getDataFolder().toPath().resolve("wild-reset.yml");recover();
    }
    boolean busy(){return busy||locked;}
    long generation(){return generation;}
    String status(){return locked?"복구 필요 · 야생 입장 잠금":busy?"초기화 진행 중":"대기 · 세대 "+generation+" · "+last;}
    private List<Path> protectedPaths(World except){return Bukkit.getWorlds().stream().filter(w->w!=except).map(w->w.getWorldPath().toAbsolutePath().normalize()).toList();}
    private YamlConfiguration read()throws Exception{YamlConfiguration y=new YamlConfiguration();if(Files.exists(journal))y.load(journal.toFile());return y;}
    private void save(YamlConfiguration y)throws Exception{DataStore.atomic(journal,y.saveToString());}
    private void recover()throws Exception{
        YamlConfiguration y=read();generation=y.getLong("generation",0);if(generation<0)throw new IllegalStateException("Invalid reset generation");String phase=y.getString("phase","idle");if(phase.equals("idle"))return;
        if(!Objects.equals(name,y.getString("world"))||Bukkit.getWorld(name)!=null)throw new IllegalStateException("Unfinished wild reset: configured world changed or world is already loaded. Preserve wild-reset.yml and archives.");
        Path src=Path.of(Objects.requireNonNull(y.getString("source"))),archive=Path.of(Objects.requireNonNull(y.getString("archive")));String id=Objects.requireNonNull(y.getString("id"));ResetPaths.source(src,container,protectedPaths(null),Objects.requireNonNull(y.getString("namespace")),Objects.requireNonNull(y.getString("key")));ResetPaths.archive(archive,archives);
        if(Files.exists(archive)){if(!Files.readString(archive.resolve(MARKER)).equals(id))throw new IllegalStateException("Reset archive marker mismatch");if(Files.exists(src)){Path quarantine=archive.resolveSibling(archive.getFileName()+"-incomplete-"+UUID.randomUUID());ResetPaths.archive(quarantine,archives);Files.move(src,quarantine);}Files.move(archive,src);}
        else if(!Files.isDirectory(src)||!Files.exists(src.resolve(MARKER))||!Files.readString(src.resolve(MARKER)).equals(id))throw new IllegalStateException("Cannot locate original wild dimension for recovery");
        y.set("phase","idle");save(y);last="중단된 초기화를 원래 야생으로 복구했습니다.";p.getLogger().warning(last);
    }
    void request(CommandSender actor,String[] args){if(!BanService.operator(actor)){p.tell(actor,"OP만 사용할 수 있습니다.");return;}if(args.length==1&&args[0].equals("상태")){p.tell(actor,status());return;}if(args.length==1&&args[0].equals("확인")){start(actor);return;}if(args.length>0)throw new IllegalArgumentException("/초기화 [확인|상태]");if(actor instanceof Player a){Gui.Menu m=p.gui.make(a,"야생 구역 초기화 확인");m.inventory.setItem(13,Gui.item(Material.TNT,"야생만 새로 생성","대상: "+p.wildWorld.getName(),"접속자는 스폰으로 이동합니다.","야생 건축물·상자·몹과 홈이 초기화됩니다.","코인·인벤토리·보관함·팀·랭킹은 유지됩니다.","기존 야생 폴더는 복구용으로 보관합니다."));p.gui.button(m,20,Material.LIME_CONCRETE,"확인 · 야생 초기화",this::start);p.gui.button(m,24,Material.RED_CONCRETE,"취소",p.gui::adminMenu);p.gui.show(a,m);}else p.tell(actor,"야생만 초기화하려면 /초기화 확인 · 현재 상태: "+status());}
    void start(CommandSender actor){
        if(!BanService.operator(actor)){p.tell(actor,"OP만 사용할 수 있습니다.");return;}if(busy()){p.tell(actor,"이미 초기화 중이거나 복구 확인이 필요합니다.");return;}if(p.backups.running()){p.tell(actor,"백업이 끝난 뒤 초기화하세요.");return;}
        World target=p.wildWorld;try{if(target==p.spawnWorld||target==p.queue.world||target==Bukkit.getWorlds().getFirst()||target.getEnvironment()!=World.Environment.NORMAL)throw new IllegalStateException("Protected or non-normal world");ResetPaths.source(target.getWorldPath(),container,protectedPaths(target),target.getKey().getNamespace(),target.getKey().getKey());Files.createDirectories(archives);ResetPaths.noLinks(archives);for(World w:Bukkit.getWorlds())if(archives.startsWith(w.getWorldPath().toAbsolutePath().normalize()))throw new IllegalStateException("Reset archives overlap a live world");}
        catch(Exception ex){p.tell(actor,"안전 검사로 초기화를 중단했습니다: "+ex.getMessage());return;}
        if(actor instanceof Player a)a.closeInventory();busy=true;p.travel.prepareReset();for(Player a:new ArrayList<>(target.getPlayers())){a.closeInventory();a.leaveVehicle();a.eject();p.travel.clearCombat(a);if(!a.teleport(p.spawn())){busy=false;p.tell(actor,"플레이어를 스폰으로 이동할 수 없어 중단했습니다.");return;}}
        p.tell(actor,"야생 입장을 잠그고 진행 중인 이동을 정리합니다.");final int[] ticks={0};waiting=Bukkit.getScheduler().runTaskTimer(p,()->{
            if(p.travel.generating()>0){if(ticks[0]++>200){waiting.cancel();waiting=null;busy=false;p.tell(actor,"진행 중인 청크 생성이 끝나지 않아 초기화를 취소했습니다.");}return;}
            waiting.cancel();waiting=null;replace(actor,target);
        },1,1);
    }
    private void replace(CommandSender actor,World target){Path source=target.getWorldPath().toAbsolutePath().normalize(),archive=null;WorldCreator creator=new WorldCreator(name).copy(target);long oldSeed=target.getSeed(),seed;do{seed=new java.security.SecureRandom().nextLong();}while(seed==oldSeed);creator.seed(seed);YamlConfiguration y=new YamlConfiguration();boolean unloaded=false,moved=false;
        try{if(!target.getPlayers().isEmpty())throw new IllegalStateException("Players remain in wild world");ResetPaths.source(source,container,protectedPaths(target),target.getKey().getNamespace(),target.getKey().getKey());archive=archives.resolve(name+"-"+System.currentTimeMillis()+"-"+UUID.randomUUID());ResetPaths.archive(archive,archives);
            if(!Bukkit.unloadWorld(target,true))throw new IllegalStateException("World unload refused");unloaded=true;String id=UUID.randomUUID().toString();DataStore.atomic(source.resolve(MARKER),id);
            y.set("generation",generation);y.set("phase","prepared");y.set("world",name);y.set("source",source.toString());y.set("archive",archive.toString());y.set("namespace",target.getKey().getNamespace());y.set("key",target.getKey().getKey());y.set("id",id);y.set("old-seed",oldSeed);y.set("new-seed",seed);save(y);
            Files.move(source,archive);moved=true;y.set("phase","moved");save(y);Files.createDirectories(source);Path settings=archive.resolve("paper-world.yml");if(Files.isRegularFile(settings))Files.copy(settings,source.resolve("paper-world.yml"));
            World fresh=Bukkit.createWorld(creator);if(fresh==null||fresh.getSeed()!=seed||!fresh.getWorldPath().toAbsolutePath().normalize().equals(source)||!fresh.getKey().equals(target.getKey()))throw new IllegalStateException("Unexpected recreated world identity/seed/path");p.wildWorld=fresh;p.applyBorder(fresh);fresh.save(true);
            long next=Math.addExact(generation,1);for(DataStore.User u:p.data.users.values()){if(u.home!=null&&u.home.getWorld()==target)u.home=null;u.homes.clear();if(u.returnTo!=null&&u.returnTo.getWorld()==target)u.returnTo=p.spawn();Player online=Bukkit.getPlayer(u.id);if(online!=null)u.wildEpoch=next;}p.data.changed();var flush=p.data.flushFuture();if(flush!=null)flush.get(20,java.util.concurrent.TimeUnit.SECONDS);
            y.set("generation",next);y.set("phase","idle");save(y);generation=next;busy=false;last="완료: "+archive.getFileName();p.tell(actor,"야생 초기화 완료. 스폰·플레이어 재산은 유지되었습니다. 복구 폴더: "+archive);p.getLogger().info("Wild reset completed; original dimension archived at "+archive);
        }catch(Exception ex){last="실패: "+ex.getMessage();p.getLogger().log(Level.SEVERE,"Wild reset failed; attempting rename-only rollback",ex);try{
                World current=Bukkit.getWorld(name);if(current!=null&&current!=target&&!Bukkit.unloadWorld(current,false))throw new IllegalStateException("Cannot unload replacement for rollback");
                if(moved){if(Files.exists(source))Files.move(source,archive.resolveSibling(archive.getFileName()+"-incomplete-"+UUID.randomUUID()));Files.move(archive,source);}
                if(unloaded){World restored=Bukkit.createWorld(new WorldCreator(name).copy(creator).seed(oldSeed));if(restored==null)throw new IllegalStateException("Cannot restore original wild world");p.wildWorld=restored;p.applyBorder(restored);}y.set("generation",generation);y.set("phase","idle");save(y);busy=false;p.tell(actor,"초기화에 실패하여 기존 야생으로 복구했습니다. 콘솔 로그를 확인하세요.");
            }catch(Exception rollback){locked=true;busy=false;p.getLogger().log(Level.SEVERE,"Wild rollback needs manual recovery; preserve wild-reset.yml and archives",rollback);p.tell(actor,"복구 확인이 필요하여 야생 입장을 잠갔습니다. 원본과 작업 기록은 보존했습니다.");}}
    }
    @EventHandler(priority=EventPriority.LOW) public void join(PlayerJoinEvent e){Player a=e.getPlayer();DataStore.User u=p.data.user(a);if(u.wildEpoch<generation){if(u.home!=null&&u.home.getWorld()==p.wildWorld)u.home=null;u.homes.clear();if(u.returnTo!=null&&u.returnTo.getWorld()==p.wildWorld)u.returnTo=p.spawn();if(a.getWorld()==p.wildWorld){a.leaveVehicle();a.teleport(p.spawn());}u.wildEpoch=generation;p.data.changed();}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(busy()&&e.getTo().getWorld()==p.wildWorld){e.setCancelled(true);p.tell(e.getPlayer(),"야생 초기화·복구 중에는 입장할 수 없습니다.");}}
}
