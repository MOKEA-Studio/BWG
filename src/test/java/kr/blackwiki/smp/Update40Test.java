package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Update40Test {
 @Test void newSpecialGearIsOnPageFourBetweenTwoAndFiveMillion(){for(String id:new String[]{"storm_sword","ender_dragon_sword","ender_lord_set"}){var p=ShopCatalogue.find(id);assertNotNull(p,id);assertEquals(ShopCatalogue.Category.SPECIAL,p.category());assertTrue(p.buy()>=2_000_000&&p.buy()<=5_000_000,id+" "+p.buy());assertEquals(0,p.sell());}}
 @Test void stormFiresOnEveryThirdHitOnly(){int procs=0;for(int n=1;n<=9;n++)if(SpecialSwordService.stormProc(n))procs++;assertEquals(3,procs);assertFalse(SpecialSwordService.stormProc(0));assertFalse(SpecialSwordService.stormProc(1));assertTrue(SpecialSwordService.stormProc(3));}
 @Test void enderLordDodgeIsTwentyPercent(){assertTrue(ArmorSetService.dodgeRoll(0));assertTrue(ArmorSetService.dodgeRoll(.199));assertFalse(ArmorSetService.dodgeRoll(.2));assertFalse(ArmorSetService.dodgeRoll(-.1));}
 @Test void enderEyesOrbitAroundTheHeadAndMove(){for(long f=0;f<120;f++)for(int i=0;i<AdminTools.EYES;i++){var v=AdminTools.eyePose(i,f).getTranslation();assertEquals(.62,Math.hypot(v.x,v.z),1e-3);assertTrue(v.y>-.1f&&v.y<.2f);}assertNotEquals(AdminTools.eyePose(0,0).getTranslation(),AdminTools.eyePose(0,1).getTranslation());}
}
