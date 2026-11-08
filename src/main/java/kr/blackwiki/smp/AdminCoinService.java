package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;

/** OP-only adjustments publish through the same native account store as shop purchases. */
final class AdminCoinService implements Listener {
    private final BlackWikiSMP p;
    private final Map<UUID,UUID> confirmations=new HashMap<>();
    AdminCoinService(BlackWikiSMP p){this.p=p;}
    private boolean permitted(CommandSender sender){if(BanService.operator(sender))return true;p.tell(sender,"OP만 코인을 지급하거나 차감할 수 있습니다.");return false;}
    void command(CommandSender sender,String[] args,boolean debit){
        if(!permitted(sender))return;if(args.length==0&&sender instanceof Player a){targets(a,0);return;}
        if(args.length!=2){p.tell(sender,(debit?"/코인차감":"/코인지급")+" [닉네임] [갯수]");return;}
        long amount=AdminCoinRules.amount(args[1]);Player target=Bukkit.getPlayerExact(args[0]);if(target==null){p.tell(sender,"접속 중인 플레이어를 찾을 수 없습니다. 닉네임을 정확히 입력하세요.");return;}apply(sender,target.getUniqueId(),amount,debit);
    }
    boolean apply(CommandSender sender,UUID id,long amount,boolean debit){
        if(!permitted(sender))return false;Player target=Bukkit.getPlayer(id);if(target==null||!target.isOnline()){p.tell(sender,"대상 플레이어가 접속을 종료했습니다. 코인을 변경하지 않았습니다.");return false;}
        if(p.backups.running()){p.tell(sender,"백업 완료 후 코인을 변경하세요.");return false;}if(!p.vaults.healthy(target)){p.tell(sender,"대상 계정의 저장 상태를 먼저 복구하세요.");return false;}
        DataStore.User user=p.data.user(target);long before=user.coins,next;try{next=AdminCoinRules.balance(before,amount,debit);}catch(IllegalArgumentException ex){p.tell(sender,ex.getMessage());return false;}
        try{user.coins=next;p.vaults.saveAccount(target);}catch(Exception ex){user.coins=before;p.vaults.syncCurrency(target,user);p.getLogger().log(java.util.logging.Level.SEVERE,"Admin coin adjustment failed for "+id,ex);p.tell(sender,"저장 실패로 코인 변경을 취소했습니다.");return false;}
        String action=debit?"차감":"지급";p.tell(sender,target.getName()+"에게 "+VaultService.money(amount)+"코인 "+action+" 완료 · 잔액 "+VaultService.money(next));if(target!=sender)p.tell(target,"관리자가 "+VaultService.money(amount)+"코인을 "+action+"했습니다. 현재 "+VaultService.money(next)+"코인");p.getLogger().info("Admin coins "+action+": actor="+sender.getName()+", target="+target.getName()+" ("+id+"), amount="+amount+", before="+before+", after="+next);return true;
    }
    void targets(Player a,int page){
        if(!permitted(a))return;List<? extends Player> players=Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).toList();int max=Math.max(0,(players.size()-1)/36),current=Math.max(0,Math.min(page,max));Gui.Menu m=p.gui.make(a,"관리자 코인 · 대상 선택 "+(current+1));
        for(int i=current*36;i<Math.min(players.size(),(current+1)*36);i++){Player target=players.get(i);UUID id=target.getUniqueId();p.gui.button(m,9+i%36,Material.PLAYER_HEAD,target.getName(),b->edit(b,id,1000,false),"보유 "+VaultService.money(p.data.user(target).coins)+"코인","지급·차감 수량 선택 후 확인");}
        if(current>0)p.gui.button(m,45,Material.ARROW,"이전",b->targets(b,current-1));if(current<max)p.gui.button(m,53,Material.ARROW,"다음",b->targets(b,current+1));p.gui.show(a,m);
    }
    private void edit(Player a,UUID id,long amount,boolean debit){
        if(!permitted(a))return;Player target=Bukkit.getPlayer(id);if(target==null){p.tell(a,"대상이 접속을 종료했습니다.");targets(a,0);return;}Gui.Menu m=p.gui.make(a,"관리자 코인 · "+target.getName());m.inventory.setItem(13,Gui.item(Material.GOLD_INGOT,(debit?"차감":"지급")+" "+VaultService.money(amount)+"코인","현재 잔액 "+VaultService.money(p.data.user(target).coins),"임의 수량: /코인지급 또는 /코인차감 닉네임 수량"));p.gui.button(m,20,Material.LEVER,debit?"차감 모드 · 클릭하여 지급":"지급 모드 · 클릭하여 차감",b->edit(b,id,amount,!debit));long[] values={1,10,100,1000,10000,100000};for(int i=0;i<values.length;i++){long n=values[i];p.gui.button(m,28+i,Material.PAPER,VaultService.money(n)+"코인 선택",b->edit(b,id,n,debit));}
        p.gui.button(m,24,Material.LIME_CONCRETE,"확인 화면으로",b->confirm(b,id,amount,debit));p.gui.button(m,45,Material.ARROW,"대상 목록",b->targets(b,0));p.gui.show(a,m);
    }
    private void confirm(Player a,UUID id,long amount,boolean debit){if(!permitted(a))return;UUID token=UUID.randomUUID();confirmations.put(a.getUniqueId(),token);p.gui.confirm(a,(debit?"차감":"지급")+" 확인 · "+VaultService.money(amount),b->{if(!token.equals(confirmations.remove(b.getUniqueId())))return;apply(b,id,amount,debit);targets(b,0);},b->{confirmations.remove(b.getUniqueId());edit(b,id,amount,debit);});}
    @EventHandler public void quit(PlayerQuitEvent e){confirmations.remove(e.getPlayer().getUniqueId());}
}
