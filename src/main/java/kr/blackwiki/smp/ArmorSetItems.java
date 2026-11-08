package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
final class ArmorSetItems {
    enum Kind {
        GUARDIAN("guardian_set","수호자의 방어구 세트","DIAMOND",499900),
        PHOENIX("phoenix_set","불사조의 갑옷 세트","NETHERITE",999900),
        IMMORTAL("immortal_set","불멸자의 다이아몬드 방어구 세트","NETHERITE",50000),
        ENDER_LORD("ender_lord_set","엔더 군주의 갑옷 세트","NETHERITE",4999900);
        final String id,name,base;final long price;Kind(String id,String name,String base,long price){this.id=id;this.name=name;this.base=base;this.price=price;}
    }
    static final List<String> PARTS=List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS");
    static final NamespacedKey KIND=new NamespacedKey("blackwikismp","armor-set"),PART=new NamespacedKey("blackwikismp","armor-part"),SCHEMA=new NamespacedKey("blackwikismp","armor-schema");
    static Kind find(String id){return Arrays.stream(Kind.values()).filter(k->k.id.equals(id)).findFirst().orElse(null);}
    static List<ItemStack> create(Kind kind){List<ItemStack> result=new ArrayList<>();for(String part:PARTS){ItemStack item=new ItemStack(Material.valueOf(kind.base+"_"+part));var meta=item.getItemMeta();meta.displayName(BlackWikiSMP.text(kind.name+" · "+switch(part){case "HELMET"->"투구";case "CHESTPLATE"->"흉갑";case "LEGGINGS"->"각반";default->"부츠";}));List<String> lore=new ArrayList<>(List.of("같은 종류의 정품 4부위 모두 착용 시 발동"));switch(kind){case GUARDIAN->lore.addAll(List.of("수호자의 인내: 최대 체력 +1칸 · 밀치기 저항 +10%","반격의 가시: 피격 시 15% 확률로 1.5 피해 반사"));case PHOENIX->lore.addAll(List.of("묵직함: 이동 속도 -3%","2칸 이하: 재생 II 3초 · 치명적 피해 1회 구원","구원: 체력 1칸 보장 · 재사용 5분"));case ENDER_LORD->lore.addAll(List.of("군주의 위엄: 최대 체력 +2칸 · 밀치기 저항 +20%","공허 회피: 적 공격·투사체 피격 20% 확률로 피해 0","엔더 순간이동 연출 · 회피 재사용 8초"));case IMMORTAL->lore.addAll(List.of("기반: 네더라이트 · 이름은 요청한 명칭","치명적 피해 시 체력 0.5칸 보장","무적·신속 II 3초 · 발동부터 재사용 5초"));}meta.lore(lore.stream().map(BlackWikiSMP::text).toList());var pd=meta.getPersistentDataContainer();pd.set(KIND,PersistentDataType.STRING,kind.id);pd.set(PART,PersistentDataType.STRING,part);pd.set(SCHEMA,PersistentDataType.INTEGER,1);item.setItemMeta(meta);result.add(item);}return result;}
    static boolean matches(ItemStack item,Kind kind,String part){if(item==null||item.getType()!=Material.valueOf(kind.base+"_"+part)||!item.hasItemMeta())return false;var pd=item.getItemMeta().getPersistentDataContainer();return pd.getOrDefault(SCHEMA,PersistentDataType.INTEGER,0)==1&&pd.getOrDefault(KIND,PersistentDataType.STRING,"").equals(kind.id)&&pd.getOrDefault(PART,PersistentDataType.STRING,"").equals(part);}
    static Kind equipped(PlayerInventory inv){ItemStack[] armor={inv.getHelmet(),inv.getChestplate(),inv.getLeggings(),inv.getBoots()};for(Kind kind:Kind.values()){boolean all=true;for(int i=0;i<4;i++)all&=matches(armor[i],kind,PARTS.get(i));if(all)return kind;}return null;}
}
