package kr.blackwiki.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarFile;

/** Standalone, always-on controller. Paper libraries supply YAML/Gson even while the server is off. */
public final class DiscordController implements AutoCloseable {
    private final DiscordOptions options;
    private final Path configFile;
    private boolean paused;
    private final DiscordRelay relay,replies;
    private final DiscordGateway gateway;
    private final ServerSupervisor server;
    private final ControlApi.Client client;
    private final PowerCommands power;
    private final DiscordInbound parser=new DiscordInbound();
    private final ArrayBlockingQueue<DiscordInbound.Message> inbox=new ArrayBlockingQueue<>(128);
    private final Map<String,Long> cooldown=new HashMap<>();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("BlackWikiSMP-controller").factory());
    private volatile boolean closed;
    private long nextPoll;
    private DiscordController(Path root,YamlConfiguration settings,DiscordOptions options)throws Exception{
        this.options=options;configFile=root.resolve("plugins/BlackWikiSMP/config.yml");
        relay=new DiscordRelay(options.outgoing());
        replies=options.channel().equals(options.control().channel())?relay:new DiscordRelay(new DiscordRelay.Settings(true,DiscordInbound.id(options.control().channel())?DiscordRelay.endpoint(options.control().channel()):null,options.token(),100,options.interval()));
        client=new ControlApi.Client(options.apiPort(),ControlApi.secret(root.resolve("plugins/BlackWikiSMP")));
        server=new ServerSupervisor(root,settings.getStringList("server-command"),root.resolve("blackwikismp-controller/server.log"),client,text->{System.out.println(text);if(options.serverEvents())relay.priority(text);});
        power=new PowerCommands(options.control());
        gateway=new DiscordGateway(options.token(),json->{var m=parser.parse(json,"");if(m==null)return;boolean control=m.guild().equals(options.control().guild())&&m.channel().equals(options.control().channel())&&PowerCommands.command(m.content());if(control||options.receive()&&DiscordInbound.chatChannel(m,options))inbox.offer(m);});
    }
    private void start(){gateway.start();worker.scheduleWithFixedDelay(()->{try{tick();}catch(Exception ex){System.err.println("도우미 작업 오류 · 설정/서버 로그를 확인하세요.");}},0,100,TimeUnit.MILLISECONDS);}
    private void tick(){
        long now=System.currentTimeMillis();if(now>=nextPoll){checkConfiguration();server.poll();nextPoll=System.currentTimeMillis()+2000;}
        if(paused)return;
        for(int n=0;n<10;n++){
            var m=inbox.poll();if(m==null)break;
            if(PowerCommands.command(m.content())){
                if(!checkConfiguration())return;
                if(m.guild().equals(options.control().guild())&&m.channel().equals(options.control().channel())){
                    String response=power.handle(m,now,new PowerCommands.Actions(){public String status(){return server.status()+" · "+gateway.status();}public String start(){System.out.println("[관리] 시작 요청 사용자 "+m.author());return server.start();}public String stop(){System.out.println("[관리] 확인된 종료 요청 사용자 "+m.author());return server.stop();}});
                    if(response!=null)replies.priority(response);
                }continue;
            }
            if(!options.receive()||!DiscordInbound.chatChannel(m,options))continue;
            if(now-cooldown.getOrDefault(m.author(),0L)<750)continue;cooldown.put(m.author(),now);
            if(cooldown.size()>10000)cooldown.entrySet().removeIf(e->now-e.getValue()>60000);
            try{if(server.online())client.chat(m);}catch(Exception ex){/* Game may be stopping. A chat failure never starts a server. */}
        }
    }
    private boolean checkConfiguration(){if(paused)return false;try{if(options.equals(DiscordOptions.read(load(configFile))))return true;}catch(Exception ignored){}paused=true;inbox.clear();gateway.close();if(replies!=relay)replies.close();relay.close();System.err.println("Discord 설정 변경/읽기 오류 감지 · 연동과 관리 명령을 일시 중지했습니다. 설정 확인 후 도우미를 재시작하세요. 게임 서버는 유지됩니다.");return false;}
    @Override public void close(){if(closed)return;closed=true;gateway.close();worker.shutdownNow();server.close();if(replies!=relay)replies.closeGracefully(1000);relay.closeGracefully(3000);}
    public static void main(String[] args){try{run(args);}catch(Exception ex){System.err.println("도우미 실행 중단: "+(ex instanceof SetupError?ex.getMessage():ex.getClass().getSimpleName()+" · 경로/설정/권한을 확인하세요. 토큰을 로그에 붙여 넣지 마세요."));System.exit(2);}}
    private static void run(String[] args)throws Exception{
        if(Runtime.version().feature()<25)throw new SetupError("Java 25 이상이 필요합니다.");
        if(args.length<2||!args[0].equals("--server"))throw new IllegalArgumentException("--server 서버폴더 [--setup]");
        Path root=Path.of(args[1]).toRealPath();if(!Files.isRegularFile(root.resolve("server.properties")))throw new SetupError("server.properties가 없습니다.");
        Path folder=root.resolve("blackwikismp-controller");Files.createDirectories(folder);
        try(FileChannel channel=FileChannel.open(folder.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=channel.tryLock()){
            if(held==null)throw new SetupError("도우미가 이미 실행 중입니다.");
            if(args.length==3&&args[2].equals("--setup")){setup(root);return;}if(args.length!=2)throw new IllegalArgumentException("잘못된 인수입니다.");
            YamlConfiguration smp=load(root.resolve("plugins/BlackWikiSMP/config.yml")),settings=load(folder.resolve("controller.yml"));
            DiscordOptions options=DiscordOptions.read(smp);
            if(!options.configured()||!options.external())throw new SetupError("enabled, bot-token, channel-id, gateway-owner를 설정하세요.");
            if(options.apiPort()<1||options.apiPort()>65535||options.control().enabled()&&!options.control().configured())throw new SetupError("관리 ID 또는 로컬 포트를 확인하세요.");
            try(DiscordController controller=new DiscordController(root,settings,options)){
                Thread hook=new Thread(controller::close,"BlackWikiSMP-controller-shutdown");Runtime.getRuntime().addShutdownHook(hook);
                controller.start();System.out.println("BlackWikiSMP "+Version.V+" 디스코드 도우미 실행 · 게임 서버가 꺼져도 이 창을 유지하세요. 설정 변경 후에는 도우미를 재시작하세요.");
                try{new CountDownLatch(1).await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}
                finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){}}
            }
        }
    }
    static void setup(Path root)throws Exception{
        if(ServerSupervisor.worldLocked(root))throw new SetupError("서버를 완전히 종료한 뒤 설정하세요.");
        Path config=root.resolve("plugins/BlackWikiSMP/config.yml"),controller=root.resolve("blackwikismp-controller/controller.yml");
        YamlConfiguration smp=load(config);if(smp.getInt("config-version",24)>40)throw new SetupError("최신 설정의 다운그레이드를 중단했습니다.");
        YamlConfiguration settings=Files.exists(controller)?load(controller):new YamlConfiguration();
        if(!settings.isSet("server-command")){
            List<Path> jars;try(var files=Files.list(root)){jars=files.filter(p->p.toString().endsWith(".jar")&&paper(p)).toList();}
            if(jars.size()!=1)throw new SetupError("서버 폴더에 실행할 Paper JAR 한 개를 두세요.");
            String java=ProcessHandle.current().info().command().orElse("java");
            settings.set("server-command",List.of(java,"-Xms512M","-Xmx2G","-jar",jars.getFirst().getFileName().toString(),"--nogui"));
        }
        if(settings.getStringList("server-command").isEmpty())throw new SetupError("server-command 목록이 비어 있습니다.");
        Path backup=config.resolveSibling("config.yml.before-discord-controller.bak");if(!Files.exists(backup))Files.copy(config,backup);
        smp.set("discord.gateway-owner","controller");ControlApi.secret(config.getParent());
        atomic(controller,settings.saveToString());atomic(config,smp.saveToString());
        System.out.println("도우미 설정 완료 · 기존 토큰/채널/게임 데이터 유지");
        System.out.println("config.yml의 discord.enabled, guild-id, channel-id, control.enabled와 허용 ID를 입력하세요. controller.yml의 server-command를 확인한 뒤 도우미를 실행하세요.");
    }
    private static final class SetupError extends IOException{SetupError(String message){super(message);}}
    private static boolean paper(Path file){try(JarFile jar=new JarFile(file.toFile())){String main=jar.getManifest().getMainAttributes().getValue("Main-Class");return main!=null&&main.toLowerCase(Locale.ROOT).contains("paperclip");}catch(Exception ex){return false;}}
    private static YamlConfiguration load(Path file)throws Exception{YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());return y;}
    private static void atomic(Path path,String value)throws IOException{Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}
}
