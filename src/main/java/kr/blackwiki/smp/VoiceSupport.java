package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
final class VoiceSupport {
    interface Backend {String status(Player a);boolean ready();void team(Player a);void leave(Player a);void tick();void close();}
    final BlackWikiSMP p;private Backend backend;private final NamespacedKey mutedKey;private volatile Set<UUID> silenced=Set.of();private volatile boolean closed;
    VoiceSupport(BlackWikiSMP p){this.p=p;mutedKey=new NamespacedKey(p,"voice-self-muted");if(Bukkit.getPluginManager().isPluginEnabled("voicechat"))try{backend=VoiceAddon.connect(this);}catch(LinkageError|RuntimeException ex){p.getLogger().log(java.util.logging.Level.WARNING,"Voice integration unavailable",ex);}}
    boolean blocked(UUID id){return closed?false:silenced.contains(id);}
    boolean ready(){return backend!=null&&backend.ready();}
    String status(){return backend==null?"음성 서버 미설치 · Simple Voice Chat 2.6.25 필요":ready()?"Simple Voice Chat 연동 중":"음성 서버 시작 대기";}
    boolean muted(Player a){return a.getPersistentDataContainer().getOrDefault(mutedKey,PersistentDataType.BYTE,(byte)0)==1;}
    void toggle(Player a){a.getPersistentDataContainer().set(mutedKey,PersistentDataType.BYTE,(byte)(muted(a)?0:1));a.saveData();tick();open(a);}
    void tick(){Set<UUID> next=new HashSet<>();for(Player a:Bukkit.getOnlinePlayers())if(!p.cfg().getBoolean("voice.enabled",true)||p.queue.waiting(a)||muted(a))next.add(a.getUniqueId());silenced=Set.copyOf(next);if(backend!=null)backend.tick();}
    void team(Player a){if(p.queue.waiting(a)||!p.cfg().getBoolean("voice.enabled",true))return;if(backend==null){p.tell(a,status());return;}backend.team(a);}
    void leave(Player a){if(backend!=null)backend.leave(a);}
    void command(Player a,String[] args){if(args.length==0){open(a);return;}switch(args[0]){case "팀"->team(a);case "나가기"->leave(a);case "음소거"->{if(!muted(a))toggle(a);}case "해제"->{if(muted(a))toggle(a);}default->open(a);}}
    void open(Player a){if(p.queue.waiting(a))return;Gui.Menu m=p.gui.make(a,"한글 음성 채팅");m.inventory.setItem(13,Gui.item(Material.NOTE_BLOCK,"연결 상태",status(),backend==null?"서버 공식 플러그인 2.6.25 설치 필요":backend.status(a),"실제 마이크 사용에는 클라이언트 모드 필요"));p.gui.button(m,20,Material.LEVER,"내 마이크 전송: "+(muted(a)?"음소거":"허용"),this::toggle,"서버에서 내 음성 패킷 전송을 차단/허용","듣기 음량은 클라이언트 V 설정");p.gui.button(m,22,Material.WHITE_BANNER,"내 팀 음성 그룹",this::team,"현재 소속 팀의 비공개 그룹으로 이동","팀 탈퇴·이적 시 자동으로 그룹에서 나갑니다.");p.gui.button(m,24,Material.OAK_DOOR,"그룹 나가기 · 근접 대화",this::leave);m.inventory.setItem(31,Gui.item(Material.BOOK,"설치·한글 사용 안내","서버: 공식 voicechat-bukkit-2.6.25.jar","클라이언트: 본인 버전의 Simple Voice Chat 모드","게임 언어 한국어 · V 설정 / M 마이크 음소거","호스팅 방화벽: UDP 24454 열기","가까운 사람과 근접 대화 · 팀 그룹은 원격 대화"));p.gui.show(a,m);}
    void close(){closed=true;if(backend!=null)backend.close();silenced=Set.of();}
}
