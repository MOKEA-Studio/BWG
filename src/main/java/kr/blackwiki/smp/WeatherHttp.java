package kr.blackwiki.smp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class WeatherHttp
implements WeatherFeed.Source,
AutoCloseable {
    private final URI endpoint;
    private final HttpClient client;
    private final Duration timeout;

    WeatherHttp() {
        this(URI.create("https://api.open-meteo.com/v1/forecast"));
    }

    WeatherHttp(URI endpoint) {
        this(endpoint, Duration.ofSeconds(8L));
    }

    WeatherHttp(URI endpoint, Duration timeout) {
        if (!(endpoint.equals(URI.create("https://api.open-meteo.com/v1/forecast")) || endpoint.getScheme().equals("http") && SetHosts.local(endpoint.getHost()))) {
            throw new IllegalArgumentException("Unsupported weather endpoint");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30L)) > 0) {
            throw new IllegalArgumentException("Invalid weather timeout");
        }
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout.compareTo(Duration.ofSeconds(5L)) < 0 ? timeout : Duration.ofSeconds(5L)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    URI uri(WeatherSettings s) {
        return URI.create(String.valueOf(this.endpoint) + "?latitude=" + s.latitude() + "&longitude=" + s.longitude() + "&current=weather_code,temperature_2m&temperature_unit=celsius&timeformat=unixtime&timezone=" + URLEncoder.encode(s.zone().getId(), StandardCharsets.UTF_8) + "&forecast_days=1");
    }

    @Override
    public WeatherData fetch(WeatherSettings settings) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(this.uri(settings)).timeout(this.timeout).header("Accept", "application/json").header("User-Agent", "BlackWikiSMP/3.5 (Minecraft weather sync)").GET().build();
        CompletableFuture<HttpResponse<String>> future = this.client.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), 32768L));
        try {
            HttpResponse<String> response = future.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw new ProviderFailure(response.statusCode(), WeatherHttp.retryAfter(response.headers().firstValue("Retry-After").orElse(""), Instant.now()));
            }
            return WeatherData.parse(response.body(), settings.latitude(), settings.longitude(), Instant.now());
        }
        catch (InterruptedException | TimeoutException ex) {
            future.cancel(true);
            throw ex;
        }
    }

    static long retryAfter(String value, Instant now) {
        try {
            return Math.max(0L, Math.min(604800L, Long.parseLong(value)));
        }
        catch (Exception ignored) {
            try {
                return Math.max(0L, Math.min(604800L, Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()));
            }
            catch (Exception ex) {
                return 0L;
            }
        }
    }

    @Override
    public void close() {
        this.client.shutdownNow();
    }

    private static final class SetHosts {
        private SetHosts() {
        }

        static boolean local(String host) {
            return host != null && Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(host);
        }
    }

    static final class ProviderFailure
    extends IOException {
        final long retrySeconds;

        ProviderFailure(int code, long seconds) {
            super("Weather HTTP " + code);
            this.retrySeconds = seconds;
        }
    }
}

