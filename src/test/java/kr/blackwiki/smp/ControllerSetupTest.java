package kr.blackwiki.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class ControllerSetupTest {
    @TempDir Path dir;
    Path config()throws Exception{Files.createDirectories(dir.resolve("plugins/BlackWikiSMP"));Path file=dir.resolve("plugins/BlackWikiSMP/config.yml");Files.writeString(file,"config-version: 28\nqueue:\n  player-slots: 73\ndiscord:\n  bot-token: unit-test-token-123456789\n  channel-id: '222222222222222222'\n");return file;}
    void paper()throws Exception{Manifest m=new Manifest();m.getMainAttributes().putValue("Manifest-Version","1.0");m.getMainAttributes().putValue("Main-Class","io.papermc.paperclip.Main");try(var out=new JarOutputStream(Files.newOutputStream(dir.resolve("paper.jar")),m)){}}
    @Test void setupPreservesExistingConfigAndCreatesPrivateKey()throws Exception{Path c=config();String before=Files.readString(c);paper();DiscordController.setup(dir);var y=YamlConfiguration.loadConfiguration(c.toFile());assertEquals("controller",y.getString("discord.gateway-owner"));assertEquals(73,y.getInt("queue.player-slots"));assertEquals("unit-test-token-123456789",y.getString("discord.bot-token"));assertEquals(before,Files.readString(c.resolveSibling("config.yml.before-discord-controller.bak")));assertFalse(Files.readString(dir.resolve("blackwikismp-controller/controller.yml")).contains("unit-test-token"));assertEquals(ControlApi.secret(c.getParent()),ControlApi.secret(c.getParent()));}
    @Test void rerunPreservesExactUserCommandArguments()throws Exception{config();paper();DiscordController.setup(dir);Path f=dir.resolve("blackwikismp-controller/controller.yml");YamlConfiguration y=YamlConfiguration.loadConfiguration(f.toFile());List<String> command=List.of("/path with spaces/java","-Xmx9G","-jar","paper custom.jar","--nogui");y.set("server-command",command);y.save(f.toFile());DiscordController.setup(dir);assertEquals(command,YamlConfiguration.loadConfiguration(f.toFile()).getStringList("server-command"));}
    @Test void setupRefusesLiveWorldAndDoesNotTouchConfig()throws Exception{Path c=config();String before=Files.readString(c);paper();Path w=dir.resolve("world");Files.createDirectories(w);try(FileChannel channel=FileChannel.open(w.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.lock()){assertThrows(Exception.class,()->DiscordController.setup(dir));assertEquals(before,Files.readString(c));assertFalse(Files.exists(dir.resolve("blackwikismp-controller/controller.yml")));}}
    @Test void futureSchemaAndMalformedYamlAreNeverOverwritten()throws Exception{Path c=config();paper();for(String value:List.of("config-version: 34\n","secret: [\n")){Files.writeString(c,value);assertThrows(Exception.class,()->DiscordController.setup(dir));assertEquals(value,Files.readString(c));}}
    @Test void genuine28ConfigGetsNewDiscordDefaultsWithoutChangingControls()throws Exception{Path c=config();var y=YamlConfiguration.loadConfiguration(c.toFile());y.set("discord.control.enabled",false);y.set("discord.relay-chat",false);y.save(c.toFile());String old=Files.readString(c);String defaults=Files.readString(Path.of("src/main/resources/config.yml"));var result=UpgradeService.migrate(c,defaults);var after=YamlConfiguration.loadConfiguration(c.toFile());assertEquals(28,result.from());assertEquals(33,result.to());assertFalse(after.getBoolean("discord.control.enabled"));assertFalse(after.getBoolean("discord.relay-chat"));assertTrue(after.getBoolean("discord.relay-discord-chat"));assertTrue(after.getBoolean("discord.relay-player-events"));assertEquals("plugin",after.getString("discord.gateway-owner"));assertEquals(old,Files.readString(c.resolveSibling("config.yml.before-3.3.bak")));assertFalse(UpgradeService.migrate(c,defaults).changed());}
}
