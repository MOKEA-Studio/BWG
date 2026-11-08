package kr.blackwiki.smp;

import java.time.ZoneId;
import org.bukkit.configuration.file.FileConfiguration;

record WeatherSettings(double latitude, double longitude, String location, int interval, int maxAge, ZoneId zone, boolean enabled, boolean hud) {
    WeatherSettings {
        WeatherData.coordinates(latitude, longitude);
        if (location == null || location.isBlank() || location.length() > 80 || zone == null || interval < 60 || interval > 3600 || maxAge < 900 || maxAge > 21600) {
            throw new IllegalArgumentException("날씨 설정 범위를 확인하세요. 갱신 60~3600초 / 유효 900~21600초");
        }
    }

    static WeatherSettings from(FileConfiguration c) {
        return new WeatherSettings(c.getDouble("real-time.weather.latitude", 35.3), c.getDouble("real-time.weather.longitude", 128.52), c.getString("real-time.weather.location", "경남 함안군 칠원읍"), c.getInt("real-time.weather.poll-seconds", 300), c.getInt("real-time.weather.max-age-seconds", 1800), ZoneId.of(c.getString("real-time.timezone", "Asia/Seoul")), c.getBoolean("real-time.weather.enabled", true), c.getBoolean("real-time.show-hud", true));
    }
}

