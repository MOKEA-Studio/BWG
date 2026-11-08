package kr.blackwiki.smp;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
/** Configuration upgrades are atomic, repeatable, and leave gameplay files untouched. */
final class UpgradeService {
    static final int SCHEMA=35;
    record Result(int from,int to,int added,boolean changed,String backup){}
    final Result result;
    final long graveExpiryEpoch;
    UpgradeService(JavaPlugin p)throws Exception{Path folder=p.getDataFolder().toPath();p.saveDefaultConfig();String defaults;try(var in=Objects.requireNonNull(p.getResource("config.yml"))){defaults=new String(in.readAllBytes(),StandardCharsets.UTF_8);}result=migrate(folder.resolve("config.yml"),defaults);p.reloadConfig();Path state=folder.resolve("upgrade.yml");YamlConfiguration y=new YamlConfiguration();if(Files.exists(state))y.load(state.toFile());boolean save=false;if(!y.isSet("grave-expiry-started-at")){y.set("grave-expiry-started-at",System.currentTimeMillis());save=true;}graveExpiryEpoch=y.getLong("grave-expiry-started-at");if(graveExpiryEpoch<=0)throw new IOException("Invalid grave expiry upgrade epoch");if(!p.getDescription().getVersion().equals(y.getString("last-version"))){y.set("schema",1);y.set("previous-version",y.getString("last-version",result.from()==24?"2.4 이전 설정":"알 수 없음"));y.set("last-version",p.getDescription().getVersion());y.set("config-version",SCHEMA);y.set("completed-at",Instant.now().toString());save=true;}if(save)DataStore.atomic(state,y.saveToString());}
    static Result migrate(Path file,String defaultText)throws Exception{YamlConfiguration current=new YamlConfiguration();current.load(file.toFile());int from=current.getInt("config-version",24);if(from>SCHEMA)throw new IOException("Configuration is newer than this plugin; refusing downgrade");YamlConfiguration defaults=new YamlConfiguration();defaults.loadFromString(defaultText);int added=0;for(String key:defaults.getKeys(true))if(!defaults.isConfigurationSection(key)&&!current.isSet(key)){current.set(key,defaults.get(key));added++;}boolean changed=from<SCHEMA||added>0;if(from<26&&current.getDouble("border.size")!=40000){current.set("border.size",40000);changed=true;}for(String old:List.of("discord.webhook-url","discord.webhook-url-env","discord.username","discord.relay-commands","discord.include-command-arguments","discord.redact-command-arguments"))if(current.contains(old)){current.set(old,null);changed=true;}if(!"bot".equals(current.getString("discord.mode"))){current.set("discord.mode","bot");changed=true;}current.set("config-version",SCHEMA);String backup="";if(changed){Path before=file.resolveSibling("config.yml.before-3.5.bak");if(!Files.exists(before))Files.copy(file,before);backup=before.getFileName().toString();DataStore.atomic(file,current.saveToString());}return new Result(from,SCHEMA,added,changed,backup);}
    String status(){return "설정 v"+SCHEMA+" · "+(result.changed()?"자동 갱신 완료 · 새 설정 "+result.added()+"개":"최신 설정 유지")+" · 데이터 보존";}
}
