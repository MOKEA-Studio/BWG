package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class WorldFilesTest {
    @TempDir Path server;
    @Test void dimensionBackupIncludesPlayerAndGlobalDataRoot()throws Exception{Path world=server.resolve("world"),dimension=world.resolve("dimensions/minecraft/blackwikismpwild");Files.createDirectories(dimension);Files.createDirectories(world.resolve("players/data"));Files.writeString(world.resolve("level.dat"),"test");assertEquals(world.toRealPath(),WorldFiles.saveRoot(dimension,server));}
    @Test void missingOrExternalSaveRootFailsClosed()throws Exception{Path dimension=server.resolve("world/dimensions/minecraft/overworld");Files.createDirectories(dimension);assertThrows(IOException.class,()->WorldFiles.saveRoot(dimension,server));assertThrows(IOException.class,()->WorldFiles.saveRoot(server.getParent(),server));}
}
