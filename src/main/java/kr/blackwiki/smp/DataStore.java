package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public final class DataStore implements AutoCloseable {
    public static final class User {
        public final UUID id;
        public String name;
        public long seconds, kills, deaths;
        public long coins,miningXp,newcomerSeconds;
        public boolean newcomer;
        public final LinkedHashMap<String,Location> homes=new LinkedHashMap<>();
        public int vaultTier,coinSeconds;
        public long normalCoinNanos,afkCoinNanos;
        public long wildEpoch;
        public final List<String> vault=new ArrayList<>(Collections.nCopies(54,""));
        public boolean hud=true, tpa=true, notices=true;
        public Location home, returnTo;
        public long rtpAt;
        User(UUID id) { this.id=id; this.name=id.toString().substring(0,8); }
    }
    public static final class Team {
        public final UUID id;
        public String name;
        public UUID owner;
        public boolean pvp;
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        Team(UUID id,String name,UUID owner,boolean pvp) {this.id=id;this.name=name;this.owner=owner;this.pvp=pvp;members.add(owner);}
    }
    final Map<UUID,User> users=new LinkedHashMap<>();
    final Map<UUID,Team> teams=new LinkedHashMap<>();
    private final BlackWikiSMP plugin;
    private final Path file;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("BlackWikiSMP-storage").factory());
    private Future<?> lastWrite;
    private boolean dirty;
    private final Map<UUID,Long> clocks=new HashMap<>();
    private long lastError;
    private final LinkedHashMap<String,ArrayDeque<String>> recent=new LinkedHashMap<>();
    DataStore(BlackWikiSMP plugin) throws IOException,InvalidConfigurationException {
        this.plugin=plugin; this.file=plugin.getDataFolder().toPath().resolve("data.yml");
        if(!Files.exists(file)) return;
        YamlConfiguration y=new YamlConfiguration(); y.load(file.toFile());
        ConfigurationSection us=y.getConfigurationSection("users");
        if(us!=null) for(String k:us.getKeys(false)) {
            UUID id=UUID.fromString(k);User u=new User(id);String p="users."+k+".";
            u.name=y.getString(p+"name",u.name);u.seconds=Math.max(0,y.getLong(p+"seconds"));
            u.kills=Math.max(0,y.getLong(p+"kills"));u.deaths=Math.max(0,y.getLong(p+"deaths"));
            u.coins=Math.max(0,y.getLong(p+"coins"));u.miningXp=Math.max(0,y.getLong(p+"mining-xp"));u.coinSeconds=Math.clamp(y.getInt(p+"coin-seconds"),0,1);u.normalCoinNanos=Math.clamp(y.getLong(p+"normal-coin-nanos",(long)u.coinSeconds*1_000_000_000L),0L,1_999_999_999L);u.afkCoinNanos=Math.clamp(y.getLong(p+"afk-coin-nanos",0L),0L,499_999_999L);u.wildEpoch=Math.max(0,y.getLong(p+"wild-epoch"));u.vaultTier=Math.clamp(y.getInt(p+"vault-tier"),0,2);
            List<String> stored=y.getStringList(p+"vault");for(int n=0;n<Math.min(54,stored.size());n++)u.vault.set(n,stored.get(n));
            u.hud=y.getBoolean(p+"hud",true);u.tpa=y.getBoolean(p+"tpa",true);u.notices=y.getBoolean(p+"notices",true);
            u.home=readLocation(y,p+"home");var hs=y.getConfigurationSection(p+"homes");if(hs==null){if(u.home!=null)u.homes.put("기본",u.home);}else{for(String key:hs.getKeys(false)){Location at=readLocation(y,p+"homes."+key);if(at!=null)u.homes.put(HomeRules.name(key),at);}if(u.homes.size()>5)throw new IOException("Too many homes: "+id);u.home=u.homes.get("기본");}u.returnTo=readLocation(y,p+"return");u.rtpAt=y.getLong(p+"rtp-at");users.put(id,u);
        }
        ConfigurationSection ts=y.getConfigurationSection("teams");Set<UUID> claimed=new HashSet<>();
        if(ts!=null) for(String k:ts.getKeys(false)) {
            String p="teams."+k+"."; UUID owner=UUID.fromString(Objects.requireNonNull(y.getString(p+"owner")));
            Team t=new Team(UUID.fromString(k),Objects.requireNonNull(y.getString(p+"name")),owner,y.getBoolean(p+"pvp"));
            for(String m:y.getStringList(p+"members"))t.members.add(UUID.fromString(m));
            if(!Rules.validTeamName(t.name,12)||t.members.stream().anyMatch(claimed::contains))throw new IOException("Invalid or overlapping team data: "+k);
            if(teams.values().stream().anyMatch(v->v.name.equalsIgnoreCase(t.name)))throw new IOException("Duplicate team name: "+t.name);
            claimed.addAll(t.members);teams.put(t.id,t);
        }
    }
    User user(Player p) {
        User u=users.computeIfAbsent(p.getUniqueId(),id->{User n=new User(id);n.hud=plugin.cfg().getBoolean("settings.default-border-hud",true);n.tpa=plugin.cfg().getBoolean("settings.default-tpa",true);n.notices=plugin.cfg().getBoolean("settings.default-notifications",true);dirty=true;return n;});
        if(!p.getName().equals(u.name)){u.name=p.getName();dirty=true;}return u;
    }
    User user(UUID id) {return users.computeIfAbsent(id,User::new);}
    String name(UUID id) {User u=users.get(id);return u==null?id.toString().substring(0,8):u.name;}
    UUID find(String name) {return users.values().stream().filter(u->u.name.equalsIgnoreCase(name)).map(u->u.id).findFirst().orElse(null);}
    void changed() {dirty=true;}
    /** Coin timer per zone: AFK pays every 0.5s, everywhere else every 2s. Sub-interval remainders persist. */
    private static final class CoinSession{long at;boolean afk;CoinSession(long at,boolean afk){this.at=at;this.afk=afk;}}
    private final Map<UUID,CoinSession> coinClocks=new HashMap<>();
    void startSession(Player a){long now=System.nanoTime();clocks.put(a.getUniqueId(),now);coinClocks.put(a.getUniqueId(),new CoinSession(now,plugin.afk.contains(a)));}
    void pauseSession(Player a){clocks.remove(a.getUniqueId());coinClocks.remove(a.getUniqueId());}
    private boolean settleCoins(Player a,long now){
        UUID id=a.getUniqueId();CoinSession session=coinClocks.computeIfAbsent(id,k->new CoinSession(now,plugin.afk.contains(a)));
        long elapsed=Math.max(0,now-session.at);session.at=now;User u=user(a);long interval=session.afk?500_000_000L:2_000_000_000L;
        CoinIntervals.Result result=CoinIntervals.advance(session.afk?u.afkCoinNanos:u.normalCoinNanos,elapsed,interval);
        if(session.afk)u.afkCoinNanos=result.remainder();else{u.normalCoinNanos=result.remainder();u.coinSeconds=(int)(u.normalCoinNanos/1_000_000_000L);}
        session.afk=plugin.afk.contains(a);u.coins=CoinClock.add(u.coins,result.coins());if(elapsed>0)dirty=true;return result.coins()>0;
    }
    void zoneChanged(Player a){if(plugin.queue.waiting(a)){coinClocks.remove(a.getUniqueId());return;}if(!coinClocks.containsKey(a.getUniqueId()))return;settleCoins(a,System.nanoTime());if(plugin.vaults!=null)plugin.vaults.syncCurrency(a,user(a));}
    void tickTime() {
        long now=System.nanoTime();Set<UUID> admitted=new HashSet<>();
        for(Player a:Bukkit.getOnlinePlayers()) if(!plugin.queue.waiting(a)) {
            UUID id=a.getUniqueId();admitted.add(id);long prev=clocks.getOrDefault(id,now);
            long sec=Math.max(0,(now-prev)/1_000_000_000L);User u=user(a);
            if(sec>0){u.seconds=CoinClock.add(u.seconds,sec);if(u.newcomer)u.newcomerSeconds=CoinClock.add(u.newcomerSeconds,sec);clocks.put(id,prev+sec*1_000_000_000L);dirty=true;}else clocks.putIfAbsent(id,now);
            boolean paid=settleCoins(a,now);if((paid||sec>0)&&plugin.vaults!=null)plugin.vaults.syncCurrency(a,u);
        }
        clocks.keySet().retainAll(admitted);coinClocks.keySet().retainAll(admitted);
    }
    void leave(Player a) {tickTime();clocks.remove(a.getUniqueId());coinClocks.remove(a.getUniqueId());}
    List<User> ranking(boolean kills) {return users.values().stream().sorted(Comparator.<User>comparingLong(u->kills?u.kills:u.seconds).reversed().thenComparing(u->u.name,String.CASE_INSENSITIVE_ORDER)).limit(10).toList();}
    List<User> coinRanking(){return users.values().stream().sorted(Comparator.<User>comparingLong(u->u.coins).reversed().thenComparing(u->u.name,String.CASE_INSENSITIVE_ORDER)).limit(10).toList();}
    String snapshot() {
        YamlConfiguration y=new YamlConfiguration(); y.set("schema",1);
        for(User u:users.values()) {
            String p="users."+u.id+".";y.set(p+"name",u.name);y.set(p+"seconds",u.seconds);y.set(p+"kills",u.kills);y.set(p+"deaths",u.deaths);
            y.set(p+"coins",u.coins);y.set(p+"mining-xp",u.miningXp);y.set(p+"coin-seconds",u.coinSeconds);y.set(p+"normal-coin-nanos",u.normalCoinNanos);y.set(p+"afk-coin-nanos",u.afkCoinNanos);y.set(p+"wild-epoch",u.wildEpoch);y.set(p+"vault-tier",u.vaultTier);y.set(p+"vault",List.copyOf(u.vault));
            y.set(p+"hud",u.hud);y.set(p+"tpa",u.tpa);y.set(p+"notices",u.notices);y.set(p+"rtp-at",u.rtpAt);
            for(var entry:u.homes.entrySet())writeLocation(y,p+"homes."+entry.getKey(),entry.getValue());writeLocation(y,p+"home",u.homes.get("기본"));writeLocation(y,p+"return",u.returnTo);
        }
        for(Team t:teams.values()) {
            String p="teams."+t.id+".";y.set(p+"name",t.name);y.set(p+"owner",t.owner.toString());y.set(p+"pvp",t.pvp);y.set(p+"members",t.members.stream().map(UUID::toString).toList());
        }
        return y.saveToString();
    }
    void save(boolean force) {
        if(!force&&!dirty)return;
        String text=snapshot();dirty=false;
        lastWrite=writer.submit(()->{try{atomic(file,text);}catch(IOException e){report("Data save failed",e);plugin.sync(()->dirty=true);throw new UncheckedIOException(e);}});
    }
    Future<?> flushFuture() {save(true);return lastWrite;}
    static void atomic(Path file,String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        try(var out=new FileOutputStream(tmp.toFile())){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    static Location readLocation(YamlConfiguration y,String p) {
        String w=y.getString(p+".world");if(w==null)return null; World world=Bukkit.getWorld(w);if(world==null)return null;
        return new Location(world,y.getDouble(p+".x"),y.getDouble(p+".y"),y.getDouble(p+".z"),(float)y.getDouble(p+".yaw"),(float)y.getDouble(p+".pitch"));
    }
    static void writeLocation(YamlConfiguration y,String p,Location l) {
        if(l==null||l.getWorld()==null)return;
        y.set(p+".world",l.getWorld().getName());y.set(p+".x",l.getX());y.set(p+".y",l.getY());y.set(p+".z",l.getZ());y.set(p+".yaw",l.getYaw());y.set(p+".pitch",l.getPitch());
    }
    private String blockKey(Location l){return l.getWorld().getUID()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();}
    void audit(String action,String who,Location at,String detail) {
        if(!plugin.cfg().getBoolean("audit.enabled",true))return;
        String line=java.time.Instant.now()+" | "+who+" | "+action+" | "+at.getWorld().getName()+" "+at.getBlockX()+" "+at.getBlockY()+" "+at.getBlockZ()+" | "+detail.replace('\n',' ');
        String key=blockKey(at);ArrayDeque<String> history=recent.computeIfAbsent(key,k->new ArrayDeque<>());history.addFirst(line);while(history.size()>8)history.removeLast();
        while(recent.size()>10000)recent.remove(recent.keySet().iterator().next());
        long max=plugin.cfg().getLong("audit.max-file-mb",16)*1024*1024;int keep=plugin.cfg().getInt("audit.files-to-keep",5);
        writer.submit(()->{try {
            Path f=file.resolveSibling("audit.log");
            if(Files.exists(f)&&Files.size(f)>max){for(int i=keep;i>=1;i--){Path src=i==1?f:file.resolveSibling("audit."+(i-1)+".log");if(Files.exists(src))Files.move(src,file.resolveSibling("audit."+i+".log"),StandardCopyOption.REPLACE_EXISTING);}}
            Files.writeString(f,line+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(IOException e){report("Audit write failed",e);}});
    }
    List<String> history(Location at) {return List.copyOf(recent.getOrDefault(blockKey(at),new ArrayDeque<>()));}
    private synchronized void report(String message,Exception e) {if(System.currentTimeMillis()-lastError>10000){lastError=System.currentTimeMillis();plugin.getLogger().log(Level.SEVERE,message,e);}}
    @Override public void close() {
        save(true);writer.shutdown();try{if(!writer.awaitTermination(15,TimeUnit.SECONDS))plugin.getLogger().severe("Storage still writing after shutdown timeout.");}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
