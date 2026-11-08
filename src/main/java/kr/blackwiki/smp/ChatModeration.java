package kr.blackwiki.smp;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

final class ChatModeration implements Listener {
    record Policy(boolean enabled,boolean block,ChatFilter filter,long cooldown){}
    private final BlackWikiSMP p;
    private volatile Policy policy;
    private final AtomicLong censored=new AtomicLong();
    ChatModeration(BlackWikiSMP p){this.p=p;reload();}
    static Policy read(org.bukkit.configuration.file.FileConfiguration c){
        String mode=c.getString("chat-filter.mode","mask");if(!Set.of("mask","block").contains(mode))throw new IllegalArgumentException("chat-filter.mode: mask 또는 block");
        return new Policy(c.getBoolean("chat-filter.enabled",true),mode.equals("block"),new ChatFilter(c.getStringList("chat-filter.blocked-words"),c.getStringList("chat-filter.allowed-words")),c.getLong("settings.chat-cooldown-ms",1000));
    }
    void reload(){policy=read(p.cfg());}
    long cooldown(){return policy.cooldown;}
    long count(){return censored.get();}
    int words(){return policy.filter.size();}
    boolean enabled(){return policy.enabled;}
    String mode(){return policy.block?"차단":"가림";}
    /** null means rejected. Callers retain private/team channel boundaries. */
    String clean(Player player,String input){Policy current=policy;if(!current.enabled)return input;ChatFilter.Result result=current.filter.apply(input);if(!result.changed())return input;censored.incrementAndGet();if(current.block){p.sync(()->p.tell(player,"허용되지 않은 욕설이 포함되어 메시지를 보내지 않았습니다."));return null;}return result.text();}
    String cleanExternal(String input){Policy current=policy;if(!current.enabled)return input;ChatFilter.Result result=current.filter.apply(input);if(!result.changed())return input;censored.incrementAndGet();return current.block?null:result.text();}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void messageCommand(PlayerCommandPreprocessEvent e){
        String raw=e.getMessage();String[] parts=raw.split("\\s+",3);String root=DiscordBridge.root(raw);
        int skip=Set.of("msg","tell","w","whisper").contains(root)?2:Set.of("me","say","teammsg","tm","r","reply").contains(root)?1:0;
        if(skip==0)return;
        String[] split=raw.split("\\s+",skip+1);if(split.length<=skip)return;
        String clean=clean(e.getPlayer(),split[skip]);if(clean==null)e.setCancelled(true);else if(!clean.equals(split[skip]))e.setMessage(String.join(" ",Arrays.copyOf(split,skip))+" "+clean);
    }
}
