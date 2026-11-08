package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
final class EventService implements Listener {
    private final BlackWikiSMP p;private final Path file;private long until;private final Map<UUID,Long> songs=new HashMap<>();static final Sound MUSIC=Sound.MUSIC_DISC_CAT;
    EventService(BlackWikiSMP p)throws Exception{this.p=p;file=p.getDataFolder().toPath().resolve("event.yml");if(Files.exists(file)){YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());if(y.getInt("schema")!=1||y.getLong("until")<0)throw new IllegalArgumentException("Invalid event.yml");until=y.getLong("until");}}
    boolean active(){return until>System.currentTimeMillis();}
    long remaining(){return Math.max(0,(until-System.currentTimeMillis()+999)/1000);}
    String status(){return active()?"드롭·PvP 코인 2배 · "+Rules.duration(remaining()):"진행 중인 이벤트 없음";}
    private void save(long value)throws Exception{YamlConfiguration y=new YamlConfiguration();y.set("schema",1);y.set("until",value);DataStore.atomic(file,y.saveToString());until=value;}
    void start(CommandSender actor,long seconds){if(!BanService.operator(actor)){p.tell(actor,"OP만 사용할 수 있습니다.");return;}if(seconds<1||seconds>604800)throw new IllegalArgumentException("이벤트는 1초~7일입니다.");if(active()){p.tell(actor,"이미 이벤트가 진행 중입니다. /이벤트 · /관리자전용이벤트끄기");return;}try{save(Math.addExact(System.currentTimeMillis(),Math.multiplyExact(seconds,1000)));broadcast("이벤트 시작! 몬스터 전리품 수량 2배 · PvP 코인 2배 · "+Rules.duration(seconds));Fx.announce(p,"⚡ 2배 이벤트 시작","몬스터 전리품·PvP 코인 2배 · "+Rules.duration(seconds),Fx.ENDER);for(Player a:Bukkit.getOnlinePlayers())if(!p.queue.waiting(a))Fx.enderBurst(a.getLocation());tick();}catch(Exception ex){p.tell(actor,"이벤트를 저장하지 못했습니다.");p.getLogger().log(java.util.logging.Level.SEVERE,"Event start save failed",ex);}}
    void stop(CommandSender actor){if(!BanService.operator(actor)){p.tell(actor,"OP만 사용할 수 있습니다.");return;}finish();}
    private void finish(){try{save(0);stopMusic();broadcast("2배 이벤트가 종료되었습니다.");Fx.announce(p,"이벤트 종료","2배 이벤트가 끝났습니다. 수고하셨습니다!",Fx.GOLD);}catch(Exception ex){p.getLogger().log(java.util.logging.Level.SEVERE,"Event end save failed",ex);stopMusic();}}
    private void broadcast(String value){for(Player a:Bukkit.getOnlinePlayers())p.tell(a,value);p.getLogger().info(value);}
    void tick(){if(!active()){if(until!=0)finish();return;}long now=System.currentTimeMillis();for(Player a:Bukkit.getOnlinePlayers())if(!p.queue.waiting(a)&&!a.isDead()&&songs.getOrDefault(a.getUniqueId(),0L)<=now)play(a);}
    private void play(Player a){a.stopSound(MUSIC,SoundCategory.RECORDS);a.playSound(a.getLocation(),MUSIC,SoundCategory.RECORDS,.5f,1);songs.put(a.getUniqueId(),System.currentTimeMillis()+185000);}
    private void stopMusic(){for(Player a:Bukkit.getOnlinePlayers())a.stopSound(MUSIC,SoundCategory.RECORDS);songs.clear();}
    boolean reward(Player killer,Player victim){if(killer==victim||p.teams.same(killer,victim)||p.queue.waiting(killer)||p.queue.waiting(victim)||p.safe(killer.getLocation())||p.safe(victim.getLocation())||p.newbies.protectedPlayer(killer)||p.newbies.protectedPlayer(victim)||!eligible(killer)||!eligible(victim)||!p.vaults.healthy(killer))return false;long amount=EventRules.reward(ThreadLocalRandom.current().nextInt(3),active());var u=p.data.user(killer);if(u.coins>Long.MAX_VALUE-amount)return false;long old=u.coins;try{u.coins+=amount;p.vaults.saveAccount(killer);p.tell(killer,"PvP 처치 보상 +"+amount+"코인"+(active()?" (이벤트 2배)":""));return true;}catch(Exception ex){u.coins=old;p.vaults.syncCurrency(killer,u);p.getLogger().log(java.util.logging.Level.SEVERE,"PvP reward failed",ex);return false;}}
    private boolean eligible(Player a){return a.getGameMode()==GameMode.SURVIVAL||a.getGameMode()==GameMode.ADVENTURE;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drops(EntityDeathEvent e){if(!active()||e.getEntity() instanceof Player||p.safe(e.getEntity().getLocation())||e.getEntity().getWorld()==p.queue.world)return;List<ItemStack> extra=new ArrayList<>();for(ItemStack item:e.getDrops())if(item!=null&&!item.getType().isAir())extra.add(item.clone());e.getDrops().addAll(extra);}
    void command(CommandSender actor,String[] args){if(!BanService.operator(actor)){p.tell(actor,"OP만 사용할 수 있습니다.");return;}if(args.length==0){if(actor instanceof Player a)menu(a);else p.tell(actor,"/관리자전용이벤트켜기 시간 분 초 · "+status());return;}if(args.length!=3)throw new IllegalArgumentException("/관리자전용이벤트켜기 시간 분 초");start(actor,EventRules.seconds(args[0],args[1],args[2]));}
    void menu(Player a){Gui.Menu m=p.gui.make(a,"서버 2배 이벤트");m.inventory.setItem(22,Gui.item(Material.JUKEBOX,status(),"몬스터 전리품 수량 2배 · 경험치·사망 아이템 제외","PvP 40/80/100 → 80/160/200코인","이벤트 음악: 음반 cat · 서버 음량 50%"));if(BanService.operator(a)){p.gui.button(m,20,Material.CLOCK,"30분 시작",b->start(b,1800));p.gui.button(m,24,Material.CLOCK,"1시간 시작",b->start(b,3600));p.gui.button(m,40,Material.BARRIER,"이벤트 종료",b->p.gui.confirm(b,"이벤트 종료",this::stop,this::menu));}p.gui.show(a,m);}
    @EventHandler public void quit(PlayerQuitEvent e){songs.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void changed(PlayerChangedWorldEvent e){songs.remove(e.getPlayer().getUniqueId());if(active()&&!p.queue.waiting(e.getPlayer()))play(e.getPlayer());}
    void close(){stopMusic();}
}
