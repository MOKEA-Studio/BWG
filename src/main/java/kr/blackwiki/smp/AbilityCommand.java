package kr.blackwiki.smp;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.*;
import java.util.*;

/** Direct PluginCommand execution gives the shop a synchronous success result. */
final class AbilityCommand implements CommandExecutor {
    private final BlackWikiSMP p;
    AbilityCommand(BlackWikiSMP p){this.p=p;}
    static PotionEffectType type(String name){return switch(name){case "saturation"->PotionEffectType.SATURATION;case "regeneration"->PotionEffectType.REGENERATION;case "resistance"->PotionEffectType.RESISTANCE;default->throw new IllegalArgumentException("saturation | regeneration | resistance");};}
    static boolean useful(Player a,PotionEffectType type,int ticks){PotionEffect old=a.getPotionEffect(type);return old==null||old.getAmplifier()==0&&!old.isInfinite()&&old.getDuration()<ticks;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!BanService.operator(sender)){p.tell(sender,"OP만 사용할 수 있습니다.");return false;}
        try{if(args.length!=3)throw new IllegalArgumentException("/상점효과 닉네임 saturation|regeneration|resistance 초");Player a=Bukkit.getPlayerExact(args[0]);int seconds=Integer.parseInt(args[2]);if(a==null||a.isDead()||seconds<1||seconds>180)throw new IllegalArgumentException("온라인 플레이어와 1~180초가 필요합니다.");PotionEffectType type=type(args[1]);int ticks=Math.multiplyExact(seconds,20);if(!useful(a,type,ticks)){p.tell(sender,"기존 효과가 더 강하거나 오래 지속됩니다.");return false;}
            if(!a.addPotionEffect(new PotionEffect(type,ticks,0,false,true,true)))return false;PotionEffect after=a.getPotionEffect(type);return after!=null&&after.getAmplifier()==0&&after.getDuration()>=ticks-1;
        }catch(IllegalArgumentException ex){p.tell(sender,ex.getMessage());return false;}
    }
}
