package kr.blackwiki.smp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class RealTimeRules {
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    RealTimeRules() {
    }

    static long ticks(Instant now) {
        return RealTimeRules.ticks(now, ZONE);
    }

    static long ticks(Instant now, ZoneId zone) {
        long nanos = now.atZone(zone).toLocalTime().toNanoOfDay();
        return Math.floorMod(nanos - 21600000000000L, 86400000000000L) * 24000L / 86400000000000L;
    }

    static long error(long actual, long expected) {
        return Math.floorMod(actual - expected + 12000L, 24000) - 12000;
    }

    static String phase(Instant now, ZoneId zone) {
        int h = now.atZone(zone).getHour();
        return h >= 4 && h < 6 ? "새벽" : (h >= 6 && h < 11 ? "아침" : (h >= 11 && h < 17 ? "점심" : (h >= 17 && h < 20 ? "저녁" : "밤")));
    }

    static String display(Instant now, ZoneId zone) {
        return RealTimeRules.phase(now, zone) + " " + CLOCK.format(now.atZone(zone));
    }
}

