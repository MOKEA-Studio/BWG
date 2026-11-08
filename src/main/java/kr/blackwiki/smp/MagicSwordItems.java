package kr.blackwiki.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

final class MagicSwordItems {
    enum Kind {
        ORIUS("orius_sword","오리우스의 검",Material.DIAMOND_SWORD,299900,11.5,1.65,.05,2),
        DEMON("demon_sword","악마의 손길이 담긴 검",Material.NETHERITE_SWORD,799900,13,1.5,.06,3),
        ANGEL("angel_sword","천사의 별 검",Material.DIAMOND_SWORD,1499900,14,1.7,.03,3),
        EXECUTIONER("executioner_sword","단죄의 사형도",Material.IRON_SWORD,20000,9,2,0,0),
        STORM("storm_sword","폭풍 군주의 검",Material.NETHERITE_SWORD,2499900,13.5,1.6,.04,3),
        ENDER("ender_dragon_sword","엔더 드래곤의 검",Material.NETHERITE_SWORD,3999900,14.5,1.6,.05,3);
        final String id,name;final Material material;final long price;final double damage,speed,movement;final int unbreaking;
        Kind(String id,String name,Material material,long price,double damage,double speed,double movement,int unbreaking){this.id=id;this.name=name;this.material=material;this.price=price;this.damage=damage;this.speed=speed;this.movement=movement;this.unbreaking=unbreaking;}
    }
    static final NamespacedKey KIND=new NamespacedKey("blackwikismp","magic-sword"),SCHEMA=new NamespacedKey("blackwikismp","magic-sword-schema");
    static Kind find(String id){return Arrays.stream(Kind.values()).filter(v->v.id.equals(id)).findFirst().orElse(null);}
    static Kind identify(ItemStack item){if(item==null||!item.hasItemMeta())return null;var pd=item.getItemMeta().getPersistentDataContainer();if(pd.getOrDefault(SCHEMA,PersistentDataType.INTEGER,0)!=1)return null;Kind kind=find(pd.getOrDefault(KIND,PersistentDataType.STRING,""));return kind!=null&&kind.material==item.getType()?kind:null;}
    static ItemStack create(Kind kind){
        ItemStack item=new ItemStack(kind.material);var meta=item.getItemMeta();meta.displayName(Component.text(kind.name,kind==Kind.DEMON?NamedTextColor.DARK_RED:kind==Kind.ANGEL?NamedTextColor.LIGHT_PURPLE:kind==Kind.STORM?NamedTextColor.AQUA:kind==Kind.ENDER?NamedTextColor.DARK_PURPLE:NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        // A custom modifier component replaces the item's vanilla defaults. Include both combat attributes.
        meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,new AttributeModifier(new NamespacedKey("blackwikismp",kind.id+"/damage"),kind.damage-1,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,new AttributeModifier(new NamespacedKey("blackwikismp",kind.id+"/speed"),kind.speed-4,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,new AttributeModifier(new NamespacedKey("blackwikismp",kind.id+"/movement"),kind.movement,AttributeModifier.Operation.MULTIPLY_SCALAR_1,EquipmentSlotGroup.MAINHAND));
        if(kind.unbreaking>0)meta.addEnchant(Enchantment.UNBREAKING,kind.unbreaking,false);meta.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,kind.id);meta.getPersistentDataContainer().set(SCHEMA,PersistentDataType.INTEGER,1);
        List<String> lore=new ArrayList<>(List.of("공격력 "+kind.damage+" · 공격 속도 "+kind.speed,"주 손에 들면 이동 속도 +"+(int)Math.round(kind.movement*100)+"%","내구성 "+kind.unbreaking));
        switch(kind){case STORM->lore.addAll(List.of("천둥의 기증: 같은 적을 3번째로 직접 공격할 때마다","번개 연출 + 방어 무시 마법 피해 4 (♥2)"));case ENDER->lore.addAll(List.of("공허의 기증: 적 직접 공격 시 25% 확률","상대 공중 부양 1초 · 어둠 2초 · 재사용 10초","처치 시 드래곤의 포효 · 흡수 II 10초"));case ORIUS->lore.addAll(List.of("혈투의 기증: 적 플레이어 직접 처치 시","체력 2칸 회복 · 신속 I 3초"));case DEMON->lore.addAll(List.of("코인의 기증: 적 플레이어 직접 처치 시","상대 잔액의 15%를 가져옵니다."));case EXECUTIONER->lore.addAll(List.of("적 체력 50% 이상일 때 직접 공격 시","방어력을 무시하는 고정 피해 3 추가"));case ANGEL->lore.addAll(List.of("천사의 기증: 적 플레이어 직접 공격 시","상대 이동 봉쇄 0.35초 · 사용자 저항 II 2초","재사용 대기시간 20초 (재접속 후에도 유지)"));}
        lore.add("팀킬·반복 처치·보호구역에서는 처치 효과 제한");meta.lore(lore.stream().map(s->Component.text(s,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)).toList());item.setItemMeta(meta);return item;
    }
}
