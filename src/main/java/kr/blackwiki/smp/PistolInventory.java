package kr.blackwiki.smp;

import org.bukkit.inventory.ItemStack;

/** Plan on clones, then publish the weapon state and ammo removal together on the server thread. */
final class PistolInventory {
    record Change(ItemStack[] contents,int loaded,int reserve){}
    private static boolean reserveSlot(int i){return i<36||i==40;}
    static int count(ItemStack[] items){return count(items,GunKind.PISTOL);}
    static int count(ItemStack[] items,GunKind kind){int count=0;for(int i=0;i<items.length;i++)if(reserveSlot(i)&&PistolItems.ammo(items[i],kind))count+=items[i].getAmount();return count;}
    static Change plan(ItemStack[] before,int held,boolean reload){
        if(before.length<36||held<0||held>8)throw new IllegalArgumentException("Invalid player inventory");var gun=PistolItems.gun(before[held]);if(gun==null)throw new IllegalArgumentException("정품 총기를 주 손에 드세요.");int reserve=count(before,gun.kind());var plan=reload?GunRules.reload(gun.kind(),gun.loaded(),reserve):GunRules.shot(gun.kind(),gun.loaded(),reserve);ItemStack[] next=InventoryPlan.copy(before);boolean consumed=false;
        for(int i=0;i<next.length;i++)if(reserveSlot(i)&&PistolItems.ammo(next[i],gun.kind())){if(next[i].getAmount()==1)next[i]=null;else next[i].setAmount(next[i].getAmount()-1);consumed=true;break;}
        if(!consumed)throw new IllegalStateException("전용 탄환이 없습니다.");next[held]=reload?PistolItems.loaded(before[held],plan.loaded()):PistolItems.fired(before[held],plan.loaded());return new Change(next,plan.loaded(),plan.reserve());
    }
}
