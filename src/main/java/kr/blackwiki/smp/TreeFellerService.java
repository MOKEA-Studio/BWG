package kr.blackwiki.smp;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
final class TreeFellerService implements Listener {
    private final BlackWikiSMP p;private final NamespacedKey placed,grown,disabled;private final Map<UUID,BukkitTask> jobs=new HashMap<>();private final Set<UUID> batching=new HashSet<>();private final Map<BlockBreakEvent,Boolean> natural=new WeakHashMap<>();
    TreeFellerService(BlackWikiSMP p){this.p=p;placed=new NamespacedKey(p,"tree-placed");grown=new NamespacedKey(p,"tree-grown");disabled=new NamespacedKey(p,"tree-feller-disabled");}
    private int index(Block b){return ((b.getY()-b.getWorld().getMinHeight())<<8)|((b.getZ()&15)<<4)|(b.getX()&15);}
    private boolean bit(Block b,NamespacedKey key){byte[] value=b.getChunk().getPersistentDataContainer().get(key,PersistentDataType.BYTE_ARRAY);return value!=null&&BitSet.valueOf(value).get(index(b));}
    private void bit(Block b,NamespacedKey key,boolean flag){var pd=b.getChunk().getPersistentDataContainer();byte[] old=pd.get(key,PersistentDataType.BYTE_ARRAY);BitSet bits=old==null?new BitSet():BitSet.valueOf(old);bits.set(index(b),flag);pd.set(key,PersistentDataType.BYTE_ARRAY,bits.toByteArray());}
    private boolean playerPlaced(Block b){return bit(b,placed)||!bit(b,grown)&&p.mining.used(b);}
    static String family(Material type){String name=type.name();return name.startsWith("STRIPPED_")||!(name.endsWith("_LOG")||name.endsWith("_WOOD"))?"":name.substring(0,name.lastIndexOf('_'));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(!family(e.getBlockPlaced().getType()).isEmpty()){bit(e.getBlockPlaced(),placed,true);bit(e.getBlockPlaced(),grown,false);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void grow(StructureGrowEvent e){for(BlockState state:e.getBlocks())if(!family(state.getType()).isEmpty()){bit(state.getBlock(),grown,true);bit(state.getBlock(),placed,false);}}
    private void moved(List<Block> blocks,BlockFace face){for(Block b:blocks)if(!family(b.getType()).isEmpty()){bit(b,placed,true);bit(b,grown,false);bit(b.getRelative(face),placed,true);bit(b.getRelative(face),grown,false);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){moved(e.getBlocks(),e.getDirection());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){moved(e.getBlocks(),e.getDirection());}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void before(BlockBreakEvent e){if(!batching.contains(e.getPlayer().getUniqueId())&&!family(e.getBlock().getType()).isEmpty())natural.put(e,!playerPlaced(e.getBlock()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){boolean eligible=Boolean.TRUE.equals(natural.remove(e));Player a=e.getPlayer();if(!eligible||!enabled(a)||batching.contains(a.getUniqueId())||jobs.containsKey(a.getUniqueId())||a.getGameMode()!=GameMode.SURVIVAL||a.isSneaking()||!a.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")||p.safe(e.getBlock().getLocation())||p.queue.waiting(a))return;
        Block first=e.getBlock();String family=family(first.getType());List<Block> logs=discover(first,family,p.cfg().getInt("tree-feller.max-logs",256));if(logs.size()<2||!leaves(logs,family))return;logs.remove(first);Deque<Block> todo=new ArrayDeque<>(logs);ItemStack tool=normalized(a.getInventory().getItemInMainHand());int slot=a.getInventory().getHeldItemSlot();Location at=first.getLocation();
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(p,()->{if(!enabled(a)||!a.isOnline()||a.isDead()||a.getWorld()!=at.getWorld()||a.getLocation().distanceSquared(at)>1024||a.getInventory().getHeldItemSlot()!=slot||!normalized(a.getInventory().getItemInMainHand()).equals(tool)||p.queue.waiting(a)||p.resets.busy()){stop(a.getUniqueId());return;}batching.add(a.getUniqueId());try{for(int i=0;i<p.cfg().getInt("tree-feller.blocks-per-tick",8)&&!todo.isEmpty();i++){Block b=todo.removeFirst();if(!family(b.getType()).equals(family)||playerPlaced(b)||p.safe(b.getLocation())||p.outside(b.getLocation()))continue;BlockBreakEvent extra=new BlockBreakEvent(b,a);Bukkit.getPluginManager().callEvent(extra);if(extra.isCancelled())continue;ItemStack current=a.getInventory().getItemInMainHand();if(current.getType().isAir()){todo.clear();break;}if(extra.isDropItems())b.breakNaturally(current,true);else b.setType(Material.AIR,false);a.damageItemStack(EquipmentSlot.HAND,1);if(a.getInventory().getItemInMainHand().getType().isAir()){todo.clear();break;}}}finally{batching.remove(a.getUniqueId());}if(todo.isEmpty())stop(a.getUniqueId());},1,1);jobs.put(a.getUniqueId(),task);
    }
    private boolean enabled(Player a){return p.cfg().getBoolean("tree-feller.enabled",true)&&a.getPersistentDataContainer().getOrDefault(disabled,PersistentDataType.BYTE,(byte)0)==0;}
    private ItemStack normalized(ItemStack item){ItemStack copy=item.clone();if(copy.hasItemMeta()&&copy.getItemMeta() instanceof Damageable damage){damage.setDamage(0);copy.setItemMeta(damage);}return copy;}
    private List<Block> discover(Block first,String family,int cap){List<Block> logs=new ArrayList<>();Set<String> visited=new HashSet<>();Deque<Block> queue=new ArrayDeque<>();queue.add(first);while(!queue.isEmpty()&&logs.size()<cap){Block b=queue.removeFirst();if(!visited.add(b.getX()+":"+b.getY()+":"+b.getZ())||!family(b.getType()).equals(family)||!b.equals(first)&&playerPlaced(b)||Math.abs(b.getX()-first.getX())>8||Math.abs(b.getZ()-first.getZ())>8||Math.abs(b.getY()-first.getY())>32)continue;logs.add(b);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){if(dx==0&&dy==0&&dz==0)continue;int x=b.getX()+dx,y=b.getY()+dy,z=b.getZ()+dz;if(y>=first.getWorld().getMinHeight()&&y<first.getWorld().getMaxHeight()&&first.getWorld().isChunkLoaded(x>>4,z>>4))queue.add(first.getWorld().getBlockAt(x,y,z));}}return logs;}
    private boolean leaves(List<Block> logs,String family){Set<String> seen=new HashSet<>();int count=0;for(Block log:logs)for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)for(int dz=-2;dz<=2;dz++){int x=log.getX()+dx,y=log.getY()+dy,z=log.getZ()+dz;if(!log.getWorld().isChunkLoaded(x>>4,z>>4)||y<log.getWorld().getMinHeight()||y>=log.getWorld().getMaxHeight()||!seen.add(x+":"+y+":"+z))continue;Block b=log.getWorld().getBlockAt(x,y,z);if(b.getType().name().equals(family+"_LEAVES")&&b.getBlockData() instanceof Leaves leaf&&!leaf.isPersistent()&&++count>=3)return true;}return false;}
    void menu(Player a){Gui.Menu m=p.gui.make(a,"나무 일괄 벌목");p.gui.button(m,22,Material.IRON_AXE,"내 벌목: "+Gui.yes(enabled(a)),b->{boolean off=b.getPersistentDataContainer().getOrDefault(disabled,PersistentDataType.BYTE,(byte)0)==1;b.getPersistentDataContainer().set(disabled,PersistentDataType.BYTE,(byte)(off?0:1));b.saveData();menu(b);},"도끼로 자연 잎이 있는 연결 원목을 베면 작동","웅크리면 한 개만 파괴 · 내구도 정상 소모");p.gui.show(a,m);}
    private void stop(UUID id){BukkitTask task=jobs.remove(id);if(task!=null)task.cancel();batching.remove(id);}
    @EventHandler public void quit(PlayerQuitEvent e){stop(e.getPlayer().getUniqueId());}
    void close(){for(UUID id:List.copyOf(jobs.keySet()))stop(id);}
}
