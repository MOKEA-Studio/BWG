package kr.blackwiki.smp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

/** One action bar combines coins, queue/travel, combat and mining without flicker. */
final class HudService {
    private record Notice(String text,long until){}
    private record Sent(String text,long time){}
    private final BlackWikiSMP p;
    private final Map<UUID,Notice> notices=new HashMap<>();
    private final Map<UUID,Sent> sent=new HashMap<>();
    private boolean active=true;
    HudService(BlackWikiSMP p){this.p=p;}
    void notice(Player a,String message){if(!active)return;notices.put(a.getUniqueId(),new Notice(message,System.nanoTime()+1_200_000_000L));refresh(a);}
    void clearNotice(Player a){notices.remove(a.getUniqueId());refresh(a);}
    String frame(Player a){
        String status="";
        if(p.queue.waiting(a))status="현재 나의 대기열 숫자: "+p.queue.position(a)+" · 전체 "+p.queue.size()+"명 · 게임 "+p.queue.active()+"/"+p.queue.slots();
        else if(p.data.user(a).hud){
            status=p.safe(a.getLocation())?"스폰 · 보호구역":"약탈 야생";
            if(p.boundaryWorld(a.getWorld()))status+=" | 경계 "+Math.max(0,(int)p.distance(a.getLocation()))+"m";
            long combat=p.travel.combatLeft(a);if(combat>0)status+=" | 전투 "+combat+"초";
            if(p.newbies.protectedPlayer(a))status+=" | 신규 보호 "+Rules.duration(p.newbies.remaining(a));
        }
        String clock;if(!p.queue.waiting(a)&&p.realTime!=null&&!(clock=p.realTime.hud()).isBlank())status+=(status.isBlank()?"":" | ")+clock;
        Notice notice=notices.get(a.getUniqueId());String extra=notice!=null&&System.nanoTime()-notice.until<0?notice.text:"";
        return HudRules.compose(p.data.user(a).coins,status,extra,p.pistols==null?"":p.pistols.hud(a));
    }
    void refresh(Player a){if(!active||!a.isOnline()||a.isDead())return;long now=System.nanoTime();String text=frame(a);Sent last=sent.get(a.getUniqueId());if(last==null||!last.text.equals(text)||now-last.time>=1_000_000_000L){a.sendActionBar(BlackWikiSMP.text(text));sent.put(a.getUniqueId(),new Sent(text,now));}}
    void tick(){if(!active)return;long now=System.nanoTime();notices.entrySet().removeIf(e->now-e.getValue().until>=0||Bukkit.getPlayer(e.getKey())==null);sent.keySet().removeIf(id->Bukkit.getPlayer(id)==null);for(Player a:Bukkit.getOnlinePlayers())refresh(a);}
    void close(){active=false;sent.clear();notices.clear();}
}
