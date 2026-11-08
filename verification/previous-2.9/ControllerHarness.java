package kr.blackwiki.smp;

import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.lang.reflect.*;
import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Verification only: production controller and real Paper; mock Discord transport. */
public final class ControllerHarness {
    static Object get(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static class Connector implements DiscordGateway.Connector{volatile DiscordGateway.Sink sink;public void connect(URI uri,DiscordGateway.Sink s){sink=s;s.opened(new DiscordGateway.Link(){public CompletionStage<?> send(String text){if(JsonParser.parseString(text).getAsJsonObject().get("op").getAsInt()==1)s.text("{\"op\":11}");return CompletableFuture.completedFuture(null);}public void abort(){}});s.text("{\"op\":10,\"d\":{\"heartbeat_interval\":300000}}");s.text("{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"session_id\":\"native-test\",\"resume_gateway_url\":\"wss://gateway.discord.gg\",\"user\":{\"id\":\"888888888888888888\"}}}");}}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]).toRealPath();YamlConfiguration cfg=new YamlConfiguration();cfg.load(root.resolve("plugins/BlackWikiSMP/config.yml").toFile());YamlConfiguration settings=new YamlConfiguration();settings.load(root.resolve("blackwikismp-controller/controller.yml").toFile());DiscordOptions options=DiscordOptions.read(cfg);var constructor=DiscordController.class.getDeclaredConstructor(Path.class,YamlConfiguration.class,DiscordOptions.class);constructor.setAccessible(true);
        try(DiscordController controller=constructor.newInstance(root,settings,options)){
            DiscordRelay old=(DiscordRelay)get(controller,"relay"),replies=(DiscordRelay)get(controller,"replies");old.close();if(replies!=old)replies.close();DiscordRelay fake=new DiscordRelay(options.outgoing(),(u,t,p)->{Files.writeString(root.resolve("controller-outbox.jsonl"),p+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);System.out.println("OUTBOX "+p);return new DiscordRelay.Reply(200,"{}",Map.of());},true);set(controller,"relay",fake);set(controller,"replies",fake);DiscordGateway previous=(DiscordGateway)get(controller,"gateway");Consumer<JsonObject> incoming=(Consumer<JsonObject>)get(previous,"messages");previous.close();Connector connector=new Connector();DiscordGateway gateway=new DiscordGateway(options.token(),incoming,connector,()->URI.create("wss://gateway.discord.gg"));set(controller,"gateway",gateway);var start=DiscordController.class.getDeclaredMethod("start");start.setAccessible(true);start.invoke(controller);ServerSupervisor supervisor=(ServerSupervisor)get(controller,"server");long deadline=System.nanoTime()+5_000_000_000L;while(!gateway.ready()&&System.nanoTime()<deadline)Thread.sleep(20);if(!gateway.ready())throw new IllegalStateException("mock gateway not ready");System.out.println("HARNESS_READY");long id=900000000000000000L;
            try(var in=new java.io.BufferedReader(new java.io.InputStreamReader(System.in))){String line;while((line=in.readLine())!=null){
                if(line.equals("exit")){Process child=(Process)get(supervisor,"child");if(child!=null&&child.isAlive()){supervisor.stop();if(!child.waitFor(40,TimeUnit.SECONDS)){child.destroy();child.waitFor(10,TimeUnit.SECONDS);}}break;}
                if(line.equals("status")){System.out.println("HARNESS_STATUS gateway="+gateway.ready()+" child="+supervisor.ownedAlive()+" "+supervisor.status());continue;}
                if(line.startsWith("console ")){Process child=(Process)get(supervisor,"child");if(child!=null&&child.isAlive()){child.outputWriter().write(line.substring(8)+"\n");child.outputWriter().flush();}continue;}
                if(line.startsWith("discord ")||line.startsWith("denied ")){boolean allowed=line.startsWith("discord ");JsonObject e=new JsonObject();e.addProperty("id",Long.toString(++id));e.addProperty("guild_id","111111111111111111");e.addProperty("channel_id","222222222222222222");e.addProperty("content",line.substring(allowed?8:7));JsonObject a=new JsonObject();a.addProperty("id",allowed?"333333333333333333":"333333333333333339");a.addProperty("username","controller-test");e.add("author",a);JsonObject packet=new JsonObject();packet.addProperty("op",0);packet.addProperty("s",2);packet.addProperty("t","MESSAGE_CREATE");packet.add("d",e);connector.sink.text(packet.toString());}
            }}
        }
    }
}
