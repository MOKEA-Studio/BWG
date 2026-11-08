package kr.blackwiki.smp;
final class EventRules {
    static long seconds(String hours,String minutes,String seconds){long h=Long.parseLong(hours),m=Long.parseLong(minutes),s=Long.parseLong(seconds);if(h<0||h>168||m<0||m>59||s<0||s>59)throw new IllegalArgumentException("시간 0~168, 분·초 0~59");long total=h*3600+m*60+s;if(total<1||total>604800)throw new IllegalArgumentException("이벤트는 1초~7일입니다.");return total;}
    static long reward(int index,boolean doubled){if(index<0||index>2)throw new IllegalArgumentException("Invalid reward index");return new long[]{40,80,100}[index]*(doubled?2:1);}
}
