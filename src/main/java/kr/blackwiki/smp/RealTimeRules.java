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
        long millis = now.atZone(zone).toLocalTime().toNanoOfDay() / 1000000L;
        return Math.floorMod(millis - 21600000L, 86400000L) * 24000L / 86400000L;
    }

    static String phase(Instant now, ZoneId zone) {
        int h = now.atZone(zone).getHour();
        return h >= 4 && h < 6 ? "새벽" : (h >= 6 && h < 11 ? "아침" : (h >= 11 && h < 17 ? "점심" : (h >= 17 && h < 20 ? "저녁" : "밤")));
    }

    static String display(Instant now, ZoneId zone) {
        return RealTimeRules.phase(now, zone) + " " + CLOCK.format(now.atZone(zone));
    }
}

