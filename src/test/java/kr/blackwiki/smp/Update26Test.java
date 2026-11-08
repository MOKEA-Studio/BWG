package kr.blackwiki.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Update26Test {
    @TempDir Path dir;
    String defaults()throws Exception{try(var in=getClass().getResourceAsStream("/config.yml")){return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}}
    @Test void twentyFiveConfigGetsExpandedBorderAndPreservesEveryOtherValue()throws Exception{
        Path f=dir.resolve("config.yml");String before="config-version: 25\nborder:\n  size: 20000\n  center-x: 123\nqueue:\n  player-slots: 71\n  max-waiting: 9\nteleport:\n  rtp-max-radius: 8765\ncustom: keep\n";Files.writeString(f,before);
        UpgradeService.migrate(f,defaults());YamlConfiguration y=new YamlConfiguration();y.load(f.toFile());
        assertEquals(35,y.getInt("config-version"));assertEquals(40000,y.getInt("border.size"));assertEquals(123,y.getInt("border.center-x"));assertEquals(71,y.getInt("queue.player-slots"));assertEquals(9,y.getInt("queue.max-waiting"));assertEquals(8765,y.getInt("teleport.rtp-max-radius"));assertEquals("keep",y.getString("custom"));assertTrue(y.getBoolean("welcome.enabled"));assertEquals(before,Files.readString(dir.resolve("config.yml.before-3.5.bak")));
        String once=Files.readString(f);assertFalse(UpgradeService.migrate(f,defaults()).changed());assertEquals(once,Files.readString(f));
    }
    @Test void fortyThousandBorderIncludesEdgesAndUsesShiftedCenter(){assertFalse(Rules.outside(20000,-20000,0,0,40000));assertTrue(Rules.outside(20000.001,0,0,0,40000));assertTrue(Rules.outside(0,-20000.001,0,0,40000));assertFalse(Rules.outside(20123,-19901,123,99,40000));assertEquals(1,Rules.edgeDistance(19999,0,0,0,40000));assertFalse(Rules.outside(15000,15000,0,0,40000));}
    @Test void gravesExpireExactlyAtTenMinutesAndOldGravesGetSingleUpgradeGrace(){long epoch=1_000_000;assertEquals(1_600_000,GraveRules.deadline(1,epoch));assertFalse(GraveRules.expired(1,epoch,1_599_999));assertTrue(GraveRules.expired(1,epoch,1_600_000));assertEquals(1,GraveRules.remainingSeconds(1,epoch,1_599_999));assertEquals(0,GraveRules.remainingSeconds(1,epoch,1_600_000));assertEquals(1_700_000,GraveRules.deadline(1_100_000,epoch));}
    GraveLedger.Grave grave(){return new GraveLedger.Grave(UUID.randomUUID(),UUID.randomUUID(),1,"wild",UUID.randomUUID(),1,64,1,true,GraveLedger.State.OPEN,List.of("first","second"),null,null,-1);}
    @Test void expiryDurablyErasesItemsAndRejectsLateWithdrawals()throws Exception{Path f=dir.resolve("graves.yml");var ledger=new GraveLedger(f);var g=grave();ledger.put(g.expire());g=new GraveLedger(f).get(g.id());assertEquals(GraveLedger.State.EXPIRED,g.state());assertEquals(List.of("",""),g.items());var expired=g;assertThrows(IllegalStateException.class,()->expired.reserve(UUID.randomUUID(),UUID.randomUUID(),0));assertEquals(expired,expired.expire());}
    @Test void expiredPendingWithdrawalCannotReopenOrReturnUndeliveredItem()throws Exception{var g=grave();var pending=g.reserve(UUID.randomUUID(),UUID.randomUUID(),1).expire();assertEquals(List.of("","second"),pending.items());var ledger=new GraveLedger(dir.resolve("graves.yml"));ledger.put(pending);var reloaded=new GraveLedger(dir.resolve("graves.yml")).get(g.id());for(boolean delivered:List.of(true,false)){var settled=reloaded.settle(delivered);assertEquals(GraveLedger.State.EXPIRED,settled.state());assertEquals(List.of("",""),settled.items());assertNull(settled.token());}}
    @Test void malformedExpiredGravesCannotHideLiveItems(){var g=grave();assertThrows(IllegalArgumentException.class,()->g.state(GraveLedger.State.EXPIRED));}
    @Test void greetingUsesExactKoreanAndAcceptsSurroundingWhitespace(){assertTrue(WelcomeRules.greeting("안녕하세요"));assertTrue(WelcomeRules.greeting("  안녕하세요  "));assertTrue(WelcomeRules.greeting(java.text.Normalizer.normalize("안녕하세요",java.text.Normalizer.Form.NFD)));for(String s:List.of("안녕하세요!","안녕 하세요","안녕하세요 여러분","/안녕하세요","hello",""))assertFalse(WelcomeRules.greeting(s));assertFalse(WelcomeRules.greeting(null));}
    @Test void rewardIsOnlyBeforeTenSecondsAndNeverSelf(){UUID newcomer=UUID.randomUUID(),other=UUID.randomUUID();var w=new WelcomeRules.Window(newcomer,"new",100);assertFalse(w.eligible(other,99));assertTrue(w.eligible(other,100));assertTrue(w.eligible(other,10_000_000_099L));assertFalse(w.eligible(other,10_000_000_100L));assertFalse(w.eligible(newcomer,101));assertEquals(100,WelcomeRules.REWARD);}
    @Test void duplicateAndReconnectShareSameUuidButDifferentNewcomersHaveSeparateRewards(){UUID newcomer=UUID.randomUUID(),who=UUID.randomUUID(),second=UUID.randomUUID();var w=new WelcomeRules.Window(newcomer,"one",0);assertTrue(w.eligible(who,1));w.claimed(who);assertFalse(w.eligible(UUID.fromString(who.toString()),2));assertTrue(w.eligible(second,2));assertTrue(new WelcomeRules.Window(UUID.randomUUID(),"two",0).eligible(who,2));}
    @Test void monotonicWindowWorksAcrossSignedNanoTimeOverflow(){long start=Long.MAX_VALUE-100;var w=new WelcomeRules.Window(UUID.randomUUID(),"n",start);assertTrue(w.active(start+200));assertFalse(w.active(start+WelcomeRules.WINDOW_NANOS));}
}
