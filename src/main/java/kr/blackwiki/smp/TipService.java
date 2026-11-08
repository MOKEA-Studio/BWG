package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.util.*;
final class TipService {
 static final List<String> MESSAGES=List.of("/기본템에서 돌도구 세트와 빵 10개를 계정당 한 번 받을 수 있습니다.", "스폰의 /상점 5에서 빵·고기·황금사과를 묶음으로 구매하세요.", "/sethome 이름으로 홈을 5개까지 저장합니다. 전투 중에는 /home을 쓸 수 없습니다.", "/팀에서 팀원·초대를 관리합니다. 팀장은 /팀 pvp on 또는 off로 팀 PvP를 바꿉니다.", "무덤은 10분 후 남은 아이템과 함께 사라집니다. /무덤으로 좌표를 확인하고 현장에서 회수하세요.", "총기는 야생에서만 발사·장전됩니다. Q로 재장전하고 전용 탄환을 준비하세요.", "/랭킹에서 플레이시간·플레이어 킬·코인 TOP 10을 확인하세요. 야생에서는 /메뉴 대신 개별 명령어를 사용합니다.", "/칭호에서 무료·구매 칭호 7종과 킬 칭호 6종을 선택합니다.", "칭호는 한 번 구매하면 영구 보유하며 재장착은 무료입니다.", "500킬에는 1,500코인, 1,000킬에는 5,000코인을 각 한 번 지급합니다.", "저격총과 샷건은 같은 정품 12게이지 산탄을 사용합니다. 일반 화약은 사용할 수 없습니다.", "스폰 발광석 가로등의 설치 상태는 OP의 /스폰조명에서 확인합니다.", "OP는 /현실시간 켜기 또는 끄기로 한국 시간 낮밤을 조절합니다.", "코인은 일반 구역 2초당 1개, AFK 0.5초당 1개입니다. 채굴도 코인을 주며 /채광 보상에서 확인하세요. 대기열·오프라인은 제외됩니다.", "보관함은 무료 1줄, 5,000코인으로 3줄, 다음 15,000코인으로 전체를 엽니다.", "플레이어 처치 보상은 40·80·100코인 중 하나입니다. 같은 상대 반복 킬은 제한됩니다.", "팀장만 팀 PvP·추방·위임을 관리합니다. 팀 이름은 최대 12글자입니다.", "경계는 40,000×40,000이며 밖으로 나가면 사망합니다. /경계로 확인하세요.", "전투 중 접속을 종료하면 사망합니다. 전투가 끝난 뒤 안전하게 이동하세요.", "스폰에서 /유저상점을 열고 손에 든 아이템의 가격을 정해 판매하세요.", "금지어 우회나 스폰 파괴를 하지 마세요. 문제 발생 시 OP에게 /플러그인상태 확인을 요청하세요.");
 private final BlackWikiSMP p;private long broadcasts;private int last=-1;private final TipCycle cycle=new TipCycle(System.nanoTime(),new Random());
 TipService(BlackWikiSMP p){this.p=p;}
 void tick(){tickAt(System.nanoTime());}
 void tickAt(long now){if(!p.cfg().getBoolean("help-tips.enabled",true))return;int tip=cycle.poll(now);if(tip<0)return;last=tip;broadcasts++;for(Player a:Bukkit.getOnlinePlayers())p.tell(a,"[도움말] "+MESSAGES.get(tip));}
 String status(){return (p.cfg().getBoolean("help-tips.enabled",true)?"켜짐":"꺼짐")+" · 5분 · 21가지 · 전송 "+broadcasts+"회";}
 void open(Player a){Gui.Menu m=p.gui.make(a,"서버 도움말 · 21가지");for(int i=0;i<MESSAGES.size();i++)m.inventory.setItem(19+i,Gui.item(Material.BOOK,"도움말 "+(i+1),MESSAGES.get(i)));if(BanService.operator(a))p.gui.button(m,40,Material.LEVER,"OP · 자동 도움말: "+Gui.yes(p.cfg().getBoolean("help-tips.enabled",true)),b->{if(!BanService.operator(b))return;p.cfg().set("help-tips.enabled",!p.cfg().getBoolean("help-tips.enabled",true));p.saveConfig();open(b);},"5분마다 21가지 중 하나를 채팅으로 안내");p.gui.show(a,m);}
}
