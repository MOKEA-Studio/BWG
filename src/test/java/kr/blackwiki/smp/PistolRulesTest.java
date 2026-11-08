package kr.blackwiki.smp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PistolRulesTest {
 @TempDir Path dir;
 @Test void reloadAndSixShotsUseSevenReserveRounds(){var state=PistolRules.reload(0,7);assertEquals(6,state.loaded());assertEquals(6,state.reserve());for(int i=0;i<6;i++)state=PistolRules.shot(state.loaded(),state.reserve());assertEquals(new PistolRules.Plan(0,0),state);}
 @Test void sixBoughtRoundsAllowReloadAndOnlyFiveShots(){var state=PistolRules.reload(0,6);for(int i=0;i<5;i++)state=PistolRules.shot(state.loaded(),state.reserve());assertEquals(new PistolRules.Plan(1,0),state);assertThrows(IllegalStateException.class,()->PistolRules.shot(1,0));}
 @Test void partialReloadRefillsAllowanceButChargesOnlyOneExtraRound(){assertEquals(new PistolRules.Plan(6,19),PistolRules.reload(3,20));}
 @Test void fullMagazineCannotChargeAnotherReload(){assertThrows(IllegalStateException.class,()->PistolRules.reload(6,100));}
 @Test void emptyReserveCannotFireEvenWithLoadedMagazine(){assertThrows(IllegalStateException.class,()->PistolRules.shot(6,0));}
 @Test void emptyMagazineCannotFireEvenWithReserve(){assertThrows(IllegalStateException.class,()->PistolRules.shot(0,64));}
 @Test void reloadCannotCreateRoundsWithoutReserve(){assertThrows(IllegalStateException.class,()->PistolRules.reload(0,0));}
 @Test void malformedMagazineOrReserveIsRejected(){for(int n:new int[]{-1,7,Integer.MAX_VALUE}){assertThrows(IllegalArgumentException.class,()->PistolRules.shot(n,10));assertThrows(IllegalArgumentException.class,()->PistolRules.reload(n,10));}assertThrows(IllegalArgumentException.class,()->PistolRules.reload(0,-1));}
 @Test void oneReserveRoundCanPayReloadButCannotAlsoBeFired(){var state=PistolRules.reload(0,1);assertEquals(new PistolRules.Plan(6,0),state);assertThrows(IllegalStateException.class,()->PistolRules.shot(state.loaded(),state.reserve()));}
 @Test void requestedCapacityDamageAndDelayAreFixed(){assertEquals(6,PistolRules.CAPACITY);assertEquals(6,PistolRules.DAMAGE);assertEquals(40,PistolRules.RELOAD_TICKS);assertTrue(PistolRules.LIFETIME_TICKS>0&&PistolRules.MAX_PROJECTILES>0);}
 @Test void cataloguePricesIncludeSingleRoundAndDiscountedSixPack(){assertEquals(100000,ShopCatalogue.find(PistolItems.GUN).total(false,1));assertEquals(500,ShopCatalogue.find(PistolItems.AMMO).total(false,1));assertEquals(2500,ShopCatalogue.find(PistolItems.PACK).total(false,1));assertEquals(3000,ShopCatalogue.find(PistolItems.AMMO).total(false,6));assertEquals(5000,ShopCatalogue.find(PistolItems.PACK).total(false,2));}
 @Test void gunsCannotBeBulkPurchasedAndAmmoCannotBeResold(){assertThrows(IllegalArgumentException.class,()->ShopCatalogue.find(PistolItems.GUN).total(false,2));for(String id:List.of(PistolItems.AMMO,PistolItems.PACK))assertThrows(IllegalArgumentException.class,()->ShopCatalogue.find(id).total(true,1));}
 @Test void allNewProductsAreAccessibleOnSpecialPageAndIdsAreUnique(){for(String id:List.of(PistolItems.GUN,PistolItems.AMMO,PistolItems.PACK))assertEquals(ShopCatalogue.Category.SPECIAL,ShopCatalogue.find(id).category());assertEquals(56,ShopCatalogue.ALL.size());assertEquals(56,new HashSet<>(ShopCatalogue.ALL.stream().map(ShopCatalogue.Product::id).toList()).size());}
 @Test void ammoHudKeepsCoinsQueueAndTeleportNotice(){String text=HudRules.compose(12345,"현재 나의 대기열 숫자: 1","이동·저장까지 5초","권총: 4/6 · 탄환 21개");assertTrue(text.startsWith("코인: 12,345"));for(String part:List.of("권총: 4/6","탄환 21개","대기열 숫자: 1","이동·저장까지 5초"))assertTrue(text.contains(part));}
 @Test void noWeaponDoesNotChangeExistingHud(){assertEquals(HudRules.compose(10,"야생","채광"),HudRules.compose(10,"야생","채광",""));}
 @Test void schema29MigrationPreservesWalletFilesDiscordAndCapacities()throws Exception{Path c=dir.resolve("config.yml"),data=dir.resolve("data.yml");String old="config-version: 29\nqueue:\n  player-slots: 73\n  max-waiting: 19\ndiscord:\n  bot-token: unit-test-token-123456789\n  gateway-owner: controller\n";Files.writeString(c,old);Files.writeString(data,"coins: 876543\n");var result=UpgradeService.migrate(c,Files.readString(Path.of("src/main/resources/config.yml")));var y=YamlConfiguration.loadConfiguration(c.toFile());assertEquals(29,result.from());assertEquals(33,result.to());assertEquals(73,y.getInt("queue.player-slots"));assertEquals(19,y.getInt("queue.max-waiting"));assertEquals("controller",y.getString("discord.gateway-owner"));assertEquals("unit-test-token-123456789",y.getString("discord.bot-token"));assertTrue(y.getBoolean("pistol.enabled"));assertEquals(old,Files.readString(c.resolveSibling("config.yml.before-3.3.bak")));assertEquals("coins: 876543\n",Files.readString(data));assertFalse(UpgradeService.migrate(c,Files.readString(Path.of("src/main/resources/config.yml"))).changed());}
 @Test void existingGunDisabledSettingIsNotOverwritten()throws Exception{Path c=dir.resolve("config.yml");Files.writeString(c,"config-version: 29\npistol:\n  enabled: false\n");UpgradeService.migrate(c,Files.readString(Path.of("src/main/resources/config.yml")));assertFalse(YamlConfiguration.loadConfiguration(c.toFile()).getBoolean("pistol.enabled"));}
 @Test void newCommandsAndAliasesAreRegistered(){var y=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/plugin.yml").toFile());assertEquals("3.3",y.getString("version"));assertTrue(y.getStringList("commands.smppistol.aliases").containsAll(List.of("권총","총기","탄환","pistol")));}
}
