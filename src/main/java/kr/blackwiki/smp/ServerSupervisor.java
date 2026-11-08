package kr.blackwiki.smp;

import com.google.gson.JsonObject;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Owns only processes it started. Discord input is never used as a program or argument. */
final class ServerSupervisor implements PowerCommands.Actions,AutoCloseable {
    private final Path root,log;
    private final String identity;
    private final List<String> command;
    private final ControlApi.Client client;
    private final Consumer<String> notice;
    private Process child;
    private boolean online,stopRequested,observed,closed;
    private String state="중지됨";
    private int players,queue,misses;
    ServerSupervisor(Path root,List<String> command,Path log,ControlApi.Client client,Consumer<String> notice)throws IOException{
        this.root=root.toRealPath();try{identity=ControlApi.identity(this.root);}catch(Exception ex){throw new IOException("서버 폴더 식별 실패");}this.command=List.copyOf(command);this.log=log;this.client=client;this.notice=notice;
        if(command.isEmpty()||command.size()>100||command.stream().anyMatch(s->s==null||s.isBlank()||s.indexOf('\0')>=0))throw new IllegalArgumentException("server-command는 실행 파일과 인수의 목록이어야 합니다.");
        if(!log.toAbsolutePath().normalize().startsWith(this.root))throw new IOException("로그는 서버 폴더 안에 저장하세요.");
    }
    static boolean worldLocked(Path root)throws IOException{
        try(var dirs=Files.list(root)){
            for(Path dir:dirs.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList()){
                Path path=dir.resolve("session.lock");if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                try(FileChannel file=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE)){
                    try(FileLock lock=file.tryLock()){if(lock==null)return true;}catch(OverlappingFileLockException ex){return true;}
                }
            }
        }return false;
    }
    synchronized void poll(){
        if(closed)return;
        if(child!=null&&!child.isAlive()){
            int code=child.exitValue();child=null;online=false;stopRequested=false;observed=false;players=queue=0;state=code==0?"중지됨":"프로세스 종료 (코드 "+code+")";
            notice.accept(code==0?"[서버 종료] 저장 후 서버가 종료되었습니다.":"[서버 종료] 프로세스가 종료되었습니다 (코드 "+code+"). 서버 로그를 확인하세요.");
            return;
        }
        try{
            JsonObject s=client.status();if(!target(s)){online=false;state="제어 포트의 다른 서버 응답 감지 · 포트를 확인하세요";return;}boolean ready=s.has("ready")&&s.get("ready").getAsBoolean();misses=0;
            players=s.has("online")?s.get("online").getAsInt():0;queue=s.has("queue")?s.get("queue").getAsInt():0;
            if(ready){online=true;state="실행 중";if(!observed){observed=true;notice.accept("[서버 시작] BlackWikiSMP 3.3 시작 완료 · 접속 가능");}}
            else{online=false;state=stopRequested||DiscordInbound.text(s,"state").equals("stopping")?"저장·종료 중":"시작 중";}
        }catch(Exception ex){
            online=false;misses++;
            if(child!=null){state=stopRequested?"저장·종료 중":observed?"프로세스 실행 중 · 플러그인 연결 확인 필요":"시작 중";return;}
            try{if(worldLocked(root)){state="다른 실행 중인 서버 감지 · 연결 확인 필요";return;}}catch(IOException failure){state="월드 잠금 상태 확인 필요";return;}
            state="중지됨";if(observed&&misses>=3){observed=false;players=queue=0;notice.accept("[서버 종료] 서버 종료를 감지했습니다.");}
        }
    }
    @Override public synchronized String start(){
        if(closed)return "도우미가 종료 중입니다.";
        if(child!=null&&child.isAlive()||online||stopRequested)return "서버가 이미 실행 중이거나 시작·종료 중입니다. "+status();
        try{
            if(worldLocked(root))return "서버가 이미 실행 중입니다. 중복 실행을 차단했습니다.";
            // Check the authenticated endpoint as well, including servers with a custom world layout.
            try{client.status();return "서버 플러그인이 이미 응답합니다. 중복 실행을 차단했습니다.";}catch(Exception ignored){}
            Files.createDirectories(log.getParent());
            child=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
            online=false;observed=false;stopRequested=false;players=queue=misses=0;state="시작 중";
            return "서버 시작을 요청했습니다. 준비가 끝나면 시작 완료 알림을 보냅니다.";
        }catch(Exception ex){return "서버를 시작하지 못했습니다. 실행 파일·server-command·로그 경로를 확인하세요.";}
    }
    @Override public synchronized String stop(){
        if(stopRequested)return "이미 저장·종료 중입니다.";
        try{if(target(client.status())&&client.stop()){stopRequested=true;state="저장·종료 중";return "저장 후 서버를 종료합니다. 도우미는 계속 실행됩니다.";}}catch(Exception ignored){}
        if(child!=null&&child.isAlive())try{child.outputWriter(StandardCharsets.UTF_8).write("stop\n");child.outputWriter(StandardCharsets.UTF_8).flush();stopRequested=true;state="저장·종료 중";return "플러그인 연결 대신 서버 콘솔로 정상 종료를 요청했습니다.";}catch(IOException ex){return "정상 종료 요청을 전달하지 못했습니다. 서버 콘솔을 확인하세요.";}
        return "실행 중인 서버와 연결되지 않았습니다. 강제 종료는 수행하지 않았습니다.";
    }
    private boolean target(JsonObject status){try{return identity.equals(DiscordInbound.text(status,"server_id"))&&status.has("pid")&&(child==null||status.get("pid").getAsLong()==child.pid());}catch(Exception ex){return false;}}
    @Override public synchronized String status(){return "BlackWikiSMP 3.3 · "+state+(online?" · 접속 "+players+"명 · 대기 "+queue+"명":"");}
    synchronized boolean ownedAlive(){return child!=null&&child.isAlive();}
    synchronized boolean online(){return online;}
    @Override public synchronized void close(){closed=true;client.close();/* Leave a running game process intact. */}
}
