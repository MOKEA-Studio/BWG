package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
final class HomeService implements Listener {
    private final BlackWikiSMP p;private final Map<UUID,Long> prompts=new ConcurrentHashMap<>();
    HomeService(BlackWikiSMP p){this.p=p;}
    boolean hasPrompt(UUID id){return prompts.getOrDefault(id,0L)>System.currentTimeMillis();}
    void ask(Player a){if(!p.travel.allowed(a))return;if(p.data.user(a).homes.size()>=5){p.tell(a,"홈은 최대 5개입니다.");return;}prompts.put(a.getUniqueId(),System.currentTimeMillis()+60000);a.closeInventory();p.tell(a,"60초 안에 채팅으로 새 홈 이름을 입력하세요. 취소: 취소");}
    void input(Player a,String value){Long until=prompts.remove(a.getUniqueId());if(until==null||until<System.currentTimeMillis()||value.strip().equals("취소"))return;try{set(a,value);}catch(IllegalArgumentException ex){p.tell(a,ex.getMessage());}}
    void set(Player a,String value){String name=HomeRules.name(value);if(!p.travel.allowed(a))return;if(a.getWorld()!=p.wildWorld||p.outside(a.getLocation())||p.resets.busy()){p.tell(a,"현재 약탈 야생 안에서만 홈을 저장할 수 있습니다.");return;}var u=p.data.user(a);if(u.homes.size()>=5&&!u.homes.containsKey(name)){p.tell(a,"홈은 최대 5개입니다.");return;}Location origin=a.getLocation().clone();p.travel.waitAction(a,5,()->{var current=p.data.user(a);if(current.homes.size()>=5&&!current.homes.containsKey(name)){p.tell(a,"홈 한도를 초과했습니다.");return;}if(origin.getWorld()!=p.wildWorld||p.resets.busy()){p.tell(a,"야생 변경으로 저장을 취소했습니다.");return;}current.homes.put(name,origin);current.home=current.homes.get("기본");p.data.changed();p.data.save(true);p.tell(a,"홈 '"+name+"' 저장 · "+current.homes.size()+"/5");});}
    void go(Player a,String value){if(!p.travel.allowed(a))return;Location target=p.data.user(a).homes.get(HomeRules.name(value));if(target==null){p.tell(a,"해당 홈이 없습니다. /home");return;}a.closeInventory();p.travel.go(a,target,5);}
    void remove(Player a,String value){if(!p.travel.allowed(a))return;String name=HomeRules.name(value);if(p.data.user(a).homes.remove(name)!=null){p.data.user(a).home=p.data.user(a).homes.get("기본");p.data.changed();p.data.save(true);p.tell(a,"홈을 삭제했습니다.");}}
    void open(Player a){if(!p.travel.allowed(a))return;Gui.Menu m=p.gui.make(a,"내 홈 · "+p.data.user(a).homes.size()+"/5");int[] slots={10,12,14,16,28};int index=0;for(var entry:p.data.user(a).homes.entrySet()){String name=entry.getKey();Location at=entry.getValue();p.gui.button(m,slots[index++],Material.RED_BED,name,b->detail(b,name),at.getWorld().getName()+" · "+at.getBlockX()+", "+at.getBlockY()+", "+at.getBlockZ(),"이동·갱신·삭제");}if(index<5)p.gui.button(m,31,Material.LODESTONE,"새 홈 저장",this::ask,"채팅으로 이름 입력 · 5초 대기");m.inventory.setItem(40,Gui.item(Material.CLOCK,"이동·저장 5초 대기","전투 중 이용 불가 · 움직이면 취소"));p.gui.show(a,m);}
    void detail(Player a,String name){if(!p.travel.allowed(a)||!p.data.user(a).homes.containsKey(name))return;Gui.Menu m=p.gui.make(a,"홈 · "+name);p.gui.button(m,20,Material.ENDER_PEARL,"이동",b->go(b,name));p.gui.button(m,24,Material.LODESTONE,"현재 위치로 갱신",b->p.gui.confirm(b,"홈 갱신",c->set(c,name),c->detail(c,name)));p.gui.button(m,40,Material.BARRIER,"삭제",b->p.gui.confirm(b,"홈 삭제",c->{remove(c,name);open(c);},c->detail(c,name)));p.gui.button(m,45,Material.ARROW,"홈 목록",this::open);p.gui.show(a,m);}
    void command(Player a,String[] args,boolean setting){if(setting){if(args.length>1)throw new IllegalArgumentException("/sethome [이름]");set(a,args.length==0?"기본":args[0]);return;}if(!p.travel.allowed(a))return;if(args.length==0){open(a);return;}switch(args[0].toLowerCase(Locale.ROOT)){case "이동","go"->go(a,args.length>1?args[1]:"기본");case "설정","set"->set(a,args.length>1?args[1]:"기본");case "삭제","delete"->{String name=args.length>1&&!args[1].equals("확인")?HomeRules.name(args[1]):"기본";if(args[args.length-1].equals("확인"))remove(a,name);else p.gui.confirm(a,"홈 삭제 · "+name,b->{remove(b,name);open(b);},this::open);}default->go(a,args[0]);}}
    @EventHandler public void quit(PlayerQuitEvent e){prompts.remove(e.getPlayer().getUniqueId());}
}
