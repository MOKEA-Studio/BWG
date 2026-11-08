package kr.blackwiki.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.logging.Level;

public final class BlackWikiSMP extends JavaPlugin implements Listener {
    DataStore data; TeamService teams; QueueService queue; TravelService travel;
    Protection protection; Gui gui; BackupService backups; NpcService npcs; Commands commands;
    VaultService vaults; ShopService shop; MarketService market; WildResetService resets;
    RewardService rewards; CoinTransferService transfers; MonsterService monsters; MiningService mining; SpecialSwordService swords;
    NewbieService newbies; ArmorSetService armor; HomeService homes; TreeFellerService trees; GraveService graves; EventService events; SerumService serums; ExecutionerService executioner; VoiceSupport voice;
    ChatModeration moderation; DiscordBridge discord; BanService bans;
    AntiXrayService antiXray; TipService tips; AdminCoinService adminCoins; PistolService pistols; StarterKitService starter; HudService hud; BedrockService bedrock; WelcomeService welcome; UpgradeService upgrades; AfkService afk; SpawnDesignService spawnDesign; AutoUpdateService updates; AdminTools admin; TitleService titles; RealTimeService realTime; SpawnLightingService lighting;
    private final long started=System.nanoTime();
    World spawnWorld,wildWorld;
    private Location spawn;
    private boolean ready;
    final Set<UUID> builders=new HashSet<>();
    private final Map<UUID,Long> lastChat=new HashMap<>();
    private long heartbeat,cleanupAt;
    @Override public void onEnable() {
        try {
            upgrades=new UpgradeService(this);validate();
            for(String old:List.of("BlackWikiLogBundle","BlackWikiLog","BlackWikiSurvival","BlackWikiQueueTab","BlackWikiPvP"))
                if(Bukkit.getPluginManager().getPlugin(old)!=null)throw new IllegalStateException("Remove legacy plugin first: "+old);
            String world=getConfig().getString("worlds.spawn","");
            spawnWorld=world.isBlank()?Bukkit.getWorlds().getFirst():Bukkit.getWorld(world);
            if(spawnWorld==null)throw new IllegalStateException("Spawn world is not loaded: "+world);
            String wild=getConfig().getString("worlds.wild","BlackWikiSMPWild");
            if(wild.equals(spawnWorld.getName())||wild.equals(getConfig().getString("worlds.queue")))throw new IllegalStateException("Spawn, wild and queue names must be distinct");
            resets=new WildResetService(this,wild);
            wildWorld=Bukkit.getWorld(wild);if(wildWorld==null)wildWorld=Bukkit.createWorld(new WorldCreator(wild).environment(World.Environment.NORMAL));
            if(wildWorld==null)throw new IllegalStateException("Cannot load wilderness");
            spawn=DataStore.readLocation((YamlConfiguration)getConfig(),"spawn.location");if(spawn==null)spawn=spawnWorld.getSpawnLocation();
            if(spawn.getWorld()!=spawnWorld)throw new IllegalStateException("Spawn location world does not match worlds.spawn");
            data=new DataStore(this);teams=new TeamService(this);queue=new QueueService(this);afk=new AfkService(this);travel=new TravelService(this);
            protection=new Protection(this);gui=new Gui(this);moderation=new ChatModeration(this);discord=new DiscordBridge(this);bans=new BanService(this);admin=new AdminTools(this);newbies=new NewbieService(this);vaults=new VaultService(this);shop=new ShopService(this);market=new MarketService(this);backups=new BackupService(this);rewards=new RewardService(this);transfers=new CoinTransferService(this);monsters=new MonsterService(this);mining=new MiningService(this);swords=new SpecialSwordService(this);armor=new ArmorSetService(this);homes=new HomeService(this);trees=new TreeFellerService(this);graves=new GraveService(this);events=new EventService(this);serums=new SerumService(this);executioner=new ExecutionerService(this);voice=new VoiceSupport(this);npcs=new NpcService(this);spawnDesign=new SpawnDesignService(this);welcome=new WelcomeService(this);starter=new StarterKitService(this);hud=new HudService(this);bedrock=new BedrockService(this);pistols=new PistolService(this);adminCoins=new AdminCoinService(this);antiXray=new AntiXrayService(this);tips=new TipService(this);titles=new TitleService(this);realTime=new RealTimeService(this);lighting=new SpawnLightingService(this);commands=new Commands(this);
            for(String key:getDescription().getCommands().keySet()){Objects.requireNonNull(getCommand(key)).setExecutor(commands);Objects.requireNonNull(getCommand(key)).setTabCompleter(commands);}
            Objects.requireNonNull(getCommand("smpability")).setExecutor(new AbilityCommand(this));
            for(Listener l:List.of(this,queue,travel,protection,gui,npcs,vaults,moderation,discord,bans,market,resets,transfers,monsters,mining,swords,newbies,armor,homes,trees,graves,events,serums,executioner,welcome,bedrock,pistols,adminCoins,admin,titles,realTime,afk))Bukkit.getPluginManager().registerEvents(l,this);
            for(World w:Bukkit.getWorlds())applyBorder(w);
            cleanupAt=System.currentTimeMillis()+getConfig().getLong("cleanup.interval-seconds",180)*1000;
            Bukkit.getScheduler().runTaskTimer(this,this::tick,20,20);
            Bukkit.getScheduler().runTaskTimer(this,hud::tick,1,5);Bukkit.getScheduler().runTaskTimer(this,data::tickTime,1,1);Bukkit.getScheduler().runTaskTimer(this,realTime::pulse,1,1);Bukkit.getScheduler().runTaskTimer(this,swords::tick,1,1);Bukkit.getScheduler().runTaskTimer(this,armor::tick,1,5);
            for(Player p:Bukkit.getOnlinePlayers()){data.user(p);bedrock.decorate(p);vaults.load(p);market.reconcile(p);graves.reconcile(p);titles.sync(p);queue.join(p);if(!queue.waiting(p))data.startSession(p);}
            ready=true;monsters.refresh();Bukkit.getScheduler().runTaskLater(this,spawnDesign::auto,20);getLogger().info("BlackWikiSMP "+Version.V+" enabled. Paper "+Bukkit.getMinecraftVersion()+" / Java "+Runtime.version().feature());
            if(!Bukkit.getMinecraftVersion().startsWith("26.2"))getLogger().warning("This build targets Paper 26.2. Current version: "+Bukkit.getMinecraftVersion());
            getLogger().info("Anti-Xray: "+antiXray.status());updates=new AutoUpdateService(this,getFile().toPath());
        }catch(Exception e){getLogger().log(Level.SEVERE,"BlackWikiSMP startup stopped to protect existing data",e);Bukkit.getPluginManager().disablePlugin(this);}
    }
    void validate(){
        try{new YamlConfiguration().load(new java.io.File(getDataFolder(),"config.yml"));}catch(Exception e){throw new IllegalArgumentException("Invalid config.yml: "+e.getMessage(),e);}
        if(getConfig().getInt("queue.player-slots",50)<1||getConfig().getInt("queue.player-slots",50)>1000||getConfig().getInt("queue.max-waiting",30)<0||getConfig().getInt("queue.max-waiting",30)>10000)throw new IllegalArgumentException("queue.player-slots: 1~1000, queue.max-waiting: 0~10000");
        if(getConfig().getInt("moderation.max-ban-days",3650)<1||getConfig().getInt("moderation.max-ban-days",3650)>36500)throw new IllegalArgumentException("moderation.max-ban-days: 1~36500");
        if(getConfig().getInt("discord.max-pending-messages",500)<10||getConfig().getInt("discord.max-pending-messages",500)>10000||getConfig().getLong("discord.flush-interval-ms",1000)<250||getConfig().getLong("discord.flush-interval-ms",1000)>60000)throw new IllegalArgumentException("Invalid Discord queue/interval");
        if(getConfig().getLong("settings.chat-cooldown-ms",1000)<0||getConfig().getLong("settings.chat-cooldown-ms",1000)>60000)throw new IllegalArgumentException("Invalid chat cooldown");
        if(cfg().getInt("bedrock.port",19132)<1||cfg().getInt("bedrock.port",19132)>65535)throw new IllegalArgumentException("bedrock.port: 1~65535");
        if(cfg().getInt("spawn-design.blocks-per-tick",1200)<100||cfg().getInt("spawn-design.blocks-per-tick",1200)>5000)throw new IllegalArgumentException("spawn-design.blocks-per-tick: 100~5000");
        if(!Set.of("plugin","controller").contains(cfg().getString("discord.gateway-owner","plugin"))||cfg().getInt("discord.control.api-port",28919)<1||cfg().getInt("discord.control.api-port",28919)>65535)throw new IllegalArgumentException("Discord gateway-owner 또는 api-port 확인 필요");
        DiscordOptions.read(cfg());
        AfkRules.distinct(cfg().getString("worlds.afk","BlackWikiSMPAFK"),cfg().getString("worlds.spawn",""),cfg().getString("worlds.wild","BlackWikiSMPWild"),cfg().getString("worlds.queue","BlackWikiSMPQueue"));
        if(cfg().getInt("afk.platform-y",100)<-60||cfg().getInt("afk.platform-y",100)>300)throw new IllegalArgumentException("afk.platform-y: -60~300");
        if(cfg().getInt("real-time.sync-period-ticks",1)<1||cfg().getInt("real-time.sync-period-ticks",1)>20)throw new IllegalArgumentException("real-time.sync-period-ticks: 1~20");
        MiningCoins.validate(getConfig());
        ChatModeration.read(getConfig());ProgressConfig.validate(getConfig());if(cfg().getInt("tree-feller.max-logs",256)<2||cfg().getInt("tree-feller.max-logs",256)>1024||cfg().getInt("tree-feller.blocks-per-tick",8)<1||cfg().getInt("tree-feller.blocks-per-tick",8)>64||cfg().getLong("newbie.duration-seconds",900)<1||cfg().getLong("newbie.duration-seconds",900)>86400||cfg().getLong("graves.owner-lock-seconds",0)<0||cfg().getLong("graves.owner-lock-seconds",0)>86400)throw new IllegalArgumentException("Invalid tree/newbie/grave settings");
        if(getConfig().getLong("market.max-price",1000000000L)<1||getConfig().getLong("market.max-price",1000000000L)>1000000000000L||getConfig().getInt("market.max-listings-per-player",20)<1||getConfig().getInt("market.max-active-listings",2000)<1||getConfig().getInt("market.max-active-listings",2000)>10000||getConfig().getInt("market.completed-history",2000)<0)throw new IllegalArgumentException("Invalid market limits");
        if(getConfig().getInt("teams.max-members",20)<2||getConfig().getInt("teams.max-name-length",12)<1)throw new IllegalArgumentException("Invalid team limits");
        if(getConfig().getDouble("border.size",40000)!=40000)throw new IllegalArgumentException("BlackWikiSMP "+Version.V+" requires border.size: 40000");
        if(getConfig().getInt("spawn.radius",500)<16||getConfig().getInt("spawn.radius",500)>2000)throw new IllegalArgumentException("spawn.radius must be 16..2000");
        if(getConfig().getInt("backup.keep",8)<1||getConfig().getInt("backup.keep",8)>100||getConfig().getInt("backup.interval-minutes",60)<1)throw new IllegalArgumentException("Invalid backup policy");
        if(getConfig().getInt("teleport.rtp-max-concurrent",2)<1||getConfig().getInt("teleport.rtp-attempts",16)<1)throw new IllegalArgumentException("Invalid RTP limits");
        if(getConfig().getInt("teleport.rtp-min-radius",600)<0||getConfig().getInt("teleport.rtp-max-radius",9000)<=getConfig().getInt("teleport.rtp-min-radius",600)||getConfig().getInt("teleport.rtp-max-radius",9000)>=20000)throw new IllegalArgumentException("Invalid RTP radius");
        if(cfg().getLong("auto-update.check-interval-minutes",30)<10||cfg().getLong("auto-update.check-interval-minutes",30)>1440)throw new IllegalArgumentException("auto-update.check-interval-minutes: 10~1440");
        if(getConfig().getInt("cleanup.interval-seconds",180)<30||getConfig().getInt("settings.persist-seconds",60)<10||getConfig().getLong("audit.max-file-mb",16)<1||getConfig().getInt("audit.files-to-keep",5)<1)throw new IllegalArgumentException("Invalid persistence/cleanup/audit settings");
    }
    @Override public void onDisable(){
        ready=false;if(afk!=null)afk.close();if(admin!=null)admin.close();if(lighting!=null)lighting.close();if(realTime!=null)realTime.close();if(hud!=null)hud.close();if(bedrock!=null)bedrock.close();Bukkit.getScheduler().cancelTasks(this);if(swords!=null)swords.close();if(armor!=null)armor.close();if(trees!=null)trees.close();if(events!=null)events.close();if(voice!=null)voice.close();if(spawnDesign!=null)spawnDesign.close();if(monsters!=null)monsters.close();
        if(pistols!=null)pistols.close();if(discord!=null)discord.close();
        if(backups!=null)backups.close();if(queue!=null)queue.shutdown();if(travel!=null)travel.close();
        for(Player a:Bukkit.getOnlinePlayers()){a.playerListName(Component.text(a.getName()));a.sendPlayerListHeaderAndFooter(Component.empty(),Component.empty());a.sendActionBar(Component.empty());}
        if(data!=null){data.tickTime();if(vaults!=null)vaults.close();data.close();}
    }
    boolean ready(){return ready;}
    org.bukkit.configuration.file.FileConfiguration cfg(){return getConfig();}
    static Component text(String s){return Component.text(s,NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false);}
    void tell(org.bukkit.command.CommandSender s,String m){s.sendMessage(Component.text("[BlackWikiSMP] ",NamedTextColor.DARK_AQUA).append(Component.text(m,NamedTextColor.GRAY)));}
    void sync(Runnable r){if(!isEnabled())return;if(Bukkit.isPrimaryThread())r.run();else Bukkit.getScheduler().runTask(this,r);}
    Location spawn(){return spawn.clone();}
    void setSpawn(Player a){if(spawnDesign!=null&&spawnDesign.busy()){tell(a,"스폰 생성이 끝난 뒤 중심을 바꾸세요.");return;}if(a.getWorld()!=spawnWorld){tell(a,"스폰 월드("+spawnWorld.getName()+")에서 설정하세요.");return;}if(outside(a.getLocation())){tell(a,"월드 경계 안에서 설정하세요.");return;}spawn=a.getLocation().clone();DataStore.writeLocation((YamlConfiguration)getConfig(),"spawn.location",spawn);saveConfig();spawnWorld.setSpawnLocation(spawn);tell(a,"스폰 위치와 보호 구역 중심을 설정했습니다.");}
    void applySpawn(Location location)throws Exception{if(location.getWorld()!=spawnWorld||outside(location))throw new IllegalArgumentException("Invalid spawn destination");YamlConfiguration desired=new YamlConfiguration();desired.loadFromString(cfg().saveToString());DataStore.writeLocation(desired,"spawn.location",location);DataStore.atomic(getDataFolder().toPath().resolve("config.yml"),desired.saveToString());reloadConfig();spawn=location.clone();spawnWorld.setSpawnLocation(location);}
    boolean safe(Location l){return l!=null&&l.getWorld()==spawnWorld&&Math.abs(l.getX()-spawn.getX())<=cfg().getInt("spawn.radius",500)&&Math.abs(l.getZ()-spawn.getZ())<=cfg().getInt("spawn.radius",500);}
    boolean boundaryWorld(World w){return w!=null&&(queue==null||w!=queue.world)&&(afk==null||!afk.at(w.getSpawnLocation()))&& (cfg().getBoolean("border.all-survival-worlds",true)||w==wildWorld||w==spawnWorld);}
    boolean outside(Location l){return boundaryWorld(l.getWorld())&&Rules.outside(l.getX(),l.getZ(),cfg().getDouble("border.center-x"),cfg().getDouble("border.center-z"),40000);}
    double distance(Location l){return Rules.edgeDistance(l.getX(),l.getZ(),cfg().getDouble("border.center-x"),cfg().getDouble("border.center-z"),40000);}
    void applyBorder(World w){if(!boundaryWorld(w))return;WorldBorder b=w.getWorldBorder();b.setCenter(cfg().getDouble("border.center-x"),cfg().getDouble("border.center-z"));b.setSize(40000);b.setWarningDistance(cfg().getInt("border.warning-distance",100));b.setWarningTimeTicks(200);}
    void checkBorder(Player a){if(!a.isDead()&&!queue.waiting(a)&&cfg().getBoolean("border.kill-outside",true)&&outside(a.getLocation())){tell(a,"40,000 × 40,000 경계를 벗어나 사망했습니다.");a.setHealth(0);}}
    @EventHandler(priority=EventPriority.MONITOR) public void coinZone(PlayerChangedWorldEvent e){if(data!=null)data.zoneChanged(e.getPlayer());}
    @EventHandler public void newWorld(org.bukkit.event.world.WorldLoadEvent e){applyBorder(e.getWorld());}
    @EventHandler public void join(PlayerJoinEvent e){Player a=e.getPlayer();data.user(a);queue.join(a);if(!queue.waiting(a))data.startSession(a);tell(a,"스폰에서 /메뉴 · 야생에서는 개별 명령어를 사용하세요. /도움말 · 경계 밖 즉사");}
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){data.leave(e.getPlayer());shop.quit(e.getPlayer());builders.remove(e.getPlayer().getUniqueId());synchronized(lastChat){lastChat.remove(e.getPlayer().getUniqueId());}}
    @EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent e){e.setRespawnLocation(spawn());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(io.papermc.paper.event.player.AsyncChatEvent e){
        UUID id=e.getPlayer().getUniqueId();String msg=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message());
        if(homes.hasPrompt(id)){e.setCancelled(true);sync(()->homes.input(e.getPlayer(),msg));return;}
        if(gui.hasPrompt(id)){e.setCancelled(true);sync(()->gui.input(e.getPlayer(),msg));return;}
        synchronized(lastChat){long now=System.currentTimeMillis();long prev=lastChat.getOrDefault(id,0L);if(now-prev<moderation.cooldown()){e.setCancelled(true);return;}lastChat.put(id,now);}
        String clean=moderation.clean(e.getPlayer(),msg);if(clean==null)e.setCancelled(true);else if(!clean.equals(msg))e.message(Component.text(clean));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void relayChat(io.papermc.paper.event.player.AsyncChatEvent e){discord.chat(e.getPlayer().getName(),net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message()));}
    List<String> statusLines(){
        Runtime r=Runtime.getRuntime();return List.of("BlackWikiSMP "+getDescription().getVersion()+" · "+(ready?"실행 중":"시작/종료 중"),"Paper "+Bukkit.getMinecraftVersion()+" / Java "+Runtime.version().feature(),"가동 시간: "+Rules.duration((System.nanoTime()-started)/1_000_000_000L),"게임 "+queue.active()+"/"+queue.slots()+" · 대기 "+queue.size()+"/"+queue.capacity(),"AFK 구역: "+afk.count()+"명 · 이동/상호작용 제한","접속 정원: "+Bukkit.getMaxPlayers()+" · TPS "+String.format(Locale.ROOT,"%.2f",Bukkit.getTPS()[0]),"메모리 "+((r.totalMemory()-r.freeMemory())/1048576)+" / "+r.maxMemory()/1048576+" MB","팀 "+data.teams.size()+" · 사용자 기록 "+data.users.size(),"백업: "+backups.status(),"Discord: "+discord.status(),"Discord 전송 "+discord.relay.sent.get()+" · 대기 "+discord.relay.queued()+" · 실패 "+discord.relay.failed.get()+" · 누락 "+discord.relay.dropped.get(),"욕설 필터: "+(moderation.enabled()?moderation.mode():"꺼짐")+" · 단어 "+moderation.words()+" · 처리 "+moderation.count(),"활성 차단: "+bans.activeCount()+"명","코인 상점: "+ShopCatalogue.ALL.size()+"개 · 일반 2초/AFK 0.5초마다 1코인 · 채굴 코인","유저상점: "+market.status(),"야생 초기화: "+resets.status(),"레벨 몬스터: "+monsters.count()+"마리 · 최대 50","채광: "+(cfg().getBoolean("mining.enabled",true)?"켜짐":"꺼짐")+" · 레벨 상승당 30코인","코인 탈취 정산: "+transfers.status(),"홈: 최대 5개 · 무덤 "+graves.count()+"개","벌목: "+Gui.yes(cfg().getBoolean("tree-feller.enabled",true)),"신규 보호: "+Gui.yes(cfg().getBoolean("newbie.enabled",true)),"이벤트: "+events.status(),"음성: "+voice.status(),"자동 업데이트: "+upgrades.status(),"스폰: "+spawnDesign.status(),"환영 보상: "+welcome.status(),"권총: "+pistols.status(),"Anti-Xray: "+antiXray.status(),"도움말: "+tips.status(),"칭호: "+titles.status(),"현실 시간: "+realTime.status(),"스폰 조명: "+lighting.status(),"기본템: "+starter.status(),"베드락: "+bedrock.status());
    }
    private void tick(){
        if(!ready)return;heartbeat++;if(heartbeat%5==0)protection.sweep();tips.tick();realTime.tick();lighting.tick();data.tickTime();queue.tick();travel.tick();npcs.tick();afk.tick();backups.tick();transfers.tick();monsters.tick();events.tick();voice.tick();graves.tick();welcome.tick();discord.tick();
        for(Player a:Bukkit.getOnlinePlayers()){
            titles.sync(a);if(queue.waiting(a))continue;checkBorder(a);if(a.isDead())continue;

            if(heartbeat%5==0&&cfg().getBoolean("settings.tab",true)){a.playerListName(titles.tab(a));a.sendPlayerListHeaderAndFooter(text("BlackWikiSMP "+Version.V+" | 약탈 SMP"),Component.text("접속 "+Bukkit.getOnlinePlayers().size()+" | 게임 "+queue.active()+"/"+queue.slots()+" | 대기 "+queue.size()+" | 핑 "+a.getPing()+"ms"));}
        }
        if(heartbeat%Math.max(10,cfg().getInt("settings.persist-seconds",60))==0)data.save(false);
        if(cfg().getBoolean("cleanup.enabled",true)){
            long remain=(cleanupAt-System.currentTimeMillis()+999)/1000;
            if(remain==cfg().getInt("cleanup.warning-seconds",30))for(Player a:Bukkit.getOnlinePlayers())if(data.user(a).notices&&!queue.waiting(a))tell(a,remain+"초 후 오래된 바닥 아이템을 청소합니다.");
            if(remain<=0){int count=cleanup();for(Player a:Bukkit.getOnlinePlayers())if(data.user(a).notices&&!queue.waiting(a))tell(a,"바닥 아이템 "+count+"개를 청소했습니다.");cleanupAt=System.currentTimeMillis()+cfg().getLong("cleanup.interval-seconds",180)*1000;}
        }
    }
    int cleanup(){int n=0;int age=cfg().getInt("cleanup.minimum-item-age-seconds",60)*20;for(World w:Bukkit.getWorlds())if(w!=queue.world)for(org.bukkit.entity.Item i:w.getEntitiesByClass(org.bukkit.entity.Item.class))if(i.getTicksLived()>=age&&i.getOwner()==null){i.remove();n++;}return n;}
}
