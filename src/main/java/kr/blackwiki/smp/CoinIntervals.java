package kr.blackwiki.smp;


final class CoinIntervals {
    static final long NORMAL = 2000000000L;
    static final long AFK = 500000000L;

    private CoinIntervals() {
    }

    static long validate(long remainder, long interval) {
        if (interval <= 0L || remainder < 0L || remainder >= interval) {
            throw new IllegalArgumentException("Invalid coin interval remainder");
        }
        return remainder;
    }

    static Result advance(long remainder, long elapsed, long interval) {
        CoinIntervals.validate(remainder, interval);
        if (elapsed < 0L) {
            throw new IllegalArgumentException("Negative elapsed time");
        }
        long small = elapsed % interval + remainder;
        return new Result(CoinClock.add(elapsed / interval, small / interval), small % interval);
    }

    record Result(long coins, long remainder) {
    }
}

