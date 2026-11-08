package kr.blackwiki.smp;

import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DiscordHttpTest {
 @Test void productionBotTransportUsesAuthorizationAndChannelJson()throws Exception{
  AtomicReference<String> body=new AtomicReference<>(),query=new AtomicReference<>(),type=new AtomicReference<>(),auth=new AtomicReference<>();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v10/channels/123456789/messages",e->{body.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));query.set(e.getRequestURI().getQuery());auth.set(e.getRequestHeaders().getFirst("Authorization"));type.set(e.getRequestHeaders().getFirst("Content-Type"));byte[] response="{\"id\":\"local-test\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,response.length);e.getResponseBody().write(response);e.close();});server.start();
  try(DiscordRelay.WebTransport transport=new DiscordRelay.WebTransport()){
   var reply=transport.send(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v10/channels/123456789/messages"),"unit-test-token-123456789",DiscordRelay.payload("[채팅] Alice: 미친 존나"));
   assertEquals(200,reply.status());assertNull(query.get());assertEquals("Bot unit-test-token-123456789",auth.get());assertTrue(type.get().startsWith("application/json"));var payload=JsonParser.parseString(body.get()).getAsJsonObject();assertTrue(payload.get("content").getAsString().contains("미친 존나"));assertEquals(0,payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
  }finally{server.stop(0);}
 }
}
