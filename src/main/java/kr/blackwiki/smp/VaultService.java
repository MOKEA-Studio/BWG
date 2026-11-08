package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Read-only preview GUI; clicks commit item transfers with the native player inventory.
 * The vault, wallet and tier are stored in the SAME player .dat as the native inventory.
 * YAML is a ranking/backup mirror. The player's PDC is authoritative when reconnecting. */
final class VaultService implements Listener {
    private final BlackWikiSMP p;
    private final NamespacedKey coinsKey,tierKey,itemsKey,progressKey,miningKey;
    private final Set<UUID> blocked=new HashSet<>();
    VaultService(BlackWikiSMP p){this.p=p;coinsKey=new NamespacedKey(p,"coins");tierKey=new NamespacedKey(p,"vault-tier");itemsKey=new NamespacedKey(p,"vault-items");progressKey=new NamespacedKey(p,"coin-progress");miningKey=new NamespacedKey(p,"mining-xp");}
    static int slots(int tier){return switch(tier){case 0->9;case 1->27;case 2->54;default->throw new IllegalArgumentException("Invalid vault tier");};}
    static long price(int tier){return switch(tier){case 0->5000;case 1->15000;default->0;};}
    static String money(long value){return String.format(Locale.ROOT,"%,d",value);}
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent e){load(e.getPlayer());}
    void load(Player a){
        DataStore.User u=p.data.user(a);p.newbies.load(a,u);var pd=a.getPersistentDataContainer();
        try{
            Long coin=pd.get(coinsKey,PersistentDataType.LONG);Integer tier=pd.get(tierKey,PersistentDataType.INTEGER);String items=pd.get(itemsKey,PersistentDataType.STRING);
            if(coin!=null||tier!=null||items!=null){
                if(coin==null||coin<0||tier==null||tier<0||tier>2||items==null)throw new IllegalStateException("Incomplete vault metadata");
                String[] list=items.split("\n",-1);if(list.length!=54)throw new IllegalStateException("Invalid vault size");
                for(int i=0;i<54;i++){if(!list[i].isEmpty()){decode(list[i]);if(i>=slots(tier))throw new IllegalStateException("Item in a locked slot");}}
                Long mining=pd.get(miningKey,PersistentDataType.LONG);if(mining!=null&&mining<0)throw new IllegalStateException("Invalid mining XP");u.miningXp=mining==null?0:mining;u.coins=coin;Integer progress=pd.get(progressKey,PersistentDataType.INTEGER);if(progress!=null&&(progress<0||progress>1))throw new IllegalStateException("Invalid coin progress");u.coinSeconds=progress==null?0:progress;u.vaultTier=tier;for(int i=0;i<54;i++)u.vault.set(i,list[i]);
            }else writePdc(a,u);
            blocked.remove(a.getUniqueId());p.data.changed();
        }catch(Exception ex){blocked.add(a.getUniqueId());p.tell(a,"보관함 데이터 검사에 실패했습니다. 관리자에게 문의하세요. 원본은 덮어쓰지 않습니다.");p.getLogger().log(java.util.logging.Level.SEVERE,"Vault load failed for "+a.getUniqueId(),ex);}
    }
    void syncCurrency(Player a,DataStore.User u){p.newbies.sync(a,u);if(!blocked.contains(a.getUniqueId())){a.getPersistentDataContainer().set(coinsKey,PersistentDataType.LONG,u.coins);a.getPersistentDataContainer().set(progressKey,PersistentDataType.INTEGER,u.coinSeconds);a.getPersistentDataContainer().set(miningKey,PersistentDataType.LONG,u.miningXp);}if(p.hud!=null)p.hud.refresh(a);}
    boolean healthy(Player a){if(blocked.contains(a.getUniqueId())){p.tell(a,"계정 데이터 검사 실패로 거래할 수 없습니다.");return false;}return true;}
    void saveAccount(Player a){if(!healthy(a))throw new IllegalStateException("Account blocked");writePdc(a,p.data.user(a));a.saveData();p.data.changed();}
    private void writePdc(Player a,DataStore.User u){p.newbies.sync(a,u);var pd=a.getPersistentDataContainer();pd.set(coinsKey,PersistentDataType.LONG,u.coins);pd.set(progressKey,PersistentDataType.INTEGER,u.coinSeconds);pd.set(miningKey,PersistentDataType.LONG,u.miningXp);pd.set(tierKey,PersistentDataType.INTEGER,u.vaultTier);pd.set(itemsKey,PersistentDataType.STRING,String.join("\n",u.vault));if(p.hud!=null)p.hud.refresh(a);}
    private boolean allowed(Player a){if(p.backups!=null&&p.backups.running()){p.tell(a,"백업 완료 후 보관함을 이용하세요.");return false;}if(blocked.contains(a.getUniqueId())){p.tell(a,"보관함 데이터를 복구한 뒤 이용할 수 있습니다. 관리자에게 문의하세요.");return false;}return p.travel.allowed(a);}
    void open(Player a){if(allowed(a))p.gui.vault(a);}
    static String encode(ItemStack item){return item==null||item.getType().isAir()?"":Base64.getEncoder().encodeToString(item.serializeAsBytes());}
    static ItemStack decode(String text){return text==null||text.isEmpty()?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(text));}
    private List<ItemStack> items(DataStore.User u){List<ItemStack> list=new ArrayList<>(54);for(String s:u.vault)list.add(decode(s));return list;}
    void deposit(Player a,int inventorySlot){
        if(!allowed(a)||inventorySlot<0||inventorySlot>=36)return;
        ItemStack original=a.getInventory().getItem(inventorySlot);if(original==null||original.getType().isAir())return;
        DataStore.User u=p.data.user(a);List<ItemStack> next=items(u);int left=original.getAmount();int limit=slots(u.vaultTier);
        for(int i=0;i<limit&&left>0;i++){ItemStack existing=next.get(i);if(existing!=null&&existing.isSimilar(original)){int add=Math.min(left,Math.max(0,existing.getMaxStackSize()-existing.getAmount()));existing.setAmount(existing.getAmount()+add);left-=add;}}
        for(int i=0;i<limit&&left>0;i++)if(next.get(i)==null){int add=Math.min(left,original.getMaxStackSize());ItemStack copy=original.clone();copy.setAmount(add);next.set(i,copy);left-=add;}
        if(left==original.getAmount()){p.tell(a,"보관함이 가득 찼습니다. 잠긴 칸을 눌러 확장하세요.");return;}
        ItemStack remainder=left==0?null:original.clone();if(remainder!=null)remainder.setAmount(left);
        ItemStack[] before=cloneContents(a.getInventory().getStorageContents());a.getInventory().setItem(inventorySlot,remainder);
        if(commit(a,u,next,before))p.tell(a,(original.getAmount()-left)+"개를 보관했습니다.");refresh(a);
    }
    void withdraw(Player a,int slot){
        if(!allowed(a))return;DataStore.User u=p.data.user(a);if(slot<0||slot>=slots(u.vaultTier))return;
        List<ItemStack> next=items(u);ItemStack stored=next.get(slot);if(stored==null)return;
        ItemStack[] before=cloneContents(a.getInventory().getStorageContents());int room=0;
        for(ItemStack at:before)if(at==null||at.getType().isAir())room+=stored.getMaxStackSize();else if(at.isSimilar(stored))room+=Math.max(0,at.getMaxStackSize()-at.getAmount());
        if(room<stored.getAmount()){p.tell(a,"인벤토리에 아이템을 전부 받을 공간이 없습니다.");return;}
        Map<Integer,ItemStack> leftover=a.getInventory().addItem(stored.clone());if(!leftover.isEmpty()){a.getInventory().setStorageContents(before);p.tell(a,"공간이 부족하여 꺼내지 않았습니다.");return;}
        next.set(slot,null);if(commit(a,u,next,before))p.tell(a,stored.getAmount()+"개를 꺼냈습니다.");refresh(a);
    }
    private void refresh(Player a){Bukkit.getScheduler().runTask(p,()->{if(a.isOnline()&&!p.queue.waiting(a)&&a.getOpenInventory().getTopInventory().getHolder() instanceof Gui.Menu menu&&menu.vault)p.gui.vault(a);});}
    private static ItemStack[] cloneContents(ItemStack[] contents){ItemStack[] copy=contents.clone();for(int i=0;i<copy.length;i++)if(copy[i]!=null)copy[i]=copy[i].clone();return copy;}
    private boolean commit(Player a,DataStore.User u,List<ItemStack> next,ItemStack[] before){
        List<String> previous=List.copyOf(u.vault);
        try{for(int i=0;i<54;i++)u.vault.set(i,encode(next.get(i)));writePdc(a,u);a.saveData();p.data.changed();return true;}
        catch(Exception ex){a.getInventory().setStorageContents(before);for(int i=0;i<54;i++)u.vault.set(i,previous.get(i));writePdc(a,u);p.tell(a,"저장에 실패하여 아이템 이동을 취소했습니다.");p.getLogger().log(java.util.logging.Level.SEVERE,"Vault transfer failed for "+a.getUniqueId(),ex);return false;}
    }
    void purchase(Player a,int expectedTier){
        if(!allowed(a))return;DataStore.User u=p.data.user(a);
        if(u.vaultTier!=expectedTier||u.vaultTier>=2){p.tell(a,"보관함 단계가 변경되었거나 이미 전체를 열었습니다.");p.gui.vaultUpgrade(a);return;}
        long cost=price(u.vaultTier);if(u.coins<cost){p.tell(a,"코인이 부족합니다. 필요 "+money(cost)+" / 보유 "+money(u.coins));return;}
        long previous=u.coins;int tier=u.vaultTier;u.coins-=cost;u.vaultTier++;
        try{writePdc(a,u);a.saveData();p.data.changed();p.tell(a,money(cost)+"코인으로 보관함을 "+slots(u.vaultTier)+"칸까지 열었습니다.");}
        catch(Exception ex){u.coins=previous;u.vaultTier=tier;writePdc(a,u);p.tell(a,"저장 실패로 구매를 취소했습니다.");p.getLogger().log(java.util.logging.Level.SEVERE,"Vault purchase failed",ex);}
        p.gui.vaultUpgrade(a);
    }
    void close(){for(Player a:Bukkit.getOnlinePlayers())if(!blocked.contains(a.getUniqueId())){writePdc(a,p.data.user(a));a.saveData();}}
}
