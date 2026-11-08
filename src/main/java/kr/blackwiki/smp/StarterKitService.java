package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.logging.Level;

/** The one-time receipt and all six stacks share the player's native .dat save. */
final class StarterKitService {
    static final List<Material> TOOLS=List.of(Material.STONE_SWORD,Material.STONE_PICKAXE,Material.STONE_AXE,Material.STONE_SHOVEL,Material.STONE_HOE);
    private final BlackWikiSMP p;
    private final NamespacedKey claimedKey;
    private final Set<UUID> blocked=new HashSet<>();
    StarterKitService(BlackWikiSMP p){this.p=p;claimedKey=new NamespacedKey(p,"starter-kit-claimed");}
    static List<ItemStack> items(){List<ItemStack> items=new ArrayList<>();for(Material tool:TOOLS)items.add(new ItemStack(tool));items.add(new ItemStack(Material.BREAD,10));return items;}
    boolean claimed(Player a){return a.getPersistentDataContainer().has(claimedKey);}
    boolean claim(Player a){
        if(!p.cfg().getBoolean("starter-kit.enabled",true)){p.tell(a,"기본템 지급이 꺼져 있습니다.");return false;}
        if(blocked.contains(a.getUniqueId())){p.tell(a,"기본템 저장 오류로 지급이 잠겼습니다. 관리자에게 문의하세요.");return false;}
        if(claimed(a)){p.tell(a,"기본템은 플레이어당 한 번만 받을 수 있습니다. 이미 수령했습니다.");return false;}
        if(!p.travel.allowed(a)||!p.vaults.healthy(a))return false;
        if(p.backups.running()){p.tell(a,"백업 완료 후 기본템을 받으세요.");return false;}
        ItemStack[] before=a.getInventory().getStorageContents();
        ItemStack[] next=InventoryPlan.add(before,items());
        if(next==null){p.tell(a,"기본템을 전부 받을 가방 공간을 확보하세요. 수령 기회는 유지됩니다.");return false;}
        try{a.getInventory().setStorageContents(next);a.getPersistentDataContainer().set(claimedKey,PersistentDataType.BYTE,(byte)1);a.saveData();p.tell(a,"기본템을 받았습니다! 돌도구 5종 + 빵 10개 · 계정당 1회");return true;}
        catch(Exception ex){blocked.add(a.getUniqueId());a.getInventory().setStorageContents(before);a.getPersistentDataContainer().remove(claimedKey);p.getLogger().log(Level.SEVERE,"Starter kit save failed for "+a.getUniqueId(),ex);p.tell(a,"저장 오류로 지급을 중단했습니다. 관리자 확인 후 재시작하세요.");return false;}
    }
    void command(Player a,String[] args){if(args.length>0&&Set.of("받기","수령","claim").contains(args[0].toLowerCase(Locale.ROOT))){claim(a);return;}open(a);}
    void open(Player a){if(p.queue.waiting(a))return;Gui.Menu m=p.gui.make(a,"기본템 · 플레이어당 1회");List<ItemStack> preview=items();int[] slots={19,20,21,22,23,25};for(int i=0;i<preview.size();i++)m.inventory.setItem(slots[i],preview.get(i));m.inventory.setItem(4,Gui.item(Material.CHEST,"돌도구 세트 + 빵 10개","돌검·돌곡괭이·돌도끼·돌삽·돌괭이","재접속·서버 재시작 뒤에도 수령 기록 유지","공간이 부족하면 지급과 수령 기록 모두 보류","명령어로 바로 수령: /기본템 받기"));p.gui.button(m,40,claimed(a)?Material.GRAY_DYE:Material.LIME_DYE,claimed(a)?"이미 받았습니다":"한 번만 받기",b->{claim(b);open(b);});p.gui.show(a,m);}
    String status(){return p.cfg().getBoolean("starter-kit.enabled",true)?"계정당 1회 · 돌도구 5종 + 빵 10개":"꺼짐";}
}
