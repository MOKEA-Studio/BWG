package kr.blackwiki.smp;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** OP tools: timed mute (chat, whispers, signs) and a player-head teleport menu. */
final class AdminTools implements Listener {
    /** Private/broadcast chat commands a muted player could use to bypass the chat block. */
    static final Set<String> CHAT_COMMANDS=Set.of("msg","tell","w","whisper","r","reply","me","say","teammsg","tm","귓","귓속말");
    private final BlackWikiSMP p;
    final BanLedger mutes=new BanLedger();
    private final Path file;

    AdminTools(BlackWikiSMP p)throws Exception{
        this.p=p;file=p.getDataFolder().toPath().resolve("mutes.yml");if(!Files.exists(file))return;
        YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());var section=y.getConfigurationSection("mutes");if(section==null)return;Map<UUID,BanLedger.Entry> map=new LinkedHashMap<>();
        for(String key:section.getKeys(false)){String q="mutes."+key+".";map.put(UUID.fromString(key),new BanLedger.Entry(UUID.fromString(key),y.getString(q+"name",key),y.getString(q+"actor","console"),y.getString(q+"reason","관리자 채팅 제한"),y.getLong(q+"created"),y.getLong(q+"expires")));}
        mutes.replace(map);
    }

    /** "10 분" → 600. Units: 초/분/시간/일 or s/m/h/d. */
    static long seconds(String unit,String amount,int maxDays){
        long mult=switch(unit.toLowerCase(Locale.ROOT)){case "초","s","sec"->1;case "분","m","min"->60;case "시간","시","h","hour"->3600;case "일","d","day"->86400;default->throw new IllegalArgumentException("단위는 초·분·시간·일 중 하나입니다.");};
        long n;try{n=Long.parseLong(amount);}catch(NumberFormatException e){throw new IllegalArgumentException("수에는 정수를 입력하세요.");}
        if(n<1||n>(long)maxDays*86400/mult)throw new IllegalArgumentException("기간은 1초 이상, 최대 "+maxDays+"일입니다.");
        return n*mult;
    }

    private boolean save(CommandSender actor,Map<UUID,BanLedger.Entry> next){
        YamlConfiguration y=new YamlConfiguration();y.set("schema",1);long now=System.currentTimeMillis();
        for(var e:next.values()){if(!e.active(now))continue;String q="mutes."+e.player()+".";y.set(q+"name",e.name());y.set(q+"actor",e.actor());y.set(q+"reason",e.reason());y.set(q+"created",e.created());y.set(q+"expires",e.expires());}
        try{DataStore.atomic(file,y.saveToString());mutes.replace(next);return true;}catch(Exception ex){p.tell(actor,"채팅 제한 파일 저장에 실패해 적용하지 않았습니다.");p.getLogger().log(java.util.logging.Level.SEVERE,"Mute save failed",ex);return false;}
    }

    void mute(CommandSender actor,String[] args){
        if(!BanService.operator(actor)){p.tell(actor,"OP만 사용할 수 있습니다.");return;}
        try{
            if(args.length>=1&&Set.of("목록","list").contains(args[0])){long now=System.currentTimeMillis();var list=mutes.snapshot().values().stream().filter(e->e.active(now)).sorted(Comparator.comparingLong(BanLedger.Entry::expires)).toList();p.tell(actor,"채팅 제한 "+list.size()+"명");for(var e:list)p.tell(actor,e.name()+" · 남은 "+Rules.duration(e.left(now))+" · "+e.reason());return;}
            if(args.length==2&&Set.of("해제","풀기","unmute").contains(args[0])){var t=p.bans.resolve(args[1]);Map<UUID,BanLedger.Entry> next=new LinkedHashMap<>(mutes.snapshot());if(next.remove(t.id())==null){p.tell(actor,t.name()+"님은 채팅 제한 상태가 아닙니다.");return;}if(!save(actor,next))return;p.tell(actor,t.name()+"님의 채팅 제한을 해제했습니다.");Player online=Bukkit.getPlayer(t.id());if(online!=null)p.tell(online,"채팅 제한이 해제되었습니다.");p.discord.moderation(actor.getName(),t.name()+" 채팅 제한 해제");return;}
            if(args.length<3){usage(actor);return;}
            var t=p.bans.resolve(args[0]);long secs=seconds(args[1],args[2],p.cfg().getInt("moderation.max-ban-days",3650));
            String reason=args.length>3?String.join(" ",Arrays.copyOfRange(args,3,args.length)):"관리자 채팅 제한";
            var e=BanLedger.create(t.id(),t.name(),actor.getName(),reason,secs,Instant.now());Map<UUID,BanLedger.Entry> next=new LinkedHashMap<>(mutes.snapshot());next.put(t.id(),e);if(!save(actor,next))return;
            p.tell(actor,t.name()+"님의 채팅·표지판을 "+Rules.duration(secs)+" 동안 제한했습니다. 사유: "+e.reason());
            Player online=Bukkit.getPlayer(t.id());if(online!=null)p.tell(online,"채팅·표지판이 "+Rules.duration(secs)+" 동안 제한되었습니다. 사유: "+e.reason());
            p.discord.moderation(actor.getName(),t.name()+" 채팅 제한 · "+Rules.duration(secs)+" · "+e.reason());
        }catch(IllegalArgumentException ex){p.tell(actor,ex.getMessage());usage(actor);}
    }
    private void usage(CommandSender a){p.tell(a,"/mute 닉네임 단위 수 [사유] · 단위: 초/분/시간/일 · 예: /mute Steve 분 10 도배");p.tell(a,"/mute 해제 닉네임 · /mute 목록");}

    private boolean blocked(Player a){BanLedger.Entry e=mutes.active(a.getUniqueId(),System.currentTimeMillis());if(e==null)return false;p.tell(a,"채팅이 제한된 상태입니다. 남은 시간: "+Rules.duration(e.left(System.currentTimeMillis()))+" · 사유: "+e.reason());return true;}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void chat(AsyncChatEvent e){UUID id=e.getPlayer().getUniqueId();if(p.homes.hasPrompt(id)||p.gui.hasPrompt(id))return;if(blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e){if(CHAT_COMMANDS.contains(DiscordBridge.root(e.getMessage()))&&blocked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void sign(SignChangeEvent e){if(blocked(e.getPlayer()))e.setCancelled(true);}

    void teleportMenu(Player a,int page){
        if(!BanService.operator(a)){p.tell(a,"OP만 사용할 수 있습니다.");return;}
        Gui.Menu m=p.gui.make(a,"관리자 TP "+(page+1));
        List<? extends Player> list=Bukkit.getOnlinePlayers().stream().filter(v->v!=a).sorted(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).toList();
        int start=page*36;
        for(int i=start;i<Math.min(start+36,list.size());i++){
            Player v=list.get(i);UUID id=v.getUniqueId();Location l=v.getLocation();
            p.gui.button(m,9+i-start,Material.PLAYER_HEAD,v.getName(),b->{Player target=Bukkit.getPlayer(id);if(target==null){p.tell(b,"플레이어가 나갔습니다.");teleportMenu(b,page);return;}b.closeInventory();b.teleport(target.getLocation());p.tell(b,target.getName()+"님에게 이동했습니다.");},
                l.getWorld().getName()+" · "+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ(),"체력 "+(int)Math.ceil(v.getHealth())+" · 핑 "+v.getPing()+"ms",p.queue.waiting(v)?"대기열":"클릭: 이 플레이어에게 TP");
            ItemStack head=m.inventory.getItem(9+i-start);if(head!=null&&head.getItemMeta() instanceof SkullMeta skull){skull.setOwningPlayer(v);head.setItemMeta(skull);}
        }
        if(list.isEmpty())m.inventory.setItem(22,Gui.item(Material.BARRIER,"접속 중인 다른 플레이어가 없습니다."));
        p.gui.pages(m,page,list.size(),b->teleportMenu(b,page-1),b->teleportMenu(b,page+1));
        p.gui.button(m,48,Material.CLOCK,"새로고침",b->teleportMenu(b,page));
        p.gui.show(a,m);
    }
}
