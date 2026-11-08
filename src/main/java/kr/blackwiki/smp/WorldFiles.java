package kr.blackwiki.smp;

import java.nio.file.*;
import java.io.IOException;

final class WorldFiles {
    /** Paper 26.2 exposes a dimension path. Back up its save root, including players and level.dat. */
    static Path saveRoot(Path dimension,Path container)throws IOException{
        Path root=container.toRealPath(),at=dimension.toRealPath();if(!at.startsWith(root)||at.equals(root))throw new IOException("World path is outside the server container");
        while(at!=null&&at.startsWith(root)&&!at.equals(root)){if(Files.isRegularFile(at.resolve("level.dat"))){ResetPaths.noLinks(at);return at;}at=at.getParent();}
        throw new IOException("Cannot find level.dat save root for "+dimension);
    }
}
