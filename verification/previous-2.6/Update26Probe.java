package kr.blackwiki.test;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Chest;
import org.bukkit.event.player.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public final class Update26Probe extends JavaPlugin implements CommandExecutor {
    Object main,data,vault,graves,ledger,welcome;Properties state=new Properties();Path sf=Path.of("state26.properties"),report=Path.of("checks26.txt");
    @Override public void onEnable(){getCommand("probe").setExecutor(this);}
    Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    Class<?> boxed(Class<?> c){return c==int.class?Integer.class:c==long.class?Long.class:c==boolean.class?Boolean.class:c;}
    Object call(Object o,String n,Object...args)throws Exception{Class<?> type=o instanceof Class<?> c?c:o.getClass();for(Method m:type.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterCount()==args.length){boolean ok=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!boxed(m.getParameterTypes()[i]).isInstance(args[i]))ok=false;if(ok){m.setAccessible(true);try{return m.invoke(o instanceof Class?null:o,args);}catch(InvocationTargetException ex){throw new RuntimeException(n,ex.getCause());}}}throw new NoSuchMethodException(n);}
    void check(boolean yes,String text)throws Exception{Files.writeString(report,(yes?"PASS ":"FAIL ")+text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);if(!yes)throw new AssertionError(text);}
    Player player(String name){return Objects.requireNonNull(Bukkit.getPlayerExact(name),name);}
    Object user(Player a)throws Exception{return call(data,"user",a);}
    long coins(Player a)throws Exception{return (long)get(user(a),"coins");}
    void coins(Player a,long n)throws Exception{set(user(a),"coins",n);call(vault,"saveAccount",a);}
    @SuppressWarnings("unchecked") List<Object> windows()throws Exception{return (List<Object>)get(welcome,"windows");}
    @SuppressWarnings("unchecked") Collection<Object> graves()throws Exception{return (Collection<Object>)call(ledger,"all");}
    String gs(Object g)throws Exception{return call(g,"state").toString();}
    UUID gid(Object g)throws Exception{return (UUID)call(g,"id");}
    Object stored(String key)throws Exception{return call(ledger,"get",UUID.fromString(state.getProperty(key)));}
    @SuppressWarnings({"unchecked","rawtypes"}) Object state(String s)throws Exception{return Enum.valueOf((Class)Class.forName("kr.blackwiki.smp.GraveLedger$State"),s);}
    Object grave(Player owner,long created,int x,int z,String status)throws Exception{
        World world=(World)get(main,"wildWorld");int y=world.getHighestBlockYAt(x,z)+3;
        Constructor<?> c=Class.forName("kr.blackwiki.smp.GraveLedger$Grave").getDeclaredConstructors()[0];c.setAccessible(true);
        String item=(String)call(Class.forName("kr.blackwiki.smp.VaultService"),"encode",new ItemStack(Material.DIAMOND,3));
        Object g=c.newInstance(UUID.randomUUID(),owner.getUniqueId(),created,world.getName(),world.getUID(),x,y,z,true,state(status),List.of(item,item),null,null,-1);call(ledger,"put",g);return g;
    }
    void record(String key,Object g)throws Exception{state.setProperty(key,gid(g).toString());}
    void save()throws Exception{try(var out=Files.newOutputStream(sf)){state.store(out,"native 2.6 checks");}}
    void done(String name)throws Exception{save();Files.writeString(Path.of(name),"OK");}
    void later(long ticks,Throwing r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable ex){fail(ex);}},ticks);}
    interface Throwing{void run()throws Exception;}
    void fail(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"Native verification failed",ex);try{Files.writeString(Path.of("FAILED"),ex.toString());}catch(Exception ignored){}}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){later(1,()->{main=Bukkit.getPluginManager().getPlugin("BlackWikiSMP");if(Files.exists(sf))try(var in=Files.newInputStream(sf)){state.load(in);}data=get(main,"data");vault=get(main,"vaults");graves=get(main,"graves");ledger=get(graves,"ledger");if(((JavaPlugin)main).getDescription().getVersion().equals("2.6"))welcome=get(main,"welcome");switch(args[0]){case "old"->old();case "base"->base();case "greeting"->greeting();case "second"->second();case "late"->late();case "restart"->restart();default->throw new IllegalArgumentException();}});return true;}
    @SuppressWarnings("unchecked") void old()throws Exception{
        check(((JavaPlugin)main).getDescription().getVersion().equals("2.5"),"old 2.5 JAR boots for genuine upgrade fixture");Player a=player("Alice");coins(a,123456);set(user(a),"vaultTier",1);
        List<String> bank=(List<String>)get(user(a),"vault");bank.set(0,(String)call(Class.forName("kr.blackwiki.smp.VaultService"),"encode",new ItemStack(Material.EMERALD,7)));call(vault,"saveAccount",a);
        Map<String,Location> homes=(Map<String,Location>)get(user(a),"homes");homes.clear();World w=(World)get(main,"wildWorld");for(int i=1;i<=5;i++)homes.put("base"+i,new Location(w,100+i,65,100));call(data,"changed");call(data,"save",true);
        Object virtual=grave(a,System.currentTimeMillis()-3_600_000,125,125,"OPEN");record("oldVirtual",virtual);Object physical=grave(a,System.currentTimeMillis()-3_600_000,130,130,"OPEN");physical=call(graves,"place",physical);record("oldPhysical",physical);
        check(!(boolean)call(physical,"virtual"),"2.5 physical grave marker placed");Object design=get(main,"spawnDesign");check((boolean)call(design,"done"),"2.5 garden created before swapping JAR");
        YamlConfiguration garden=YamlConfiguration.loadConfiguration(Path.of("plugins/BlackWikiSMP/spawn-build.yml").toFile());World spawn=(World)get(main,"spawnWorld");int x=garden.getInt("x"),y=garden.getInt("y"),z=garden.getInt("z");spawn.getBlockAt(x+40,y,z+40).setType(Material.GOLD_BLOCK,false);state.setProperty("gardenX",x+40+"");state.setProperty("gardenY",y+"");state.setProperty("gardenZ",z+40+"");state.setProperty("gardenJournal",Files.readString(Path.of("plugins/BlackWikiSMP/spawn-build.yml")));state.setProperty("oldWallet",coins(a)+"");done("OLD_READY");
    }
    @SuppressWarnings("unchecked") void base()throws Exception{
        Player a=player("Alice");var plugin=(JavaPlugin)main;check(plugin.getDescription().getVersion().equals("2.6")&&plugin.isEnabled(),"2.6 final JAR loads after 2.5 swap");
        check(plugin.getConfig().getInt("config-version")==26&&plugin.getConfig().getInt("border.size")==40000,"existing configuration automatically expands border to 40000");check(plugin.getConfig().getInt("queue.player-slots")==17&&plugin.getConfig().getInt("queue.max-waiting")==11,"existing player and queue capacities preserved");
        for(World w:Bukkit.getWorlds())if((boolean)call(main,"boundaryWorld",w))check(w.getWorldBorder().getSize()==40000,"native border expanded in "+w.getName());
        World wild=(World)get(main,"wildWorld");check(!(boolean)call(main,"outside",new Location(wild,20000,70,-20000))&&(boolean)call(main,"outside",new Location(wild,20000.01,70,0)),"40k exact edge allowed and outside edge detected");
        check(coins(a)>=Long.parseLong(state.getProperty("oldWallet"))&&(int)get(user(a),"vaultTier")==1,"native wallet and vault tier survive JAR replacement");List<String> bank=(List<String>)get(user(a),"vault");ItemStack item=(ItemStack)call(Class.forName("kr.blackwiki.smp.VaultService"),"decode",bank.get(0));check(item.getType()==Material.EMERALD&&item.getAmount()==7,"vault contents preserved");check(((Map<?,?>)get(user(a),"homes")).size()==5,"five homes preserved");
        World spawn=(World)get(main,"spawnWorld");check(spawn.getBlockAt(Integer.parseInt(state.getProperty("gardenX")),Integer.parseInt(state.getProperty("gardenY")),Integer.parseInt(state.getProperty("gardenZ"))).getType()==Material.GOLD_BLOCK,"customized 2.5 garden block survives update");check(state.getProperty("gardenJournal").equals(Files.readString(Path.of("plugins/BlackWikiSMP/spawn-build.yml"))),"completed garden journal unchanged");
        long epoch=(long)get(get(main,"upgrades"),"graveExpiryEpoch");state.setProperty("epoch",epoch+"");check(epoch>System.currentTimeMillis()-120_000&&gs(stored("oldVirtual")).equals("OPEN"),"pre-upgrade graves receive one ten-minute grace period");
        call(graves,"expireAt",epoch+599999);check(gs(stored("oldPhysical")).equals("OPEN"),"legacy grave remains before exact grace deadline");call(graves,"expireAt",epoch+600000);check(gs(stored("oldVirtual")).equals("EXPIRED")&&gs(stored("oldPhysical")).equals("EXPIRED"),"legacy physical and virtual graves expire at deadline");Object old=stored("oldPhysical");Location loc=new Location(wild,(int)call(old,"x"),(int)call(old,"y"),(int)call(old,"z"));check(loc.getBlock().getType()==Material.AIR,"expired physical chest removed without item drops");
        long created=System.currentTimeMillis();Object fresh=grave(a,created,150,150,"OPEN");fresh=call(graves,"place",fresh);record("freshPhysical",fresh);call(graves,"expireAt",created+599999);check(gs(stored("freshPhysical")).equals("OPEN"),"new grave lasts until 600 seconds");call(graves,"expireAt",created+600000);check(gs(stored("freshPhysical")).equals("EXPIRED"),"new grave expires exactly at 600 seconds");
        Object pending=grave(a,created,155,155,"OPEN");pending=call(pending,"reserve",a.getUniqueId(),UUID.randomUUID(),0);call(ledger,"put",pending);record("pending",pending);call(graves,"expireAt",created+600000);call(graves,"reconcile",a);check(gs(stored("pending")).equals("EXPIRED")&&call(stored("pending"),"token")==null,"undelivered pending withdrawal is discarded and cannot reopen expired grave");
        Object delivered=grave(a,created,160,160,"OPEN");UUID token=UUID.randomUUID();delivered=call(delivered,"reserve",a.getUniqueId(),token,0);call(ledger,"put",delivered);record("delivered",delivered);call(graves,"receipt",a,"loot:"+token);a.getInventory().addItem(new ItemStack(Material.DIAMOND,3));a.saveData();call(graves,"expireAt",created+600000);int before=count(a,Material.DIAMOND);call(graves,"reconcile",a);check(gs(stored("delivered")).equals("EXPIRED")&&call(stored("delivered"),"token")==null&&count(a,Material.DIAMOND)==before,"already-delivered native receipt reconciles without duplicate or confiscation");
        Object survive=grave(a,System.currentTimeMillis(),165,165,"OPEN");record("restartGrave",survive);state.setProperty("restartCreated",call(survive,"created").toString());
        plugin.getConfig().set("settings.chat-cooldown-ms",0);call(get(main,"moderation"),"reload");check(windows().isEmpty(),"existing Alice and Bob do not open newcomer windows");
        Bukkit.dispatchCommand(a,"환영");check(PlainTextComponentSerializer.plainText().serialize(a.getOpenInventory().title()).contains("환영"),"Korean welcome command opens GUI");Bukkit.dispatchCommand(a,"명령어");check(PlainTextComponentSerializer.plainText().serialize(a.getOpenInventory().title()).contains("명령어"),"existing command guide still opens");a.closeInventory();done("BASE_READY");
    }
    int count(Player a,Material material){int n=0;for(ItemStack i:a.getInventory().getContents())if(i!=null&&i.getType()==material)n+=i.getAmount();return n;}
    AsyncChatEvent chat(Player a,String msg,boolean cancel){Component c=Component.text(msg);Set<Audience> viewers=new HashSet<>(Bukkit.getOnlinePlayers());var e=new AsyncChatEvent(false,a,viewers,ChatRenderer.defaultRenderer(),c,c,SignedMessage.system(msg,c));e.setCancelled(cancel);Bukkit.getPluginManager().callEvent(e);return e;}
    void greeting()throws Exception{
        Player a=player("Alice"),b=player("Bob"),fresh=player("Fresh26");check(windows().size()==1,"genuine first join opens exactly one welcome window");coins(a,1000);coins(b,2000);coins(fresh,500);
        chat(a,"안녕하세요!",false);check(coins(a)==1000,"nonmatching greeting earns no coins");chat(a,"안녕하세요",true);check(coins(a)==1000,"cancelled public chat earns no coins");chat(fresh,"안녕하세요",false);check(coins(fresh)==500,"newcomer cannot welcome themself for reward");
        chat(a,"안녕하세요",false);check(coins(a)==1100,"public Korean greeting pays exactly 100 coins");chat(a,"안녕하세요",false);check(coins(a)==1100,"repeat greeting gives no second payout");chat(b,"  안녕하세요  ",false);check(coins(b)==2100,"another player can independently receive the reward");
        var pdc=a.getPersistentDataContainer();check(pdc.get(new NamespacedKey("blackwikismp","coins"),PersistentDataType.LONG)==1100,"reward saved to native wallet PDC");check(fresh.getPersistentDataContainer().has(new NamespacedKey("blackwikismp","welcome-first-join-seen")),"first-join marker saved before reward window");
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(fresh,Component.empty()));check(windows().size()==1,"replayed join event cannot reopen first-join reward");done("GREETING_READY");
    }
    void second()throws Exception{
        Player a=player("Alice"),b=player("Bob");long before=coins(a);chat(a,"안녕하세요",false);check(coins(a)==before+100,"new newcomer gives its own independent 100 coins");long after=coins(a);chat(a,"안녕하세요",false);check(coins(a)==after,"overlapping newcomers still each pay only once");
        Object queue=get(main,"queue");Object fifo=get(queue,"fifo");call(fifo,"add",b.getUniqueId());long bob=coins(b);chat(b,"안녕하세요",false);check(coins(b)==bob,"queued player cannot collect welcome coins");call(fifo,"remove",b.getUniqueId());
        state.setProperty("lateBefore",coins(b)+"");done("SECOND_READY");
    }
    void late()throws Exception{Player b=player("Bob");long before=coins(b);chat(b,"안녕하세요",false);check(coins(b)==before,"greeting after real ten-second window gets no reward");state.setProperty("restartWallet",coins(player("Alice"))+"");done("LATE_READY");}
    void restart()throws Exception{
        Player a=player("Alice"),fresh=player("Fresh26");check((long)get(get(main,"upgrades"),"graveExpiryEpoch")==Long.parseLong(state.getProperty("epoch")),"restart preserves original grave-expiry epoch");check(windows().isEmpty(),"rejoining known newcomer after server restart opens no window");check(coins(a)>=Long.parseLong(state.getProperty("restartWallet")),"native welcome coins survive restart");check(gs(stored("oldPhysical")).equals("EXPIRED")&&gs(stored("freshPhysical")).equals("EXPIRED"),"expired graves never reopen after restart");check(gs(stored("restartGrave")).equals("OPEN")&&call(stored("restartGrave"),"created").toString().equals(state.getProperty("restartCreated")),"unexpired grave retains original creation deadline");check(fresh.getPersistentDataContainer().has(new NamespacedKey("blackwikismp","welcome-first-join-seen")),"newcomer receipt survives actual reconnect");
        var plugin=(JavaPlugin)main;check(plugin.getConfig().getInt("border.size")==40000&&plugin.getConfig().getInt("queue.player-slots")==17,"expanded border and prior capacity remain after restart");done("ALL_READY");
    }
}
