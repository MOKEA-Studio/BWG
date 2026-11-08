package kr.blackwiki.smp;

import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.JarFile;

/** Offline CLI only. Never invoked by Bukkit; Python supplies Paper's existing libraries. */
public final class BedrockInstaller {
    private record Dependency(String name,Path source){}
    private BedrockInstaller(){}
    public static void main(String[] args){try{install(args);}catch(Exception ex){System.err.println("설치 실패: "+ex.getMessage());System.exit(2);}}
    private static void install(String[] args)throws Exception{
        if(Runtime.version().feature()<25)throw new IOException("Java 25 이상으로 실행하세요.");
        Map<String,String> options=new HashMap<>();for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("잘못된 설치 인수");options.put(args[i],args[i+1]);}
        Path root=Path.of(Objects.requireNonNull(options.get("--server"))).toRealPath(),bundle=Path.of(Objects.requireNonNull(options.get("--bundle"))).toRealPath();
        if(!Files.isRegularFile(root.resolve("server.properties")))throw new IOException("server.properties가 있는 Paper 서버 폴더를 지정하세요.");
        Properties properties=new Properties();try(var in=Files.newBufferedReader(root.resolve("server.properties"),StandardCharsets.UTF_8)){properties.load(in);}
        List<FileChannel> channels=new ArrayList<>();List<FileLock> locks=new ArrayList<>();
        try{
            try(var dirs=Files.list(root)){for(Path dir:dirs.filter(Files::isDirectory).toList()){Path lock=dir.resolve("session.lock");if(!Files.isRegularFile(lock))continue;FileChannel channel=FileChannel.open(lock,StandardOpenOption.READ,StandardOpenOption.WRITE);channels.add(channel);FileLock held=channel.tryLock();if(held==null)throw new IOException("서버가 실행 중입니다. 완전히 종료한 뒤 설치하세요.");locks.add(held);}}
            Path plugins=root.resolve("plugins"),config=plugins.resolve("BlackWikiSMP/config.yml"),gFile=plugins.resolve("Geyser-Spigot/config.yml"),fFile=plugins.resolve("floodgate/config.yml");
            YamlConfiguration smp=load(config),g=load(gFile),f=load(fFile);
            if(smp.getInt("config-version",26)>40)throw new IOException("3.3보다 최신 설정입니다. 설치를 중단했습니다.");
            int port=options.containsKey("--port")?Integer.parseInt(options.get("--port")):g.getInt("bedrock.port",smp.getInt("bedrock.port",19132));
            int javaPort=Integer.parseInt(properties.getProperty("server-port","25565"));if(port<1||port>65535||port==javaPort)throw new IOException("Java 서버와 다른 베드락 포트를 선택하세요. 기본값은 19132입니다.");
            Map<Path,byte[]> writes=new LinkedHashMap<>();Map<Path,Path> archives=new LinkedHashMap<>();
            Path main=locate(bundle,"BlackWikiSMP-3.3.jar");if(!"BlackWikiSMP".equals(pluginName(main)))throw new IOException("3.3 플러그인 JAR를 확인하세요.");
            List<Dependency> deps=new ArrayList<>(List.of(new Dependency("BlackWikiSMP",main)));
            JsonArray manifest=JsonParser.parseString(Files.readString(bundle.resolve("bedrock/dependencies.json"))).getAsJsonArray();
            for(JsonElement element:manifest){JsonObject d=element.getAsJsonObject();Path src=locate(bundle,d.get("file_name").getAsString());String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(src)));if(!sha.equals(d.get("sha256").getAsString()))throw new IOException("공식 플러그인 검증 실패: "+src.getFileName());String name=pluginName(src);if(name==null)throw new IOException("플러그인 설명자가 없습니다: "+src.getFileName());deps.add(new Dependency(name,src));}
            Map<String,Path> destinations=new LinkedHashMap<>();
            for(Dependency d:deps){Path target=plugins.resolve(d.source.getFileName());destinations.put(d.name,target);if(Files.exists(target)&&!d.name.equals(pluginName(target)))throw new IOException("다른 파일과 이름이 겹칩니다: "+target.getFileName());writes.put(target,Files.readAllBytes(d.source));}
            String stamp=DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").format(ZonedDateTime.now(ZoneId.of("Asia/Seoul")));Path archive=root.resolve("disabled-plugins/BlackWikiSMP-3.3-"+stamp);
            if(Files.isDirectory(plugins))try(var files=Files.list(plugins)){for(Path jar:files.filter(p->p.getFileName().toString().endsWith(".jar")).toList()){String name=pluginName(jar);Path target=destinations.get(name);if(target!=null&&(!jar.equals(target)||!Arrays.equals(Files.readAllBytes(jar),writes.get(target))))archives.put(jar,archive.resolve("plugins").resolve(jar.getFileName()));}}
            if(!Files.exists(gFile))g=resource("/bedrock/geyser.yml");if(!Files.exists(fFile))f=resource("/bedrock/floodgate.yml");
            String auth=g.isConfigurationSection("java")||g.getInt("config-version",0)>=5?"java.auth-type":"remote.auth-type";g.set(auth,"floodgate");g.set("bedrock.port",port);g.set("bedrock.clone-remote-port",false);
            f.set("username-prefix",".");f.set("replace-spaces",true);if(f.getString("metrics.uuid","").contains("${")||!f.isSet("metrics.uuid"))f.set("metrics.uuid",UUID.randomUUID().toString());
            smp.set("bedrock.port",port);if(options.containsKey("--public-address"))smp.set("bedrock.public-address",BedrockSetup.address(options.get("--public-address")));
            writes.put(gFile,g.saveToString().getBytes(StandardCharsets.UTF_8));writes.put(fFile,f.saveToString().getBytes(StandardCharsets.UTF_8));writes.put(config,smp.saveToString().getBytes(StandardCharsets.UTF_8));
            Path serverProperties=root.resolve("server.properties");String before=Files.readString(serverProperties);String after=before.replaceAll("(?m)^enforce-secure-profile=.*$","enforce-secure-profile=false");if(!after.matches("(?s).*\\benforce-secure-profile=.*"))after+=(after.endsWith("\n")?"":"\n")+"enforce-secure-profile=false\n";writes.put(serverProperties,after.getBytes(StandardCharsets.UTF_8));
            writes.entrySet().removeIf(e->{try{return Files.isRegularFile(e.getKey())&&Arrays.equals(Files.readAllBytes(e.getKey()),e.getValue());}catch(IOException ex){return false;}});
            for(Path path:writes.keySet())within(root,path);for(var entry:archives.entrySet()){within(root,entry.getKey());within(root,entry.getValue());}
            if(writes.isEmpty()&&archives.isEmpty()){System.out.println("이미 BlackWikiSMP 3.3과 베드락 설정이 적용되어 있습니다.");return;}
            // Validate everything first, then restore old files if any installation write fails.
            List<Map.Entry<Path,Path>> moved=new ArrayList<>();List<Path> installed=new ArrayList<>();Map<Path,Path> originals=new LinkedHashMap<>();
            try{
                for(var entry:archives.entrySet()){Files.createDirectories(entry.getValue().getParent());Files.move(entry.getKey(),entry.getValue());moved.add(entry);}
                for(var entry:writes.entrySet()){Path target=entry.getKey();if(Files.isRegularFile(target)){Path copy=archive.resolve("settings").resolve(root.relativize(target));Files.createDirectories(copy.getParent());Files.copy(target,copy);originals.put(target,copy);}installed.add(target);atomic(target,entry.getValue());}
            }catch(Exception ex){
                boolean restored=true;Collections.reverse(installed);for(Path target:installed)try{Path original=originals.get(target);if(original!=null)Files.move(original,target,StandardCopyOption.REPLACE_EXISTING);else if(Files.exists(target))Files.delete(target);}catch(IOException failure){restored=false;}
                Collections.reverse(moved);for(var entry:moved)try{Files.move(entry.getValue(),entry.getKey(),StandardCopyOption.REPLACE_EXISTING);}catch(IOException failure){restored=false;}
                throw new IOException(restored?"설치 쓰기에 실패해 이전 파일로 복원했습니다.":"설치 쓰기에 실패했습니다. 보관 폴더에서 복구하세요: "+root.relativize(archive));
            }
            System.out.println("BlackWikiSMP 3.3 + Geyser + Floodgate + ViaVersion 설치 완료");System.out.println("이전 JAR·변경 전 설정 보관 위치: "+root.relativize(archive));System.out.println("베드락 닉네임: .닉네임 / 접속 포트: "+port+" (TCP와 UDP)");System.out.println("Java 25로 서버를 시작하세요. 기존 월드와 플레이어 데이터는 유지됩니다.");
        }finally{for(FileLock held:locks)held.close();for(FileChannel c:channels)c.close();}
    }
    private static Path locate(Path bundle,String file)throws IOException{for(Path p:List.of(bundle.resolve("binary").resolve(file),bundle.resolve("plugins").resolve(file),bundle.resolve("bedrock/dependencies").resolve(file)))if(Files.isRegularFile(p))return p;throw new IOException("필요한 JAR가 없습니다: "+file);}
    private static YamlConfiguration load(Path file)throws Exception{YamlConfiguration y=new YamlConfiguration();if(Files.exists(file))try{y.load(file.toFile());}catch(Exception ex){throw new IOException("YAML 형식 확인 필요: "+file.getFileName());}return y;}
    private static YamlConfiguration resource(String name)throws Exception{YamlConfiguration y=new YamlConfiguration();try(var in=Objects.requireNonNull(BedrockInstaller.class.getResourceAsStream(name))){y.loadFromString(new String(in.readAllBytes(),StandardCharsets.UTF_8));}return y;}
    private static String pluginName(Path path){try(JarFile jar=new JarFile(path.toFile())){var entry=jar.getJarEntry("plugin.yml");if(entry==null)entry=jar.getJarEntry("paper-plugin.yml");if(entry==null)return null;YamlConfiguration y=new YamlConfiguration();try(var in=jar.getInputStream(entry)){y.loadFromString(new String(in.readAllBytes(),StandardCharsets.UTF_8));}return y.getString("name");}catch(Exception ex){return null;}}
    private static void within(Path root,Path path)throws IOException{Path candidate=path.toAbsolutePath().normalize();if(!candidate.startsWith(root)||Files.isSymbolicLink(candidate))throw new IOException("서버 밖 경로 또는 연결 파일입니다: "+path.getFileName());while(!Files.exists(candidate))candidate=candidate.getParent();if(!candidate.toRealPath().startsWith(root))throw new IOException("서버 밖 연결 경로입니다: "+path.getFileName());}
    private static void atomic(Path path,byte[] bytes)throws IOException{Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".install29.tmp");try(var out=new FileOutputStream(temp.toFile())){out.write(bytes);out.getFD().sync();}try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}
}
