package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomyTest {
    @Test void twoSecondRewardsPreserveOddSecond(){
        assertEquals(new CoinClock.Result(0,1),CoinClock.advance(0,1));assertEquals(new CoinClock.Result(1,0),CoinClock.advance(1,1));assertEquals(new CoinClock.Result(30,0),CoinClock.advance(0,60));assertEquals(new CoinClock.Result(1,1),CoinClock.advance(1,2));
        long coins=0;int progress=0;for(int i=0;i<1001;i++){var r=CoinClock.advance(progress,1);coins+=r.coins();progress=r.progress();}assertEquals(500,coins);assertEquals(1,progress);
    }
    @Test void longPauseDoesNotOverflow(){assertEquals(new CoinClock.Result(Long.MAX_VALUE/2+1,0),CoinClock.advance(1,Long.MAX_VALUE));assertEquals(Long.MAX_VALUE,CoinClock.add(Long.MAX_VALUE-1,2));assertThrows(IllegalArgumentException.class,()->CoinClock.advance(2,1));assertThrows(IllegalArgumentException.class,()->CoinClock.advance(0,-1));}
    @Test void allRequestedPricesAndSellingRules(){
        String[] ids={"leather_set","iron_set","diamond_set","netherite_set","shield","protection_1","protection_2","protection_3","protection_4","wooden_sword","stone_sword","iron_sword","diamond_sword","netherite_sword","sharpness_1","sharpness_2","sharpness_3","sharpness_4","sharpness_5","anvil","iron_ingot","diamond","netherite_ingot","lapis_lazuli","redstone","coal","gunpowder","firework_rocket","saturation_60","saturation_180","regeneration_20","regeneration_50","resistance_10","resistance_30"};
        long[] buys={500,1500,3500,15000,500,1500,3000,4500,6000,100,250,600,1500,5000,800,1600,2400,3200,4000,567,300,500,3000,30,20,15,10,50,777,2500,777,2500,777,2500};
        assertEquals(59,ShopCatalogue.ALL.size());assertEquals(59,new HashSet<>(ShopCatalogue.ALL.stream().map(ShopCatalogue.Product::id).toList()).size());for(int i=0;i<ids.length;i++)assertEquals(buys[i],ShopCatalogue.find(ids[i]).buy(),ids[i]);
        Map<String,Long> sales=Map.of("iron_ingot",100L,"diamond",250L,"netherite_ingot",1500L,"lapis_lazuli",30L,"redstone",20L,"coal",15L,"gunpowder",10L);
        for(var product:ShopCatalogue.ALL)assertEquals(sales.getOrDefault(product.id(),0L).longValue(),product.sell(),product.id());
        for(String set:List.of("leather_set","iron_set","diamond_set","netherite_set"))assertEquals(4,ShopCatalogue.find(set).materials().size());assertEquals(6,ShopCatalogue.ALL.stream().filter(ShopCatalogue.Product::ability).count());
    }
    @Test void invalidQuantitiesAndUnsupportedSalesRejected(){assertEquals(19200,ShopCatalogue.find("iron_ingot").total(false,64));assertThrows(IllegalArgumentException.class,()->ShopCatalogue.find("leather_set").total(false,2));assertThrows(IllegalArgumentException.class,()->ShopCatalogue.find("saturation_60").total(false,2));assertThrows(IllegalArgumentException.class,()->ShopCatalogue.find("firework_rocket").total(true,1));assertThrows(IllegalArgumentException.class,()->ShopCatalogue.find("diamond").total(false,0));assertThrows(IllegalArgumentException.class,()->ShopCatalogue.find("diamond").total(false,65));}
}
