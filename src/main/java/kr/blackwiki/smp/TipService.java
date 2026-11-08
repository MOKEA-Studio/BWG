package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.util.*;
final class TipService {
 static final List<String> MESSAGES=List.of("/기본템에서 돌도구 세트와 빵 10개를 계정당 한 번 받을 수 있습니다.","스폰의 /상점 5에서 빵·고기·황금사과를 묶음으로 구매하세요.","/sethome 이름으로 홈을 5개까지 저장합니다. 전투 중에는 /home을 쓸 수 없습니다.","/팀에서 팀원·초대를 관리합니다. 팀장은 /팀 pvp on 또는 off로 팀 PvP를 바꿉니다.","무덤은 10분 후 남은 아이템과 함께 사라집니다. /무덤으로 좌표를 확인하고 현장에서 회수하세요.","총기는 야생에서만 발사·장전됩니다. Q로 재장전하고 전용 탄환을 준비하세요.","/랭킹에서 플레이시간·플레이어 킬·코인 TOP 10을 확인하세요. 야생에서는 /메뉴 대신 개별 명령어를 사용합니다.");
 private final BlackWikiSMP p;private long broadcasts;private int last=-1;private final TipCycle cycle=new TipCycle(System.nanoTime(),new Random());
 TipService(BlackWikiSMP p){this.p=p;}
 void tick(){tickAt(System.nanoTime());}
 void tickAt(long now){if(!p.cfg().getBoolean("help-tips.enabled",true))return;int tip=cycle.poll(now);if(tip<0)return;last=tip;broadcasts++;for(Player a:Bukkit.getOnlinePlayers())p.tell(a,"[도움말] "+MESSAGES.get(tip));}
 String status(){return (p.cfg().getBoolean("help-tips.enabled",true)?"켜짐":"꺼짐")+" · 5분 · 7가지 · 전송 "+broadcasts+"회";}
 void open(Player a){Gui.Menu m=p.gui.make(a,"서버 도움말 · 7가지");for(int i=0;i<MESSAGES.size();i++)m.inventory.setItem(19+i,Gui.item(Material.BOOK,"도움말 "+(i+1),MESSAGES.get(i)));if(BanService.operator(a))p.gui.button(m,40,Material.LEVER,"OP · 자동 도움말: "+Gui.yes(p.cfg().getBoolean("help-tips.enabled",true)),b->{if(!BanService.operator(b))return;p.cfg().set("help-tips.enabled",!p.cfg().getBoolean("help-tips.enabled",true));p.saveConfig();open(b);},"5분마다 7가지 중 하나를 채팅으로 안내");p.gui.show(a,m);}
}
