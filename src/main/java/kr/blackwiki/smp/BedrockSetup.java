package kr.blackwiki.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;

/** Only public bridge settings are merged; keys, linking databases and other options remain. */
final class BedrockSetup {
    record Result(boolean changed,int port){}
    private BedrockSetup(){}
    static int port(Path file,int fallback)throws Exception{if(!Files.exists(file))return fallback;YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());int port=y.getInt("bedrock.port",fallback);return port>=1&&port<=65535?port:fallback;}
    static Result prepare(Path floodgate,Path geyser,int port)throws Exception{
        if(port<1||port>65535)throw new IllegalArgumentException("Bedrock port must be 1..65535");
        if(!Files.isRegularFile(floodgate)||!Files.isRegularFile(geyser))throw new java.io.IOException("Bridge configuration not generated; start Geyser and Floodgate once");
        YamlConfiguration f=new YamlConfiguration(),g=new YamlConfiguration();f.load(floodgate.toFile());g.load(geyser.toFile());
        // Validate both before mutating either one, including legacy Geyser remote.auth-type.
        boolean fc=set(f,"username-prefix",".")|set(f,"replace-spaces",true);
        String java=g.isConfigurationSection("java")||g.getInt("config-version",0)>=5?"java":"remote";
        boolean gc=set(g,java+".auth-type","floodgate")|set(g,"bedrock.port",port);
        if(g.getBoolean("bedrock.clone-remote-port",false))gc|=set(g,"bedrock.clone-remote-port",false);
        if(fc)write(floodgate,f);if(gc)write(geyser,g);
        return new Result(fc||gc,port);
    }
    private static boolean set(YamlConfiguration y,String key,Object value){if(Objects.equals(y.get(key),value))return false;y.set(key,value);return true;}
    private static void write(Path file,YamlConfiguration y)throws Exception{Path backup=file.resolveSibling(file.getFileName()+".before-blackwikismp-2.8.bak");if(!Files.exists(backup))Files.copy(file,backup);DataStore.atomic(file,y.saveToString());}
    static String address(String input){String value=input.strip();if(value.length()>253||value.isEmpty()||!value.matches("[\\p{L}\\p{N}._:\\-\\[\\]]+"))throw new IllegalArgumentException("접속 주소는 공인 IP 또는 도메인만 입력하세요. 포트는 따로 설정합니다.");return value;}
}
