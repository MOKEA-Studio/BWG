package kr.blackwiki.smp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Plugin version from plugin.yml, so tab, Discord and logs never show a stale hardcoded number. Works without Bukkit (Discord controller). */
final class Version {
    private Version(){}
    static final String V=read();
    private static String read(){
        try(InputStream in=Version.class.getResourceAsStream("/plugin.yml")){
            var m=Pattern.compile("(?m)^version:\\s*'?([^'\\s]+)'?").matcher(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            return m.find()?m.group(1):"?";
        }catch(Exception ex){return "?";}
    }
}
