package kr.blackwiki.smp;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Deliberately accepts only one custom dimension leaf, never a server/world root. */
final class ResetPaths {
    static void source(Path source,Path container,Collection<Path> protectedPaths,String namespace,String key)throws IOException{
        Path src=source.toAbsolutePath().normalize(),root=container.toAbsolutePath().normalize();
        if(!namespace.matches("[a-z0-9_.-]+")||!key.matches("[a-z0-9_.-]+")||Set.of("overworld","the_nether","the_end").contains(key))throw new IOException("Not a managed custom dimension key");
        if(!src.startsWith(root)||src.equals(root)||!src.endsWith(Path.of("dimensions",namespace,key)))throw new IOException("Not a custom dimension directory: "+src);
        for(Path other:protectedPaths){Path at=other.toAbsolutePath().normalize();if(src.startsWith(at)||at.startsWith(src))throw new IOException("Reset overlaps a protected world: "+at);}
        noLinks(src);noLinks(root);
    }
    static void archive(Path archive,Path root)throws IOException{Path dest=archive.toAbsolutePath().normalize(),base=root.toAbsolutePath().normalize();if(!dest.startsWith(base)||dest.equals(base))throw new IOException("Invalid reset archive path");noLinks(dest);noLinks(base);}
    static void noLinks(Path path)throws IOException{Path at=path.toAbsolutePath().normalize();while(at!=null){if(Files.isSymbolicLink(at))throw new IOException("Symbolic links are not allowed in reset paths");at=at.getParent();}}
}
