package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real JDK WebSocket handshake/framing against a local mock, never Discord. */
class GatewaySocketTest {
    @Test void fragmentedWebSocketTextArrivesOnceAndBinaryIsRejected()throws Exception{
        AtomicReference<String> message=new AtomicReference<>();AtomicInteger closed=new AtomicInteger();CountDownLatch got=new CountDownLatch(1),finish=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress());var connector=new DiscordGateway.JdkConnector()){
            Thread server=Thread.startVirtualThread(()->{try(Socket socket=listener.accept()){
                var in=socket.getInputStream();StringBuilder request=new StringBuilder();while(!request.toString().endsWith("\r\n\r\n")){int b=in.read();if(b<0)throw new EOFException();request.append((char)b);}
                String key=Arrays.stream(request.toString().split("\r\n")).filter(l->l.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")).findFirst().orElseThrow().split(":",2)[1].strip();String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                var out=socket.getOutputStream();out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));frame(out,1,"{\"content\":\"안녕".getBytes(StandardCharsets.UTF_8));frame(out,128,"하세요😀\"}".getBytes(StandardCharsets.UTF_8));assertTrue(got.await(3,TimeUnit.SECONDS));frame(out,130,new byte[]{1,2});finish.await(3,TimeUnit.SECONDS);
            }catch(Throwable ex){failure.set(ex);}});
            connector.connect(URI.create("ws://127.0.0.1:"+listener.getLocalPort()),new DiscordGateway.Sink(){public void opened(DiscordGateway.Link link){}public void text(String text){message.set(text);got.countDown();}public void closed(int code){closed.set(code);finish.countDown();}public void failed(){finish.countDown();}});
            assertTrue(got.await(4,TimeUnit.SECONDS));assertTrue(finish.await(4,TimeUnit.SECONDS));server.join(4000);if(failure.get()!=null)throw new AssertionError(failure.get());assertEquals("{\"content\":\"안녕하세요😀\"}",message.get());assertEquals(1003,closed.get());
        }
    }
    static void frame(OutputStream out,int flags,byte[] data)throws IOException{out.write(flags);if(data.length<126)out.write(data.length);else{out.write(126);out.write(data.length>>>8);out.write(data.length);}out.write(data);out.flush();}
}
