package kr.blackwiki.smp;

/** Wall-clock deadlines survive restarts. Old graves get one upgrade grace period. */
final class GraveRules {
    static final long LIFETIME_MILLIS=600_000L;
    private GraveRules(){}
    static long deadline(long created,long upgradeEpoch){return Math.addExact(Math.max(created,upgradeEpoch),LIFETIME_MILLIS);}
    static boolean expired(long created,long upgradeEpoch,long now){return now>=deadline(created,upgradeEpoch);}
    static long remainingSeconds(long created,long upgradeEpoch,long now){return Math.max(0,(deadline(created,upgradeEpoch)-now+999)/1000);}
}
