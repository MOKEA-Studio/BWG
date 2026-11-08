package kr.blackwiki.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Preserves the 3.0 PDC keys and UUIDs while adding typed weapons and real durability. */
final class PistolItems {
    static final String GUN="small_pistol",AMMO="small_pistol_ammo",PACK="small_pistol_ammo_pack";
    static final NamespacedKey KIND=new NamespacedKey("blackwikismp","pistol-kind"),SCHEMA=new NamespacedKey("blackwikismp","pistol-schema"),ID=new NamespacedKey("blackwikismp","pistol-id"),LOADED=new NamespacedKey("blackwikismp","pistol-loaded");
    record Gun(UUID id,int loaded,GunKind kind,int used){}
    static boolean product(String id){return GunKind.gun(id)!=null||ammoProduct(id);}
    static boolean ammoProduct(String id){return GunKind.ammo(id)!=null;}
    static boolean pack(String id){GunKind k=GunKind.ammo(id);return k!=null&&id.equals(k.packId);}
    static int packSize(String id){GunKind k=GunKind.ammo(id);return pack(id)?k.packSize:1;}
    static boolean tagged(ItemStack item){return item!=null&&!item.getType().isAir()&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(KIND);}
    static Gun gun(ItemStack item){
        if(item==null||item.getType()!=Material.CARROT_ON_A_STICK||item.getAmount()!=1||!item.hasItemMeta())return null;
        try{var meta=item.getItemMeta();var d=meta.getPersistentDataContainer();if(d.getOrDefault(SCHEMA,PersistentDataType.INTEGER,0)!=1)return null;GunKind kind=GunKind.gun(d.get(KIND,PersistentDataType.STRING));if(kind==null)return null;String text=d.get(ID,PersistentDataType.STRING);UUID id=UUID.fromString(text);int loaded=d.getOrDefault(LOADED,PersistentDataType.INTEGER,-1),used=((Damageable)meta).getDamage();return id.toString().equals(text)&&loaded>=0&&loaded<=kind.capacity&&used>=0&&used<kind.durability?new Gun(id,loaded,kind,used):null;}catch(Exception ex){return null;}
    }
    static GunKind ammoKind(ItemStack item){if(item==null||!item.hasItemMeta()||item.getAmount()<1||item.getAmount()>64)return null;try{var d=item.getItemMeta().getPersistentDataContainer();if(d.getOrDefault(SCHEMA,PersistentDataType.INTEGER,0)!=1)return null;String id=d.get(KIND,PersistentDataType.STRING);if(item.getType()==Material.IRON_INGOT&&GunKind.SNIPER.ammoId.equals(id))return GunKind.SNIPER;for(var k:GunKind.values())if(k.ammoId.equals(id)&&item.getType()==k.ammoMaterial)return k;}catch(Exception ignored){}return null;}
    static boolean legacySniper(ItemStack item){return item!=null&&item.getType()==Material.IRON_INGOT&&ammoKind(item)==GunKind.SNIPER;}
    static boolean ammo(ItemStack item){return ammoKind(item)!=null;}
    static boolean ammo(ItemStack item,GunKind kind){GunKind ammo=ammoKind(item);return ammo!=null&&ammo.ammunition()==kind.ammunition();}
    private static Component text(String text,NamedTextColor color){return Component.text(text,color).decoration(TextDecoration.ITALIC,false);}
    static ItemStack pistol(){return create(GunKind.PISTOL);}
    static ItemStack create(GunKind kind){
        ItemStack item=new ItemStack(Material.CARROT_ON_A_STICK);var meta=(Damageable)item.getItemMeta();meta.setMaxStackSize(1);meta.setMaxDamage(kind.durability);meta.setDamage(0);var d=meta.getPersistentDataContainer();d.set(KIND,PersistentDataType.STRING,kind.id);d.set(SCHEMA,PersistentDataType.INTEGER,1);d.set(ID,PersistentDataType.STRING,UUID.randomUUID().toString());d.set(LOADED,PersistentDataType.INTEGER,0);item.setItemMeta(meta);return loaded(item,0);
    }
    static ItemStack loaded(ItemStack item,int count){
        Gun gun=gun(item);if(gun==null||count<0||count>gun.kind.capacity)throw new IllegalArgumentException("Invalid firearm item");GunKind k=gun.kind;ItemStack next=item.clone();var m=(Damageable)next.getItemMeta();m.setUnbreakable(false);m.removeItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);m.setMaxDamage(k.durability);m.setMaxStackSize(1);m.displayName(text(k.name,NamedTextColor.GOLD));m.getPersistentDataContainer().set(LOADED,PersistentDataType.INTEGER,count);
        String damage=k==GunKind.SHOTGUN?"5개 산탄 합계 피해 24 (♥12)":k==GunKind.SNIPER?"공격 피해 10 (♥5) · 고속 화살":"공격 피해 6 (♥3)";
        m.lore(List.of(damage+" · 탄창 "+count+"/"+k.capacity,"우클릭 발사 · Q/좌클릭 재장전","재장전 "+k.seconds()+"초 · 완료 시 탄환 1개 추가 소비","발사마다 "+k.ammoName+" 1개 소비","내구도 "+(k.durability-m.getDamage())+"/"+k.durability+" · 발사마다 1 감소","야생 월드에서만 발사 · /총기 안내").stream().map(s->text(s,NamedTextColor.GRAY)).toList());next.setItemMeta(m);return next;
    }
    static ItemStack fired(ItemStack item,int count){Gun gun=gun(item);if(gun==null)throw new IllegalArgumentException("Invalid firearm");if(gun.used+1>=gun.kind.durability)return null;ItemStack next=item.clone();var m=(Damageable)next.getItemMeta();m.setDamage(gun.used+1);next.setItemMeta(m);return loaded(next,count);}
    static ItemStack bullets(int count){return bullets(GunKind.PISTOL,count);}
    static ItemStack bullets(GunKind kind,int count){kind=kind.ammunition();if(count<1||count>64)throw new IllegalArgumentException("Invalid ammo stack");ItemStack item=new ItemStack(kind.ammoMaterial,count);var meta=item.getItemMeta();meta.displayName(text(kind.ammoName,NamedTextColor.YELLOW));meta.lore(List.of((kind==GunKind.SHOTGUN?"샷건·저격총 공용":kind.name+" 전용")+" · 일반 재료는 사용 불가","발사마다 1개 · 재장전 완료 때 추가 1개","모루·조합대 가공 불가").stream().map(s->text(s,NamedTextColor.GRAY)).toList());meta.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,kind.ammoId);meta.getPersistentDataContainer().set(SCHEMA,PersistentDataType.INTEGER,1);item.setItemMeta(meta);return item;}
    static List<ItemStack> items(String id,int quantity){
        GunKind gun=GunKind.gun(id);if(quantity<1||quantity>64||gun!=null&&quantity!=1)throw new IllegalArgumentException("총기는 1개, 탄환 상품은 1~64묶음입니다.");if(gun!=null)return List.of(create(gun));GunKind kind=GunKind.ammo(id);if(kind==null)throw new IllegalArgumentException("Unknown firearm product");int count=quantity*packSize(id);List<ItemStack> result=new ArrayList<>();while(count>0){int n=Math.min(count,64);result.add(bullets(kind,n));count-=n;}return result;
    }
}
