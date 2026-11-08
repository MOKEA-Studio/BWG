package kr.blackwiki.smp;

import org.bukkit.configuration.ConfigurationSection;
import java.net.URI;
import java.util.*;

/** Shared by the plugin and the optional always-on controller. Never prints credentials. */
record DiscordOptions(boolean enabled,String token,String channel,String guild,String owner,boolean receive,boolean playerEvents,boolean serverEvents,int capacity,long interval,PowerCommands.Policy control,int apiPort) {
    static DiscordOptions read(ConfigurationSection c){
        String token=c.getString("discord.bot-token","").strip(),env=c.getString("discord.bot-token-env","BLACKWIKISMP_DISCORD_BOT_TOKEN");
        if(token.isEmpty()&&!env.isBlank()&&env.matches("[A-Z_][A-Z0-9_]{0,100}"))token=Optional.ofNullable(System.getenv(env)).orElse("").strip();
        try{if(!token.isEmpty())token=DiscordRelay.token(token);}catch(IllegalArgumentException ex){token="";}
        String channel=c.getString("discord.channel-id","").strip(),guild=c.getString("discord.guild-id","").strip(),owner=c.getString("discord.gateway-owner","plugin");
        String controlChannel=c.getString("discord.control.channel-id","").strip();
        var policy=new PowerCommands.Policy(c.getBoolean("discord.control.enabled",false),guild,controlChannel,Set.copyOf(c.getStringList("discord.control.allowed-user-ids")),Set.copyOf(c.getStringList("discord.control.allowed-role-ids")),c.getInt("discord.control.confirmation-seconds",30));
        return new DiscordOptions(c.getBoolean("discord.enabled",false),token,channel,guild,owner,c.getBoolean("discord.relay-discord-chat",true),c.getBoolean("discord.relay-player-events",true),c.getBoolean("discord.relay-server-events",true),c.getInt("discord.max-pending-messages",500),c.getLong("discord.flush-interval-ms",1000),policy,c.getInt("discord.control.api-port",28919));
    }
    DiscordRelay.Settings outgoing(){URI endpoint=null;try{endpoint=DiscordRelay.endpoint(channel);}catch(Exception ignored){}return new DiscordRelay.Settings(enabled,endpoint,token,capacity,interval);}
    boolean configured(){return enabled&&!token.isEmpty()&&DiscordInbound.id(channel);}
    boolean external(){return owner.equals("controller");}
    @Override public String toString(){return "DiscordOptions[enabled="+enabled+", token=[숨김], channel="+channel+", owner="+owner+"]";}
}
