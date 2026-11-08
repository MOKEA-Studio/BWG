package kr.blackwiki.smp;

/** Whole real-time seconds: preserve the odd second across reconnects. */
final class CoinClock {
    record Result(long coins,int progress){}
    static Result advance(int progress,long seconds){
        if(progress<0||progress>1||seconds<0)throw new IllegalArgumentException("Invalid coin clock");
        long coins=seconds/2;int remainder=(int)(seconds%2)+progress;
        return new Result(coins+remainder/2,remainder%2);
    }
    static long add(long balance,long amount){if(balance<0||amount<0)throw new IllegalArgumentException("Negative balance");return balance>Long.MAX_VALUE-amount?Long.MAX_VALUE:balance+amount;}
}
