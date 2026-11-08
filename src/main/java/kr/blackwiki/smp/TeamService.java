package kr.blackwiki.smp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

final class TeamService {
    record Invite(UUID team,long expires) {}
    private final BlackWikiSMP p;
    private final Map<UUID,UUID> membership=new HashMap<>();
    private final Map<UUID,Invite> invites=new HashMap<>();
    TeamService(BlackWikiSMP p){this.p=p;for(DataStore.Team t:p.data.teams.values())for(UUID u:t.members)membership.put(u,t.id);}
    DataStore.Team of(UUID id){return p.data.teams.get(membership.get(id));}
    DataStore.Team of(Player a){return of(a.getUniqueId());}
    boolean same(Player a,Player b){DataStore.Team t=of(a);return t!=null&&t==of(b);}
    boolean friendly(Player a,Player b){DataStore.Team t=of(a);return t!=null&&t==of(b)&&!t.pvp;}
    List<DataStore.Team> all(){return p.data.teams.values().stream().sorted(Comparator.comparing(t->t.name,String.CASE_INSENSITIVE_ORDER)).toList();}
    void create(Player a,String name) {
        if(of(a)!=null){p.tell(a,"이미 팀에 가입되어 있습니다.");return;}
        if(!Rules.validTeamName(name,p.cfg().getInt("teams.max-name-length",12))){p.tell(a,"팀 이름은 한글·영문·숫자·밑줄로 1~12글자까지 가능합니다.");return;}
        if(all().stream().anyMatch(t->t.name.equalsIgnoreCase(name))){p.tell(a,"이미 사용 중인 팀 이름입니다.");return;}
        DataStore.Team t=new DataStore.Team(UUID.randomUUID(),name,a.getUniqueId(),p.cfg().getBoolean("teams.default-friendly-fire"));
        p.data.teams.put(t.id,t);membership.put(a.getUniqueId(),t.id);p.data.changed();p.data.save(true);p.tell(a,"팀 ["+name+"]을 만들었습니다.");p.gui.team(a);
    }
    boolean leader(Player a,DataStore.Team t){if(t==null||!t.owner.equals(a.getUniqueId())){p.tell(a,"팀장만 사용할 수 있습니다.");return false;}return true;}
    void invite(Player a,Player target){
        DataStore.Team t=of(a);if(!leader(a,t))return;
        if(target==null||p.queue.waiting(target)){p.tell(a,"입장한 온라인 플레이어를 선택하세요.");return;}
        if(of(target)!=null){p.tell(a,"이미 다른 팀에 가입한 플레이어입니다.");return;}
        if(t.members.size()>=p.cfg().getInt("teams.max-members",20)){p.tell(a,"팀 인원이 가득 찼습니다.");return;}
        invites.put(target.getUniqueId(),new Invite(t.id,System.currentTimeMillis()+p.cfg().getInt("teams.invite-seconds",60)*1000L));
        p.tell(a,target.getName()+"님에게 초대했습니다.");p.tell(target,"["+t.name+"] 팀 초대가 도착했습니다. /팀 수락 또는 /팀 GUI에서 확인하세요.");
    }
    Invite invitation(Player a){Invite i=invites.get(a.getUniqueId());if(i!=null&&(i.expires<System.currentTimeMillis()||!p.data.teams.containsKey(i.team))){invites.remove(a.getUniqueId());return null;}return i;}
    void accept(Player a){
        Invite i=invitation(a);if(i==null){p.tell(a,"유효한 팀 초대가 없습니다.");return;}
        DataStore.Team t=p.data.teams.get(i.team);
        if(of(a)!=null){p.tell(a,"한 사람은 한 팀에만 가입할 수 있습니다.");return;}
        if(t.members.size()>=p.cfg().getInt("teams.max-members",20)){p.tell(a,"팀 인원이 가득 찼습니다.");return;}
        t.members.add(a.getUniqueId());membership.put(a.getUniqueId(),t.id);invites.remove(a.getUniqueId());changed(t,a.getName()+"님이 가입했습니다.");p.gui.team(a);
    }
    void deny(Player a){invites.remove(a.getUniqueId());p.tell(a,"팀 초대를 거절했습니다.");}
    void kick(Player a,UUID target){
        DataStore.Team t=of(a);if(!leader(a,t))return;
        if(t.owner.equals(target)||!t.members.contains(target)){p.tell(a,"이 플레이어는 추방할 수 없습니다.");return;}
        t.members.remove(target);membership.remove(target);changed(t,p.data.name(target)+"님을 추방했습니다.");Player b=Bukkit.getPlayer(target);if(b!=null)p.tell(b,"팀에서 추방되었습니다.");
    }
    void transfer(Player a,UUID target){DataStore.Team t=of(a);if(!leader(a,t))return;if(!t.members.contains(target)||target.equals(a.getUniqueId())){p.tell(a,"다른 팀원을 선택하세요.");return;}t.owner=target;changed(t,p.data.name(target)+"님에게 팀장을 위임했습니다.");}
    void pvp(Player a,Boolean value){DataStore.Team t=of(a);if(!leader(a,t))return;t.pvp=value==null?!t.pvp:value;changed(t,"팀원끼리 PvP: "+(t.pvp?"켜짐":"꺼짐"));}
    void leave(Player a){DataStore.Team t=of(a);if(t==null){p.tell(a,"가입된 팀이 없습니다.");return;}if(t.owner.equals(a.getUniqueId())){p.tell(a,"팀장은 팀장을 위임하거나 /팀 해체 확인 을 사용하세요.");return;}t.members.remove(a.getUniqueId());membership.remove(a.getUniqueId());changed(t,a.getName()+"님이 탈퇴했습니다.");p.tell(a,"팀을 탈퇴했습니다.");}
    void disband(Player a){DataStore.Team t=of(a);if(!leader(a,t))return;for(UUID u:t.members){membership.remove(u);Player v=Bukkit.getPlayer(u);if(v!=null)p.tell(v,"["+t.name+"] 팀이 해체되었습니다.");}p.data.teams.remove(t.id);invites.entrySet().removeIf(e->e.getValue().team.equals(t.id));p.data.changed();p.data.save(true);}
    void chat(Player a,String text){DataStore.Team t=of(a);if(t==null){p.tell(a,"가입된 팀이 없습니다.");return;}if(text.length()>256){p.tell(a,"메시지가 너무 깁니다.");return;}text=p.moderation.clean(a,text);if(text==null)return;for(UUID id:t.members){Player b=Bukkit.getPlayer(id);if(b!=null)p.tell(b,"[팀 "+t.name+"] "+a.getName()+": "+text);}}
    private void changed(DataStore.Team t,String message){p.data.changed();p.data.save(true);for(UUID u:t.members){Player v=Bukkit.getPlayer(u);if(v!=null)p.tell(v,message);}}
}
