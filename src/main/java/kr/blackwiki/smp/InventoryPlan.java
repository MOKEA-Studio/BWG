package kr.blackwiki.smp;

import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Plan on clones first; callers publish only complete transfers. Equipment is excluded. */
final class InventoryPlan {
    static ItemStack[] copy(ItemStack[] source){ItemStack[] next=source.clone();for(int i=0;i<next.length;i++)if(next[i]!=null)next[i]=next[i].clone();return next;}
    static ItemStack[] add(ItemStack[] source,List<ItemStack> items){
        ItemStack[] next=copy(source);
        for(ItemStack item:items){int left=item.getAmount();for(int i=0;i<next.length&&left>0;i++){ItemStack at=next[i];if(at!=null&&at.isSimilar(item)){int amount=Math.min(left,Math.max(0,at.getMaxStackSize()-at.getAmount()));at.setAmount(at.getAmount()+amount);left-=amount;}}
            for(int i=0;i<next.length&&left>0;i++)if(next[i]==null||next[i].getType().isAir()){int amount=Math.min(left,item.getMaxStackSize());next[i]=item.clone();next[i].setAmount(amount);left-=amount;}
            if(left>0)return null;
        }return next;
    }
    static ItemStack[] remove(ItemStack[] source,ItemStack plain,int quantity){
        ItemStack[] next=copy(source);int left=quantity;
        for(int i=0;i<next.length&&left>0;i++){ItemStack at=next[i];if(at!=null&&at.isSimilar(plain)){int amount=Math.min(left,at.getAmount());left-=amount;if(amount==at.getAmount())next[i]=null;else at.setAmount(at.getAmount()-amount);}}
        return left==0?next:null;
    }
}
