package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import java.nio.file.*;
import java.util.*;

final class NpcService implements Listener {
    record Spec(String id,String role,String name,Location location){}
    private final BlackWikiSMP p;
    private final NamespacedKey key;
    private final Map<String,Spec> specs=new LinkedHashMap<>();
    private final Map<UUID,Long> clicks=new HashMap<>();
    private int tick;
    NpcService(BlackWikiSMP p) throws Exception {this.p=p;key=new NamespacedKey(p,"guide");Path f=p.getDataFolder().toPath().resolve("npcs.yml");if(Files.exists(f)){YamlConfiguration y=new YamlConfiguration();y.load(f.toFile());for(String id:y.getKeys(false)){Location l=DataStore.readLocation(y,id+".location");String role=y.getString(id+".role","");if(l!=null&&p.safe(l)&&validRole(role))specs.put(id,new Spec(id,role,y.getString(id+".name",role),l));}}else if(p.cfg().getBoolean("npcs.auto-create-guides",true))defaults();}
    void spawnMenu(Location at){String id="spawn-menu";Spec old=specs.get(id);if(old!=null&&old.location.getWorld().isChunkLoaded(old.location.getBlockX()>>4,old.location.getBlockZ()>>4))for(Entity entity:old.location.getChunk().getEntities())if(id.equals(entity.getPersistentDataContainer().get(key,PersistentDataType.STRING)))entity.remove();Spec next=new Spec(id,"메뉴","메뉴 안내 · 우클릭",at.clone());specs.put(id,next);ensure(next);save();}
    void defaults(){
        int i=0;
        for(String role:List.of("메뉴","야생","스폰","팀","랭킹","설정","대기열","도움말")){
            if(specs.size()>=100)break;
            if(specs.values().stream().anyMatch(s->s.role.equals(role)))continue;
            Location l=p.spawn().add(-6+(i%4)*4,0,i<4?-4:4);i++;
            int y=l.getWorld().getHighestBlockYAt(l.getBlockX(),l.getBlockZ(),HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if(Math.abs(y+1-l.getY())<=8&&l.getWorld().getBlockAt(l.getBlockX(),y,l.getBlockZ()).getType().isSolid())l.setY(y+1);
            String id=UUID.randomUUID().toString();Spec spec=new Spec(id,role,role+" 안내",l);specs.put(id,spec);ensure(spec);
        }
        save();
    }
    boolean validRole(String role){return Set.of("메뉴","야생","스폰","팀","랭킹","설정","대기열","도움말").contains(role);}
    boolean isNpc(Entity e){return e.getPersistentDataContainer().has(key,PersistentDataType.STRING);}
    void create(Player a,String role,String name){if(!validRole(role)){p.tell(a,"종류: 메뉴, 야생, 스폰, 팀, 랭킹, 설정, 대기열, 도움말");return;}if(!p.safe(a.getLocation())){p.tell(a,"스폰 보호구역 안에서 NPC를 만들 수 있습니다.");return;}if(specs.size()>=100){p.tell(a,"안내 NPC는 최대 100개입니다.");return;}String id=UUID.randomUUID().toString();Spec spec=new Spec(id,role,name.codePoints().limit(24).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString(),a.getLocation().clone());specs.put(id,spec);ensure(spec);save();p.tell(a,"["+role+"] 안내 NPC를 만들었습니다.");}
    private void ensure(Spec s){if(!s.location.getWorld().isChunkLoaded(s.location.getBlockX()>>4,s.location.getBlockZ()>>4))return;List<Entity> matches=Arrays.stream(s.location.getChunk().getEntities()).filter(e->s.id.equals(e.getPersistentDataContainer().get(key,PersistentDataType.STRING))).toList();if(matches.size()>1)matches.stream().skip(1).forEach(Entity::remove);if(!matches.isEmpty())return;
        s.location.getWorld().spawn(s.location,Villager.class,v->{v.getPersistentDataContainer().set(key,PersistentDataType.STRING,s.id);v.setAI(false);v.setInvulnerable(true);v.setSilent(true);v.setCollidable(false);v.setGravity(false);v.setPersistent(true);v.setRemoveWhenFarAway(false);v.customName(BlackWikiSMP.text(s.name));v.setCustomNameVisible(true);});}
    void tick(){if(++tick%10==0){for(Spec s:specs.values())ensure(s);clicks.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue()>60000);}}
    @EventHandler public void chunk(ChunkLoadEvent e){for(Entity ent:e.getChunk().getEntities())if(isNpc(ent)&&!specs.containsKey(ent.getPersistentDataContainer().get(key,PersistentDataType.STRING)))ent.remove();for(Spec s:specs.values())if(s.location.getWorld()==e.getWorld()&&s.location.getBlockX()>>4==e.getChunk().getX()&&s.location.getBlockZ()>>4==e.getChunk().getZ())Bukkit.getScheduler().runTask(p,()->ensure(s));}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(PlayerInteractEntityEvent e){if(!isNpc(e.getRightClicked()))return;e.setCancelled(true);Player a=e.getPlayer();if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND||p.queue.waiting(a))return;long now=System.currentTimeMillis();if(now-clicks.getOrDefault(a.getUniqueId(),0L)<400)return;clicks.put(a.getUniqueId(),now);Spec s=specs.get(e.getRightClicked().getPersistentDataContainer().get(key,PersistentDataType.STRING));if(s==null)return;switch(s.role){case "메뉴"->p.gui.main(a);case "야생","스폰"->p.gui.worlds(a);case "팀"->p.gui.team(a);case "랭킹"->p.gui.rankings(a);case "설정"->p.gui.settings(a);case "대기열"->p.gui.queue(a);default->p.gui.help(a);}}
    void removeNearest(Player a){Spec s=specs.values().stream().filter(v->v.location.getWorld()==a.getWorld()&&v.location.distanceSquared(a.getLocation())<=25).min(Comparator.comparingDouble(v->v.location.distanceSquared(a.getLocation()))).orElse(null);if(s==null){p.tell(a,"5블록 안에 안내 NPC가 없습니다.");return;}specs.remove(s.id);for(Entity ent:s.location.getChunk().getEntities())if(s.id.equals(ent.getPersistentDataContainer().get(key,PersistentDataType.STRING)))ent.remove();save();p.tell(a,"안내 NPC를 삭제했습니다.");}
    void list(org.bukkit.command.CommandSender a){p.tell(a,"안내 NPC "+specs.size()+"개");for(Spec s:specs.values())p.tell(a,s.role+" · "+s.name+" · "+s.location.getBlockX()+","+s.location.getBlockY()+","+s.location.getBlockZ());}
    private void save(){YamlConfiguration y=new YamlConfiguration();for(Spec s:specs.values()){y.set(s.id+".role",s.role);y.set(s.id+".name",s.name);DataStore.writeLocation(y,s.id+".location",s.location);}try{DataStore.atomic(p.getDataFolder().toPath().resolve("npcs.yml"),y.saveToString());}catch(Exception e){p.getLogger().log(java.util.logging.Level.SEVERE,"NPC save failed",e);}}
}
