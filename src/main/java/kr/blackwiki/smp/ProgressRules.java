package kr.blackwiki.smp;

/** Pure rules shared by gameplay and tests. Mob levels are always 1..50. */
final class ProgressRules {
    static final int MAX_MOB_LEVEL=50;
    static double weight(int level,double decay){if(level<1||level>50||!Double.isFinite(decay)||decay<=0||decay>=1)throw new IllegalArgumentException("Invalid monster level/decay");return Math.pow(decay,level-1);}
    static int mobLevel(double roll,double decay){if(!Double.isFinite(roll)||roll<0||roll>=1)throw new IllegalArgumentException("Invalid roll");double total=0;for(int i=1;i<=50;i++)total+=weight(i,decay);double cursor=roll*total;for(int i=1;i<=50;i++){cursor-=weight(i,decay);if(cursor<0)return i;}return 50;}
    static long mobReward(int level,boolean strong){if(level<1||level>50)throw new IllegalArgumentException("Invalid monster level");return level<26?0:level==50?(strong?75:35):(strong?30:10);}
    static double healthFactor(int level,double bonus){if(level<1||level>50||!Double.isFinite(bonus)||bonus<0||bonus>1)throw new IllegalArgumentException("Invalid monster health scaling");return 1+(level-1)*bonus;}
    static long threshold(int level,int base,int growth){if(level<1||base<1||growth<0)throw new IllegalArgumentException("Invalid mining curve");long n=level-1L;return Math.addExact(Math.multiplyExact(n,base),Math.multiplyExact(n,n-1)/2*growth);}
    static int miningLevel(long xp,int maxLevel,int base,int growth){if(xp<0||maxLevel<2||maxLevel>10000)throw new IllegalArgumentException("Invalid mining progression");int lo=1,hi=maxLevel;while(lo<hi){int mid=(lo+hi+1)/2;if(threshold(mid,base,growth)<=xp)lo=mid;else hi=mid-1;}return lo;}
    static long stolen(long balance){if(balance<0)throw new IllegalArgumentException("Negative balance");return balance/100*15+balance%100*15/100;}
}
