package kr.blackwiki.smp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;

final class TitleCatalogue {
    static final List<Title> PAID = List.of(new Title("sprout", "새싹 생존자", 0L, 0L, 0L, NamedTextColor.GREEN), new Title("wanderer", "황야의 방랑자", 2000L, 0L, 0L, NamedTextColor.AQUA), new Title("pioneer", "철벽의 개척자", 7500L, 0L, 0L, NamedTextColor.BLUE), new Title("stalker", "심야의 추격자", 20000L, 0L, 0L, NamedTextColor.DARK_AQUA), new Title("ruler", "전장의 지배자", 75000L, 0L, 0L, NamedTextColor.GOLD), new Title("death_lord", "죽음의 지배자", 250000L, 0L, 0L, NamedTextColor.RED), new Title("abyss_king", "심연의 약탈왕", 750000L, 0L, 0L, NamedTextColor.DARK_PURPLE));
    static final List<Title> KILLS = List.of(new Title("kill_1", "첫 승리", 0L, 1L, 0L, NamedTextColor.GREEN), new Title("kill_10", "신입 사냥꾼", 0L, 10L, 0L, NamedTextColor.AQUA), new Title("kill_50", "황야의 결투가", 0L, 50L, 0L, NamedTextColor.BLUE), new Title("kill_100", "전장의 추격자", 0L, 100L, 0L, NamedTextColor.GOLD), new Title("kill_500", "피의 군주", 0L, 500L, 1500L, NamedTextColor.RED), new Title("kill_1000", "불멸의 약탈왕", 0L, 1000L, 5000L, NamedTextColor.LIGHT_PURPLE));

    TitleCatalogue() {
    }

    static List<Title> all() {
        ArrayList<Title> list = new ArrayList<Title>(PAID);
        list.addAll(KILLS);
        return List.copyOf(list);
    }

    static Title find(String id) {
        return TitleCatalogue.all().stream().filter(t -> t.id.equals(id) || t.name.equals(id) || t.label().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("칭호 이름 또는 ID를 확인하세요. /칭호"));
    }

    static Set<String> due(long kills, Set<String> claimed) {
        if (kills < 0L) {
            throw new IllegalArgumentException("Invalid kills");
        }
        LinkedHashSet<String> due = new LinkedHashSet<String>();
        for (Title t : KILLS) {
            if (t.reward <= 0L || kills < t.kills || claimed.contains(t.id)) continue;
            due.add(t.id);
        }
        return due;
    }

    record Title(String id, String name, long price, long kills, long reward, NamedTextColor color) {
        boolean purchase() {
            return this.kills == 0L;
        }

        String label() {
            return "[" + this.name + "]";
        }
    }
}

