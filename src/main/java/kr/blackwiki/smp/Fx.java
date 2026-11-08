package kr.blackwiki.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.time.Duration;

/** Shared one-shot "ender" effects. Only data-less particles, so no particle-data mismatch across versions. */
final class Fx {
    private Fx(){}
    static final TextColor ENDER=TextColor.color(0xC77DFF),GOLD=TextColor.color(0xFFD95A);
    /** Burst at a location: portal swirl, end rods, totem sparkles. One shot, so it cannot leave a trail. */
    static void enderBurst(Location at){
        World w=at.getWorld();if(w==null)return;Location c=at.clone().add(0,1,0);
        w.spawnParticle(Particle.PORTAL,c,120,.6,1,.6,1.2);w.spawnParticle(Particle.REVERSE_PORTAL,c,60,.5,.9,.5,.08);
        w.spawnParticle(Particle.END_ROD,c,40,.7,1,.7,.06);w.spawnParticle(Particle.TOTEM_OF_UNDYING,c,40,.5,.9,.5,.4);
        w.playSound(c,Sound.ENTITY_ENDERMAN_TELEPORT,SoundCategory.PLAYERS,1f,.7f);
    }
    /** Title + dragon growl for every online player (not those still in the queue). */
    static void announce(BlackWikiSMP p,String title,String subtitle,TextColor color){
        Title t=Title.title(Component.text(title,color).decorate(TextDecoration.BOLD),Component.text(subtitle,TextColor.color(0xE0E0E0)),Title.Times.times(Duration.ofMillis(250),Duration.ofMillis(2500),Duration.ofMillis(600)));
        for(Player a:Bukkit.getOnlinePlayers()){if(p.queue!=null&&p.queue.waiting(a))continue;a.showTitle(t);a.playSound(a.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,SoundCategory.MASTER,.6f,1f);}
    }
}
