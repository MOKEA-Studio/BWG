package kr.blackwiki.smp;

import java.security.SecureRandom;
import java.util.*;

/** Fixed administrative actions only: never evaluates Discord content as a console/shell command. */
final class PowerCommands {
    record Policy(boolean enabled,String guild,String channel,Set<String> users,Set<String> roles,int confirmationSeconds){
        Policy{users=Set.copyOf(users);roles=Set.copyOf(roles);if(confirmationSeconds<10||confirmationSeconds>120)throw new IllegalArgumentException("Discord 종료 확인 시간은 10~120초입니다.");}
        boolean configured(){return enabled&&DiscordInbound.id(guild)&&DiscordInbound.id(channel)&&(!users.isEmpty()||!roles.isEmpty());}
        boolean allowed(DiscordInbound.Message m){return configured()&&m.guild().equals(guild)&&m.channel().equals(channel)&&(users.contains(m.author())||m.roles().stream().anyMatch(roles::contains));}
    }
    interface Actions{String status();String start();String stop();}
    private record Challenge(String nonce,long until){}
    private final Policy policy;
    private final Map<String,Challenge> challenges=new HashMap<>();
    private final SecureRandom random=new SecureRandom();
    PowerCommands(Policy policy){this.policy=policy;}
    static boolean command(String content){String root=content.split("\\s+",2)[0];return Set.of("!서버켜기","!서버끄기","!서버상태","!서버도움말").contains(root);}
    synchronized String handle(DiscordInbound.Message m,long now,Actions actions){
        if(!command(m.content()))return null;
        if(!policy.allowed(m))return "서버 관리 명령 권한이 없습니다. 관리 채널·서버 ID·허용 사용자/역할을 확인하세요.";
        challenges.entrySet().removeIf(e->e.getValue().until<=now);
        String[] parts=m.content().split("\\s+");return switch(parts[0]){
            case "!서버상태"->actions.status();
            case "!서버켜기"->parts.length==1?actions.start():"사용법: !서버켜기";
            case "!서버끄기"->{
                if(parts.length==1){String nonce=String.format(Locale.ROOT,"%08d",random.nextInt(100_000_000));challenges.put(m.author(),new Challenge(nonce,now+policy.confirmationSeconds*1000L));yield policy.confirmationSeconds+"초 안에 같은 계정으로 `!서버끄기 확인 "+nonce+"` 입력 시 저장 후 서버를 종료합니다.";}
                Challenge challenge=challenges.get(m.author());if(parts.length!=3||!parts[1].equals("확인")||challenge==null||challenge.until<=now||!challenge.nonce.equals(parts[2]))yield "종료 확인이 없거나 만료되었습니다. !서버끄기 로 다시 요청하세요.";
                challenges.remove(m.author());yield actions.stop();
            }
            default->"!서버상태 · !서버켜기 · !서버끄기 (관리자 확인 후 정상 저장 종료)";
        };
    }
}
