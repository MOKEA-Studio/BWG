package kr.blackwiki.smp;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ControlApiTest {
    static final String SECRET="x".repeat(43);
    @TempDir Path dir;
    HttpResponse<String> request(int port,String path,String method,String auth,String body)throws Exception{
        try(var client=HttpClient.newHttpClient()){var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(3));if(auth!=null)b.header("Authorization",auth);return client.send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    }
    @Test void missingOrWrongKeyCannotReadOrStop()throws Exception{AtomicInteger stops=new AtomicInteger();try(var api=new ControlApi(0,SECRET,c->{},stops::incrementAndGet)){for(String key:new String[]{null,"Bearer wrong"}){assertEquals(401,request(api.port(),"/v1/status","GET",key,"").statusCode());assertEquals(401,request(api.port(),"/v1/stop","POST",key,"{}").statusCode());}assertEquals(0,stops.get());}}
    @Test void authenticatedStatusHasNoSecretAndUsesSnapshot()throws Exception{try(var api=new ControlApi(0,SECRET,c->{},()->{})){JsonObject s=new JsonObject();s.addProperty("ready",true);s.addProperty("online",7);api.snapshot(s);try(var client=new ControlApi.Client(api.port(),SECRET)){assertEquals(7,client.status().get("online").getAsInt());}assertFalse(request(api.port(),"/v1/status","GET","Bearer "+SECRET,"").body().contains(SECRET));}}
    @Test void chatIsAcceptedAsPlainDataAndCannotExecuteCommands()throws Exception{List<ControlApi.Chat> got=new ArrayList<>();try(var api=new ControlApi(0,SECRET,got::add,()->{});var client=new ControlApi.Client(api.port(),SECRET)){assertTrue(client.chat(Discord29Test.message(Discord29Test.U,Set.of(),"/op somebody")));assertEquals("/op somebody",got.getFirst().content());assertEquals(404,request(api.port(),"/v1/command","POST","Bearer "+SECRET,"{\"command\":\"op somebody\"}").statusCode());}}
    @Test void invalidJsonOversizeWrongMethodAndEmptyChatAreRejected()throws Exception{try(var api=new ControlApi(0,SECRET,c->fail("unexpected chat"),()->fail("unexpected stop"))){assertEquals(400,request(api.port(),"/v1/chat","POST","Bearer "+SECRET,"[").statusCode());assertEquals(413,request(api.port(),"/v1/chat","POST","Bearer "+SECRET,"x".repeat(9000)).statusCode());assertEquals(400,request(api.port(),"/v1/chat","POST","Bearer "+SECRET,"{}").statusCode());assertEquals(405,request(api.port(),"/v1/stop","GET","Bearer "+SECRET,"").statusCode());}}
    @Test void validStopCallsOnlyFixedStopAction()throws Exception{AtomicInteger stops=new AtomicInteger();try(var api=new ControlApi(0,SECRET,c->{},stops::incrementAndGet);var client=new ControlApi.Client(api.port(),SECRET)){assertTrue(client.stop());assertTrue(client.stop());long end=System.nanoTime()+1_000_000_000L;while(stops.get()==0&&System.nanoTime()<end)Thread.yield();assertEquals(1,stops.get());}}
    @Test void keyIsStablePrivateAndNeverReplacedWhenInvalid()throws Exception{String first=ControlApi.secret(dir);assertEquals(first,ControlApi.secret(dir));assertTrue(first.matches("[A-Za-z0-9_-]{43}"));if(Files.getFileStore(dir).supportsFileAttributeView("posix"))assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("discord-control.key"))));Files.writeString(dir.resolve("discord-control.key"),"bad");assertThrows(Exception.class,()->ControlApi.secret(dir));assertEquals("bad",Files.readString(dir.resolve("discord-control.key")));}
    @Test void symlinkKeyIsRejected()throws Exception{Path target=dir.resolve("other");Files.writeString(target,SECRET);Files.createSymbolicLink(dir.resolve("discord-control.key"),target);assertThrows(Exception.class,()->ControlApi.secret(dir));}
    @Test void supervisorRefusesSecondProcessWhenWorldLocked()throws Exception{Path w=dir.resolve("world");Files.createDirectories(w);try(FileChannel ch=FileChannel.open(w.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=ch.lock();var s=new ServerSupervisor(dir,List.of("does-not-exist"),dir.resolve("log/server.log"),new ControlApi.Client(1,SECRET),text->{})){assertTrue(s.start().contains("중복"));assertFalse(s.ownedAlive());}}
    @Test void ownedServerCanStopAndStartAgainWithoutStoppingController()throws Exception{
        Path script=dir.resolve("fake.py");Files.writeString(script,"import sys\nfrom pathlib import Path\nPath('started').write_text('ready')\nfor line in sys.stdin:\n if line.strip()=='stop':break\n");List<String> notices=new ArrayList<>();
        try(var s=new ServerSupervisor(dir,List.of("python3",script.toString()),dir.resolve("log/server.log"),new ControlApi.Client(1,SECRET),notices::add)){
            assertTrue(s.start().contains("요청"));await(()->Files.exists(dir.resolve("started")));assertTrue(s.ownedAlive());assertTrue(s.start().contains("이미"));assertTrue(s.stop().contains("정상 종료"));await(()->{s.poll();return !s.ownedAlive();});assertEquals(1,notices.size());Files.delete(dir.resolve("started"));assertTrue(s.start().contains("요청"));await(()->Files.exists(dir.resolve("started")));assertTrue(s.stop().contains("정상 종료"));await(()->{s.poll();return !s.ownedAlive();});assertEquals(2,notices.size());
        }
    }
    @Test void activeApiPreventsStartingEvenWithoutWorldLock()throws Exception{try(var api=new ControlApi(0,SECRET,c->{},()->{});var s=new ServerSupervisor(dir,List.of("does-not-exist"),dir.resolve("server.log"),new ControlApi.Client(api.port(),SECRET),text->{})){assertTrue(s.start().contains("중복"));assertFalse(s.ownedAlive());}}
    @Test void repeatedPollDoesNotDuplicateStartNotice()throws Exception{List<String> notices=new ArrayList<>();try(var api=new ControlApi(0,SECRET,c->{},()->{});var s=new ServerSupervisor(dir,List.of("does-not-exist"),dir.resolve("server.log"),new ControlApi.Client(api.port(),SECRET),notices::add)){JsonObject state=new JsonObject();state.addProperty("ready",true);state.addProperty("server_id",ControlApi.identity(dir));state.addProperty("pid",ProcessHandle.current().pid());state.addProperty("online",3);state.addProperty("queue",2);api.snapshot(state);s.poll();s.poll();assertEquals(1,notices.size());assertTrue(s.status().contains("접속 3명 · 대기 2명"));assertTrue(s.stop().contains("저장 후"));}}
    @Test void wrongServerIdentityCannotBeAnnouncedOrStopped()throws Exception{List<String> notices=new ArrayList<>();AtomicInteger stops=new AtomicInteger();try(var api=new ControlApi(0,SECRET,c->{},stops::incrementAndGet);var s=new ServerSupervisor(dir,List.of("does-not-exist"),dir.resolve("server.log"),new ControlApi.Client(api.port(),SECRET),notices::add)){JsonObject state=new JsonObject();state.addProperty("ready",true);state.addProperty("server_id","wrong-folder");state.addProperty("pid",ProcessHandle.current().pid());api.snapshot(state);s.poll();assertTrue(notices.isEmpty());assertFalse(s.online());s.stop();assertEquals(0,stops.get());}}
    @Test void identityIsStableForSameCanonicalFolder()throws Exception{assertEquals(ControlApi.identity(dir),ControlApi.identity(dir.resolve(".")));Path other=dir.resolve("other");Files.createDirectories(other);assertNotEquals(ControlApi.identity(dir),ControlApi.identity(other));}
    static void await(java.util.function.BooleanSupplier test)throws Exception{long until=System.nanoTime()+4_000_000_000L;while(!test.getAsBoolean()){if(System.nanoTime()>until)fail("timed out");Thread.sleep(20);}}
}
