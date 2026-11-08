package kr.blackwiki.smp;
import java.util.*;
/** One message per five real minutes; a shuffled bag avoids repeats until all seven have appeared. */
final class TipCycle {
 static final long INTERVAL=300_000_000_000L;
 private final Random random;private final Deque<Integer> bag=new ArrayDeque<>();private long due;private int last=-1;
 TipCycle(long now,Random random){this.random=Objects.requireNonNull(random);due=now+INTERVAL;}
 int poll(long now){if(now-due<0)return -1;due=now+INTERVAL;if(bag.isEmpty()){List<Integer> indices=new ArrayList<>(java.util.stream.IntStream.range(0,TipService.MESSAGES.size()).boxed().toList());Collections.shuffle(indices,random);if(indices.getFirst()==last)Collections.swap(indices,0,1);bag.addAll(indices);}last=bag.removeFirst();return last;}
}
