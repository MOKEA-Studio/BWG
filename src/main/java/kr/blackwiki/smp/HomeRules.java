package kr.blackwiki.smp;
import java.util.Locale;
final class HomeRules {
    static final int MAX=5;
    static String name(String value){String name=value.strip().toLowerCase(Locale.ROOT);if(!name.matches("[\\p{L}\\p{N}_-]{1,16}"))throw new IllegalArgumentException("홈 이름은 문자·숫자·_·- 1~16자입니다.");return name;}
}
