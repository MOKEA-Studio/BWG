package kr.blackwiki.smp;

import java.text.Normalizer;
import java.util.*;

/** A separate one-shot reward for each newcomer; only the main thread mutates claims. */
final class WelcomeRules {
    static final long WINDOW_NANOS=10_000_000_000L,REWARD=100;
    private WelcomeRules(){}
    static boolean greeting(String text){return text!=null&&Normalizer.normalize(text.strip(),Normalizer.Form.NFC).equals("안녕하세요");}
    static final class Window {
        final UUID newcomer;
        final String name;
        final long opened;
        private final Set<UUID> claimers=new HashSet<>();
        Window(UUID newcomer,String name,long opened){this.newcomer=Objects.requireNonNull(newcomer);this.name=Objects.requireNonNull(name);this.opened=opened;}
        boolean active(long now){long elapsed=now-opened;return elapsed>=0&&elapsed<WINDOW_NANOS;}
        boolean eligible(UUID who,long received){return !newcomer.equals(who)&&active(received)&&!claimers.contains(who);}
        void claimed(UUID who){claimers.add(who);}
        long remaining(long now){return Math.max(0,(WINDOW_NANOS-(now-opened)+999_999_999L)/1_000_000_000L);}
    }
}
