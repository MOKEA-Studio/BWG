package kr.blackwiki.smp;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.command.CommandSender;
import java.time.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** All game operations run on the main thread; the Gateway only enqueues messages. */
final class DiscordBridge implements Listener,AutoCloseable {
    record Policy(boolean chat,boolean moderation){}
    private final BlackWikiSMP p;
    final DiscordRelay relay;
    private DiscordRelay replies;
    private volatile DiscordOptions options;
    private volatile Policy policy;
    private volatile String setup="";
    private DiscordGateway gateway;
    private ControlApi api;
    private PowerCommands power;
    private final DiscordInbound parser=new DiscordInbound();
    private final ArrayBlockingQueue<DiscordInbound.Message> inbox=new ArrayBlockingQueue<>(200);
    private final Map<String,Long> lastChat=new HashMap<>();
    private final Set<UUID> present=new HashSet<>();
    private boolean started,stopping;
    final AtomicLong received=new AtomicLong(),rejected=new AtomicLong();
    DiscordBridge(BlackWikiSMP p){this.p=p;options=DiscordOptions.read(p.cfg());relay=new DiscordRelay(options.outgoing());Bukkit.getOnlinePlayers().forEach(a->present.add(a.getUniqueId()));reload();}
    void reload(){
        DiscordOptions next=DiscordOptions.read(p.cfg()),old=options;
        policy=new Policy(p.cfg().getBoolean("discord.relay-chat",true),p.cfg().getBoolean("discord.relay-moderation",true));
        options=next;relay.reload(next.outgoing());power=new PowerCommands(next.control());
        if(next.channel().equals(next.control().channel())){if(replies!=null&&replies!=relay)replies.close();replies=relay;}else{var rs=new DiscordRelay.Settings(next.enabled(),DiscordInbound.id(next.control().channel())?DiscordRelay.endpoint(next.control().channel()):null,next.token(),100,next.interval());if(replies==null||replies==relay)replies=new DiscordRelay(rs);else replies.reload(rs);}
        boolean needGateway=next.configured()&&!next.external()&&(next.receive()||next.control().enabled());
        if(!next.equals(old)||gateway==null||gateway.fatal()){if(gateway!=null)gateway.close();gateway=null;inbox.clear();if(needGateway){gateway=new DiscordGateway(next.token(),this::accept);gateway.start();}}
        setup=!next.enabled()?"":next.token().isEmpty()?"봇 토큰 입력·형식 확인 필요":!DiscordInbound.id(next.channel())?"채팅 채널 ID 입력 필요":"";
        if(next.control().enabled()&&!next.control().configured())setup="전원 관리: 서버·관리 채널·허용 ID 설정 필요";
        if(api!=null&&(!next.external()||!next.enabled()||next.apiPort()!=old.apiPort())){api.close();api=null;}
        if(next.external()&&next.enabled()&&api==null)try{api=new ControlApi(next.apiPort(),ControlApi.secret(p.getDataFolder().toPath()),chat->p.sync(()->deliver(chat.name(),chat.content())),()->p.sync(this::stop));}catch(Exception ex){setup="외부 도우미 연결 포트·인증 키 확인 필요";p.getLogger().warning(setup);}
        snapshot();
    }
    private void accept(JsonObject json){
        DiscordGateway current=gateway;var m=parser.parse(json,current==null?"":current.botId());if(m==null)return;
        DiscordOptions o=options;boolean control=m.guild().equals(o.control().guild())&&m.channel().equals(o.control().channel())&&PowerCommands.command(m.content());
        if(!(control||o.receive()&&DiscordInbound.chatChannel(m,o)))return;if(!inbox.offer(m))rejected.incrementAndGet();
    }
    void tick(){
        for(int n=0;n<20;n++){var m=inbox.poll();if(m==null)break;DiscordOptions o=options;
            if(PowerCommands.command(m.content())){
                if(m.guild().equals(o.control().guild())&&m.channel().equals(o.control().channel())){
                    String response=power.handle(m,System.currentTimeMillis(),new PowerCommands.Actions(){public String status(){return "BlackWikiSMP 3.3 실행 중 · 접속 "+Bukkit.getOnlinePlayers().size()+" · 대기 "+p.queue.size();}public String start(){return "서버가 이미 실행 중입니다. 종료 후 다시 켜려면 외부 도우미를 상시 실행하세요.";}public String stop(){p.getLogger().info("Discord 관리자 종료 요청: "+m.author());DiscordBridge.this.stop();return "저장 후 서버를 종료합니다.";}});if(response!=null)replies.priority(response);
                }continue;
            }
            if(!o.enabled()||!o.receive()||!DiscordInbound.chatChannel(m,o))continue;
            long now=System.currentTimeMillis();if(now-lastChat.getOrDefault(m.author(),0L)<750){rejected.incrementAndGet();continue;}lastChat.put(m.author(),now);
            if(lastChat.size()>10000)lastChat.entrySet().removeIf(e->now-e.getValue()>60000);
            deliver(m.name(),m.content());
        }snapshot();
    }
    private void deliver(String name,String raw){
        if(!p.ready()||!options.enabled()||!options.receive())return;
        String clean=p.moderation.cleanExternal(DiscordInbound.plain(raw,500));if(clean==null||clean.isBlank()){rejected.incrementAndGet();return;}
        Component line=Component.text("[Discord] ",NamedTextColor.BLUE).append(Component.text(DiscordInbound.plain(name,48)+": ",NamedTextColor.AQUA)).append(Component.text(clean,NamedTextColor.WHITE));
        Bukkit.getOnlinePlayers().forEach(a->a.sendMessage(line));Bukkit.getConsoleSender().sendMessage(line);received.incrementAndGet();
    }
    private void snapshot(){if(api==null)return;JsonObject s=new JsonObject();s.addProperty("ready",p.ready()&&!stopping);s.addProperty("state",stopping?"stopping":p.ready()?"running":"starting");s.addProperty("version","3.3");s.addProperty("pid",ProcessHandle.current().pid());try{s.addProperty("server_id",ControlApi.identity(Bukkit.getWorldContainer().toPath()));}catch(Exception ex){s.addProperty("ready",false);}s.addProperty("online",Bukkit.getOnlinePlayers().size());s.addProperty("queue",p.queue==null?0:p.queue.size());s.addProperty("gateway_owner",options.owner());api.snapshot(s);}
    private void stop(){if(stopping)return;stopping=true;snapshot();Bukkit.getScheduler().runTaskLater(p,Bukkit::shutdown,20);}
    String status(){return !options.enabled()?"봇 연동 꺼짐":!setup.isEmpty()?setup:relay.state()+" · "+(options.external()?(api!=null&&api.connected()?"외부 도우미 연결됨":"외부 도우미 연결 대기"):gateway==null?"수신 꺼짐":gateway.status())+" · 수신 "+received.get();}
    static String root(String command){String s=command.strip().split("\\s+",2)[0];if(s.startsWith("/"))s=s.substring(1);int colon=s.lastIndexOf(':');return s.substring(colon+1).toLowerCase(Locale.ROOT);}
    static String safe(String text){String flat=text.replace('\n',' ').replace('\r',' ').replaceAll("[\\p{Cc}\\p{Cf}]","");StringBuilder out=new StringBuilder();for(int i=0;i<flat.length();i++){char c=flat.charAt(i);if("\\*_~`|>".indexOf(c)>=0)out.append('\\');out.append(c);}return out.toString();}
    private void post(String kind,String name,String message,boolean priority){String line=DiscordRelay.tag(kind,name.replaceAll("[\\p{Cc}\\p{Cf}]",""),safe(message),System.currentTimeMillis());if(priority)relay.priority(line);else relay.offer(line);}
    void chat(String name,String message){if(policy.chat)post("채팅",name,message,false);}
    void moderation(String actor,String message){if(policy.moderation)post("관리",actor,message,true);}
    @EventHandler(priority=EventPriority.MONITOR) public void loaded(ServerLoadEvent e){if(e.getType()!=ServerLoadEvent.LoadType.STARTUP||started)return;started=true;if(options.serverEvents()&&!options.external())post("서버","BlackWikiSMP 3.3","시작 완료 · 접속 가능",true);}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){if(present.add(e.getPlayer().getUniqueId())&&options.playerEvents())post("입장",e.getPlayer().getName(),"서버에 입장했습니다. · 접속 "+Bukkit.getOnlinePlayers().size()+"명",true);}
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){if(present.remove(e.getPlayer().getUniqueId())&&options.playerEvents()&&!Bukkit.getServer().isStopping())post("퇴장",e.getPlayer().getName(),"서버에서 퇴장했습니다. · 접속 "+Math.max(0,Bukkit.getOnlinePlayers().size()-1)+"명",true);}
    void test(CommandSender a){if(!BanService.operator(a)){p.tell(a,"OP만 사용할 수 있습니다.");return;}boolean queued=relay.priority("[연결 테스트] BlackWikiSMP 3.3 · "+safe(a.getName()));p.tell(a,queued?"테스트 전송 대기 · 수신/관리 명령은 Discord 채널에서 확인하세요. /플러그인상태":"전송하지 않았습니다: "+status());}
    @Override public void close(){if(started&&options.serverEvents()&&!options.external()&&Bukkit.getServer().isStopping())post("서버","BlackWikiSMP 3.3","정상 종료 · 월드와 데이터를 저장합니다.",true);if(gateway!=null)gateway.close();if(api!=null)api.close();inbox.clear();if(replies!=relay)replies.closeGracefully(1000);relay.closeGracefully(3000);}
}
