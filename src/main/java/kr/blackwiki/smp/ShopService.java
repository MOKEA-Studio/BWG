package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import java.util.*;

final class ShopService {
    private final BlackWikiSMP p;
    private record Quote(UUID token,ShopCatalogue.Product product,boolean sell,int quantity,long expires){}
    private final Map<UUID,Quote> quotes=new HashMap<>();
    ShopService(BlackWikiSMP p){this.p=p;}
    boolean allowed(Player a){if(!p.safe(a.getLocation())){p.tell(a,"상점·유저상점은 스폰 보호구역에서만 이용할 수 있습니다.");return false;}if(p.backups!=null&&p.backups.running()){p.tell(a,"백업 중입니다. 완료 후 거래하세요.");return false;}return p.vaults.healthy(a)&&p.travel.allowed(a);}
    void open(Player a){page(a,0);}
    void category(Player a,ShopCatalogue.Category category){page(a,switch(category){case ARMOUR->0;case ATTACK->1;case MATERIALS,EFFECTS->2;case SPECIAL->3;case FOOD->4;});}
    void page(Player a,int page){if(!allowed(a))return;if(page<0||page>4)throw new IllegalArgumentException("상점 페이지는 1~5입니다.");String[] names={"방어구","공격","재료 · 능력","특별 장비","식량"};Gui.Menu m=p.gui.make(a,"상점 "+(page+1)+"/5 · "+names[page]);
        for(int i=0;i<5;i++){final int target=i;p.gui.button(m,i,Material.BOOK,(i+1)+"페이지 · "+names[i],b->page(b,target));}
        int slot=10;for(var product:ShopCatalogue.ALL){boolean include=switch(page){case 0->product.category()==ShopCatalogue.Category.ARMOUR;case 1->product.category()==ShopCatalogue.Category.ATTACK;case 2->product.category()==ShopCatalogue.Category.MATERIALS||product.category()==ShopCatalogue.Category.EFFECTS;case 3->product.magic();default->product.food();};if(!include)continue;
            int at=slot++;if(product.magic()){ItemStack preview=product.items(1).get(product.armor()?1:0);var meta=preview.getItemMeta();if(product.armor()||product.ammo())meta.displayName(BlackWikiSMP.text(product.name()));var lore=new ArrayList<>(Objects.requireNonNull(meta.lore()));lore.add(BlackWikiSMP.text("가격: "+VaultService.money(product.buy())+"코인"));meta.lore(lore);preview.setItemMeta(meta);m.inventory.setItem(at,preview);m.actions.put(at,b->product(b,product,1));}
            else p.gui.button(m,at,product.icon(),product.name(),b->product(b,product,1),"구매: "+VaultService.money(product.buy())+"코인",product.sell()>0?"판매: "+VaultService.money(product.sell())+"코인":"구매 전용",product.food()?"1묶음 "+product.units()+"개 가격":product.ability()?"효과 I · "+product.seconds()+"초":product.set()?"머리·상의·하의·신발 4개":"1개 가격","ID: "+product.id());}
        if(page>0)p.gui.button(m,45,Material.ARROW,"이전 · "+page+"페이지",b->page(b,page-1));if(page<4)p.gui.button(m,53,Material.ARROW,"다음 · "+(page+2)+"페이지",b->page(b,page+1));p.gui.button(m,48,Material.CHEST_MINECART,"유저상점",b->p.market.open(b,0,false),"/유저상점");p.gui.button(m,50,Material.DIAMOND_PICKAXE,"채광 레벨",b->p.mining.open(b),"/채광");p.gui.show(a,m);
    }
    void product(Player a,ShopCatalogue.Product product,int quantity){if(!allowed(a))return;Gui.Menu m=p.gui.make(a,product.name());m.inventory.setItem(13,Gui.item(product.icon(),product.name(),"선택 수량: "+product.quantityLabel(quantity),"보유: "+VaultService.money(p.data.user(a).coins)+"코인"));p.gui.button(m,20,Material.LIME_WOOL,"구매 "+VaultService.money(product.total(false,quantity))+"코인",b->quote(b,product,false,quantity));if(product.sell()>0)p.gui.button(m,24,Material.GOLD_INGOT,"판매 "+VaultService.money(product.total(true,quantity))+"코인",b->quote(b,product,true,quantity),"이름·인챈트 등 변경이 없는 일반 재료만");if(!product.ability()&&!product.set()&&(!product.magic()||product.ammo())){int[] values=(product.food()||PistolItems.pack(product.id()))?new int[]{1,2,4,8}:product.ammo()?new int[]{1,6,16,64}:new int[]{1,16,64};for(int i=0;i<values.length;i++){int q=values[i];p.gui.button(m,30+i,Material.PAPER,product.quantityLabel(q)+" 선택",b->product(b,product,q));}}p.gui.button(m,45,Material.ARROW,"분류로",b->category(b,product.category()));p.gui.show(a,m);}
    void quote(Player a,ShopCatalogue.Product product,boolean sell,int quantity){if(!allowed(a))return;long cost=product.total(sell,quantity);Quote q=new Quote(UUID.randomUUID(),product,sell,quantity,System.currentTimeMillis()+30000);quotes.put(a.getUniqueId(),q);Gui.Menu m=p.gui.make(a,(sell?"판매":"구매")+" 확인");m.inventory.setItem(13,Gui.item(product.icon(),product.name()+" × "+product.quantityLabel(quantity),"합계: "+VaultService.money(cost)+"코인","확인 유효 시간: 30초"));p.gui.button(m,20,Material.LIME_CONCRETE,"확인",b->commit(b,q.token));p.gui.button(m,24,Material.RED_CONCRETE,"취소",b->{quotes.remove(b.getUniqueId());product(b,product,quantity);});p.gui.show(a,m);}
    boolean commit(Player a,UUID token){Quote q=quotes.get(a.getUniqueId());if(q==null||!q.token.equals(token)||q.expires<System.currentTimeMillis()){p.tell(a,"확인 화면이 만료되었습니다. 다시 선택하세요.");return false;}quotes.remove(a.getUniqueId());return transact(a,q.product,q.sell,q.quantity);}
    boolean transact(Player a,ShopCatalogue.Product product,boolean sell,int quantity){
        if(!allowed(a))return false;long cost=product.total(sell,quantity);DataStore.User u=p.data.user(a);if(!sell&&u.coins<cost){p.tell(a,"코인이 부족합니다. 필요: "+VaultService.money(cost));return false;}if(sell&&u.coins>Long.MAX_VALUE-cost){p.tell(a,"코인 한도를 초과합니다.");return false;}
        ItemStack[] before=InventoryPlan.copy(a.getInventory().getStorageContents()),next=before;PotionEffect old=null;
        if(product.ability()){if(sell)throw new IllegalArgumentException("능력은 판매할 수 없습니다.");var type=AbilityCommand.type(product.effect());if(!AbilityCommand.useful(a,type,product.seconds()*20)){p.tell(a,"기존 효과가 더 강하거나 오래 지속되어 구매할 수 없습니다.");return false;}old=a.getPotionEffect(type);}
        else{next=sell?InventoryPlan.remove(before,new ItemStack(product.icon()),quantity):InventoryPlan.add(before,product.items(quantity));if(next==null){p.tell(a,sell?"판매할 일반 재료가 부족합니다.":"인벤토리에 상품 전체를 받을 공간이 없습니다.");return false;}}
        long oldCoins=u.coins;try{
            if(product.ability()&&!Objects.requireNonNull(p.getCommand("smpability")).execute(Bukkit.getConsoleSender(),"smpability",new String[]{a.getName(),product.effect(),Integer.toString(product.seconds())})){p.tell(a,"능력 적용에 실패하여 코인을 차감하지 않았습니다.");return false;}
            a.getInventory().setStorageContents(next);u.coins=sell?u.coins+cost:u.coins-cost;p.vaults.saveAccount(a);p.tell(a,product.name()+" × "+quantity+(sell?" 판매 완료 +":" 구매 완료 -")+VaultService.money(cost)+"코인");return true;
        }catch(Exception ex){a.getInventory().setStorageContents(before);u.coins=oldCoins;if(product.ability()){a.removePotionEffect(AbilityCommand.type(product.effect()));if(old!=null)a.addPotionEffect(old);}p.vaults.syncCurrency(a,u);p.tell(a,"저장 실패로 거래를 취소했습니다.");p.getLogger().log(java.util.logging.Level.SEVERE,"Shop transaction failed",ex);return false;}
    }
    void quit(Player a){quotes.remove(a.getUniqueId());}
    void command(Player a,String[] args){if(args.length==0){open(a);return;}if(args.length==1){if(args[0].matches("[1-5]")){page(a,Integer.parseInt(args[0])-1);return;}if(args[0].equals("식량")){page(a,4);return;}if(args[0].equals("특별")){page(a,3);return;}}if(args.length<2||args.length>3)throw new IllegalArgumentException("/상점 [구매|판매 상품ID 수량]");boolean sale=switch(args[0]){case "판매","sell"->true;case "구매","buy"->false;default->throw new IllegalArgumentException("구매 또는 판매를 입력하세요.");};int quantity=args.length==3?Integer.parseInt(args[2]):1;quote(a,ShopCatalogue.find(args[1]),sale,quantity);}
}
