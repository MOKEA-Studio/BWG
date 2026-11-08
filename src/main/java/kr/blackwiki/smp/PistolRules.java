package kr.blackwiki.smp;

/** A magazine is a firing allowance; every shot and every completed reload also consumes reserve ammo. */
final class PistolRules {
    static final int CAPACITY=6,RELOAD_TICKS=40,FIRE_TICKS=10,LIFETIME_TICKS=60,MAX_PROJECTILES=2048;
    static final double DAMAGE=6,SPEED=4,RANGE=80;
    record Plan(int loaded,int reserve){}
    private PistolRules(){}
    private static void check(int loaded,int reserve){if(loaded<0||loaded>CAPACITY||reserve<0)throw new IllegalArgumentException("Invalid pistol state");}
    static Plan shot(int loaded,int reserve){check(loaded,reserve);if(loaded==0||reserve==0)throw new IllegalStateException("탄창 또는 전용 탄환이 비어 있습니다.");return new Plan(loaded-1,reserve-1);}
    static Plan reload(int loaded,int reserve){check(loaded,reserve);if(loaded==CAPACITY)throw new IllegalStateException("이미 6발이 장전되어 있습니다.");if(reserve==0)throw new IllegalStateException("재장전용 전용 탄환 1개가 필요합니다.");return new Plan(CAPACITY,reserve-1);}
}
