package kr.blackwiki.smp;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;

final class RealTimeService
implements Listener,
AutoCloseable {
    private final BlackWikiSMP p;
    private final Path file;
    private final Map<UUID, Boolean> previous = new LinkedHashMap<UUID, Boolean>();
    private final Map<UUID, WeatherJournal.State> weatherPrevious = new LinkedHashMap<UUID, WeatherJournal.State>();
    private final WeatherJournal weatherJournal;
    private final WeatherFeed weather;
    private WeatherSettings settings;
    private boolean fault;
    private boolean busy;
    private boolean weatherFault;
    private boolean journalInvalid;
    private boolean closed;
    private String weatherProblem = "";
    private String settingsProblem = "";

    RealTimeService(BlackWikiSMP p) throws Exception {
        this.p = p;
        this.file = p.getDataFolder().toPath().resolve("real-time-state.yml");
        if (Files.exists(this.file, new LinkOption[0])) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(this.file.toFile());
            if (y.getInt("schema") != 1) {
                throw new IllegalArgumentException("Unknown clock schema");
            }
            ConfigurationSection section = y.getConfigurationSection("worlds");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    Object object = section.get(id);
                    if (!(object instanceof Boolean)) {
                        throw new IllegalArgumentException("Invalid clock rule");
                    }
                    Boolean rule = (Boolean)object;
                    this.previous.put(UUID.fromString(id), rule);
                }
            }
        }
        this.weatherJournal = new WeatherJournal(p.getDataFolder().toPath().resolve("real-weather-state.yml"));
        try {
            this.weatherPrevious.putAll(this.weatherJournal.load());
        }
        catch (Exception ex) {
            this.weatherFault = true;
            this.journalInvalid = true;
            this.weatherProblem = "날씨 복구 기록 확인 필요";
            p.getLogger().log(Level.WARNING, "Weather recovery journal invalid; weather paused, original retained", ex);
        }
        this.weather = new WeatherFeed(p.getDataFolder().toPath().resolve("real-weather-cache.yml"), new WeatherHttp());
        this.tick();
    }

    boolean enabled() {
        return this.p.cfg().getBoolean("real-time.enabled", false);
    }

    private boolean target(World w) {
        return w.getEnvironment() == World.Environment.NORMAL && w != this.p.queue.world;
    }

    private void save() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.set("schema", 1);
        this.previous.forEach((id, rule) -> y.set("worlds." + String.valueOf(id), rule));
        DataStore.atomic(this.file, y.saveToString());
    }

    void tick() {
        this.applyAt(Instant.now());
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(this.p.cfg().getString("real-time.timezone", "Asia/Seoul"));
        }
        catch (Exception ex) {
            return RealTimeRules.ZONE;
        }
    }

    void applyAt(Instant instant) {
        block18: {
            if (this.closed || this.busy || this.p.backups.running() || this.p.resets.busy()) {
                return;
            }
            this.busy = true;
            try {
                try {
                    this.settings = WeatherSettings.from(this.p.cfg());
                    this.settingsProblem = "";
                }
                catch (Exception ex) {
                    this.settings = null;
                    this.settingsProblem = "날씨 설정 확인 필요";
                }
                if (this.settings == null) {
                    this.weather.pause();
                } else {
                    this.weather.configure(this.settings, this.enabled() && this.settings.enabled() && !this.weatherFault, instant);
                }
                if (!this.fault || !this.enabled()) {
                    try {
                        if (this.enabled()) {
                            this.applyTime(instant);
                        } else {
                            this.restoreTime();
                            this.fault = false;
                        }
                    }
                    catch (Exception ex) {
                        this.fault = true;
                        this.p.getLogger().log(Level.SEVERE, "Real time synchronization paused", ex);
                    }
                }
                if (this.journalInvalid || this.weatherFault && this.enabled() && (this.settings == null || this.settings.enabled())) break block18;
                try {
                    WeatherData sample;
                    WeatherData weatherData = sample = this.enabled() && this.settings != null && this.settings.enabled() ? this.weather.usable(this.settings, instant) : null;
                    if (sample == null) {
                        this.restoreWeather(true);
                    } else {
                        this.applyWeather(sample);
                    }
                    if (!this.enabled()) {
                        this.weatherFault = false;
                    }
                }
                catch (Exception ex) {
                    this.weatherFault = true;
                    this.weatherProblem = "날씨 복구/적용 확인 필요";
                    this.p.getLogger().log(Level.WARNING, "Weather synchronization paused; recovery journal retained", ex);
                }
            }
            finally {
                this.busy = false;
            }
        }
    }

    private void applyTime(Instant instant) throws Exception {
        long time = RealTimeRules.ticks(instant, this.zone());
        for (World w : Bukkit.getWorlds()) {
            if (!this.target(w)) continue;
            if (!this.previous.containsKey(w.getUID())) {
                this.previous.put(w.getUID(), Boolean.TRUE.equals(w.getGameRuleValue(GameRules.ADVANCE_TIME)));
                try {
                    this.save();
                }
                catch (Exception ex) {
                    this.previous.remove(w.getUID());
                    throw ex;
                }
            }
            w.setGameRule(GameRules.ADVANCE_TIME, false);
            if (w.getTime() == time) continue;
            w.setTime(time);
        }
    }

    private void restoreTime() throws Exception {
        for (UUID id : List.copyOf(this.previous.keySet())) {
            World w = Bukkit.getWorld((UUID)id);
            if (w == null) continue;
            w.setGameRule(GameRules.ADVANCE_TIME, this.previous.get(id));
            w.save(true);
            this.previous.remove(id);
            this.save();
        }
    }

    private WeatherJournal.State capture(World w) {
        return new WeatherJournal.State(Boolean.TRUE.equals(w.getGameRuleValue(GameRules.ADVANCE_WEATHER)), w.hasStorm(), w.isThundering(), w.getWeatherDuration(), w.getThunderDuration(), w.getClearWeatherDuration());
    }

    private void applyWeather(WeatherData sample) throws Exception {
        for (World w : Bukkit.getWorlds()) {
            if (!this.target(w)) continue;
            if (!this.weatherPrevious.containsKey(w.getUID())) {
                this.weatherPrevious.put(w.getUID(), this.capture(w));
                try {
                    this.weatherJournal.save(this.weatherPrevious);
                }
                catch (Exception ex) {
                    this.weatherPrevious.remove(w.getUID());
                    throw ex;
                }
            }
            w.setGameRule(GameRules.ADVANCE_WEATHER, false);
            if (w.hasStorm() != sample.storm()) {
                w.setStorm(sample.storm());
            }
            if (w.isThundering() != sample.thunder()) {
                w.setThundering(sample.thunder());
            }
            w.setClearWeatherDuration(0);
            if (w.hasStorm() == sample.storm() && w.isThundering() == sample.thunder()) continue;
            throw new IllegalStateException("Another weather handler rejected synchronization");
        }
    }

    private void restoreOne(World w, WeatherJournal.State s) {
        if (w.hasStorm() != s.storm()) {
            w.setStorm(s.storm());
        }
        if (w.isThundering() != s.thunder()) {
            w.setThundering(s.thunder());
        }
        w.setWeatherDuration(s.rainTicks());
        w.setThunderDuration(s.thunderTicks());
        w.setClearWeatherDuration(s.clearTicks());
        w.setGameRule(GameRules.ADVANCE_WEATHER, s.advance());
        if (w.hasStorm() != s.storm() || w.isThundering() != s.thunder()) {
            throw new IllegalStateException("Weather restoration was cancelled");
        }
    }

    private void restoreWeather(boolean durable) throws Exception {
        for (UUID id : List.copyOf(this.weatherPrevious.keySet())) {
            World w = Bukkit.getWorld((UUID)id);
            if (w == null) continue;
            this.restoreOne(w, this.weatherPrevious.get(id));
            if (durable) {
                w.save(true);
            }
            this.weatherPrevious.remove(id);
            this.weatherJournal.save(this.weatherPrevious);
        }
    }

    private boolean allowed(CommandSender sender) {
        if (!BanService.operator(sender)) {
            this.p.tell(sender, "OP만 현실 시간·날씨를 변경할 수 있습니다.");
            return false;
        }
        if (this.busy || this.p.backups.running() || this.p.resets.busy()) {
            this.p.tell(sender, "저장·백업·초기화가 끝난 뒤 다시 시도하세요.");
            return false;
        }
        return true;
    }

    private boolean setting(CommandSender sender, String key, boolean value) {
        if (!this.allowed(sender)) {
            return false;
        }
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(this.p.cfg().saveToString());
            y.set(key, value);
            DataStore.atomic(this.p.getDataFolder().toPath().resolve("config.yml"), y.saveToString());
            this.p.reloadConfig();
            this.fault = false;
            if (!this.journalInvalid) {
                this.weatherFault = false;
            }
            this.tick();
            this.p.tell(sender, this.status());
            return !this.fault && !this.weatherFault;
        }
        catch (Exception ex) {
            this.p.tell(sender, "현실 시간·날씨 설정 저장에 실패했습니다.");
            this.p.getLogger().log(Level.SEVERE, "Clock/weather setting failed", ex);
            return false;
        }
    }

    boolean setEnabled(CommandSender sender, boolean enable) {
        return this.setting(sender, "real-time.enabled", enable);
    }

    String hud() {
        if (!this.enabled() || !this.p.cfg().getBoolean("real-time.show-hud", true) || this.fault) {
            return "";
        }
        Instant now = Instant.now();
        WeatherData sample = this.settings != null && this.settings.enabled() && !this.weatherFault ? this.weather.usable(this.settings, now) : null;
        return "현실 " + RealTimeRules.display(now, this.zone()) + (String)(sample == null ? "" : " · " + sample.label());
    }

    String status() {
        return (this.fault ? "오류 · 정지" : (this.enabled() ? "켜짐" : "꺼짐")) + " · " + RealTimeRules.display(Instant.now(), this.zone()) + " · " + this.zone().getId() + " · " + (this.settings == null ? this.settingsProblem : (!this.settings.enabled() ? "날씨 연동 꺼짐" : (this.weatherFault ? this.weatherProblem : this.weather.status(this.settings, Instant.now()))));
    }

    void command(CommandSender sender, String[] args) {
        if (!BanService.operator(sender)) {
            this.p.tell(sender, "OP만 사용할 수 있습니다.");
            return;
        }
        if (args.length == 1 && Set.of("켜기", "끄기", "on", "off").contains(args[0])) {
            this.setEnabled(sender, Set.of("켜기", "on").contains(args[0]));
            return;
        }
        if (args.length == 2 && args[0].equals("날씨") && Set.of("켜기", "끄기", "on", "off").contains(args[1])) {
            this.setting(sender, "real-time.weather.enabled", Set.of("켜기", "on").contains(args[1]));
            return;
        }
        if (args.length == 1 && args[0].equals("날씨갱신")) {
            if (!this.allowed(sender)) {
                return;
            }
            this.p.tell(sender, this.enabled() && this.settings != null && this.settings.enabled() && this.weather.refresh(Instant.now()) ? "날씨를 다시 조회합니다." : "현실 시간과 날씨를 켜고 진행 중 조회/30초 재조회 간격을 확인하세요.");
            return;
        }
        if (sender instanceof Player) {
            Player a = (Player)sender;
            Gui.Menu m = this.p.gui.make(a, "현실 시간·날씨 · 한국 기준");
            m.inventory.setItem(13, Gui.item(Material.CLOCK, this.status(), "새벽 04~06 · 아침 06~11 · 점심 11~17", "저녁 17~20 · 밤 20~04 · 24시간 계속 흐름"));
            m.inventory.setItem(31, Gui.item(Material.WATER_BUCKET, this.settings == null ? "날씨 설정 확인 필요" : this.settings.location(), this.settings == null ? "" : this.weather.status(this.settings, Instant.now()), "Open-Meteo · 기본 5분마다 최신 제공값 조회", "눈은 추운 바이옴에서 눈으로 표시됩니다."));
            this.p.gui.button(m, 20, Material.LIME_WOOL, "현실 동기화 켜기", b -> {
                this.setEnabled((CommandSender)b, true);
                this.command((CommandSender)b, new String[0]);
            }, new String[0]);
            this.p.gui.button(m, 24, Material.RED_WOOL, "현실 동기화 끄기", b -> {
                this.setEnabled((CommandSender)b, false);
                this.command((CommandSender)b, new String[0]);
            }, new String[0]);
            this.p.gui.button(m, 29, Material.LEVER, "날씨만: " + Gui.yes(this.settings != null && this.settings.enabled()), b -> {
                this.setting((CommandSender)b, "real-time.weather.enabled", this.settings == null || !this.settings.enabled());
                this.command((CommandSender)b, new String[0]);
            }, new String[0]);
            this.p.gui.button(m, 33, Material.COMPASS, "시간 HUD: " + Gui.yes(this.settings != null && this.settings.hud()), b -> {
                this.setting((CommandSender)b, "real-time.show-hud", this.settings == null || !this.settings.hud());
                this.command((CommandSender)b, new String[0]);
            }, new String[0]);
            this.p.gui.button(m, 40, Material.CLOCK, "날씨 다시 조회", b -> this.command((CommandSender)b, new String[]{"날씨갱신"}), new String[0]);
            this.p.gui.show(a, m);
        } else {
            this.p.tell(sender, this.status() + " · /현실시간 켜기|끄기 · /현실시간 날씨 켜기|끄기");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void world(WorldLoadEvent e) {
        this.tick();
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void sleep(TimeSkipEvent e) {
        if (this.enabled() && this.target(e.getWorld()) && e.getSkipReason() == ClockTimeSkipEvent.SkipReason.NIGHT_SKIP) {
            e.setCancelled(true);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.weather.close();
        this.busy = true;
        try {
            World w;
            for (Map.Entry<UUID, Boolean> entry : this.previous.entrySet()) {
                w = Bukkit.getWorld((UUID)entry.getKey());
                if (w == null) continue;
                w.setGameRule(GameRules.ADVANCE_TIME, entry.getValue());
            }
            for (Map.Entry<UUID, WeatherJournal.State> entry : this.weatherPrevious.entrySet()) {
                w = Bukkit.getWorld((UUID)entry.getKey());
                if (w == null) continue;
                try {
                    this.restoreOne(w, (WeatherJournal.State)entry.getValue());
                }
                catch (Exception ex) {
                    this.p.getLogger().warning("Weather restoration deferred for " + w.getName());
                }
            }
        }
        finally {
            this.busy = false;
        }
    }
}

