package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Merge only our profile, preserve other settings and make restart requirements explicit. */
final class AntiXrayService {
 private record Target(Path path,World.Environment environment,int height){}
 private final BlackWikiSMP p;private boolean restartRequired;private int configured;private String problem="";
 AntiXrayService(BlackWikiSMP p){this.p=p;if(p.cfg().getBoolean("anti-xray.auto-configure",true))configure(Bukkit.getConsoleSender(),false);}
 void configure(CommandSender actor,boolean announce){
  Map<Path,byte[]> originals=new LinkedHashMap<>();Map<Path,String> writes=new LinkedHashMap<>();List<Path> published=new ArrayList<>();
  try{
   Path base=Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();List<Target> targets=new ArrayList<>();targets.add(new Target(base.resolve("config/paper-world-defaults.yml"),World.Environment.NORMAL,320));
   for(World w:Bukkit.getWorlds())if(w!=p.queue.world)targets.add(new Target(w.getWorldFolder().toPath().toAbsolutePath().normalize().resolve("paper-world.yml"),w.getEnvironment(),w.getMaxHeight()));
   configured=0;for(Target target:targets){Path path=target.path;YamlConfiguration y=new YamlConfiguration();if(Files.exists(path)){if(Files.isSymbolicLink(path)||!Files.isRegularFile(path))throw new java.io.IOException("Unsafe Paper configuration path");originals.put(path,Files.readAllBytes(path));y.load(path.toFile());}else originals.put(path,null);configured++;
    if(AntiXrayProfile.matches(y,target.environment,target.height))continue;AntiXrayProfile.apply(y,target.environment,target.height);writes.put(path,y.saveToString());
   }
   String stamp=Long.toString(System.currentTimeMillis())+"-"+UUID.randomUUID().toString().substring(0,8);
   for(var entry:writes.entrySet()){byte[] before=originals.get(entry.getKey());if(before!=null)Files.write(entry.getKey().resolveSibling(entry.getKey().getFileName()+".blackwikismp-3.3-"+stamp+".bak"),before,StandardOpenOption.CREATE_NEW);}
   for(var entry:writes.entrySet()){Files.createDirectories(entry.getKey().getParent());DataStore.atomic(entry.getKey(),entry.getValue());published.add(entry.getKey());}
   problem="";if(!writes.isEmpty()){restartRequired=true;p.getLogger().warning("Anti-Xray 3.3 profiles saved for "+writes.size()+" files. Fully stop and start Paper once more to activate them.");}if(announce)p.tell(actor,writes.isEmpty()?"Anti-Xray 강화 설정이 이미 일치합니다. "+status():"Anti-Xray 강화 설정 저장 완료 · 서버 전체 재시작 1회 필요 · 변경 전 설정 백업 보존");
  }catch(Exception ex){for(Path path:published)try{byte[] before=originals.get(path);if(before==null)Files.deleteIfExists(path);else DataStore.atomic(path,new String(before,StandardCharsets.UTF_8));}catch(Exception rollback){ex.addSuppressed(rollback);}problem=ex.getMessage();p.getLogger().log(java.util.logging.Level.SEVERE,"Anti-Xray profile configuration failed",ex);if(announce)p.tell(actor,"Anti-Xray 설정 실패: "+problem);}
 }
 String status(){return !problem.isEmpty()?"설정 오류: "+problem:"엔진 3 · 월드별 광물·매장 상자 · 높이 전체 · 우회 권한 사용 안 함 · "+configured+"개 설정 파일"+(restartRequired?" · 전체 재시작 필요":" · 파일 설정 일치");}
 void status(CommandSender actor){p.tell(actor,status());p.tell(actor,"Paper Anti-Xray는 서버 전체 재시작으로 적용됩니다. 노출된 블록·알려진 시드·모든 ESP를 완전히 차단하지는 못합니다.");}
}
