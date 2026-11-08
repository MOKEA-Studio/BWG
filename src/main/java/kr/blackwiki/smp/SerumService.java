package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
final class SerumService implements Listener {
    static final NamespacedKey KEY=new NamespacedKey("blackwikismp","giant-serum");private final BlackWikiSMP p;
    SerumService(BlackWikiSMP p){this.p=p;}
    static ItemStack item(){ItemStack item=new ItemStack(Material.POTION);PotionMeta meta=(PotionMeta)item.getItemMeta();meta.displayName(BlackWikiSMP.text("거인의 혈청"));meta.setColor(Color.fromRGB(195,65,125));meta.lore(List.of(BlackWikiSMP.text("주 손에 들고 우클릭 → 사용 확인"),BlackWikiSMP.text("최대 체력 영구 +1칸 · 플레이어당 최대 3회")));meta.getPersistentDataContainer().set(KEY,PersistentDataType.INTEGER,1);item.setItemMeta(meta);return item;}
    static boolean isSerum(ItemStack item){return item!=null&&item.getType()==Material.POTION&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().getOrDefault(KEY,PersistentDataType.INTEGER,0)==1;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void use(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK||!isSerum(e.getItem()))return;e.setCancelled(true);Player a=e.getPlayer();if(!p.travel.allowed(a)||!p.vaults.healthy(a))return;ItemStack expected=a.getInventory().getItemInMainHand().clone();p.gui.confirm(a,"거인의 혈청 사용 · "+p.armor.serumUses(a)+"/3",b->consume(b,expected),this::open);}
    boolean consume(Player a,ItemStack expected){if(!p.travel.allowed(a)||!p.vaults.healthy(a)||p.backups.running())return false;int old=p.armor.serumUses(a);if(old>=3){p.tell(a,"혈청은 플레이어당 최대 3회입니다.");return false;}ItemStack actual=a.getInventory().getItemInMainHand();if(!isSerum(actual)||!actual.equals(expected)){p.tell(a,"손에 든 혈청이 변경되었습니다. 다시 시도하세요.");return false;}ItemStack before=actual.clone();try{ItemStack next=actual.clone();next.setAmount(next.getAmount()-1);a.getInventory().setItemInMainHand(next);p.armor.setSerumUses(a,old+1);a.saveData();p.tell(a,"거인의 혈청 · 최대 체력 영구 +1칸 · "+(old+1)+"/3");return true;}catch(Exception ex){a.getInventory().setItemInMainHand(before);p.armor.setSerumUses(a,old);p.getLogger().log(java.util.logging.Level.SEVERE,"Serum transaction failed",ex);return false;}}
    void open(Player a){Gui.Menu m=p.gui.make(a,"거인의 혈청");m.inventory.setItem(22,Gui.item(Material.POTION,"사용 "+p.armor.serumUses(a)+"/3 · 영구 체력 +"+p.armor.serumUses(a)+"칸","상점 4페이지 · 75,000코인 / 병","주 손의 혈청을 우클릭하고 확인하세요.","사망·장비 해제·재접속 후에도 유지"));p.gui.button(m,31,Material.EMERALD,"특별 상점",b->p.shop.page(b,3));p.gui.show(a,m);}
}
