package kr.blackwiki.smp;

import java.util.Locale;
import java.util.Set;

final class AfkRules {
    private AfkRules() {
    }

    static String worldName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("worlds.afk: 영문·숫자·밑줄·하이픈 1~64자");
        }
        return value;
    }

    static String root(String message) {
        int colon;
        String root = message.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("/")) {
            root = root.substring(1);
        }
        return (colon = root.lastIndexOf(58)) < 0 ? root : root.substring(colon + 1);
    }

    static boolean allowedCommand(String message) {
        String[] parts = message.strip().split("\\s+");
        if (Set.of("blacksmp", "blackwikismp", "smp", "메뉴").contains(AfkRules.root(message)) && parts.length > 1 && !Set.of("help", "도움말").contains(parts[1].toLowerCase(Locale.ROOT))) {
            return false;
        }
        return Set.of("smpafk", "afk", "잠수", "잠수구역", "worlds", "월드", "구역", "spawn", "스폰", "로비", "wilderness", "야생", "rtp", "blacksmp", "blackwikismp", "smp", "메뉴", "명령어", "smpcommands").contains(AfkRules.root(message));
    }

    static void distinct(String afk, String ... other) {
        AfkRules.worldName(afk);
        for (String name : other) {
            if (!afk.equalsIgnoreCase(name)) continue;
            throw new IllegalArgumentException("AFK 월드는 스폰·야생·대기열과 달라야 합니다.");
        }
    }
}

