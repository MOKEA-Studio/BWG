package kr.blackwiki.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AdminToolsTest {
 @Test void muteUnitsConvertToSeconds(){assertEquals(30,AdminTools.seconds("초","30",3650));assertEquals(600,AdminTools.seconds("분","10",3650));assertEquals(7200,AdminTools.seconds("시간","2",3650));assertEquals(86400,AdminTools.seconds("d","1",3650));}
 @Test void badMuteInputIsRejected(){assertThrows(IllegalArgumentException.class,()->AdminTools.seconds("주","1",3650));assertThrows(IllegalArgumentException.class,()->AdminTools.seconds("분","0",3650));assertThrows(IllegalArgumentException.class,()->AdminTools.seconds("분","x",3650));assertThrows(IllegalArgumentException.class,()->AdminTools.seconds("일","3651",3650));assertThrows(IllegalArgumentException.class,()->AdminTools.seconds("초",String.valueOf(Long.MAX_VALUE),3650));}
 @Test void whisperCommandsAreCoveredIncludingNamespaces(){assertTrue(AdminTools.CHAT_COMMANDS.contains(DiscordBridge.root("/minecraft:msg Bob hi")));assertTrue(AdminTools.CHAT_COMMANDS.contains(DiscordBridge.root("/r hi")));assertFalse(AdminTools.CHAT_COMMANDS.contains(DiscordBridge.root("/spawn")));}
 @Test void commandsAreRegistered(){var y=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/plugin.yml").toFile());assertTrue(y.getStringList("commands.smpmute.aliases").contains("mute"));assertTrue(y.getStringList("commands.smpadtp.aliases").contains("adtp"));assertTrue(y.getStringList("commands.smpking.aliases").contains("king"));assertTrue(y.getStringList("commands.smphack.aliases").contains("핵"));assertTrue(y.getStringList("commands.smpsafe.aliases").contains("안전일시"));}
 @Test void crownPartsStayCloseAboveTheHeadAndKeepMoving(){for(long f=0;f<200;f++){var h=AdminTools.helmetPose(f).getTranslation();assertTrue(h.y>.2f&&h.y<.4f);for(int i=0;i<AdminTools.SPARKS;i++){var t=AdminTools.sparkPose(i,f);var v=t.getTranslation();assertTrue(Math.hypot(v.x,v.z)<.5&&v.y>.1f&&v.y<.5f);assertTrue(t.getScale().x>.3f&&t.getScale().x<.5f);}}assertNotEquals(AdminTools.sparkPose(0,0).getTranslation(),AdminTools.sparkPose(0,1).getTranslation());}
}
