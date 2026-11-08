package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoUpdateTest {
 @TempDir Path dir;
 @Test void versionsCompareNumerically(){assertTrue(AutoUpdateService.compare("3.10","3.9")>0);assertTrue(AutoUpdateService.compare("3.4","3.3")>0);assertEquals(0,AutoUpdateService.compare("3.3","3.3.0"));assertTrue(AutoUpdateService.compare("3.3","3.4")<0);}
 @Test void oddTagsAreRejected(){assertThrows(NumberFormatException.class,()->AutoUpdateService.compare("3.4-beta","3.3"));}
 @Test void readsPluginNameAndVersionFromJar()throws Exception{Path jar=dir.resolve("a.jar");try(var out=new JarOutputStream(Files.newOutputStream(jar))){out.putNextEntry(new JarEntry("plugin.yml"));out.write("name: BlackWikiSMP\nversion: '3.4'\n".getBytes());}assertArrayEquals(new String[]{"BlackWikiSMP","3.4"},AutoUpdateService.describe(jar));}
}
