package kr.blackwiki.smp;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import java.util.*;

final class ShopCatalogue {
    enum Category { ARMOUR, ATTACK, MATERIALS, EFFECTS, SPECIAL, FOOD }
    record Product(String id,String name,Category category,Material icon,long buy,long sell,List<Material> materials,String enchant,int level,String effect,int seconds){
        boolean food(){return category==Category.FOOD;}
        int units(){return food()?FoodBundles.units(id):PistolItems.packSize(id);}
        String quantityLabel(int quantity){return food()?quantity+"묶음 ("+(quantity*units())+"개)":PistolItems.pack(id)?quantity+"묶음 ("+(quantity*units())+"발)":quantity+"개";}
        boolean ammo(){return PistolItems.ammoProduct(id);}
        boolean firearm(){return GunKind.gun(id)!=null;}
        boolean armor(){return ArmorSetItems.find(id)!=null;}
        boolean serum(){return id.equals("giant_serum");}
        boolean ability(){return effect!=null;}
        boolean set(){return materials.size()>1;}
        boolean magic(){return category==Category.SPECIAL;}
        List<ItemStack> items(int quantity){
            if(ability())return List.of();
            if(PistolItems.product(id))return PistolItems.items(id,quantity);
            if(armor()){if(quantity!=1)throw new IllegalArgumentException("세트는 한 번에 1개입니다.");return ArmorSetItems.create(ArmorSetItems.find(id));}
            if(serum()){if(quantity!=1)throw new IllegalArgumentException("혈청은 한 번에 1병입니다.");return List.of(SerumService.item());}
            if(magic()){if(quantity!=1)throw new IllegalArgumentException("마검은 한 번에 1개만 구매할 수 있습니다.");return List.of(MagicSwordItems.create(Objects.requireNonNull(MagicSwordItems.find(id))));}
            if(quantity<1||quantity>64||(set()&&quantity!=1))throw new IllegalArgumentException("Invalid quantity");
            List<ItemStack> result=new ArrayList<>();
            for(Material material:materials){if(food()){int count=FoodBundles.delivered(id,quantity);while(count>0){int n=Math.min(count,material.getMaxStackSize());result.add(new ItemStack(material,n));count-=n;}continue;}ItemStack item=new ItemStack(material,food()?1:quantity);
                if(enchant!=null){EnchantmentStorageMeta meta=(EnchantmentStorageMeta)item.getItemMeta();meta.addStoredEnchant(enchant.equals("protection")?Enchantment.PROTECTION:Enchantment.SHARPNESS,level,false);item.setItemMeta(meta);}
                if(material==Material.FIREWORK_ROCKET){FireworkMeta meta=(FireworkMeta)item.getItemMeta();meta.setPower(1);item.setItemMeta(meta);}result.add(item);
            }return result;
        }
        long total(boolean sale,int amount){if(amount<1||amount>64||(ability()||set()||magic()&&!ammo())&&amount!=1)throw new IllegalArgumentException("수량은 1~64, 세트·능력·무기는 1개입니다.");long price=sale?sell:buy;if(price<=0)throw new IllegalArgumentException("이 상품은 판매할 수 없습니다.");return Math.multiplyExact(price,amount);}
    }
    static final List<Product> ALL;
    static {List<Product> list=new ArrayList<>();
        armour(list,"leather","가죽 갑옷 세트",500);armour(list,"iron","철 갑옷 세트",1500);armour(list,"diamond","다이아 갑옷 세트",3500);armour(list,"netherite","네더라이트 갑옷 세트",15000);
        add(list,"shield","방패",Category.ARMOUR,Material.SHIELD,500,0);
        for(int i=1;i<=4;i++)book(list,"protection_"+i,"보호 "+i+" 효과부여책",Category.ARMOUR,1500L*i,"protection",i);
        Material[] swords={Material.WOODEN_SWORD,Material.STONE_SWORD,Material.IRON_SWORD,Material.DIAMOND_SWORD,Material.NETHERITE_SWORD};String[] swordNames={"목검","돌검","철검","다이아검","네더라이트 검"};long[] swordPrices={100,250,600,1500,5000};
        for(int i=0;i<swords.length;i++)add(list,swords[i].name().toLowerCase(Locale.ROOT),swordNames[i],Category.ATTACK,swords[i],swordPrices[i],0);
        for(int i=1;i<=5;i++)book(list,"sharpness_"+i,"날카로움 "+i+" 효과부여책",Category.ATTACK,800L*i,"sharpness",i);
        add(list,"anvil","모루",Category.MATERIALS,Material.ANVIL,567,0);add(list,"iron_ingot","철 주괴",Category.MATERIALS,Material.IRON_INGOT,300,100);add(list,"diamond","다이아몬드",Category.MATERIALS,Material.DIAMOND,500,250);add(list,"netherite_ingot","네더라이트 주괴",Category.MATERIALS,Material.NETHERITE_INGOT,3000,1500);add(list,"lapis_lazuli","청금석",Category.MATERIALS,Material.LAPIS_LAZULI,30,30);add(list,"redstone","레드스톤",Category.MATERIALS,Material.REDSTONE,20,20);add(list,"coal","석탄",Category.MATERIALS,Material.COAL,15,15);add(list,"gunpowder","화약",Category.MATERIALS,Material.GUNPOWDER,10,10);add(list,"firework_rocket","폭죽 (비행 시간 1)",Category.MATERIALS,Material.FIREWORK_ROCKET,50,0);
        effect(list,"saturation_60","포화 1분",777,"saturation",60,Material.COOKED_BEEF);effect(list,"saturation_180","포화 3분",2500,"saturation",180,Material.GOLDEN_CARROT);effect(list,"regeneration_20","재생 20초",777,"regeneration",20,Material.GHAST_TEAR);effect(list,"regeneration_50","재생 50초",2500,"regeneration",50,Material.GOLDEN_APPLE);effect(list,"resistance_10","저항 10초",777,"resistance",10,Material.IRON_CHESTPLATE);effect(list,"resistance_30","저항 30초",2500,"resistance",30,Material.NETHERITE_CHESTPLATE);
        for(var kind:MagicSwordItems.Kind.values())list.add(new Product(kind.id,kind.name,Category.SPECIAL,kind.material,kind.price,0,List.of(kind.material),null,0,null,0));
        for(var kind:ArmorSetItems.Kind.values()){List<Material> parts=ArmorSetItems.PARTS.stream().map(part->Material.valueOf(kind.base+"_"+part)).toList();list.add(new Product(kind.id,kind.name,Category.SPECIAL,parts.get(1),kind.price,0,parts,null,0,null,0));}
        list.add(new Product("giant_serum","거인의 혈청",Category.SPECIAL,Material.POTION,75000,0,List.of(Material.POTION),null,0,null,0));
        for(var kind:GunKind.values()){
            add(list,kind.id,kind.name,Category.SPECIAL,Material.CARROT_ON_A_STICK,kind.price,0);
            add(list,kind.ammoId,kind.ammoName+" · 1발",Category.SPECIAL,kind.ammoMaterial,kind.ammoPrice,0);
            if(kind.packId!=null)add(list,kind.packId,kind.ammoName+" · "+kind.packSize+"발 묶음",Category.SPECIAL,kind.ammoMaterial,kind.packPrice,0);
        }
        for(var food:FoodBundles.ALL)add(list,food.id(),food.name(),Category.FOOD,food.material(),food.price(),0);
        ALL=List.copyOf(list);
    }
    private static void add(List<Product> l,String id,String name,Category c,Material m,long buy,long sell){l.add(new Product(id,name,c,m,buy,sell,List.of(m),null,0,null,0));}
    private static void armour(List<Product> l,String id,String name,long price){String prefix=id.toUpperCase(Locale.ROOT);List<Material> ms=List.of(Material.valueOf(prefix+"_HELMET"),Material.valueOf(prefix+"_CHESTPLATE"),Material.valueOf(prefix+"_LEGGINGS"),Material.valueOf(prefix+"_BOOTS"));l.add(new Product(id+"_set",name,Category.ARMOUR,ms.get(1),price,0,ms,null,0,null,0));}
    private static void book(List<Product> l,String id,String name,Category c,long price,String enchant,int level){l.add(new Product(id,name,c,Material.ENCHANTED_BOOK,price,0,List.of(Material.ENCHANTED_BOOK),enchant,level,null,0));}
    private static void effect(List<Product> l,String id,String name,long price,String effect,int seconds,Material icon){l.add(new Product(id,name,Category.EFFECTS,icon,price,0,List.of(),null,0,effect,seconds));}
    static Product find(String id){return ALL.stream().filter(v->v.id.equalsIgnoreCase(id)).findFirst().orElseThrow(()->new IllegalArgumentException("상품 ID를 확인하세요. /상점"));}
}
