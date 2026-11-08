package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

final class BackupService implements AutoCloseable {
    private final BlackWikiSMP p;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("BlackWikiSMP-backup").factory());
    private final Map<World,Boolean> saves=new LinkedHashMap<>();
    private volatile boolean running;
    private volatile String last="아직 백업하지 않았습니다.";
    private Future<?> job;
    private long next;
    BackupService(BlackWikiSMP p){this.p=p;resetTimer();}
    boolean running(){return running;}
    String status(){return running?"백업 진행 중":"대기 중";}
    String lastResult(){return last;}
    void resetTimer(){next=System.currentTimeMillis()+p.cfg().getLong("backup.interval-minutes",60)*60000;}
    void tick(){if(p.cfg().getBoolean("backup.enabled",true)&&System.currentTimeMillis()>=next){resetTimer();start(Bukkit.getConsoleSender());}}
    private Path directory(){String name=p.cfg().getString("backup.directory","");return name.isBlank()?Bukkit.getWorldContainer().toPath().resolve("backups/BlackWikiSMP").toAbsolutePath().normalize():Path.of(name).toAbsolutePath().normalize();}
    void start(CommandSender actor){
        if(p.spawnDesign!=null&&p.spawnDesign.busy()){p.tell(actor,"스폰 생성 완료 후 백업하세요.");return;}
        if(p.resets!=null&&p.resets.busy()){p.tell(actor,"야생 초기화·복구 중에는 백업할 수 없습니다.");return;}
        if(running){p.tell(actor,"이미 백업을 진행 중입니다.");return;}running=true;
        try {
            Path requested=directory();Files.createDirectories(requested);Path dest=requested.toRealPath();List<Path> roots=new ArrayList<>();
            if(p.cfg().getBoolean("backup.include-worlds",true))for(World w:Bukkit.getWorlds()){Path root=WorldFiles.saveRoot(w.getWorldPath(),Bukkit.getWorldContainer().toPath());if(dest.startsWith(root))throw new IOException("Backup directory must be outside every world folder");if(roots.stream().noneMatch(root::startsWith)){roots.removeIf(r->r.startsWith(root));roots.add(root);}}
            Map<String,byte[]> pluginSnapshot=new LinkedHashMap<>();
            pluginSnapshot.put("data.yml",p.data.snapshot().getBytes(StandardCharsets.UTF_8));
            pluginSnapshot.put("config.yml",p.cfg().saveToString().getBytes(StandardCharsets.UTF_8));
            pluginSnapshot.put("transfers.yml",p.transfers.ledger.snapshot().getBytes(StandardCharsets.UTF_8));
            pluginSnapshot.put("market.yml",p.market.ledger.snapshot().getBytes(StandardCharsets.UTF_8));
            Path resetFile=p.getDataFolder().toPath().resolve("wild-reset.yml");if(Files.isRegularFile(resetFile))pluginSnapshot.put("wild-reset.yml",Files.readAllBytes(resetFile));
            for(String name:List.of("graves.yml","event.yml","spawn-build.yml","upgrade.yml","discord-control.key")){Path extra=p.getDataFolder().toPath().resolve(name);if(Files.isRegularFile(extra))pluginSnapshot.put(name,Files.readAllBytes(extra));}
            pluginSnapshot.put("bans.yml",p.bans.snapshot().getBytes(StandardCharsets.UTF_8));
            Path npcFile=p.getDataFolder().toPath().resolve("npcs.yml");if(Files.isRegularFile(npcFile))pluginSnapshot.put("npcs.yml",Files.readAllBytes(npcFile));
            Future<?> flush=p.data.flushFuture();
            // Paper 26.2's save(true) flushes region writes synchronously. Nested command dispatch
            // can be deferred, so save-off/save-all console commands cannot be used as a barrier.
            for(World w:Bukkit.getWorlds()){saves.put(w,w.isAutoSave());w.setAutoSave(false);w.save(true);}
            for(org.bukkit.entity.Player player:Bukkit.getOnlinePlayers())player.saveData();
            List<Path> worldRoots=List.copyOf(roots);int keep=p.cfg().getInt("backup.keep",8);Path pluginDir=p.getDataFolder().toPath().toAbsolutePath();
            p.tell(actor,"백업을 시작했습니다. 월드 저장을 잠시 멈추고 압축합니다.");
            job=worker.submit(()->{
                Path partial=null;
                try {
                    if(flush!=null)flush.get(60,TimeUnit.SECONDS);Files.createDirectories(dest);
                    String name="BlackWikiSMP-"+ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS"))+".zip";
                    Path complete=dest.resolve(name);partial=dest.resolve(name+".partial");
                    try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(partial)))){
                        out.setLevel(1);putText(out,"RESTORE.txt","BlackWikiSMP 3.3 backup\nStop server before restoring. Restore worlds/ directories to their original paths and plugins/BlackWikiSMP/ to plugins/BlackWikiSMP/. Never restore while the server is running.\n");
                        StringBuilder manifest=new StringBuilder("created="+Instant.now()+"\n");int i=0;
                        for(Path root:worldRoots){String entry="worlds/"+(i++)+"-"+root.getFileName()+"/";manifest.append(entry).append(" -> ").append(root).append('\n');zipTree(out,root,entry);}
                        // Plugin files are copied from a separate immutable snapshot to avoid live write races.
                        for(var snapshot:pluginSnapshot.entrySet()){out.putNextEntry(new ZipEntry("plugins/BlackWikiSMP/"+snapshot.getKey()));out.write(snapshot.getValue());out.closeEntry();}
                        putText(out,"manifest.txt",manifest.toString());
                    }
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Backup cancelled");
                    // Publish only verified archives; failed or interrupted copies never count toward retention.
                    try(ZipFile zip=new ZipFile(partial.toFile())){byte[] buffer=new byte[65536];for(ZipEntry e:Collections.list(zip.entries()))try(InputStream in=zip.getInputStream(e)){CRC32 crc=new CRC32();for(int read;(read=in.read(buffer))!=-1;)crc.update(buffer,0,read);if(crc.getValue()!=e.getCrc())throw new IOException("Backup CRC mismatch: "+e.getName());}}
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Backup cancelled");
                    try{Files.move(partial,complete,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(partial,complete);}
                    try(var files=Files.list(dest)){List<Path> archives=files.filter(f->f.getFileName().toString().matches("BlackWikiSMP-\\d{8}-\\d{6}-\\d{3}\\.zip")).sorted(Comparator.reverseOrder()).toList();for(int n=keep;n<archives.size();n++)Files.delete(archives.get(n));}
                    last="완료: "+name;p.sync(()->p.tell(actor,"백업 완료: "+name));
                }catch(Exception e){last="실패: "+e.getClass().getSimpleName();p.getLogger().log(java.util.logging.Level.SEVERE,"Backup failed; previous archives retained",e);p.sync(()->p.tell(actor,"백업 실패. 콘솔 로그를 확인하세요. 이전 백업은 유지됩니다."));if(partial!=null)try{Files.deleteIfExists(partial);}catch(IOException ignored){}
                }finally{p.sync(()->{restore();running=false;p.graves.afterBackup();p.transfers.tick();for(var a:Bukkit.getOnlinePlayers())p.market.reconcile(a);});}
            });
        }catch(Exception e){restore();running=false;last="실패: "+e.getMessage();p.tell(actor,"백업을 시작할 수 없습니다: "+e.getMessage());p.getLogger().log(java.util.logging.Level.SEVERE,"Backup preparation failed",e);}
    }
    private static void putText(ZipOutputStream out,String name,String value)throws IOException{out.putNextEntry(new ZipEntry(name));out.write(value.getBytes(StandardCharsets.UTF_8));out.closeEntry();}
    static void zipTree(ZipOutputStream out,Path root,String prefix)throws IOException {
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path dir,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();return Files.isSymbolicLink(dir)?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(!attrs.isRegularFile()||Files.isSymbolicLink(file)||file.getFileName().toString().equals("session.lock")||file.getFileName().toString().endsWith(".tmp"))return FileVisitResult.CONTINUE;
                out.putNextEntry(new ZipEntry(prefix+root.relativize(file).toString().replace(File.separatorChar,'/')));Files.copy(file,out);out.closeEntry();return FileVisitResult.CONTINUE;
            }
        });
    }
    private void restore(){for(var e:saves.entrySet())e.getKey().setAutoSave(e.getValue());saves.clear();}
    void applyAntiXray(CommandSender actor){p.antiXray.configure(actor,true);}
    void antiStatus(CommandSender actor){p.antiXray.status(actor);}
    @Override public void close(){if(job!=null)job.cancel(true);worker.shutdownNow();restore();try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}running=false;}
}
