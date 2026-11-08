package kr.blackwiki.smp;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Loopback-only status/chat/graceful-stop API. There is deliberately no arbitrary command endpoint. */
final class ControlApi implements AutoCloseable {
    record Chat(String name,String content){}
    private final HttpServer server;
    private final ExecutorService workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon(true).name("BlackWikiSMP-control-http").factory());
    private final String secret;
    private final Consumer<Chat> chat;
    private final Runnable stop;
    private final java.util.concurrent.atomic.AtomicBoolean stopAccepted=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long contact;
    boolean connected(){return contact>0&&System.currentTimeMillis()-contact<10000;}
    private volatile String snapshot="{\"ready\":false,\"state\":\"starting\"}";
    ControlApi(int port,String secret,Consumer<Chat> chat,Runnable stop)throws Exception{
        if(port<0||port>65535||secret==null||!secret.matches("[A-Za-z0-9_-]{40,128}"))throw new IllegalArgumentException("Invalid loopback API settings");
        this.secret=secret;this.chat=chat;this.stop=stop;server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),16);server.setExecutor(workers);server.createContext("/",this::handle);server.start();
    }
    int port(){return server.getAddress().getPort();}
    void snapshot(JsonObject state){snapshot=state.toString();}
    private void handle(HttpExchange e){
        try{
            List<String> auth=e.getRequestHeaders().get("Authorization");
            if(!e.getRemoteAddress().getAddress().isLoopbackAddress()||auth==null||auth.size()!=1||!MessageDigest.isEqual(("Bearer "+secret).getBytes(StandardCharsets.UTF_8),auth.getFirst().getBytes(StandardCharsets.UTF_8))){respond(e,401,"{\"error\":\"unauthorized\"}");return;}
            contact=System.currentTimeMillis();String path=e.getRequestURI().getPath(),method=e.getRequestMethod();
            if(path.equals("/v1/status")&&method.equals("GET")){respond(e,200,snapshot);return;}
            if(!method.equals("POST")){respond(e,405,"{\"error\":\"method\"}");return;}
            byte[] body=e.getRequestBody().readNBytes(8193);if(body.length>8192){respond(e,413,"{\"error\":\"size\"}");return;}
            if(path.equals("/v1/chat")){JsonObject json=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();String name=DiscordInbound.plain(DiscordInbound.text(json,"name"),48),content=DiscordInbound.plain(DiscordInbound.text(json,"content"),500);if(name.isEmpty()||content.isEmpty()){respond(e,400,"{\"error\":\"empty\"}");return;}chat.accept(new Chat(name,content));respond(e,202,"{\"accepted\":true}");}
            else if(path.equals("/v1/stop")){respond(e,202,"{\"accepted\":true}");if(stopAccepted.compareAndSet(false,true))stop.run();}
            else respond(e,404,"{\"error\":\"not_found\"}");
        }catch(Exception ex){try{respond(e,400,"{\"error\":\"request\"}");}catch(Exception ignored){}}finally{e.close();}
    }
    private static void respond(HttpExchange e,int code,String text)throws Exception{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(code,bytes.length);e.getResponseBody().write(bytes);}
    static String identity(Path root)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root.toRealPath().toString().getBytes(StandardCharsets.UTF_8)));}
    static String secret(Path folder)throws Exception{
        Path file=folder.resolve("discord-control.key");if(Files.isSymbolicLink(file))throw new java.io.IOException("Control key cannot be a symbolic link");
        if(!Files.exists(file)){Files.createDirectories(folder);byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String value=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);try{var attr=Files.getFileStore(folder).supportsFileAttributeView("posix")?new java.nio.file.attribute.FileAttribute<?>[]{java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))}:new java.nio.file.attribute.FileAttribute<?>[0];try(var out=java.nio.channels.FileChannel.open(file,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),attr)){var buffer=java.nio.ByteBuffer.wrap((value+"\n").getBytes(StandardCharsets.UTF_8));while(buffer.hasRemaining())out.write(buffer);out.force(true);}}catch(FileAlreadyExistsException ignored){}}
        String value=Files.readString(file,StandardCharsets.UTF_8).strip();if(!value.matches("[A-Za-z0-9_-]{40,128}"))throw new java.io.IOException("Invalid control key; preserve existing file for review");return value;
    }
    static final class Client implements AutoCloseable {
        private final HttpClient client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
        private final int port;private final String secret;
        Client(int port,String secret){this.port=port;this.secret=secret;}
        JsonObject status()throws Exception{var response=request("GET","/v1/status","");if(response.statusCode()!=200)throw new java.io.IOException("Control status unavailable");return JsonParser.parseString(response.body()).getAsJsonObject();}
        boolean chat(DiscordInbound.Message m)throws Exception{JsonObject body=new JsonObject();body.addProperty("name",m.name());body.addProperty("content",m.content());return request("POST","/v1/chat",body.toString()).statusCode()==202;}
        boolean stop()throws Exception{return request("POST","/v1/stop","{}").statusCode()==202;}
        private HttpResponse<String> request(String method,String path,String body)throws Exception{var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(3)).header("Authorization","Bearer "+secret).header("Content-Type","application/json").method(method,method.equals("GET")?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();return client.send(r,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
        @Override public void close(){client.shutdownNow();}
    }
    @Override public void close(){server.stop(0);workers.shutdownNow();}
}
