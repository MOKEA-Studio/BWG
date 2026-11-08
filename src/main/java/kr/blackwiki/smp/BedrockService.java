package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/** Uses the actual Floodgate API; a dot in an ordinary name is never proof of Bedrock auth. */
final class BedrockService implements Listener {
    private record Styled(Component before,Component after){}
    private final Map<UUID,Styled> styles=new HashMap<>();
    private final BlackWikiSMP p;
    private final Plugin geyser,floodgate,via;
    private Object api;
    private Method isBedrock,prefix,runtimeGetter,runtimeEnabled,runtimeShutdown,runtimeNethernet,runtimeRaknet;
    private String problem="";
    private boolean restart;
    private int port;
    BedrockService(BlackWikiSMP p){
        this.p=p;geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");floodgate=Bukkit.getPluginManager().getPlugin("floodgate");via=Bukkit.getPluginManager().getPlugin("ViaVersion");port=p.cfg().getInt("bedrock.port",19132);
        if(floodgate!=null&&floodgate.isEnabled())try{Class<?> type=Class.forName("org.geysermc.floodgate.api.FloodgateApi",true,floodgate.getClass().getClassLoader());api=type.getMethod("getInstance").invoke(null);isBedrock=type.getMethod("isFloodgatePlayer",UUID.class);prefix=type.getMethod("getPlayerPrefix");}catch(ReflectiveOperationException|LinkageError ex){problem="Floodgate API 확인 필요";p.getLogger().log(Level.WARNING,"Could not initialize optional Floodgate integration",ex);}
        if(geyser!=null)try{Class<?> type=Class.forName("org.geysermc.geyser.GeyserImpl",true,geyser.getClass().getClassLoader());runtimeGetter=type.getMethod("getInstance");runtimeEnabled=type.getMethod("isEnabled");runtimeShutdown=type.getMethod("isShuttingDown");runtimeRaknet=type.getMethod("getGeyserServer");try{runtimeNethernet=type.getMethod("getNetherNetServer");}catch(NoSuchMethodException ignored){}}catch(ReflectiveOperationException|LinkageError ex){problem="Geyser API 확인 필요";}
        if(!p.cfg().getBoolean("bedrock.enabled",true)||geyser==null||floodgate==null)return;
        Path gc=geyser.getDataFolder().toPath().resolve("config.yml"),fc=floodgate.getDataFolder().toPath().resolve("config.yml");
        try{
            if(p.upgrades.result.from()<28){int existing=BedrockSetup.port(gc,port);if(existing!=port){port=existing;p.cfg().set("bedrock.port",port);p.saveConfig();}}
            if(p.cfg().getBoolean("bedrock.auto-configure",true))restart=BedrockSetup.prepare(fc,gc,port).changed();
            if(api!=null&&!".".equals(prefix.invoke(api)))restart=true;
            if(restart)p.getLogger().info("Bedrock settings prepared: dot prefix / Floodgate authentication. Fully restart once to activate changed bridge settings.");
        }catch(Exception ex){problem="연동 설정 확인 필요 · 원본 보존";p.getLogger().log(Level.WARNING,"Could not prepare Bedrock configuration; base SMP remains active",ex);}
    }
    boolean bedrock(Player a){if(api==null)return false;try{return Boolean.TRUE.equals(isBedrock.invoke(api,a.getUniqueId()));}catch(ReflectiveOperationException ex){return false;}}
    String visibleName(Player a){return bedrock(a)&&!a.getName().startsWith(".")?"."+a.getName():a.getName();}
    void decorate(Player a){if(!bedrock(a)||a.getName().startsWith(".")||PlainTextComponentSerializer.plainText().serialize(a.displayName()).startsWith("."))return;Component before=a.displayName(),after=Component.text(".").append(before);styles.put(a.getUniqueId(),new Styled(before,after));a.displayName(after);}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){decorate(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){styles.remove(e.getPlayer().getUniqueId());}
    void close(){for(var entry:styles.entrySet()){Player a=Bukkit.getPlayer(entry.getKey());if(a!=null&&a.displayName().equals(entry.getValue().after))a.displayName(entry.getValue().before);}styles.clear();}
    private boolean runtimeLive(){if(runtimeGetter==null)return false;try{Object live=runtimeGetter.invoke(null);return live!=null&&Boolean.TRUE.equals(runtimeEnabled.invoke(live))&&!Boolean.TRUE.equals(runtimeShutdown.invoke(live))&&(runtimeRaknet.invoke(live)!=null||runtimeNethernet!=null&&runtimeNethernet.invoke(live)!=null);}catch(ReflectiveOperationException ex){return false;}}
    boolean ready(){return p.cfg().getBoolean("bedrock.enabled",true)&&problem.isEmpty()&&!restart&&api!=null&&geyser!=null&&geyser.isEnabled()&&floodgate!=null&&floodgate.isEnabled()&&via!=null&&via.isEnabled()&&runtimeLive();}
    String status(){if(!p.cfg().getBoolean("bedrock.enabled",true))return "연동 안내 꺼짐";if(!problem.isEmpty())return problem;List<String> missing=new ArrayList<>();if(geyser==null||!geyser.isEnabled())missing.add("Geyser");if(floodgate==null||!floodgate.isEnabled())missing.add("Floodgate");if(via==null||!via.isEnabled())missing.add("ViaVersion");if(!missing.isEmpty())return "설치 필요: "+String.join(" + ",missing);if(restart)return "설정 준비 완료 · 서버 전체 재시작 필요";return ready()?"공식 연동 감지 · .닉네임 · 포트 "+port:"Geyser 연결 초기화 확인 필요";}
    void menu(Player a){Gui.Menu m=p.gui.make(a,"휴대폰 · 베드락 접속");String address=p.cfg().getString("bedrock.public-address","");m.inventory.setItem(13,Gui.item(Material.GRASS_BLOCK,"휴대폰에서도 같은 서버로!",address.isBlank()?"서버 주소: 운영자에게 문의하세요.":"서버 주소: "+address,"베드락 포트: "+port,"닉네임: .닉네임 (예: .blackwiki3786)","Xbox 계정으로 로그인한 베드락 에디션 사용"));m.inventory.setItem(22,Gui.item(Material.COMPARATOR,"연동 상태",status(),bedrock(a)?"현재 접속: 베드락 인증 감지":"현재 접속: Java 에디션","외부 접속에는 베드락 포트 개방이 필요합니다."));m.inventory.setItem(31,Gui.item(Material.BOOK,"휴대폰에서 들어오기","플레이 → 서버 → 서버 추가","위 서버 주소와 베드락 포트를 입력","Java용 SRV 포트와 별도인 베드락 포트를 사용"));if(BanService.operator(a))m.inventory.setItem(40,Gui.item(Material.COMMAND_BLOCK,"운영자 설정","/베드락 주소 공인IP또는도메인","/베드락 포트 숫자 · 변경 뒤 전체 재시작","기본 최신 연결: 19132 TCP와 UDP 개방","Geyser·Floodgate·ViaVersion 설치 꾸러미 사용"));p.gui.button(m,49,Material.CLOCK,"새로고침",this::menu);p.gui.show(a,m);}
    void command(CommandSender actor,String[] args){
        if(args.length>0&&Set.of("주소","address","포트","port").contains(args[0].toLowerCase(Locale.ROOT))){if(!BanService.operator(actor)){p.tell(actor,"OP만 접속 주소·포트를 설정할 수 있습니다.");return;}if(args.length!=2)throw new IllegalArgumentException("/베드락 주소 공인IP또는도메인 | /베드락 포트 숫자");if(Set.of("주소","address").contains(args[0].toLowerCase(Locale.ROOT))){p.cfg().set("bedrock.public-address",BedrockSetup.address(args[1]));p.saveConfig();p.tell(actor,"휴대폰 접속 안내 주소를 저장했습니다.");}else{int selected=Integer.parseInt(args[1]);if(selected<1||selected>65535||selected==Bukkit.getPort())throw new IllegalArgumentException("1~65535 범위에서 Java 서버와 다른 포트를 선택하세요.");p.cfg().set("bedrock.port",selected);p.saveConfig();restart=true;p.tell(actor,"베드락 포트 설정을 저장했습니다. 방화벽 설정 후 서버를 전체 재시작하세요.");}return;}
        if(actor instanceof Player a)menu(a);else p.tell(actor,status());
    }
}
