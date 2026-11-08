package kr.blackwiki.smp;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.time.Duration;
import java.util.*;

final class MiningService implements Listener {
    private final BlackWikiSMP p;
    private final NamespacedKey provenance,minY;
    private final Map<UUID,Long> messages=new HashMap<>();
    MiningService(BlackWikiSMP p){this.p=p;provenance=new NamespacedKey(p,"mining-provenance");minY=new NamespacedKey(p,"mining-provenance-min-y");}
    int max(){return p.cfg().getInt("mining.max-level",100);}
    int base(){return p.cfg().getInt("mining.base-xp",100);}
    int growth(){return p.cfg().getInt("mining.xp-growth",50);}
    int level(Player a){return ProgressRules.miningLevel(p.data.user(a).miningXp,max(),base(),growth());}
    private int index(Block block){return ((block.getY()-block.getWorld().getMinHeight())<<8)|((block.getZ()&15)<<4)|(block.getX()&15);}
    boolean used(Block block){var pd=block.getChunk().getPersistentDataContainer();byte[] bytes=pd.get(provenance,PersistentDataType.BYTE_ARRAY);if(bytes==null)return false;return pd.getOrDefault(minY,PersistentDataType.INTEGER,block.getWorld().getMinHeight())!=block.getWorld().getMinHeight()||BitSet.valueOf(bytes).get(index(block));}
    void mark(Block block){var pd=block.getChunk().getPersistentDataContainer();int minimum=block.getWorld().getMinHeight();if(pd.has(minY,PersistentDataType.INTEGER)&&pd.get(minY,PersistentDataType.INTEGER)!=minimum)return;byte[] bytes=pd.get(provenance,PersistentDataType.BYTE_ARRAY);BitSet positions=bytes==null?new BitSet():BitSet.valueOf(bytes);int at=index(block);if(positions.get(at))return;positions.set(at);pd.set(minY,PersistentDataType.INTEGER,minimum);pd.set(provenance,PersistentDataType.BYTE_ARRAY,positions.toByteArray());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void placed(BlockPlaceEvent e){if(e instanceof BlockMultiPlaceEvent multi)for(var state:multi.getReplacedBlockStates())mark(state.getBlock());else mark(e.getBlockPlaced());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){for(Block block:e.getBlocks()){mark(block);mark(block.getRelative(e.getDirection()));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){for(Block block:e.getBlocks()){mark(block);mark(block.getRelative(e.getDirection()));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void form(BlockFormEvent e){mark(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void changed(EntityChangeBlockEvent e){mark(e.getBlock());}
    int experience(Material type){if(type==Material.DIAMOND_ORE||type==Material.DEEPSLATE_DIAMOND_ORE||type==Material.ANCIENT_DEBRIS)return p.cfg().getInt("mining.rare-ore-xp",10);if(type.name().endsWith("_ORE"))return p.cfg().getInt("mining.ore-xp",5);return type.isBlock()&&type.isSolid()&&!type.isAir()?p.cfg().getInt("mining.block-xp",1):0;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){award(e.getPlayer(),e.getBlock());}
    boolean award(Player a,Block block){if(!p.cfg().getBoolean("mining.enabled",true)||a.getGameMode()!=GameMode.SURVIVAL&&a.getGameMode()!=GameMode.ADVENTURE||p.queue.waiting(a)||p.safe(block.getLocation())||a.getWorld()==p.queue.world||p.resets.busy()||!p.vaults.healthy(a)||used(block))return false;int amount=experience(block.getType());if(amount<=0)return false;mark(block);var u=p.data.user(a);int before=level(a);long cap=ProgressRules.threshold(max(),base(),growth());if(u.miningXp>=cap)return false;u.miningXp=Math.min(cap,CoinClock.add(u.miningXp,amount));int after=level(a);p.vaults.syncCurrency(a,u);p.data.changed();
        if(after>before){long reward=(after-before)*30L;p.rewards.credit(a,reward);p.tell(a,"채광 레벨 "+before+" → "+after+" · +"+reward+"코인");a.showTitle(Title.title(BlackWikiSMP.text("채광 Lv. "+after),Component.text("레벨 상승 보상 +"+reward+"코인"),Title.Times.times(Duration.ofMillis(150),Duration.ofMillis(1100),Duration.ofMillis(300))));a.playSound(a.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.5f,1.2f);}
        else if(System.currentTimeMillis()-messages.getOrDefault(a.getUniqueId(),0L)>=250){messages.put(a.getUniqueId(),System.currentTimeMillis());long since=u.miningXp-ProgressRules.threshold(after,base(),growth()),needed=ProgressRules.threshold(after+1,base(),growth())-ProgressRules.threshold(after,base(),growth());p.hud.notice(a,"채광 Lv."+after+" · "+since+"/"+needed+" XP");}return true;
    }
    void open(Player a){if(p.queue.waiting(a)){p.gui.queue(a);return;}Gui.Menu m=p.gui.make(a,"성장 · 채광 레벨");int level=level(a);long total=p.data.user(a).miningXp,here=ProgressRules.threshold(level,base(),growth());String progress=level==max()?"최고 레벨 도달":(total-here)+" / "+(ProgressRules.threshold(level+1,base(),growth())-here)+" XP";m.inventory.setItem(13,Gui.item(Material.DIAMOND_PICKAXE,"채광 Lv."+level+" / "+max(),progress,"총 경험치: "+VaultService.money(total),"레벨이 오를 때마다 +30코인","자연 블록 1 · 광석 5 · 희귀 광석 10 XP (기본)","설치·재생성·반복 채굴 위치는 경험치 제외"));m.inventory.setItem(20,Gui.item(Material.ZOMBIE_HEAD,"몬스터 레벨 1~50","엔더 드래곤·워든·위더 제외","일반 Lv.26~49: 10 / Lv.50: 35코인","강한 몬스터 Lv.26~49: 30 / Lv.50: 75코인","1~25레벨 보상 없음 · 높은 레벨은 희귀"));p.gui.button(m,24,Material.DIAMOND_SWORD,"특별 마검 상점",b->p.shop.page(b,3),"/상점 4");p.gui.button(m,40,Material.GOLD_NUGGET,"내 코인",p.gui::coins);p.gui.show(a,m);}
    private boolean operator(Player a){if(!BanService.operator(a)){p.tell(a,"OP만 사용할 수 있습니다.");return false;}if(p.backups.running()||p.resets.busy()){p.tell(a,"백업·야생 초기화 완료 후 설정하세요.");return false;}return true;}
    private void change(Player a,String path,Object value){if(!operator(a))return;p.cfg().set(path,value);ProgressConfig.validate(p.cfg());p.saveConfig();if(path.startsWith("monsters."))p.monsters.refresh();admin(a);}
    void admin(Player a){if(!operator(a))return;Gui.Menu m=p.gui.make(a,"몬스터 · 채광 설정");
        p.gui.button(m,10,Material.ZOMBIE_HEAD,"몬스터 레벨: "+Gui.yes(p.cfg().getBoolean("monsters.enabled",true)),b->change(b,"monsters.enabled",!p.cfg().getBoolean("monsters.enabled",true)),"최대 50 · 보스 3종 제외");
        p.gui.button(m,12,Material.SPAWNER,"고레벨 출현 가중치: "+p.cfg().getDouble("monsters.spawn-decay",.94),b->{double value=p.cfg().getDouble("monsters.spawn-decay",.94);change(b,"monsters.spawn-decay",value>=.98?.90:Math.round((value+.02)*100)/100.0);},"클릭: 0.90 → 0.92 → 0.94 → 0.96 → 0.98","작을수록 높은 레벨이 더 드물게 출현","이미 스폰한 몬스터 레벨은 유지");
        p.gui.button(m,14,Material.RED_DYE,"레벨당 체력 보너스: "+Math.round(p.cfg().getDouble("monsters.health-bonus-per-level",.04)*100)+"%",b->{double value=p.cfg().getDouble("monsters.health-bonus-per-level",.04);change(b,"monsters.health-bonus-per-level",value>=.10?0:Math.round((value+.02)*100)/100.0);},"클릭: 0%부터 2%씩, 10% 후 0%","원래 체력 기준 · 기존 몬스터도 갱신");
        m.inventory.setItem(16,Gui.item(Material.IRON_SWORD,"강한 몬스터 기준","공격력 "+p.cfg().getDouble("monsters.strong-attack-damage",6)+" 이상 또는 지정 종류","종류 목록: config.yml → monsters.strong-types","보상: 26~49레벨 30 / 50레벨 75코인"));
        p.gui.button(m,28,Material.DIAMOND_PICKAXE,"채광 성장: "+Gui.yes(p.cfg().getBoolean("mining.enabled",true)),b->change(b,"mining.enabled",!p.cfg().getBoolean("mining.enabled",true)),"레벨 상승마다 30코인 고정");
        p.gui.button(m,30,Material.EXPERIENCE_BOTTLE,"첫 레벨 필요 XP: "+base(),b->change(b,"mining.base-xp",base()>=500?50:base()+50),"클릭: 50씩 증가, 500 후 50","경험치 곡선을 바꾸면 현재 레벨도 다시 계산");
        p.gui.button(m,32,Material.CLOCK,"레벨당 필요 XP 증가: "+growth(),b->change(b,"mining.xp-growth",growth()>=100?0:growth()+25),"클릭: 25씩 증가, 100 후 0");
        p.gui.button(m,34,Material.EMERALD,"채광 최고 레벨: "+max(),b->change(b,"mining.max-level",max()>=500?50:max()+50),"클릭: 50씩 증가, 500 후 50","몬스터 최고 레벨은 항상 50입니다.");p.gui.show(a,m);
    }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e){messages.remove(e.getPlayer().getUniqueId());}
}
