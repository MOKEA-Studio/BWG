package kr.blackwiki.smp;

import java.time.*;
import java.util.*;

final class BanLedger {
    record Entry(UUID player,String name,String actor,String reason,long created,long expires){
        boolean active(long now){return expires>now;}
        long left(long now){return Math.max(0,(expires-now+999)/1000);}
    }
    private volatile Map<UUID,Entry> entries=Map.of();
    Map<UUID,Entry> snapshot(){return entries;}
    void replace(Map<UUID,Entry> value){entries=Map.copyOf(value);}
    Entry active(UUID id,long now){Entry e=entries.get(id);return e!=null&&e.active(now)?e:null;}
    static long seconds(String days,String hours,String minutes,String seconds,int maxDays){
        try{
            long d=Long.parseLong(days),h=Long.parseLong(hours),m=Long.parseLong(minutes),s=Long.parseLong(seconds);
            if(d<0||d>maxDays||h<0||h>23||m<0||m>59||s<0||s>59)throw new IllegalArgumentException("일 0~"+maxDays+", 시간 0~23, 분/초 0~59로 입력하세요.");
            long total=Math.addExact(Math.addExact(Math.multiplyExact(d,86400),Math.multiplyExact(h,3600)),Math.addExact(Math.multiplyExact(m,60),s));
            if(total<1||total>Math.multiplyExact((long)maxDays,86400))throw new IllegalArgumentException("차단 기간은 최소 1초, 최대 "+maxDays+"일입니다.");return total;
        }catch(NumberFormatException|ArithmeticException e){throw new IllegalArgumentException("차단 기간에는 범위 안의 정수만 입력하세요.");}
    }
    static Entry create(UUID id,String name,String actor,String reason,long seconds,Instant now){
        if(seconds<1)throw new IllegalArgumentException("차단 기간은 최소 1초입니다.");
        String clean=reason.replaceAll("[\\p{Cc}\\p{Cf}]"," ").strip();if(clean.isEmpty()||clean.codePointCount(0,clean.length())>256)throw new IllegalArgumentException("사유는 1~256글자로 입력하세요.");
        return new Entry(id,name,actor,clean,now.toEpochMilli(),now.plusSeconds(seconds).toEpochMilli());
    }
}
