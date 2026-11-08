package kr.blackwiki.smp;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import java.util.*;

/**
 * Status lives in a per-player sidebar with an animated title; the action bar is kept
 * for short-lived things only (weapon state, notices). Players with the HUD setting off
 * get the old one-line action bar instead.
 */
final class HudService {
    private record Notice(String text,long until){}
    private record Sent(String text,long time){}
    static final String TITLE="BLACKWIKI SMP";
    private static final TextColor LABEL=TextColor.color(0xA0A8B8),GOLD=TextColor.color(0xFFC94D),SAFE=TextColor.color(0x5CF08A),WILD=TextColor.color(0xFF6B6B),AQUA=TextColor.color(0x5CE1FF),SOFT=TextColor.color(0xC7B8FF),DIM=TextColor.color(0x6B7280);
    private final BlackWikiSMP p;
    private final Map<UUID,Notice> notices=new HashMap<>();
    private final Map<UUID,Sent> sent=new HashMap<>();
    private final Map<UUID,Board> boards=new HashMap<>();
    private boolean active=true;
    private long frame;
    HudService(BlackWikiSMP p){this.p=p;}
    void notice(Player a,String message){if(!active)return;notices.put(a.getUniqueId(),new Notice(message,System.nanoTime()+1_200_000_000L));refresh(a);}
    void clearNotice(Player a){notices.remove(a.getUniqueId());refresh(a);}

    /** Legacy one-line HUD for players who switched the sidebar off. */
    String frame(Player a){
        String status="";
        if(p.queue.waiting(a))status="현재 나의 대기열 숫자: "+p.queue.position(a)+" · 전체 "+p.queue.size()+"명 · 게임 "+p.queue.active()+"/"+p.queue.slots();
        if(p.afk.contains(a))status="구역 3 · AFK · 주민 우클릭으로 이동";
        String clock;if(!p.queue.waiting(a)&&p.realTime!=null&&!(clock=p.realTime.hud()).isBlank())status+=(status.isBlank()?"":" | ")+clock;
        return HudRules.compose(p.data.user(a).coins,status,notice(a),weapon(a));
    }
    private String notice(Player a){Notice n=notices.get(a.getUniqueId());return n!=null&&System.nanoTime()-n.until<0?n.text:"";}
    private String weapon(Player a){return p.pistols==null?"":p.pistols.hud(a);}

    void refresh(Player a){
        if(!active||!a.isOnline()||a.isDead())return;
        boolean sidebar=p.data.user(a).hud;
        if(sidebar)board(a).render(lines(a),title());else drop(a);
        String text=sidebar?String.join(" | ",List.of(weapon(a),notice(a)).stream().filter(s->!s.isBlank()).toList()):frame(a);
        long now=System.nanoTime();Sent last=sent.get(a.getUniqueId());
        if(text.isBlank()){sent.remove(a.getUniqueId());return;}
        if(last==null||!last.text.equals(text)||now-last.time>=1_000_000_000L){a.sendActionBar(BlackWikiSMP.text(text));sent.put(a.getUniqueId(),new Sent(text,now));}
    }

    private List<Component> lines(Player a){
        List<Component> out=new ArrayList<>();
        out.add(Component.empty());
        out.add(row("코인 ",String.format(Locale.ROOT,"%,d",p.data.user(a).coins),GOLD));
        if(p.queue.waiting(a)){
            out.add(row("대기 순번 ",p.queue.position(a)+" / "+p.queue.size(),AQUA));
            out.add(row("게임 ",p.queue.active()+" / "+p.queue.slots(),AQUA));
        }else{
            boolean safe=p.safe(a.getLocation());
            boolean afk=p.afk.contains(a);out.add(row("구역 ",afk?"AFK · 공허":safe?"스폰 · 보호구역":"약탈 야생",afk?SOFT:safe?SAFE:WILD));if(afk)out.add(Component.text("주민 우클릭 → 구역 선택",DIM));
            if(p.boundaryWorld(a.getWorld()))out.add(row("경계 ",String.format(Locale.ROOT,"%,dm",Math.max(0,(int)p.distance(a.getLocation()))),AQUA));
            long combat=p.travel.combatLeft(a);if(combat>0)out.add(row("⚔ 전투 ",combat+"초",WILD).decorate(TextDecoration.BOLD));
            if(p.newbies.protectedPlayer(a))out.add(row("신규 보호 ",Rules.duration(p.newbies.remaining(a)),SAFE));
            String clock=p.realTime==null?"":p.realTime.hud();
            if(!clock.isBlank()){out.add(Component.empty());out.add(Component.text(clock.replaceFirst("^현실 ",""),SOFT));}
        }
        out.add(Component.empty());
        out.add(Component.text("접속 "+Bukkit.getOnlinePlayers().size()+"명 · 핑 "+a.getPing()+"ms",DIM));
        return out;
    }
    private static Component row(String label,String value,TextColor color){return Component.text(label,LABEL).append(Component.text(value,color));}

    /** Cyan→violet gradient with a white shine sweeping across, then a short pause. */
    private Component title(){return title(frame);}
    static Component title(long frame){
        int n=TITLE.length(),period=n+12;long pos=Math.floorMod(frame,period);
        TextComponent.Builder b=Component.text();
        for(int i=0;i<n;i++){
            char c=TITLE.charAt(i);double t=n==1?0:(double)i/(n-1);
            int base=lerp(0x5CE1FF,0xB57BFF,t);
            int d=(int)Math.abs(i-pos);int rgb=d==0?0xFFFFFF:d==1?lerp(base,0xFFFFFF,.55):base;
            b.append(Component.text(c,TextColor.color(rgb)));
        }
        return Component.text().append(Component.text("✦ ",GOLD)).append(b.build().decorate(TextDecoration.BOLD)).append(Component.text(" ✦",GOLD)).build();
    }
    static int lerp(int a,int b,double t){int r=(int)Math.round(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*t),g=(int)Math.round(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*t),bl=(int)Math.round((a&255)+((b&255)-(a&255))*t);return (r<<16)|(g<<8)|bl;}

    private Board board(Player a){
        Board b=boards.get(a.getUniqueId());
        if(b==null||a.getScoreboard()!=b.board){b=new Board();boards.put(a.getUniqueId(),b);a.setScoreboard(b.board);}
        return b;
    }
    private void drop(Player a){Board b=boards.remove(a.getUniqueId());if(b!=null&&a.getScoreboard()==b.board)a.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());}

    /** Fixed hidden entry keys with custom display names: only changed lines are re-sent, so nothing flickers. */
    private static final class Board {
        final Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard();
        final Objective obj=board.registerNewObjective("bwsmp",Criteria.DUMMY,Component.text(TITLE));
        final List<Component> shown=new ArrayList<>();Component shownTitle;
        Board(){obj.setDisplaySlot(DisplaySlot.SIDEBAR);obj.numberFormat(NumberFormat.blank());}
        void render(List<Component> lines,Component title){
            if(!title.equals(shownTitle)){obj.displayName(title);shownTitle=title;}
            for(int i=0;i<lines.size();i++){
                if(i<shown.size()&&shown.get(i).equals(lines.get(i)))continue;
                Score s=obj.getScore("l"+i);s.setScore(lines.size()-i);s.customName(lines.get(i));
                if(i<shown.size())shown.set(i,lines.get(i));else shown.add(lines.get(i));
            }
            for(int i=shown.size()-1;i>=lines.size();i--){board.resetScores("l"+i);shown.remove(i);}
            // Scores encode order; re-number when the line count changed.
            if(shown.size()==lines.size())for(int i=0;i<lines.size();i++){Score s=obj.getScore("l"+i);if(s.getScore()!=lines.size()-i)s.setScore(lines.size()-i);}
        }
    }

    void tick(){
        if(!active)return;frame++;long now=System.nanoTime();
        notices.entrySet().removeIf(e->now-e.getValue().until>=0||Bukkit.getPlayer(e.getKey())==null);
        sent.keySet().removeIf(id->Bukkit.getPlayer(id)==null);boards.keySet().removeIf(id->Bukkit.getPlayer(id)==null);
        for(Player a:Bukkit.getOnlinePlayers())refresh(a);
    }
    void close(){active=false;for(UUID id:boards.keySet()){Player a=Bukkit.getPlayer(id);if(a!=null)a.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());}boards.clear();sent.clear();notices.clear();}
}
