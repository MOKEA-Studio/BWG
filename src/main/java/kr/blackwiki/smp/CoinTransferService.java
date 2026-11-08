package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.logging.Level;

/** Two-player debit/credit journal. A native receipt makes each side idempotent. */
final class CoinTransferService implements Listener {
    private final BlackWikiSMP p;final TransferLedger ledger;private final NamespacedKey key;private boolean fault;
    CoinTransferService(BlackWikiSMP p)throws Exception{this.p=p;key=new NamespacedKey(p,"coin-transfer-receipts");ledger=new TransferLedger(p.getDataFolder().toPath().resolve("transfers.yml"));}
    private Set<String> receipts(Player a){String value=a.getPersistentDataContainer().getOrDefault(key,PersistentDataType.STRING,"");return new LinkedHashSet<>(value.isEmpty()?List.of():Arrays.asList(value.split("\n")));}
    private boolean has(Player a,String side,UUID id){return receipts(a).contains(side+"/"+id);}
    private void mark(Player a,String side,UUID id){Set<String> set=receipts(a);set.add(side+"/"+id);if(set.size()>1000)throw new IllegalStateException("Too many unsettled coin transfers");a.getPersistentDataContainer().set(key,PersistentDataType.STRING,String.join("\n",set));}
    private void clean(Player a){Set<String> set=receipts(a);if(set.removeIf(s->{try{var t=ledger.get(UUID.fromString(s.split("/",2)[1]));return t==null||t.state()==TransferLedger.State.COMMITTED||t.state()==TransferLedger.State.CANCELLED;}catch(Exception ex){return false;}})){a.getPersistentDataContainer().set(key,PersistentDataType.STRING,String.join("\n",set));p.vaults.saveAccount(a);}}
    long steal(Player receiver,Player payer){if(fault||!p.vaults.healthy(receiver)||!p.vaults.healthy(payer))return 0;long amount=ProgressRules.stolen(p.data.user(payer).coins);if(amount==0)return 0;try{ledger.put(new TransferLedger.Transfer(UUID.randomUUID(),payer.getUniqueId(),receiver.getUniqueId(),amount,TransferLedger.State.PREPARED));tick();if(p.backups.running())p.tell(receiver,"코인 탈취 "+VaultService.money(amount)+"코인을 백업 완료 후 정산합니다.");return amount;}catch(Exception ex){fail(ex);return 0;}}
    @EventHandler(priority=EventPriority.LOW) public void join(PlayerJoinEvent e){tick();}
    int pending(){return (int)ledger.all().stream().filter(t->t.state()==TransferLedger.State.PREPARED||t.state()==TransferLedger.State.DEBITED).count();}
    String status(){return fault?"저장 오류 · 탈취 정산 잠금":"미완료 "+pending()+"건";}
    void tick(){if(fault||p.backups!=null&&p.backups.running())return;try{
        for(var original:ledger.all()){var t=original;Player payer=Bukkit.getPlayer(t.payer()),receiver=Bukkit.getPlayer(t.receiver());
            if(t.state()==TransferLedger.State.PREPARED&&payer!=null&&p.vaults.healthy(payer)){
                if(!has(payer,"debit",t.id())){long actual=Math.min(t.amount(),p.data.user(payer).coins);if(actual==0){ledger.put(new TransferLedger.Transfer(t.id(),t.payer(),t.receiver(),t.amount(),TransferLedger.State.CANCELLED));continue;}if(actual!=t.amount()){t=new TransferLedger.Transfer(t.id(),t.payer(),t.receiver(),actual,t.state());ledger.put(t);}mark(payer,"debit",t.id());p.data.user(payer).coins-=t.amount();p.vaults.saveAccount(payer);p.tell(payer,"악마의 검에 의해 "+VaultService.money(t.amount())+"코인을 빼앗겼습니다.");}
                t=new TransferLedger.Transfer(t.id(),t.payer(),t.receiver(),t.amount(),TransferLedger.State.DEBITED);ledger.put(t);
            }
            if(t.state()==TransferLedger.State.DEBITED&&receiver!=null&&p.vaults.healthy(receiver)){
                if(!has(receiver,"credit",t.id())){var u=p.data.user(receiver);if(u.coins>Long.MAX_VALUE-t.amount())continue;mark(receiver,"credit",t.id());u.coins+=t.amount();p.vaults.saveAccount(receiver);p.tell(receiver,"코인의 기증 +"+VaultService.money(t.amount())+"코인");}
                ledger.put(new TransferLedger.Transfer(t.id(),t.payer(),t.receiver(),t.amount(),TransferLedger.State.COMMITTED));
            }
        }
        for(Player a:Bukkit.getOnlinePlayers())if(p.vaults.healthy(a))clean(a);ledger.compact();
    }catch(Exception ex){fail(ex);}}
    private void fail(Exception ex){fault=true;p.getLogger().log(Level.SEVERE,"Coin transfer journal failure: preserve transfers.yml and native player data; reconcile after restart",ex);}
}
