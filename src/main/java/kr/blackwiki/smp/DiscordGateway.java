package kr.blackwiki.smp;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Discord v10 Gateway: heartbeat ACK watchdog, resume, fatal intent/auth handling and bounded frames. */
final class DiscordGateway implements AutoCloseable {
    interface Link {CompletionStage<?> send(String text);void abort();}
    interface Sink {void opened(Link link);void text(String text);void closed(int code);void failed();}
    interface Connector extends AutoCloseable {void connect(URI uri,Sink sink);default void close(){}}
    interface Discovery {URI gateway()throws Exception;}
    static final class JdkConnector implements Connector {
        private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
        @Override public void connect(URI uri,Sink sink){
            client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(uri,new WebSocket.Listener(){
                private final StringBuilder fragments=new StringBuilder();
                private CompletableFuture<?> tail=CompletableFuture.completedFuture(null);
                @Override public void onOpen(WebSocket ws){sink.opened(new Link(){public synchronized CompletionStage<?> send(String text){tail=tail.handle((v,e)->null).thenCompose(v->ws.sendText(text,true));return tail;}public void abort(){ws.abort();}});ws.request(1);}
                @Override public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){if(fragments.length()+data.length()>2_097_152){ws.abort();sink.closed(1009);return null;}fragments.append(data);if(last){String text=fragments.toString();fragments.setLength(0);sink.text(text);}ws.request(1);return null;}
                @Override public CompletionStage<?> onBinary(WebSocket ws,java.nio.ByteBuffer data,boolean last){ws.abort();sink.closed(1003);return null;}
                @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason){sink.closed(code);return null;}
                @Override public void onError(WebSocket ws,Throwable error){sink.failed();}
            }).exceptionally(ex->{sink.failed();return null;});
        }
        @Override public void close(){client.shutdownNow();}
    }
    private final String token;
    private final Consumer<JsonObject> messages;
    private final Connector connector;
    private final Discovery discovery;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("BlackWikiSMP-gateway").factory());
    private final Object lock=new Object();
    private Link link;
    private ScheduledFuture<?> heartbeat,reconnect,identify;
    private long generation,sequence=-1;
    private int failures;
    private String session="",resume="";
    private boolean closed,fatal,awaitingAck,connecting;
    private volatile boolean ready;
    private volatile String status="Gateway 연결 대기",botId="";
    final AtomicLong received=new AtomicLong(),reconnections=new AtomicLong();
    private static final AtomicLong IDENTIFY_AFTER=new AtomicLong();
    DiscordGateway(String token,Consumer<JsonObject> messages){this(token,messages,new JdkConnector(),()->discover(token));}
    DiscordGateway(String token,Consumer<JsonObject> messages,Connector connector,Discovery discovery){this.token=DiscordRelay.token(token);this.messages=messages;this.connector=connector;this.discovery=discovery;}
    void start(){synchronized(lock){if(closed||fatal||connecting||reconnect!=null||link!=null)return;reconnect=timer.schedule(this::connect,0,TimeUnit.MILLISECONDS);}}
    private static URI discover(String token)throws Exception{
        try(HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()){
            HttpRequest r=HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/gateway/bot")).timeout(Duration.ofSeconds(8)).header("Authorization","Bot "+DiscordRelay.token(token)).header("User-Agent","BlackWikiSMP/"+Version.V).GET().build();
            var reply=client.send(r,HttpResponse.BodyHandlers.ofString());if(reply.statusCode()==401)throw new GatewayFatal("봇 토큰 인증 실패");if(reply.statusCode()==429)throw new IllegalStateException("Gateway 요청 제한");if(reply.statusCode()!=200)throw new IllegalStateException("Gateway 조회 실패");
            JsonObject d=JsonParser.parseString(reply.body()).getAsJsonObject();if(d.has("session_start_limit")&&d.getAsJsonObject("session_start_limit").get("remaining").getAsInt()==0)throw new GatewayFatal("Gateway 세션 시작 한도 · 나중에 도우미/연동을 재시작하세요");return gatewayUri(d.get("url").getAsString());
        }
    }
    static URI gatewayUri(String raw){URI uri=URI.create(raw);String host=uri.getHost();if(!"wss".equals(uri.getScheme())||host==null||!(host.equals("gateway.discord.gg")||host.endsWith(".discord.gg"))||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IllegalArgumentException("Invalid Discord Gateway endpoint");return URI.create("wss://"+host+(uri.getPort()==443?":443":"")+"/?v=10&encoding=json");}
    private void connect(){
        long epoch;String resumed;
        synchronized(lock){reconnect=null;if(closed||fatal)return;connecting=true;epoch=++generation;ready=false;awaitingAck=false;resumed=resume;status="Gateway 연결 중";}
        try{URI target=resumed.isEmpty()?discovery.gateway():gatewayUri(resumed);connector.connect(target,new Sink(){
            public void opened(Link next){synchronized(lock){if(closed||epoch!=generation){next.abort();return;}connecting=false;link=next;}}
            public void text(String text){packet(epoch,text);}
            public void closed(int code){disconnect(epoch,code);}
            public void failed(){disconnect(epoch,1006);}
        });}catch(GatewayFatal ex){synchronized(lock){if(epoch==generation){connecting=false;fatal=true;status=ex.getMessage();}}}catch(Exception ex){retry(epoch,Math.min(60_000,1000L<<Math.min(5,failures++)),false);}
    }
    private void packet(long epoch,String raw){
        synchronized(lock){if(closed||fatal||epoch!=generation)return;}
        try{
            JsonObject packet=JsonParser.parseString(raw).getAsJsonObject();int op=packet.get("op").getAsInt();JsonElement data=packet.get("d");
            synchronized(lock){
                if(closed||epoch!=generation)return;
                if(packet.has("s")&&!packet.get("s").isJsonNull())sequence=packet.get("s").getAsLong();
                switch(op){
                    case 10->{long interval=data.getAsJsonObject().get("heartbeat_interval").getAsLong();if(interval<500||interval>300_000)throw new IllegalArgumentException("Invalid heartbeat");cancel(heartbeat);awaitingAck=false;heartbeat=timer.scheduleAtFixedRate(()->beat(epoch,true),(long)(Math.random()*interval),interval,TimeUnit.MILLISECONDS);authenticate(epoch);}
                    case 11->awaitingAck=false;
                    case 1->beat(epoch,false);
                    case 7->retry(epoch,250,false);
                    case 9->retry(epoch,1000+(long)(Math.random()*4000),data==null||data.isJsonNull()||!data.getAsBoolean());
                    case 0->{String type=DiscordInbound.text(packet,"t");if(type.equals("READY")){JsonObject readyData=data.getAsJsonObject();session=readyData.get("session_id").getAsString();resume=gatewayUri(readyData.get("resume_gateway_url").getAsString()).toString();botId=readyData.getAsJsonObject("user").get("id").getAsString();ready=true;failures=0;status="양방향 Gateway 연결됨";}else if(type.equals("RESUMED")){ready=true;failures=0;status="양방향 Gateway 세션 복구됨";}else if(type.equals("MESSAGE_CREATE")){received.incrementAndGet();try{messages.accept(data.getAsJsonObject());}catch(RuntimeException ex){status="Gateway 수신 처리 확인 필요";}}}
                    default->{}
                }
            }
        }catch(Exception ex){retry(epoch,2000,false);}
    }
    private void authenticate(long epoch){
        if(!session.isEmpty()&&sequence>=0){JsonObject d=new JsonObject();d.addProperty("token",token);d.addProperty("session_id",session);d.addProperty("seq",sequence);send(epoch,6,d);return;}
        long now=System.currentTimeMillis(),slot=IDENTIFY_AFTER.getAndUpdate(old->Math.max(old,now)+5000),delay=Math.max(0,slot-now);cancel(identify);
        identify=timer.schedule(()->{synchronized(lock){if(closed||epoch!=generation)return;JsonObject d=new JsonObject();d.addProperty("token",token);d.addProperty("intents",1|512|32768);JsonObject properties=new JsonObject();properties.addProperty("os",System.getProperty("os.name","unknown"));properties.addProperty("browser","BlackWikiSMP");properties.addProperty("device","BlackWikiSMP");d.add("properties",properties);send(epoch,2,d);}},delay,TimeUnit.MILLISECONDS);
    }
    private void beat(long epoch,boolean watchdog){synchronized(lock){if(closed||epoch!=generation||link==null)return;if(watchdog&&awaitingAck){retry(epoch,1000,false);return;}awaitingAck=true;send(epoch,1,sequence<0?JsonNull.INSTANCE:new JsonPrimitive(sequence));}}
    void heartbeatNow(){synchronized(lock){beat(generation,true);}}
    private void send(long epoch,int opcode,JsonElement data){Link current=link;if(current==null)return;JsonObject packet=new JsonObject();packet.addProperty("op",opcode);packet.add("d",data);try{current.send(packet.toString()).whenComplete((v,ex)->{if(ex!=null)retry(epoch,1000,false);});}catch(Exception ex){retry(epoch,1000,false);}}
    private void disconnect(long epoch,int code){
        synchronized(lock){if(closed||epoch!=generation)return;if(code==4004||code==4010||code==4011||code==4013||code==4014){fatal=true;ready=false;status=code==4014?"Message Content Intent 승인/활성화 필요":code==4004?"봇 토큰 인증 실패":"Gateway 설정 확인 필요 ("+code+")";cancel(heartbeat);cancel(identify);link=null;return;}}
        retry(epoch,code==4008?60_000:Math.min(60_000,1000L<<Math.min(5,failures++)),code==4007||code==4009);
    }
    private void retry(long epoch,long delay,boolean reset){
        synchronized(lock){if(closed||fatal||epoch!=generation)return;generation++;connecting=false;cancel(heartbeat);cancel(identify);ready=false;awaitingAck=false;if(reset){session="";resume="";sequence=-1;}Link old=link;link=null;if(old!=null)old.abort();status="Gateway 재연결 대기";reconnections.incrementAndGet();if(reconnect==null)reconnect=timer.schedule(this::connect,delay,TimeUnit.MILLISECONDS);}
    }
    private static void cancel(Future<?> task){if(task!=null)task.cancel(false);}
    boolean fatal(){synchronized(lock){return fatal;}}
    boolean ready(){return ready;}
    String botId(){return botId;}
    String status(){return status;}
    @Override public void close(){synchronized(lock){if(closed)return;closed=true;generation++;ready=false;cancel(heartbeat);cancel(reconnect);cancel(identify);if(link!=null)link.abort();link=null;status="Gateway 종료";}timer.shutdownNow();try{connector.close();}catch(Exception ignored){}}
    private static final class GatewayFatal extends Exception{GatewayFatal(String message){super(message);}}
}
