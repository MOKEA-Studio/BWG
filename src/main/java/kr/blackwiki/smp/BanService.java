package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class BanService implements Listener {
    record Target(UUID id,String name){}
    private final BlackWikiSMP p;
    final BanLedger ledger=new BanLedger();
    private final Path file;
    BanService(BlackWikiSMP p)throws Exception{
        this.p=p;file=p.getDataFolder().toPath().resolve("bans.yml");if(!Files.exists(file))return;
        YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());Map<UUID,BanLedger.Entry> map=new LinkedHashMap<>();var section=y.getConfigurationSection("bans");if(section==null)return;
        for(String key:section.getKeys(false)){UUID id=UUID.fromString(key);String q="bans."+key+".";long created=y.getLong(q+"created"),expires=y.getLong(q+"expires");if(created<0||expires<=created)throw new IOException("Invalid ban dates: "+key);
            map.put(id,new BanLedger.Entry(id,y.getString(q+"name",key),y.getString(q+"actor","console"),y.getString(q+"reason","관리자 차단"),created,expires));}
        ledger.replace(map);
    }
    static boolean operator(CommandSender sender){return sender instanceof Player a?a.isOp():sender instanceof ConsoleCommandSender||sender instanceof RemoteConsoleCommandSender;}
    private boolean require(CommandSender sender){if(operator(sender))return true;p.tell(sender,"이 명령어는 OP만 사용할 수 있습니다.");return false;}
    Target resolve(String input){
        try{UUID id=UUID.fromString(input);DataStore.User u=p.data.users.get(id);BanLedger.Entry e=ledger.snapshot().get(id);return new Target(id,u!=null?u.name:e!=null?e.name():id.toString());}catch(IllegalArgumentException ignored){}
        Map<UUID,String> candidates=new LinkedHashMap<>();
        for(DataStore.User u:p.data.users.values())if(u.name.equalsIgnoreCase(input))candidates.put(u.id,u.name);
        for(BanLedger.Entry e:ledger.snapshot().values())if(e.name().equalsIgnoreCase(input))candidates.put(e.player(),e.name());
        Player live=Bukkit.getPlayerExact(input);if(live!=null)candidates.put(live.getUniqueId(),live.getName());
        if(candidates.size()>1)throw new IllegalArgumentException("닉네임 변경 이력이 겹칩니다. 정확한 UUID를 입력하세요.");
        if(candidates.isEmpty())throw new IllegalArgumentException("접속 기록이 없는 닉네임입니다. 접속한 닉네임 또는 정확한 UUID를 입력하세요.");
        var e=candidates.entrySet().iterator().next();return new Target(e.getKey(),e.getValue());
    }
    int activeCount(){long now=System.currentTimeMillis();return (int)ledger.snapshot().values().stream().filter(e->e.active(now)).count();}
    List<BanLedger.Entry> active(){long now=System.currentTimeMillis();return ledger.snapshot().values().stream().filter(e->e.active(now)).sorted(Comparator.comparingLong(BanLedger.Entry::expires)).toList();}
    private String yaml(Map<UUID,BanLedger.Entry> map){YamlConfiguration y=new YamlConfiguration();y.set("schema",1);for(var e:map.values()){String q="bans."+e.player()+".";y.set(q+"name",e.name());y.set(q+"actor",e.actor());y.set(q+"reason",e.reason());y.set(q+"created",e.created());y.set(q+"expires",e.expires());}return y.saveToString();}
    String snapshot(){return yaml(ledger.snapshot());}
    private boolean save(CommandSender actor,Map<UUID,BanLedger.Entry> next){try{DataStore.atomic(file,yaml(next));ledger.replace(next);return true;}catch(Exception e){p.tell(actor,"차단 파일 저장에 실패해 작업을 적용하지 않았습니다.");p.getLogger().log(java.util.logging.Level.SEVERE,"Ban save failed",e);return false;}}
    boolean ban(CommandSender actor,UUID target,long seconds,String reason){
        if(!require(actor))return false;if(seconds<1||seconds>p.cfg().getLong("moderation.max-ban-days",3650)*86400){p.tell(actor,"차단 기간 범위를 확인하세요.");return false;}
        Target resolved=resolve(target.toString());BanLedger.Entry e=BanLedger.create(target,resolved.name,actor.getName(),reason,seconds,Instant.now());
        Map<UUID,BanLedger.Entry> next=new LinkedHashMap<>(ledger.snapshot());next.put(target,e);if(!save(actor,next))return false;
        Player online=Bukkit.getPlayer(target);if(online!=null)online.kick(BlackWikiSMP.text(message(e)));
        p.tell(actor,resolved.name+"님을 "+Rules.duration(seconds)+" 동안 차단했습니다. 사유: "+e.reason());
        p.discord.moderation(actor.getName(),resolved.name+" 차단 · "+Rules.duration(seconds)+" · "+e.reason());return true;
    }
    boolean unban(CommandSender actor,UUID target){
        if(!require(actor))return false;Map<UUID,BanLedger.Entry> next=new LinkedHashMap<>(ledger.snapshot());BanLedger.Entry removed=next.remove(target);if(removed==null){p.tell(actor,"BlackWikiSMP 차단 기록이 없습니다.");return false;}if(!save(actor,next))return false;
        p.tell(actor,removed.name()+"님의 차단을 해제했습니다.");p.discord.moderation(actor.getName(),removed.name()+" 차단 해제");return true;
    }
    String message(BanLedger.Entry e){return "BlackWikiSMP 접속 차단\n사유: "+e.reason()+"\n남은 시간: "+Rules.duration(e.left(System.currentTimeMillis()))+"\n해제 시각: "+java.time.Instant.ofEpochMilli(e.expires()).atZone(java.time.ZoneId.of("Asia/Seoul")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));}
    void command(CommandSender actor,String[] args){
        if(!require(actor))return;
        if(args.length==0){if(actor instanceof Player a)p.gui.banTargets(a,0);else usage(actor);return;}
        if(Set.of("목록","list").contains(args[0])){if(actor instanceof Player a)p.gui.bans(a,0);else {p.tell(actor,"활성 차단 "+activeCount()+"명");for(var e:active())p.tell(actor,e.name()+" · "+Rules.duration(e.left(System.currentTimeMillis()))+" · "+e.reason());}return;}
        if(Set.of("풀기","해제","unban","pardon").contains(args[0])){if(args.length!=2){usage(actor);return;}unban(actor,resolve(args[1]).id);return;}
        Target target=resolve(args[0]);
        if(args.length==1&&actor instanceof Player a){p.gui.banChoices(a,target.id);return;}
        if(args.length<6){usage(actor);return;}
        long duration=BanLedger.seconds(args[1],args[2],args[3],args[4],p.cfg().getInt("moderation.max-ban-days",3650));ban(actor,target.id,duration,String.join(" ",Arrays.copyOfRange(args,5,args.length)));
    }
    private void usage(CommandSender a){p.tell(a,"/벤 닉네임 일 시간 분 초 사유 | /벤 풀기 닉네임 | /벤 목록");p.tell(a,"예: /벤 Steve 0 0 10 0 규칙 위반 (10분)");}
    @EventHandler(priority=EventPriority.HIGHEST) public void preLogin(AsyncPlayerPreLoginEvent e){BanLedger.Entry ban=ledger.active(e.getUniqueId(),System.currentTimeMillis());if(ban!=null)e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,message(ban));}
    @EventHandler(priority=EventPriority.HIGHEST) public void login(PlayerLoginEvent e){BanLedger.Entry ban=ledger.active(e.getPlayer().getUniqueId(),System.currentTimeMillis());if(ban!=null)e.disallow(PlayerLoginEvent.Result.KICK_BANNED,message(ban));}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){BanLedger.Entry ban=ledger.active(e.getPlayer().getUniqueId(),System.currentTimeMillis());if(ban!=null)e.getPlayer().kick(BlackWikiSMP.text(message(ban)));}
}
