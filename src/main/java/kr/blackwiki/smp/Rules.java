package kr.blackwiki.smp;

import java.util.*;

/** Pure rules shared by runtime and tests; no server objects. */
public final class Rules {
    private Rules() {}
    public static boolean validTeamName(String name, int limit) {
        return name != null && !name.isBlank() && name.codePointCount(0, name.length()) <= Math.min(12, limit)
            && name.codePoints().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
    }
    public static boolean outside(double x, double z, double cx, double cz, double size) {
        return !Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x-cx) > size/2 || Math.abs(z-cz) > size/2;
    }
    public static double edgeDistance(double x, double z, double cx, double cz, double size) {
        return size/2 - Math.max(Math.abs(x-cx), Math.abs(z-cz));
    }
    public static String duration(long s) {
        s=Math.max(0,s);
        return (s/86400)+"일 "+(s/3600%24)+"시간 "+(s/60%60)+"분 "+(s%60)+"초";
    }
    public static final class Fifo {
        private final LinkedHashSet<UUID> waiting = new LinkedHashSet<>();
        public boolean add(UUID id) { return waiting.add(id); }
        public boolean remove(UUID id) { return waiting.remove(id); }
        public boolean contains(UUID id) { return waiting.contains(id); }
        public int size() { return waiting.size(); }
        public int position(UUID id) { int i=0; for(UUID v:waiting) { i++; if(v.equals(id))return i; }return 0; }
        public UUID peek() { return waiting.stream().findFirst().orElse(null); }
        public List<UUID> list() { return List.copyOf(waiting); }
    }
}
