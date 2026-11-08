package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

final class Commands implements CommandExecutor,TabCompleter {
    private final BlackWikiSMP p;
    Commands(BlackWikiSMP p){this.p=p;}
    private boolean admin(CommandSender a){if(a.hasPermission("blackwikismp.admin"))return true;p.tell(a,"관리자 권한이 필요합니다.");return false;}
    private Player player(CommandSender a){if(a instanceof Player b)return b;p.tell(a,"플레이어만 사용할 수 있습니다.");return null;}
    private static String arg(String[] a,int i){return a.length>i?a[i].toLowerCase(Locale.ROOT):"";}
    private static boolean on(String s){return Set.of("on","켜기","켜짐","true").contains(s);}
    private static boolean bool(String s){return Set.of("on","off","켜기","끄기","켜짐","꺼짐","true","false").contains(s);}
    private int number(String s,int lo,int hi){int n=Integer.parseInt(s);if(n<lo||n>hi)throw new IllegalArgumentException(lo+"~"+hi+" 사이의 숫자가 필요합니다.");return n;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try {
            String cmd=command.getName();Player a=sender instanceof Player v?v:null;
            if(cmd.equals("smpcoingive")||cmd.equals("smpcoindebit")){p.adminCoins.command(sender,args,cmd.equals("smpcoindebit"));return true;}
            if(cmd.equals("smprealtime")){p.realTime.command(sender,args);return true;}if(cmd.equals("smplights")){p.lighting.command(sender,args);return true;}
            if(cmd.equals("smpspawndesign")){p.spawnDesign.command(sender,args);return true;}if(cmd.equals("smpupgrade")){if(BanService.operator(sender)){p.tell(sender,p.upgrades.status());p.tell(sender,"자동 업데이트: "+p.updates.status());}else p.tell(sender,"OP만 사용할 수 있습니다.");return true;}
            if(cmd.equals("smpeventstart")){p.events.command(sender,args);return true;}if(cmd.equals("smpeventstop")){p.events.stop(sender);return true;}if(cmd.equals("smpevent")&&a==null){p.tell(sender,p.events.status());return true;}
            if(cmd.equals("smpbedrock")&&a==null){p.bedrock.command(sender,args);return true;}
            if(cmd.equals("smpreset")){p.resets.request(sender,args);return true;}
            if(Set.of("smpban","smpstatus","smpdiscord","smpmute","smpadtp","smpking","smphack","smpsafe").contains(cmd)){if(!BanService.operator(sender)){p.tell(sender,"OP만 사용할 수 있습니다.");return true;}switch(cmd){case "smpban"->p.bans.command(sender,args);case "smpmute"->p.admin.mute(sender,args);case "smpking"->p.admin.king(sender,args);case "smphack"->p.admin.hack(sender,args);case "smpsafe"->p.admin.safeOverride(sender,args);case "smpadtp"->{if(sender instanceof org.bukkit.entity.Player op)p.admin.teleportMenu(op,0);else p.tell(sender,"게임 안에서 사용하세요.");}case "smpstatus"->status(sender);case "smpdiscord"->discord(sender,args);}return true;}
            if(a!=null&&!a.hasPermission("blackwikismp.use")&&!a.hasPermission("blackwikismp.admin")){p.tell(a,"이 기능을 사용할 권한이 없습니다.");return true;}
            if(a!=null&&p.queue.waiting(a)&&!cmd.equals("queue")&&!cmd.equals("smpcommands")){p.gui.queue(a);return true;}
            // Console-safe administration is routed before the player-only GUI entry points.
            if(cmd.equals("blacksmp")){smp(sender,args);return true;}
            if(cmd.equals("backup")){backup(sender,label,args);return true;}
            if(cmd.equals("smpcleanup")){if(admin(sender))p.tell(sender,"바닥 아이템 "+p.cleanup()+"개를 청소했습니다.");return true;}
            if(a==null){p.tell(sender,"사용: smp help | smp status | backup start | smp antixray apply");return true;}
            switch(cmd){
                case "spawn"->{a.closeInventory();p.travel.go(a,p.spawn());}
                case "wilderness"->{a.closeInventory();p.travel.wild(a);}
                case "worlds"->{switch(arg(args,0)){case "1","스폰","spawn"->p.travel.go(a,p.spawn());case "2","야생","wild"->p.travel.wild(a);default->p.gui.worlds(a);}}
                case "team"->team(a,args);
                case "ranking"->{switch(arg(args,0)){case "킬","kills","kill"->p.gui.ranking(a,true);case "시간","time","playtime"->p.gui.ranking(a,false);case "코인","coins","money"->p.gui.coinRanking(a);default->p.gui.rankings(a);}}
                case "smpprogress"->{if(arg(args,0).equals("관리"))p.mining.admin(a);else p.mining.open(a);}
                case "smptips"->p.tips.open(a);
                case "smppistol"->p.pistols.command(a,args);
                case "smpshop"->p.shop.command(a,args);
                case "smpmarket"->p.market.command(a,args);
                case "smpcommands"->p.gui.commandGuide(a,0);
                case "smptitle"->p.titles.command(a,args);
                case "coins"->{switch(arg(args,0)){case "랭킹","ranking"->p.gui.coinRanking(a);case "잔액","balance"->p.tell(a,"현재 보유: "+VaultService.money(p.data.user(a).coins)+"코인");default->p.gui.coins(a);}}
                case "vault"->{if(Set.of("업그레이드","upgrade","확장").contains(arg(args,0))){if(arg(args,1).equals("확인"))p.vaults.purchase(a,p.data.user(a).vaultTier);else p.gui.vaultUpgrade(a);}else p.vaults.open(a);}
                case "queue"->{switch(arg(args,0)){case "나가기","leave"->a.kick(BlackWikiSMP.text("대기를 취소하고 접속을 종료했습니다."));case "테스트","test"->{if(admin(a)){a.closeInventory();p.queue.enqueue(a,true);}}case "목록","list"->{if(admin(a))for(UUID id:p.queue.fifo.list())p.tell(a,p.queue.fifo.position(id)+". "+p.data.name(id));}default->p.gui.queue(a);}}
                case "smpsettings"->settings(a,args);
                case "border"->{if(Set.of("표시","hud").contains(arg(args,0))){p.data.user(a).hud=!p.data.user(a).hud;p.data.changed();p.hud.refresh(a);p.tell(a,"스코어보드 HUD: "+Gui.yes(p.data.user(a).hud));}else p.gui.border(a);}
                case "smpnpc"->{if(admin(a))npc(a,args);}
                case "tpa"->{if(args.length!=1)p.tell(a,"/tpa 닉네임");else p.travel.request(a,Bukkit.getPlayerExact(args[0]));}
                case "tpaaccept"->p.travel.accept(a);
                case "tpadeny"->p.travel.deny(a);
                case "home"->p.homes.command(a,args,false);
                case "sethome"->p.homes.command(a,args,true);
                case "smpstarter"->p.starter.command(a,args);
                case "smpbedrock"->p.bedrock.command(a,args);
                case "smpwelcome"->p.welcome.menu(a);
                case "smpgraves"->p.graves.list(a,0);
                case "smpnewbie"->p.newbies.open(a);
                case "smptrees"->p.trees.menu(a);
                case "smpevent"->p.events.menu(a);
                case "smpvoice"->p.voice.command(a,args);
                case "smpserum"->p.serums.open(a);
                case "trash"->p.gui.trash(a);
                case "blocklog"->{if(admin(a))logs(a,args);}
                case "invsee"->{if(admin(a)){if(args.length!=1)p.tell(a,"/인벤토리 닉네임");else {Player b=Bukkit.getPlayerExact(args[0]);if(b==null)p.tell(a,"온라인 플레이어를 찾을 수 없습니다.");else p.gui.inspect(a,b);}}}
                case "smpstatus"->{if(admin(a))p.gui.status(a);}
                default->p.gui.main(a);
            }
        }catch(IllegalArgumentException ex){p.tell(sender,"입력 오류: "+ex.getMessage());}catch(Exception ex){p.tell(sender,"작업 중 오류가 발생했습니다. 관리자에게 콘솔 로그를 전달하세요.");p.getLogger().log(java.util.logging.Level.SEVERE,"Command failed: "+command.getName(),ex);}
        return true;
    }
    private void smp(CommandSender a,String[] args){
        Player b=a instanceof Player v?v:null;
        if(args.length==0){if(b!=null)p.gui.main(b);else help(a);return;}
        switch(arg(args,0)){
            case "help","도움말"->help(a);
            case "upgrade","업데이트"->{if(BanService.operator(a)){p.tell(a,p.upgrades.status());p.tell(a,"자동 업데이트: "+p.updates.status());}else p.tell(a,"OP만 사용할 수 있습니다.");}
            case "features","기능"->{if(BanService.operator(a)&&b!=null)p.gui.features(b);else p.tell(a,"OP 플레이어 전용입니다.");}
            case "admin","관리"->{if(admin(a)&&b!=null)p.gui.adminMenu(b);}
            case "status","상태"->status(a);
            case "setspawn","스폰설정"->{if(admin(a)&&b!=null)p.setSpawn(b);}
            case "build","건축"->{if(admin(a)&&b!=null){if(!p.builders.remove(b.getUniqueId()))p.builders.add(b.getUniqueId());p.tell(a,"스폰 건축 모드: "+Gui.yes(p.builders.contains(b.getUniqueId())));}}
            case "waiting","대기정원"->{if(admin(a)){if(args.length!=2){p.tell(a,"/smp waiting 0~10000");return;}p.cfg().set("queue.max-waiting",number(args[1],0,10000));p.queue.applyCapacity();p.saveConfig();p.tell(a,"대기열 정원: "+p.queue.capacity());}}
            case "slots","슬롯"->{if(admin(a)){if(args.length<2){p.tell(a,"/smp slots 1~1000");return;}int n=number(args[1],1,1000);p.cfg().set("queue.player-slots",n);p.queue.applyCapacity();p.saveConfig();p.tell(a,"게임 슬롯을 "+n+"명으로 설정했습니다. 기존 플레이어를 내보내지 않습니다.");}}
            case "queue","대기열","cleanup","청소"->{if(admin(a)){String key=Set.of("queue","대기열").contains(arg(args,0))?"queue.enabled":"cleanup.enabled";if(!bool(arg(args,1))){p.tell(a,"/smp "+args[0]+" on|off");return;}p.cfg().set(key,on(args[1]));p.queue.applyCapacity();p.saveConfig();p.tell(a,key+": "+Gui.yes(on(args[1])));}}
            case "antixray","엑스레이"->{if(admin(a)){if(Set.of("apply","적용").contains(arg(args,1)))p.backups.applyAntiXray(a);else p.backups.antiStatus(a);}}
            case "reload","리로드"->{if(admin(a)){if(p.backups.status().equals("백업 진행 중")){p.tell(a,"백업 완료 후 설정을 다시 불러오세요.");return;}org.bukkit.configuration.file.FileConfiguration previous=p.cfg();String backup=previous.saveToString();try{p.reloadConfig();p.validate();if(!Objects.equals(previous.get("worlds"),p.cfg().get("worlds"))){ // World identity changes need a full restart, checked below by names.
                            String sw=p.cfg().getString("worlds.spawn","");if((!sw.isBlank()&&!sw.equals(p.spawnWorld.getName()))||!p.cfg().getString("worlds.wild","BlackWikiSMPWild").equals(p.wildWorld.getName())||!p.cfg().getString("worlds.queue","BlackWikiSMPQueue").equals(p.queue.world.getName()))throw new IllegalArgumentException("월드 이름 변경은 서버 전체 재시작이 필요합니다.");}
                        for(World w:Bukkit.getWorlds())p.applyBorder(w);p.queue.applyCapacity();p.moderation.reload();p.discord.reload();p.backups.resetTimer();p.monsters.refresh();p.tell(a,"설정을 다시 불러왔습니다. 스폰 위치·월드·플랫폼 변경은 전체 재시작하세요.");
                    }catch(Exception e){try{p.cfg().loadFromString(backup);}catch(Exception ignored){}p.tell(a,"설정 적용 실패: "+e.getMessage()+". 이전 설정으로 유지합니다.");}}}
            default->help(a);
        }
    }
    private void status(CommandSender a){if(!BanService.operator(a)){p.tell(a,"OP만 사용할 수 있습니다.");return;}if(a instanceof Player b)p.gui.status(b);else p.statusLines().forEach(s->p.tell(a,s));}
    private void discord(CommandSender a,String[] args){if(!BanService.operator(a)){p.tell(a,"OP만 사용할 수 있습니다.");return;}switch(arg(args,0)){case "채널","channel"->{if(args.length!=2)throw new IllegalArgumentException("/디스코드 채널 숫자ID");DiscordRelay.endpoint(args[1]);p.cfg().set("discord.channel-id",args[1]);p.saveConfig();p.discord.reload();p.tell(a,"봇 전송 채널을 변경했습니다. /디스코드 테스트");}case "양방향","receive","알림","events"->{if(args.length!=2||!Set.of("on","off","켜기","끄기").contains(arg(args,1)))throw new IllegalArgumentException("/디스코드 양방향|알림 on|off");boolean on=Set.of("on","켜기").contains(arg(args,1));if(Set.of("양방향","receive").contains(arg(args,0)))p.cfg().set("discord.relay-discord-chat",on);else{p.cfg().set("discord.relay-player-events",on);p.cfg().set("discord.relay-server-events",on);}p.saveConfig();p.discord.reload();p.tell(a,"Discord 설정 저장 · 외부 도우미 모드는 도우미도 재시작하세요.");}case "토큰","token"->p.tell(a,"봇 토큰은 config.yml의 discord.bot-token 또는 서버 환경 변수에만 입력하세요.");case "테스트","test"->p.discord.test(a);case "켜기","on","끄기","off"->{p.cfg().set("discord.enabled",Set.of("켜기","on").contains(arg(args,0)));p.saveConfig();p.discord.reload();p.tell(a,"Discord: "+p.discord.status());}default->{if(a instanceof Player b)p.gui.discord(b);else p.tell(a,"Discord: "+p.discord.status());}}}
    private void team(Player a,String[] args){switch(arg(args,0)){
        case "생성","create"->{if(args.length!=2)p.tell(a,"/팀 생성 이름 (1~12글자)");else p.teams.create(a,args[1]);}
        case "초대","invite"->{if(args.length!=2)p.tell(a,"/팀 초대 닉네임");else p.teams.invite(a,Bukkit.getPlayerExact(args[1]));}
        case "수락","accept"->p.teams.accept(a);
        case "거절","deny"->p.teams.deny(a);
        case "목록","list"->p.gui.teamList(a,0);
        case "멤버","members","관리"->p.gui.members(a,0);
        case "추방","kick","위임","transfer"->{if(args.length!=2){p.tell(a,"/팀 "+args[0]+" 닉네임");return;}UUID id=p.data.find(args[1]);if(id==null){p.tell(a,"플레이어를 찾을 수 없습니다.");return;}if(Set.of("추방","kick").contains(arg(args,0)))p.teams.kick(a,id);else p.teams.transfer(a,id);}
        case "pvp"->{if(!bool(arg(args,1)))p.tell(a,"/팀 pvp on|off");else p.teams.pvp(a,on(args[1]));}
        case "나가기","leave"->p.teams.leave(a);
        case "해체","disband"->{if(arg(args,1).equals("확인"))p.teams.disband(a);else p.gui.confirm(a,"팀 해체",p.teams::disband,p.gui::team);}
        case "채팅","chat"->{if(args.length<2)p.tell(a,"/팀 채팅 내용");else p.teams.chat(a,String.join(" ",Arrays.copyOfRange(args,1,args.length)));}
        default->p.gui.team(a);
    }}
    private void settings(Player a,String[] args){if(args.length<2){p.gui.settings(a);return;}if(!bool(args[1])){p.tell(a,"/설정 경계|tpa|알림 on|off");return;}DataStore.User u=p.data.user(a);switch(arg(args,0)){case "경계","hud"->{u.hud=on(args[1]);p.hud.refresh(a);}case "tpa"->u.tpa=on(args[1]);case "알림","notifications"->u.notices=on(args[1]);default->{p.tell(a,"/설정 경계|tpa|알림 on|off");return;}}p.data.changed();p.tell(a,"설정을 변경했습니다.");}
    private void backup(CommandSender a,String label,String[] args){if(!admin(a))return;if(label.equals("자동백업")&&args.length==1&&args[0].matches("[0-9]+")){p.cfg().set("backup.interval-minutes",number(args[0],1,10080));p.cfg().set("backup.enabled",true);p.saveConfig();p.backups.resetTimer();p.tell(a,args[0]+"분마다 자동 백업합니다.");return;}switch(arg(args,0)){case "시작","start","now"->p.backups.start(a);case "on","off"->{p.cfg().set("backup.enabled",on(args[0]));p.saveConfig();p.backups.resetTimer();p.tell(a,"자동 백업: "+Gui.yes(on(args[0])));}case "간격","interval"->{if(args.length<2){p.tell(a,"/백업 간격 분");return;}p.cfg().set("backup.interval-minutes",number(args[1],1,10080));p.saveConfig();p.backups.resetTimer();p.tell(a,"백업 간격을 변경했습니다.");}case "상태","status"->p.tell(a,p.backups.status()+" / "+p.backups.lastResult());default->{if(a instanceof Player b)p.gui.backup(b);else p.tell(a,"backup start | backup on/off | backup interval 분 | backup status");}}}
    private void npc(Player a,String[] args){switch(arg(args,0)){case "추가","add"->{if(args.length<2)p.tell(a,"/npc 추가 메뉴|야생|스폰|팀|랭킹|설정|대기열|도움말 [표시이름]");else p.npcs.create(a,args[1],args.length>2?String.join(" ",Arrays.copyOfRange(args,2,args.length)):args[1]+" 안내");}case "기본","defaults"->{p.npcs.defaults();p.tell(a,"없는 종류의 안내 NPC를 스폰에 배치했습니다.");}case "삭제","remove"->p.npcs.removeNearest(a);case "목록","list"->p.npcs.list(a);default->p.gui.npc(a);}}
    private void logs(Player a,String[] args){Block b;if(args.length==3){int x=number(args[0],-30000000,30000000),y=number(args[1],a.getWorld().getMinHeight(),a.getWorld().getMaxHeight()-1),z=number(args[2],-30000000,30000000);b=a.getWorld().getBlockAt(x,y,z);}else b=a.getTargetBlockExact(6);if(b==null){p.tell(a,"6블록 안의 블록을 바라보거나 /로그 x y z 를 사용하세요.");return;}List<String> history=p.data.history(b.getLocation());if(history.isEmpty())p.tell(a,"최근 메모리 기록이 없습니다. 과거 기록은 plugins/BlackWikiSMP/audit*.log 에 저장됩니다.");else history.forEach(v->p.tell(a,v));}
    private void help(CommandSender a){p.tell(a,"메뉴: /메뉴 (Shift+F) · /월드 · /스폰 · /야생 · /팀 · /랭킹 · /설정 · /경계");p.tell(a,"팀: /팀 생성 이름 | 초대 닉 | 수락 | 거절 | 목록 | 멤버 | 추방 닉 | 위임 닉 | pvp on/off | 나가기 | 해체 확인 | 채팅 내용");p.tell(a,"개인: /home [이름] · /sethome [이름] · /홈 삭제 이름 확인 · /무덤 · /신규보호 · /벌목 · /음성채팅 · /이벤트 · /혈청 · /tpa 닉 · /휴지통");p.tell(a,"권총: /권총 · /권총 재장전 · /탄환 · 상점 4페이지");p.tell(a,"성장: /채광 · 상점: /상점 [1~4] · /유저상점 [등록 가격|내목록|구매 번호|취소 번호|정산] · 기록: /랭킹 시간 | /랭킹 킬 | /랭킹 코인 · /코인 · /보관함 [업그레이드] · /명령어 · /대기열 [목록|나가기]");if(a.hasPermission("blackwikismp.admin")){p.tell(a,"관리: /smp admin | setspawn | build | slots 숫자 | queue on/off | cleanup on/off | status | reload");p.tell(a,"관리: /백업 시작|on|off|간격 분|상태 · /자동백업 분 · /smp antixray apply|status · /npc · /로그 · /인벤토리 닉 · /청소");}if(BanService.operator(a))p.tell(a,"OP: /스폰꾸미기 · /업데이트상태 · /관리자전용이벤트켜기 시간 분 초 · /관리자전용이벤트끄기 · /smp features · /초기화 [확인|상태] · /상점효과 닉네임 효과 초 · /벤 닉네임 일 시간 분 초 사유 · /벤 풀기 닉네임 · /벤 목록 · /디스코드 · /플러그인상태");}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] a){String cmd=c.getName();List<String> options=new ArrayList<>();if(cmd.equals("smprealtime")){if(!BanService.operator(s))return List.of();return (a.length==1?List.of("켜기","끄기","상태","날씨","날씨갱신"):a.length==2&&a[0].equals("날씨")?List.of("켜기","끄기"):List.<String>of()).stream().filter(v->v.startsWith(arg(a,a.length-1))).toList();}if(cmd.equals("smpmute")){if(!BanService.operator(s))return List.of();if(a.length==1){options.addAll(List.of("해제","목록"));Bukkit.getOnlinePlayers().forEach(v->options.add(v.getName()));}else if(a.length==2&&!Set.of("해제","목록").contains(a[0]))options.addAll(List.of("초","분","시간","일"));else if(a.length==2&&a[0].equals("해제"))Bukkit.getOnlinePlayers().forEach(v->options.add(v.getName()));else if(a.length==3)options.addAll(List.of("10","30","60"));String last=a.length==0?"":a[a.length-1];return options.stream().filter(o->o.startsWith(last)).toList();}if(cmd.equals("smpcoingive")||cmd.equals("smpcoindebit")){if(!BanService.operator(s))return List.of();if(a.length==1)Bukkit.getOnlinePlayers().forEach(v->options.add(v.getName()));else if(a.length==2)options.addAll(List.of("1","100","1000","10000"));return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(arg(a,a.length-1))).toList();}if(cmd.equals("smpban")){if(!BanService.operator(s))return List.of();if(a.length==1){options.addAll(List.of("풀기","목록"));Bukkit.getOnlinePlayers().forEach(v->options.add(v.getName()));}else if(a.length==2&&arg(a,0).equals("풀기"))p.bans.active().forEach(e->options.add(e.name()));return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(arg(a,a.length-1))).toList();}if(cmd.equals("smpdiscord")){if(!BanService.operator(s))return List.of();return List.of("상태","테스트","켜기","끄기","채널","양방향","알림").stream().filter(v->v.startsWith(arg(a,a.length-1))).toList();}if(a.length==1){switch(cmd){case "smpprogress"->{if(BanService.operator(s))options.add("관리");}case "blacksmp"->{options.addAll(List.of("help"));if(s.hasPermission("blackwikismp.admin"))options.addAll(List.of("admin","setspawn","build","slots","waiting","queue","cleanup","antixray","status","reload","features","upgrade"));}case "smppistol"->options.add("재장전");case "smpstarter"->options.add("받기");case "smpbedrock"->options.add("상태");case "smpshop"->options.addAll(List.of("1","2","3","4","5","특별","식량","구매","판매"));case "smpmarket"->options.addAll(List.of("등록","내목록","목록","구매","취소","정산"));case "smpreset"->{if(BanService.operator(s))options.addAll(List.of("확인","상태"));}case "team"->options.addAll(List.of("생성","초대","수락","거절","목록","멤버","추방","위임","pvp","나가기","해체","채팅"));case "ranking"->options.addAll(List.of("시간","킬","코인"));case "smpsettings"->options.addAll(List.of("경계","tpa","알림"));case "worlds"->options.addAll(List.of("스폰","야생","1","2"));case "home"->{options.addAll(List.of("이동","설정","삭제"));if(s instanceof Player player)options.addAll(p.data.user(player).homes.keySet());}case "smpvoice"->options.addAll(List.of("팀","나가기","음소거","해제"));case "queue"->{options.addAll(List.of("나가기"));if(s.hasPermission("blackwikismp.admin"))options.addAll(List.of("목록","테스트"));}case "backup"->{if(s.hasPermission("blackwikismp.admin"))options.addAll(List.of("시작","상태","on","off","간격"));}case "smpnpc"->{if(s.hasPermission("blackwikismp.admin"))options.addAll(List.of("추가","삭제","목록"));}case "vault"->options.add("업그레이드");case "coins"->options.addAll(List.of("잔액","랭킹"));case "border"->options.add("표시");case "tpa","invsee"->Bukkit.getOnlinePlayers().forEach(v->options.add(v.getName()));}}
        else if(a.length==2){if(cmd.equals("smpshop"))ShopCatalogue.ALL.stream().filter(v->!Set.of("판매","sell").contains(arg(a,0))||v.sell()>0).forEach(v->options.add(v.id()));else if(cmd.equals("team")&&Set.of("초대","invite").contains(arg(a,0)))Bukkit.getOnlinePlayers().forEach(v->options.add(v.getName()));else if(cmd.equals("team")&&Set.of("추방","위임","kick","transfer").contains(arg(a,0))&&s instanceof Player v){DataStore.Team t=p.teams.of(v);if(t!=null)t.members.forEach(id->options.add(p.data.name(id)));}else if(cmd.equals("smpnpc")&&s.hasPermission("blackwikismp.admin"))options.addAll(List.of("메뉴","야생","스폰","팀","랭킹","설정","대기열","도움말"));else if(cmd.equals("blacksmp")&&arg(a,0).equals("antixray")&&s.hasPermission("blackwikismp.admin"))options.addAll(List.of("apply","status"));else options.addAll(List.of("on","off","확인"));}
        String prefix=a[a.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
