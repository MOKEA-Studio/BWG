package kr.blackwiki.smp;

import java.text.Normalizer;
import java.util.*;

/** Immutable, thread-safe filter. Index mapping preserves text outside masked spans. */
final class ChatFilter {
    record Result(String text,boolean changed) {}
    private final List<String> blocked,allowed;
    ChatFilter(Collection<String> blocked,Collection<String> allowed){
        this.blocked=terms(blocked);List<String> permit=new ArrayList<>(allowed);
        permit.add("미친");permit.add("존나");this.allowed=terms(permit);
    }
    int size(){return blocked.size();}
    private static List<String> terms(Collection<String> raw){
        if(raw.size()>500)throw new IllegalArgumentException("욕설/허용 목록은 각각 500개까지입니다.");
        return raw.stream().filter(Objects::nonNull).map(String::strip).peek(s->{if(s.length()>64)throw new IllegalArgumentException("필터 단어는 64글자 이하로 설정하세요.");}).map(s->normal(s).text).filter(s->!s.isEmpty()).distinct().toList();
    }
    record Normal(String text,int[] start,int[] end) {}
    static Normal normal(String input){
        StringBuilder text=new StringBuilder();List<Integer> start=new ArrayList<>(),end=new ArrayList<>();
        for(int at=0;at<input.length();){int cp=input.codePointAt(at),next=at+Character.charCount(cp);
            if(cp=='§'&&next<input.length()&&"0123456789abcdefklmnorx".indexOf(Character.toLowerCase(input.charAt(next)))>=0){at=next+1;continue;}
            String normalized=Normalizer.normalize(new String(Character.toChars(cp)),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
            for(int i=0;i<normalized.length();){int n=normalized.codePointAt(i);i+=Character.charCount(n);if(!Character.isLetterOrDigit(n))continue;
                text.appendCodePoint(n);for(int j=0;j<Character.charCount(n);j++){start.add(at);end.add(next);}
            }at=next;
        }
        return new Normal(text.toString(),start.stream().mapToInt(Integer::intValue).toArray(),end.stream().mapToInt(Integer::intValue).toArray());
    }
    Result apply(String original){
        Normal n=normal(original);boolean[] permitted=new boolean[n.text.length()],safeOriginal=new boolean[original.length()],masked=new boolean[original.length()];
        for(String word:allowed)for(int at=n.text.indexOf(word);at>=0;at=n.text.indexOf(word,at+1)){
            Arrays.fill(permitted,at,at+word.length(),true);Arrays.fill(safeOriginal,n.start[at],n.end[at+word.length()-1],true);
        }
        boolean changed=false;
        for(String word:blocked)for(int at=n.text.indexOf(word);at>=0;at=n.text.indexOf(word,at+1)){
            int to=at+word.length();boolean latin=word.chars().allMatch(c->c>='a'&&c<='z');
            int left=n.start[at],right=n.end[to-1];
            if(latin&&((left>0&&asciiLetter(Character.toLowerCase(original.charAt(left-1))))||(right<original.length()&&asciiLetter(Character.toLowerCase(original.charAt(right))))))continue;
            int first=-1,last=-1;for(int i=at;i<to;i++)if(!permitted[i]){if(first<0)first=i;last=i;}
            if(first<0)continue;changed=true;for(int i=n.start[first];i<n.end[last];i++)if(!safeOriginal[i])masked[i]=true;
        }
        if(!changed)return new Result(original,false);
        StringBuilder result=new StringBuilder();for(int at=0;at<original.length();){int cp=original.codePointAt(at);boolean hide=false;for(int i=0;i<Character.charCount(cp);i++)hide|=masked[at+i];if(hide)result.append('*');else result.appendCodePoint(cp);at+=Character.charCount(cp);}
        return new Result(result.toString(),true);
    }
    private static boolean asciiLetter(char c){return c>='a'&&c<='z';}
}
