package kr.blackwiki.smp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.logging.Level;

final class WelcomeService implements Listener {
    private final BlackWikiSMP p;
    private final NamespacedKey seen;
    // Immutable publication makes chat reception safe on Paper's asynchronous chat thread.
    private volatile List<WelcomeRules.Window> windows=List.of();
    WelcomeService(BlackWikiSMP p){this.p=p;seen=new NamespacedKey(p,"welcome-first-join-seen");}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){
        Player a=e.getPlayer();var pd=a.getPersistentDataContainer();
        if(pd.has(seen,PersistentDataType.BYTE))return;
        boolean first=!a.hasPlayedBefore();
        try{pd.set(seen,PersistentDataType.BYTE,(byte)1);a.saveData();}
        catch(Exception ex){p.getLogger().log(Level.SEVERE,"Could not persist first-join welcome marker for "+a.getUniqueId(),ex);return;}
        if(!first||!p.cfg().getBoolean("welcome.enabled",true))return;
        List<WelcomeRules.Window> next=new ArrayList<>(windows);
        next.add(new WelcomeRules.Window(a.getUniqueId(),a.getName(),System.nanoTime()));windows=List.copyOf(next);
        for(Player other:Bukkit.getOnlinePlayers())p.tell(other,"신규 유저 "+a.getName()+"님이 처음 접속했습니다! 10초 안에 채팅으로 안녕하세요 → 100코인 (본인 제외 · 신규 유저당 1회)");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void chat(AsyncChatEvent e){
        if(!WelcomeRules.greeting(PlainTextComponentSerializer.plainText().serialize(e.message())))return;
        long received=System.nanoTime();List<WelcomeRules.Window> active=windows.stream().filter(w->w.active(received)).toList();
        if(!active.isEmpty())p.sync(()->greet(e.getPlayer(),active,received));
    }
    void greet(Player a,List<WelcomeRules.Window> candidates,long received){
        if(!a.isOnline()||a.isDead()||p.queue.waiting(a)||!p.cfg().getBoolean("welcome.enabled",true)||!p.vaults.healthy(a))return;
        List<WelcomeRules.Window> eligible=candidates.stream().filter(w->w.eligible(a.getUniqueId(),received)).toList();
        if(eligible.isEmpty())return;
        var u=p.data.user(a);long before=u.coins,amount=Math.multiplyExact(WelcomeRules.REWARD,eligible.size());
        if(before>Long.MAX_VALUE-amount){p.tell(a,"코인 보유 한도에 도달했습니다.");return;}
        try{u.coins=before+amount;p.vaults.saveAccount(a);for(var w:eligible)w.claimed(a.getUniqueId());p.tell(a,"환영 인사 보상 +"+amount+"코인! (신규 유저 "+eligible.size()+"명 · 각 1회)");}
        catch(Exception ex){u.coins=before;p.vaults.syncCurrency(a,u);p.getLogger().log(Level.SEVERE,"Could not save welcome reward for "+a.getUniqueId(),ex);}
    }
    void tick(){long now=System.nanoTime();windows=windows.stream().filter(w->w.active(now)).toList();}
    String status(){long now=System.nanoTime();return (p.cfg().getBoolean("welcome.enabled",true)?"켜짐":"꺼짐")+" · 인사 가능 신규 유저 "+windows.stream().filter(w->w.active(now)).count()+"명";}
    void menu(Player a){Gui.Menu m=p.gui.make(a,"신규 유저 환영 보상");m.inventory.setItem(4,Gui.item(Material.EMERALD,"인사 한 번으로 100코인","신규 유저 첫 접속 후 10초 안에","공개 채팅으로 정확히: 안녕하세요","신규 유저당 각 플레이어 1회 · 본인 제외","재접속으로 다시 시작되지 않습니다."));long now=System.nanoTime();int slot=9;for(var w:windows)if(w.active(now)&&slot<45)m.inventory.setItem(slot++,Gui.item(Material.PLAYER_HEAD,w.name+"님 환영하기","남은 시간: "+w.remaining(now)+"초",w.eligible(a.getUniqueId(),now)?"안녕하세요 를 채팅에 입력하세요.":"이미 받았거나 본인입니다."));if(slot==9)m.inventory.setItem(22,Gui.item(Material.CLOCK,"지금은 진행 중인 환영 보상이 없습니다."));p.gui.button(m,49,Material.CLOCK,"새로고침",this::menu);p.gui.show(a,m);}
}
