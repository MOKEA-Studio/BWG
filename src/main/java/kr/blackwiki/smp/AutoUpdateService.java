package kr.blackwiki.smp;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.jar.JarFile;

/** Stages the newest GitHub release JAR in Paper's update folder; Paper swaps it in on the next restart. */
final class AutoUpdateService {
    private static final Pattern REPO=Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private final BlackWikiSMP p;
    private final Path jar;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile String status="확인 대기 중",staged;

    AutoUpdateService(BlackWikiSMP p,Path jar){
        this.p=p;this.jar=jar;
        long period=p.cfg().getLong("auto-update.check-interval-minutes",30)*60*20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(p,this::check,20*60,period);
    }

    String status(){return status;}
    /** /업데이트상태 확인: check GitHub right now instead of waiting for the next interval. */
    void checkNow(org.bukkit.command.CommandSender sender){
        p.tell(sender,"GitHub 최신 릴리스를 확인합니다…");
        Bukkit.getScheduler().runTaskAsynchronously(p,()->{check();p.sync(()->p.tell(sender,"자동 업데이트: "+status));});
    }

    synchronized void check(){
        String repo=p.cfg().getString("auto-update.repository","").trim(),current=p.getDescription().getVersion();
        if(!p.cfg().getBoolean("auto-update.enabled",true)){status="꺼짐";return;}
        if(!REPO.matcher(repo).matches()){status="config.yml의 auto-update.repository(owner/repo)를 입력하세요";return;}
        Path dir=Bukkit.getUpdateFolderFile().toPath(),temp=dir.resolve(jar.getFileName()+".download");
        try{
            JsonObject release=JsonParser.parseString(get("https://api.github.com/repos/"+repo+"/releases/latest")).getAsJsonObject();
            String tag=release.get("tag_name").getAsString(),version=tag.replaceFirst("^[vV]","");
            if(compare(version,current)<=0){status="최신 버전 "+current+" · 확인 "+now();return;}
            if(version.equals(staged)){status=version+" 받음 · 서버 재시작 시 적용";return;}
            String url=null;
            for(JsonElement e:release.getAsJsonArray("assets")){JsonObject a=e.getAsJsonObject();String name=a.get("name").getAsString();if(name.startsWith("BlackWikiSMP")&&name.endsWith(".jar"))url=a.get("browser_download_url").getAsString();}
            if(url==null)throw new IOException("릴리스 "+tag+"에 BlackWikiSMP*.jar 첨부 파일이 없습니다");
            Files.createDirectories(dir);
            HttpResponse<Path> r=client.send(request(url).build(),HttpResponse.BodyHandlers.ofFile(temp));
            if(r.statusCode()!=200)throw new IOException("JAR 다운로드 HTTP "+r.statusCode());
            String[] desc=describe(temp);
            if(!"BlackWikiSMP".equals(desc[0])||!version.equals(desc[1]))throw new IOException("받은 JAR가 BlackWikiSMP "+version+"이 아닙니다 ("+desc[0]+" "+desc[1]+")");
            // Same file name as the running JAR so both Paper (by plugin name) and Bukkit (by file name) pick it up.
            Files.move(temp,dir.resolve(jar.getFileName()),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            staged=version;status=version+" 받음 · 서버 재시작 시 적용";
            p.getLogger().info("새 버전 "+version+"을(를) "+dir+"에 받았습니다. 다음 서버 재시작 때 적용됩니다.");
            p.sync(()->{for(Player a:Bukkit.getOnlinePlayers())if(a.isOp())p.tell(a,"새 버전 "+version+" 다운로드 완료 · 서버 재시작 시 적용됩니다.");});
        }catch(Exception ex){status="확인 실패: "+ex.getMessage();p.getLogger().warning("자동 업데이트 "+status);}
        finally{try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }

    private HttpRequest.Builder request(String url){return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).header("User-Agent","BlackWikiSMP-AutoUpdate");}
    private String get(String url)throws Exception{HttpResponse<String> r=client.send(request(url).header("Accept","application/vnd.github+json").build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(r.statusCode()==404)throw new IOException("릴리스 없음 또는 저장소 이름 오류 (404)");if(r.statusCode()!=200)throw new IOException("GitHub HTTP "+r.statusCode());return r.body();}
    private static String now(){return DateTimeFormatter.ofPattern("MM-dd HH:mm").format(ZonedDateTime.now(ZoneId.of("Asia/Seoul")));}

    static String[] describe(Path file)throws Exception{
        try(JarFile j=new JarFile(file.toFile())){var entry=j.getJarEntry("plugin.yml");if(entry==null)throw new IOException("plugin.yml이 없는 JAR");YamlConfiguration y=new YamlConfiguration();try(var in=j.getInputStream(entry)){y.loadFromString(new String(in.readAllBytes(),StandardCharsets.UTF_8));}return new String[]{y.getString("name"),y.getString("version")};}
    }

    /** Numeric dotted compare: 3.10 > 3.9. Throws on non-numeric parts so odd tags never install. */
    static int compare(String a,String b){
        String[] x=a.split("\\."),y=b.split("\\.");
        for(int i=0;i<Math.max(x.length,y.length);i++){int c=Integer.compare(i<x.length?Integer.parseInt(x[i]):0,i<y.length?Integer.parseInt(y[i]):0);if(c!=0)return c;}
        return 0;
    }
}
