package kr.blackwiki.smp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

record WeatherData(double latitude, double longitude, int code, double celsius, Instant observed, Instant received) {
    WeatherData {
        WeatherData.coordinates(latitude, longitude);
        WeatherData.kind(code);
        if (!Double.isFinite(celsius) || celsius < -100.0 || celsius > 70.0 || observed == null || received == null) {
            throw new IllegalArgumentException("Invalid weather data");
        }
    }

    static void coordinates(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("날씨 위도/경도를 확인하세요.");
        }
    }

    static Kind kind(int code) {
        return switch (code) {
            case 0, 1 -> Kind.CLEAR;
            case 2, 3 -> Kind.CLOUD;
            case 45, 48 -> Kind.FOG;
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN;
            case 71, 73, 75, 77, 85, 86 -> Kind.SNOW;
            case 95, 96, 97, 99 -> Kind.THUNDER;
            default -> throw new IllegalArgumentException("Unknown weather code");
        };
    }

    Kind kind() {
        return WeatherData.kind(this.code);
    }

    boolean storm() {
        return Set.of(Kind.RAIN, Kind.SNOW, Kind.THUNDER).contains(this.kind());
    }

    boolean thunder() {
        return this.kind() == Kind.THUNDER;
    }

    String label() {
        return switch (this.kind().ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> "맑음";
            case 1 -> {
                if (this.code == 2) {
                    yield "구름 조금";
                }
                yield "흐림";
            }
            case 2 -> "안개";
            case 3 -> "비";
            case 4 -> "눈";
            case 5 -> "뇌우";
        };
    }

    boolean matches(double lat, double lon) {
        return Double.compare(this.latitude, lat) == 0 && Double.compare(this.longitude, lon) == 0;
    }

    boolean fresh(Instant now, int maxAge) {
        return WeatherData.age(this.observed, now, maxAge) && WeatherData.age(this.received, now, maxAge);
    }

    private static boolean age(Instant time, Instant now, int seconds) {
        long age = Duration.between(time, now).getSeconds();
        return age >= -300L && age <= (long)seconds;
    }

    static WeatherData parse(String json, double lat, double lon, Instant now) {
        if (json == null || json.length() > 32768) {
            throw new IllegalArgumentException("Weather response size");
        }
        try {
            JsonObject root = JsonParser.parseString((String)json).getAsJsonObject();
            if (root.has("error") && root.get("error").getAsBoolean()) {
                throw new IllegalArgumentException("Provider error");
            }
            JsonObject units = root.getAsJsonObject("current_units");
            JsonObject current = root.getAsJsonObject("current");
            if (units == null || !"unixtime".equals(units.get("time").getAsString()) || !"°C".equals(units.get("temperature_2m").getAsString())) {
                throw new IllegalArgumentException("Unexpected weather units");
            }
            int code = current.get("weather_code").getAsBigDecimal().intValueExact();
            long stamp = current.get("time").getAsBigDecimal().longValueExact();
            WeatherData sample = new WeatherData(lat, lon, code, current.get("temperature_2m").getAsDouble(), Instant.ofEpochSecond(stamp), now);
            if (!sample.fresh(now, 1800)) {
                throw new IllegalArgumentException("Weather provider data is too old/future");
            }
            return sample;
        }
        catch (IllegalArgumentException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Malformed weather response", ex);
        }
    }

    static enum Kind {
        CLEAR,
        CLOUD,
        FOG,
        RAIN,
        SNOW,
        THUNDER;

    }
}

