package kr.blackwiki.smp;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, ordered bot message outbox. No server-thread HTTP and no redirects. */
final class DiscordRelay implements AutoCloseable {
    record Settings(boolean enabled,URI endpoint,String token,int capacity,long interval){@Override public String toString(){return "DiscordSettings[enabled="+enabled+", endpoint="+endpoint+", token=[숨김], capacity="+capacity+"]";}}
    record Reply(int status,String body,Map<String,List<String>> headers){}
    interface Transport extends AutoCloseable {
        Reply send(URI endpoint,String token,String payload)throws Exception;
        default void close(){}
    }
    static final class WebTransport implements Transport {
        private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        @Override public Reply send(URI endpoint,String token,String payload)throws Exception{
            HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json; charset=utf-8").header("User-Agent","BlackWikiSMP/"+Version.V).header("Authorization","Bot "+token).POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
            HttpResponse<String> r=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));return new Reply(r.statusCode(),r.body(),r.headers().map());
        }
    @Override public void close(){client.shutdownNow();}
    }
    private final Object lock=new Object();
    private final ArrayDeque<String> pending=new ArrayDeque<>(),urgent=new ArrayDeque<>();
    private final Transport transport;
    private final ScheduledExecutorService worker;
    private Settings settings;
    private String batch;
    private int batchLines,retries;
    private long next,revision;
    private boolean stopped,permanent,sending,accepting=true;
    private String state="꺼짐";
    final AtomicLong sent=new AtomicLong(),failed=new AtomicLong(),dropped=new AtomicLong();
    DiscordRelay(Settings settings){this(settings,new WebTransport(),true);}
    DiscordRelay(Settings settings,Transport transport,boolean schedule){
        this.transport=transport;this.worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("BlackWikiSMP-discord").factory());reload(settings);
        if(schedule)worker.scheduleWithFixedDelay(()->{try{pump(System.currentTimeMillis());}catch(Exception ignored){synchronized(lock){state="전송 작업 오류";}}},250,250,TimeUnit.MILLISECONDS);
    }
    static URI endpoint(String channel){if(!channel.matches("[0-9]{6,22}"))throw new IllegalArgumentException("Discord 채널 ID는 숫자입니다.");return URI.create("https://discord.com/api/v10/channels/"+channel+"/messages");}
    static String token(String value){String token=value.strip();if(token.startsWith("Bot "))token=token.substring(4).strip();if(!token.matches("[A-Za-z0-9._-]{20,256}"))throw new IllegalArgumentException("봇 토큰 형식 확인 필요");return token;}
    void reload(Settings nextSettings){synchronized(lock){if(java.util.Objects.equals(settings,nextSettings)&&!permanent)return;revision++;settings=nextSettings;pending.clear();urgent.clear();batch=null;batchLines=0;retries=0;next=0;permanent=false;state=!settings.enabled?"꺼짐":settings.endpoint==null||settings.token==null||settings.token.isEmpty()?"봇 토큰·채널 ID 설정 필요":"봇 전송 대기";}}
    boolean offer(String line){synchronized(lock){if(stopped||!accepting||!settings.enabled||settings.endpoint==null||settings.token==null||settings.token.isEmpty()||permanent)return false;if(pending.size()+urgent.size()+(batch==null?0:batchLines)>=settings.capacity){dropped.incrementAndGet();return false;}pending.add(clip(line,1850));return true;}}
    boolean priority(String line){synchronized(lock){if(stopped||!accepting||!settings.enabled||settings.endpoint==null||settings.token==null||settings.token.isEmpty()||permanent)return false;if(pending.size()+urgent.size()+(batch==null?0:batchLines)>=settings.capacity){if(!pending.isEmpty()){pending.removeLast();dropped.incrementAndGet();}else if(urgent.size()>=4){dropped.incrementAndGet();return false;}}urgent.addLast(clip(line,1850));return true;}}
    String state(){synchronized(lock){return state;}}
    int queued(){synchronized(lock){return pending.size()+urgent.size()+(batch==null?0:batchLines);}}
    static String clip(String text,int limit){if(text.length()<=limit)return text;int end=limit-1;if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;return text.substring(0,end)+"…";}
    /** Lines from {@link #tag} carry kind/name/time; anything else becomes a plain embed. */
    static final char SEP='\u0000',RS='\u001e';
    static String tag(String kind,String name,String message,long millis){return SEP+kind+SEP+name+SEP+message+SEP+millis;}
    static JsonObject embed(String line){
        JsonObject e=new JsonObject();String[] f=line.split(String.valueOf(SEP),-1);
        if(f.length==5&&f[0].isEmpty()){
            String kind=f[1];e.addProperty("color",switch(kind){case "채팅"->0x5865F2;case "입장"->0x57F287;case "퇴장"->0xED4245;case "서버"->0xFEE75C;case "관리"->0xEB459E;default->0x99AAB5;});
            JsonObject author=new JsonObject();author.addProperty("name",clip(f[2],250));e.add("author",author);
            e.addProperty("description",clip(f[3],4000));
            JsonObject footer=new JsonObject();footer.addProperty("text",switch(kind){case "채팅"->"💬 채팅";case "입장"->"🟢 입장";case "퇴장"->"🔴 퇴장";case "서버"->"🖥️ 서버";case "관리"->"🛡️ 관리";default->kind;});e.add("footer",footer);
            try{e.addProperty("timestamp",Instant.ofEpochMilli(Long.parseLong(f[4])).toString());}catch(NumberFormatException ignored){}
        }else{e.addProperty("color",0x99AAB5);e.addProperty("description",clip(line,4000));}
        return e;
    }
    static String payload(String batch){JsonObject json=new JsonObject();JsonArray embeds=new JsonArray();for(String line:batch.split(String.valueOf(RS)))embeds.add(embed(line));json.add("embeds",embeds);json.addProperty("tts",false);JsonObject mentions=new JsonObject();mentions.add("parse",new JsonArray());json.add("allowed_mentions",mentions);return json.toString();}
    static double retrySeconds(Reply r){
        try{JsonElement n=JsonParser.parseString(r.body).getAsJsonObject().get("retry_after");if(n!=null){double v=n.getAsDouble();if(Double.isFinite(v)&&v>=0)return Math.max(.25,v);}}catch(Exception ignored){}
        for(var h:r.headers.entrySet())if(h.getKey().equalsIgnoreCase("Retry-After"))try{return Math.max(.25,Double.parseDouble(h.getValue().getFirst()));}catch(Exception ignored){}
        return 2;
    }
    void pump(long now){
        Settings current;String body;long generation;
        synchronized(lock){
            if(stopped||sending||!settings.enabled||settings.endpoint==null||settings.token==null||settings.token.isEmpty()||permanent||now<next)return;
            if(batch==null){if(pending.isEmpty()&&urgent.isEmpty())return;StringBuilder joined=new StringBuilder();batchLines=0;while(!urgent.isEmpty()||!pending.isEmpty()){ArrayDeque<String> source=urgent.isEmpty()?pending:urgent;String line=source.peek();if(!joined.isEmpty()&&(batchLines>=10||joined.length()+1+line.length()>5000))break;if(!joined.isEmpty())joined.append(RS);joined.append(source.remove());batchLines++;}batch=joined.toString();retries=0;}
            current=settings;generation=revision;body=payload(batch);sending=true;
        }
        Reply response;
        try{response=transport.send(current.endpoint,current.token,body);}catch(Exception e){synchronized(lock){sending=false;if(generation==revision){failed.incrementAndGet();state="일시적 연결 실패";backoff(now);}}return;}
        synchronized(lock){
            sending=false;if(stopped||generation!=revision)return;
            int status=response.status;
            if(status>=200&&status<300){sent.addAndGet(batchLines);batch=null;batchLines=0;retries=0;state="정상 전송";next=Math.max(now+current.interval,delay(now,remainingDelay(response)));}
            else if(status==429){state="전송 제한 대기";next=delay(now,retrySeconds(response));}
            else if(status>=500){failed.incrementAndGet();state="Discord 서버 오류";backoff(now);}
            else{failed.incrementAndGet();dropped.addAndGet(batchLines+pending.size()+urgent.size());batch=null;batchLines=0;pending.clear();urgent.clear();permanent=true;state=switch(status){case 401->"봇 토큰 인증 실패 · 설정 후 리로드";case 403->"봇 채널 권한 부족 · 채널 보기/메시지 보내기 확인";case 404->"채널 ID 또는 봇 초대 확인 필요";default->"HTTP "+status+" · 설정 확인 후 리로드";};}
        }
    }
    static long delay(long now,double seconds){if(!Double.isFinite(seconds)||seconds<0)seconds=2;double ms=Math.ceil(seconds*1000);return ms>=Long.MAX_VALUE-now?Long.MAX_VALUE:now+(long)ms;}
    static double remainingDelay(Reply r){String remaining=null,reset=null;for(var entry:r.headers.entrySet()){if(entry.getKey().equalsIgnoreCase("X-RateLimit-Remaining")&&!entry.getValue().isEmpty())remaining=entry.getValue().getFirst();if(entry.getKey().equalsIgnoreCase("X-RateLimit-Reset-After")&&!entry.getValue().isEmpty())reset=entry.getValue().getFirst();}try{double seconds=Double.parseDouble(reset);return "0".equals(remaining)&&Double.isFinite(seconds)&&seconds>=0?seconds:0;}catch(Exception ex){return 0;}}
    private void backoff(long now){if(++retries>5){dropped.addAndGet(batchLines);batch=null;batchLines=0;retries=0;}next=now+Math.min(60000,1000L<<Math.min(6,retries));}
    void closeGracefully(long milliseconds){synchronized(lock){accepting=false;}long deadline=System.nanoTime()+Math.max(0,Math.min(5000,milliseconds))*1_000_000L;while(System.nanoTime()-deadline<0){synchronized(lock){if(pending.isEmpty()&&urgent.isEmpty()&&batch==null&&!sending)break;}try{Thread.sleep(20);}catch(InterruptedException ex){Thread.currentThread().interrupt();break;}}close();}
    @Override public void close(){synchronized(lock){stopped=true;pending.clear();urgent.clear();batch=null;batchLines=0;}worker.shutdownNow();try{transport.close();}catch(Exception ignored){}}
}
