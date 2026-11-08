package kr.blackwiki.smp;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.configuration.file.YamlConfiguration;

final class WeatherFeed
implements AutoCloseable {
    private final Source source;
    private final Path cache;
    private final ExecutorService worker;
    private final Clock clock;
    private WeatherSettings settings;
    private WeatherData sample;
    private Future<?> pending;
    private Instant next = Instant.EPOCH;
    private Instant lastRequest = Instant.EPOCH;
    private Instant notBefore = Instant.EPOCH;
    private long revision;
    private boolean closed;
    private boolean wasActive;
    private String issue = "연결 대기";

    WeatherFeed(Path cache, Source source) {
        this(cache, source, Clock.systemUTC());
    }

    WeatherFeed(Path cache, Source source, Clock clock) {
        this.cache = cache;
        this.source = source;
        this.clock = clock;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BlackWikiSMP-weather");
            t.setDaemon(true);
            return t;
        });
        try {
            if (Files.exists(cache, new LinkOption[0])) {
                YamlConfiguration y = new YamlConfiguration();
                y.load(cache.toFile());
                if (y.getInt("schema") != 1) {
                    throw new IllegalArgumentException("Weather cache schema");
                }
                this.sample = new WeatherData(y.getDouble("latitude"), y.getDouble("longitude"), y.getInt("code"), y.getDouble("celsius"), Instant.parse(y.getString("observed")), Instant.parse(y.getString("received")));
                this.issue = "캐시 사용";
            }
        }
        catch (Exception ex) {
            this.sample = null;
            this.issue = "캐시 확인 필요";
        }
    }

    private boolean sameRequest(WeatherSettings s) {
        return this.settings != null && Double.compare(this.settings.latitude(), s.latitude()) == 0 && Double.compare(this.settings.longitude(), s.longitude()) == 0 && this.settings.zone().equals(s.zone()) && this.settings.interval() == s.interval() && this.settings.maxAge() == s.maxAge();
    }

    synchronized void configure(WeatherSettings value, boolean active, Instant now) {
        if (this.closed) {
            return;
        }
        boolean changed = !this.sameRequest(value) || this.wasActive != active;
        this.settings = value;
        if (changed) {
            ++this.revision;
            if (this.pending != null) {
                this.pending.cancel(true);
            }
            this.pending = null;
            this.next = Instant.EPOCH;
        }
        this.wasActive = active;
        if (!active) {
            return;
        }
        if (this.pending != null && this.pending.isDone()) {
            this.pending = null;
        }
        if (!(this.pending != null || now.isBefore(this.next) || now.isBefore(this.lastRequest.plusSeconds(30L)) || now.isBefore(this.notBefore))) {
            this.poll(now);
        }
    }

    private void poll(Instant now) {
        long version = this.revision;
        WeatherSettings request = this.settings;
        this.lastRequest = now;
        this.next = now.plusSeconds(request.interval());
        this.pending = this.worker.submit(() -> {
            try {
                WeatherData result = this.source.fetch(request);
                this.accept(version, request, result);
            }
            catch (Exception ex) {
                this.failed(version, ex);
            }
        });
    }

    private synchronized void accept(long version, WeatherSettings request, WeatherData value) {
        if (this.closed || version != this.revision) {
            return;
        }
        this.pending = null;
        Instant now = this.clock.instant();
        if (!value.matches(this.settings.latitude(), this.settings.longitude()) || !value.fresh(now, this.settings.maxAge())) {
            this.issue = "오래된 제공값";
            return;
        }
        this.sample = value;
        this.issue = "";
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.set("schema", 1);
            y.set("latitude", value.latitude());
            y.set("longitude", value.longitude());
            y.set("code", value.code());
            y.set("celsius", value.celsius());
            y.set("observed", value.observed().toString());
            y.set("received", value.received().toString());
            DataStore.atomic(this.cache, y.saveToString());
        }
        catch (Exception ex) {
            this.issue = "캐시 저장 대기";
        }
    }

    private synchronized void failed(long version, Exception error) {
        if (!this.closed && version == this.revision) {
            this.pending = null;
            this.issue = "날씨 연결 대기";
            if (error instanceof WeatherHttp.ProviderFailure) {
                WeatherHttp.ProviderFailure failure = (WeatherHttp.ProviderFailure)error;
                if (failure.retrySeconds > 0L) {
                    this.notBefore = this.clock.instant().plusSeconds(failure.retrySeconds);
                    this.issue = "제공자 재조회 대기";
                }
            }
        }
    }

    synchronized void pause() {
        if (this.wasActive || this.pending != null) {
            ++this.revision;
            if (this.pending != null) {
                this.pending.cancel(true);
            }
            this.pending = null;
            this.next = Instant.EPOCH;
        }
        this.wasActive = false;
    }

    synchronized WeatherData usable(WeatherSettings s, Instant now) {
        return this.sample != null && this.sample.matches(s.latitude(), s.longitude()) && this.sample.fresh(now, s.maxAge()) ? this.sample : null;
    }

    synchronized boolean refresh(Instant now) {
        if (this.closed || !this.wasActive || this.settings == null || this.pending != null || now.isBefore(this.lastRequest.plusSeconds(30L)) || now.isBefore(this.notBefore)) {
            return false;
        }
        this.poll(now);
        return true;
    }

    synchronized String status(WeatherSettings s, Instant now) {
        WeatherData current = this.usable(s, now);
        return current == null ? (this.issue.isEmpty() ? "날씨 연결 대기" : this.issue) + " · 기본 날씨" : current.label() + " · " + String.format(Locale.ROOT, "%.1f℃", current.celsius()) + (this.issue.isEmpty() ? "" : " · 캐시");
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        ++this.revision;
        if (this.pending != null) {
            this.pending.cancel(true);
        }
        this.pending = null;
        this.worker.shutdownNow();
        Source source = this.source;
        if (source instanceof AutoCloseable) {
            AutoCloseable c = (AutoCloseable)(source);
            try {
                c.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    static interface Source {
        public WeatherData fetch(WeatherSettings var1) throws Exception;
    }
}

