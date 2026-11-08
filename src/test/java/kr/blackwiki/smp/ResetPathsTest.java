package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResetPathsTest {
    @TempDir Path server;
    @Test void onlySeparateCustomDimensionCanBeReset()throws Exception{Path spawn=server.resolve("world/dimensions/minecraft/overworld"),wild=server.resolve("world/dimensions/minecraft/blackwikismpwild");Files.createDirectories(spawn);Files.createDirectories(wild);assertDoesNotThrow(()->ResetPaths.source(wild,server,List.of(spawn),"minecraft","blackwikismpwild"));assertThrows(IOException.class,()->ResetPaths.source(spawn,server,List.of(spawn),"minecraft","overworld"));assertThrows(IOException.class,()->ResetPaths.source(server.resolve("world"),server,List.of(spawn),"minecraft","blackwikismpwild"));assertThrows(IOException.class,()->ResetPaths.source(wild,server,List.of(wild.resolve("region")),"minecraft","blackwikismpwild"));assertThrows(IOException.class,()->ResetPaths.source(wild,server,List.of(wild.getParent()),"minecraft","blackwikismpwild"));}
    @Test void pathTraversalAndLinksAreRejected()throws Exception{Path root=server.resolve("world/dimensions/minecraft"),real=server.resolve("real");Files.createDirectories(root);Files.createDirectories(real);Path linked=root.resolve("blackwikismpwild");Files.createSymbolicLink(linked,real);assertThrows(IOException.class,()->ResetPaths.source(linked,server,List.of(),"minecraft","blackwikismpwild"));assertThrows(IOException.class,()->ResetPaths.source(server.getParent().resolve("dimensions/minecraft/blackwikismpwild"),server,List.of(),"minecraft","blackwikismpwild"));assertThrows(IOException.class,()->ResetPaths.archive(server.resolve("elsewhere"),server.resolve("archives")));assertThrows(IOException.class,()->ResetPaths.source(root.resolve("blackwikismpwild"),server,List.of(),"minecraft","../wild"));}
}
