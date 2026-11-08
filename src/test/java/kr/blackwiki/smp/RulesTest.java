package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
 @TempDir Path temp;
 @Test void koreanAndUnicodeNamesHaveTwelveCodepointLimit(){assertTrue(Rules.validTeamName("블랙위키팀_123",12));assertTrue(Rules.validTeamName("가".repeat(12),12));assertFalse(Rules.validTeamName("가".repeat(13),12));assertTrue(Rules.validTeamName("𝔄".repeat(12),12));assertFalse(Rules.validTeamName("𝔄".repeat(13),12));}
 @Test void namesCannotInjectFormattingOrYamlPaths(){for(String s:List.of(""," ","a.b","팀 이름","&cOP","§4","팀\n명","/op","<red>"))assertFalse(Rules.validTeamName(s,12),s);}
 @Test void exactBoundaryAndDiagonalUseSquare(){assertFalse(Rules.outside(10000,-10000,0,0,20000));assertFalse(Rules.outside(9999,9999,0,0,20000));assertTrue(Rules.outside(10000.001,0,0,0,20000));assertTrue(Rules.outside(0,-10000.001,0,0,20000));}
 @Test void shiftedBorderAndNonFiniteAreHandled(){assertFalse(Rules.outside(15000,-5000,5000,5000,20000));assertTrue(Rules.outside(Double.NaN,0,0,0,20000));assertTrue(Rules.outside(Double.POSITIVE_INFINITY,0,0,0,20000));assertEquals(1,Rules.edgeDistance(9999,10,0,0,20000));}
 @Test void fifoIsStableAcrossDuplicateJoinRemovalAndReconnect(){Rules.Fifo q=new Rules.Fifo();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();q.add(a);q.add(b);q.add(c);assertFalse(q.add(b));assertEquals(2,q.position(b));q.remove(b);assertEquals(List.of(a,c),q.list());q.add(b);assertEquals(List.of(a,c,b),q.list());q.remove(a);assertEquals(c,q.peek());assertEquals(0,q.position(a));}
 @Test void fifoCannotBeModifiedThroughSnapshot(){Rules.Fifo q=new Rules.Fifo();q.add(UUID.randomUUID());assertThrows(UnsupportedOperationException.class,()->q.list().clear());assertEquals(1,q.size());}
 @Test void durationKeepsDaysAndNeverGoesNegative(){assertEquals("1일 2시간 3분 4초",Rules.duration(93784));assertEquals("0일 0시간 0분 0초",Rules.duration(-1));}
 @Test void atomicSaveReplacesCompleteUtf8AndLeavesNoTemporaryFile()throws Exception{Path f=temp.resolve("data.yml");DataStore.atomic(f,"팀: 첫번째\n");DataStore.atomic(f,"팀: 두번째\n");assertEquals("팀: 두번째\n",Files.readString(f));assertFalse(Files.exists(temp.resolve("data.yml.tmp")));}
 @Test void backupDoesNotFollowSymlinksOrCopySessionLocks()throws Exception{Path world=Files.createDirectory(temp.resolve("world"));Files.writeString(world.resolve("session.lock"),"locked");Files.writeString(world.resolve("good.mca"),"region-data");Files.writeString(world.resolve("partial.tmp"),"partial");Path secret=Files.writeString(temp.resolve("outside.txt"),"outside-world");Files.createSymbolicLink(world.resolve("linked.txt"),secret);Path zip=temp.resolve("backup.zip");try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){BackupService.zipTree(out,world,"worlds/0-world/");}try(ZipFile z=new ZipFile(zip.toFile())){assertEquals(1,z.size());assertEquals("region-data",new String(z.getInputStream(z.getEntry("worlds/0-world/good.mca")).readAllBytes()));}}
}
