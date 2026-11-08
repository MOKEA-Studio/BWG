package kr.blackwiki.smp;
import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class AdminCoinRulesTest {
 @Test void positiveIntegerAmountsAreAccepted(){for(String n:new String[]{"1","1000","00012",Long.MAX_VALUE+""})assertTrue(AdminCoinRules.amount(n)>0);}
 @Test void invalidAmountsNeverChangeBalance(){for(String n:new String[]{"0","-1","+1","1.5","1e3","NaN",""," ","1,000","9223372036854775808","9999999999999999999999"})assertThrows(IllegalArgumentException.class,()->AdminCoinRules.amount(n));assertThrows(IllegalArgumentException.class,()->AdminCoinRules.amount(null));}
 @Test void grantAndDebitUseExactDeltas(){assertEquals(1500,AdminCoinRules.balance(1000,500,false));assertEquals(500,AdminCoinRules.balance(1000,500,true));assertEquals(0,AdminCoinRules.balance(1000,1000,true));}
 @Test void insufficientBalanceIsRejectedInsteadOfClamped(){assertThrows(IllegalArgumentException.class,()->AdminCoinRules.balance(100,101,true));}
 @Test void overflowIsRejectedInsteadOfWrapping(){assertThrows(IllegalArgumentException.class,()->AdminCoinRules.balance(Long.MAX_VALUE,1,false));assertEquals(Long.MAX_VALUE,AdminCoinRules.balance(Long.MAX_VALUE-1,1,false));}
 @Test void malformedStoredBalanceAndZeroAreRejected(){assertThrows(IllegalArgumentException.class,()->AdminCoinRules.balance(-1,1,false));for(long n:new long[]{0,-1,Long.MIN_VALUE})assertThrows(IllegalArgumentException.class,()->AdminCoinRules.balance(100,n,true));}
 @Test void requestedKoreanAliasesAreRegistered(){var y=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/plugin.yml").toFile());assertTrue(y.getStringList("commands.smpcoingive.aliases").contains("코인지급"),y.saveToString());assertTrue(y.getStringList("commands.smpcoindebit.aliases").contains("코인차감"),y.saveToString());assertTrue(y.getStringList("commands.smpcommands.aliases").contains("명령어"));assertEquals("3.4",y.getString("version"));}
}
