package kr.blackwiki.smp;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudTest {
 @Test void titleTextStaysTheSameWhileColorsMove(){var plain=PlainTextComponentSerializer.plainText();for(long f=0;f<60;f++)assertEquals("✦ "+HudService.TITLE+" ✦",plain.serialize(HudService.title(f)));assertNotEquals(HudService.title(0),HudService.title(1));assertEquals(HudService.title(0),HudService.title(HudService.TITLE.length()+12));}
 @Test void lerpHitsEndpoints(){assertEquals(0x5CE1FF,HudService.lerp(0x5CE1FF,0xB57BFF,0));assertEquals(0xB57BFF,HudService.lerp(0x5CE1FF,0xB57BFF,1));}
 @Test void versionComesFromPluginYml(){var y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.nio.file.Path.of("src/main/resources/plugin.yml").toFile());assertEquals(y.getString("version"),Version.V);}
}
