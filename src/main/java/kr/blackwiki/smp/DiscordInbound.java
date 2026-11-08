package kr.blackwiki.smp;

import com.google.gson.*;
import java.util.*;

/** Discord IDs remain strings. Bots, webhooks, DMs and messages from other channels are excluded. */
final class DiscordInbound {
    record Message(String id,String guild,String channel,String author,String name,Set<String> roles,String content){}
    private final LinkedHashMap<String,Boolean> seen=new LinkedHashMap<>();
    static boolean id(String id){return id!=null&&id.matches("[0-9]{6,22}");}
    static String text(JsonObject object,String key){try{var e=object.get(key);return e==null||e.isJsonNull()?"":e.getAsString();}catch(Exception ex){return "";}}
    static String plain(String value,int max){String s=value.replaceAll("[\\p{Cc}\\p{Cf}]"," ").strip();return DiscordRelay.clip(s,max);}
    synchronized Message parse(JsonObject event,String botId){
        try{
            String guild=text(event,"guild_id"),channel=text(event,"channel_id"),message=text(event,"id");if(!id(guild)||!id(channel)||!id(message)||event.has("webhook_id"))return null;
            int type=event.has("type")?event.get("type").getAsInt():0;if(type!=0&&type!=19)return null;
            JsonObject author=event.getAsJsonObject("author");String who=text(author,"id");if(!id(who)||who.equals(botId)||author.has("bot")&&author.get("bot").getAsBoolean()||author.has("system")&&author.get("system").getAsBoolean())return null;
            String content=text(event,"content");if(content.isBlank()||content.length()>4000)return null;
            if(seen.containsKey(message))return null;
            Set<String> roles=new HashSet<>();if(event.has("member")){JsonObject member=event.getAsJsonObject("member");if(member.has("roles"))for(JsonElement role:member.getAsJsonArray("roles"))roles.add(role.getAsString());}
            String name=text(author,"global_name");if(name.isEmpty())name=text(author,"username");if(name.isEmpty())name=who;
            seen.put(message,true);while(seen.size()>10000)seen.remove(seen.keySet().iterator().next());
            return new Message(message,guild,channel,who,plain(name,48),Set.copyOf(roles),content.strip());
        }catch(Exception ex){return null;}
    }
    static boolean chatChannel(Message m,DiscordOptions o){return m.channel.equals(o.channel())&&(o.guild().isBlank()||m.guild.equals(o.guild()));}
}
