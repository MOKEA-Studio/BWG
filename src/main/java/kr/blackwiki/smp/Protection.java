package kr.blackwiki.smp;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.StructureGrowEvent;
import java.util.*;

/** Spatial checks apply to both ends of indirect actions. No wilderness land claims. */
final class Protection implements Listener {
    private final BlackWikiSMP p;
    Protection(BlackWikiSMP p){this.p=p;}
    boolean protectedAt(Location l){return l.getWorld()==p.queue.world||p.safe(l);}
    boolean build(Player a,Location l){return !p.queue.waiting(a)&&(!protectedAt(l)||((p.admin.overrides(a)||a.hasPermission("blackwikismp.spawn.build")&&p.builders.contains(a.getUniqueId()))&&l.getWorld()!=p.queue.world));}
    boolean crosses(Location a,Location b){return protectedAt(a)||protectedAt(b);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(!build(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);else p.data.audit("PLACE",e.getPlayer().getName(),e.getBlock().getLocation(),e.getBlock().getType().name());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void multi(BlockMultiPlaceEvent e){if(e.getReplacedBlockStates().stream().anyMatch(s->!build(e.getPlayer(),s.getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void destroy(BlockBreakEvent e){if(!build(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);else p.data.audit("BREAK",e.getPlayer().getName(),e.getBlock().getLocation(),e.getBlock().getType().name());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){if(p.queue.waiting(e.getPlayer())){e.setCancelled(true);return;}if(e.getClickedBlock()!=null&&!build(e.getPlayer(),e.getClickedBlock().getLocation())){Material t=e.getClickedBlock().getType();if(t!=Material.CRAFTING_TABLE&&t!=Material.ENDER_CHEST)e.setCancelled(true);}else if(e.getClickedBlock()==null&&protectedAt(e.getPlayer().getLocation())&&!build(e.getPlayer(),e.getPlayer().getLocation())&&e.hasItem()){Material t=e.getItem().getType();if(t==Material.FIREWORK_ROCKET||t==Material.END_CRYSTAL||t==Material.FLINT_AND_STEEL||t==Material.FIRE_CHARGE||t==Material.TNT_MINECART||t.name().endsWith("_BUCKET"))e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityInteract(PlayerInteractEntityEvent e){if(p.npcs.isNpc(e.getRightClicked()))return;if(!build(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityAt(PlayerInteractAtEntityEvent e){if(p.npcs.isNpc(e.getRightClicked()))return;if(!build(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void armor(PlayerArmorStandManipulateEvent e){if(!build(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucket(PlayerBucketEmptyEvent e){if(!build(e.getPlayer(),e.getBlock().getLocation())||!build(e.getPlayer(),e.getBlockClicked().getRelative(e.getBlockFace()).getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){if(!build(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(p.npcs.isNpc(e.getEntity())){e.setCancelled(true);return;}
        Location at=e.getEntity().getLocation();Player by0=e instanceof EntityDamageByEntityEvent x?TravelService.attacker(x.getDamager()):null;if(!(e.getEntity() instanceof Player)&&p.admin.overrides(by0))return;if(protectedAt(at)&&(e.getEntity() instanceof Player||p.cfg().getBoolean("spawn.protect-mobs",true))){e.setCancelled(true);return;}
        if(e instanceof EntityDamageByEntityEvent by){Player a=TravelService.attacker(by.getDamager());if(a!=null&&(protectedAt(a.getLocation())||p.queue.waiting(a))){e.setCancelled(true);return;}if(a!=null&&e.getEntity() instanceof Player b&&p.teams.friendly(a,b))e.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void potion(PotionSplashEvent e){Player a=e.getPotion().getShooter() instanceof Player v?v:null;for(LivingEntity l:e.getAffectedEntities())if(protectedAt(l.getLocation())||(a!=null&&(protectedAt(a.getLocation())||p.queue.waiting(a)||(l instanceof Player b&&p.teams.friendly(a,b)))))e.setIntensity(l,0);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){Player a=e.getEntity().getSource() instanceof Player v?v:null;e.getAffectedEntities().removeIf(l->protectedAt(l.getLocation())||(a!=null&&(protectedAt(a.getLocation())||(l instanceof Player b&&p.teams.friendly(a,b)))));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){if(protectedAt(e.getLocation())){e.setCancelled(true);return;}e.blockList().removeIf(b->protectedAt(b.getLocation()));p.data.audit("EXPLODE",e.getEntityType().name(),e.getLocation(),"blocks="+e.blockList().size());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosionBlock(BlockExplodeEvent e){if(protectedAt(e.getBlock().getLocation())){e.setCancelled(true);return;}e.blockList().removeIf(b->protectedAt(b.getLocation()));p.data.audit("BLOCK_EXPLODE","block",e.getBlock().getLocation(),"blocks="+e.blockList().size());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void prime(ExplosionPrimeEvent e){if(protectedAt(e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void launch(ProjectileLaunchEvent e){if(e.getEntity().getShooter() instanceof Player a&&protectedAt(a.getLocation())&&!build(a,a.getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent e){Location from=e.getSource().getLocation(),to=e.getDestination().getLocation();if((from!=null&&protectedAt(from))||(to!=null&&protectedAt(to)))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frost(org.bukkit.event.block.EntityBlockFormEvent e){if(protectedAt(e.getBlock().getLocation())&&(!(e.getEntity() instanceof Player a)||!build(a,e.getBlock().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spread(BlockSpreadEvent e){if(crosses(e.getSource().getLocation(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fire(BlockIgniteEvent e){if(protectedAt(e.getBlock().getLocation())&&(e.getPlayer()==null||!build(e.getPlayer(),e.getBlock().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void flow(BlockFromToEvent e){if(crosses(e.getBlock().getLocation(),e.getToBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void grow(StructureGrowEvent e){if(e.getBlocks().stream().anyMatch(s->protectedAt(s.getLocation()))&&(e.getPlayer()==null||!build(e.getPlayer(),e.getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){if(e.getBlocks().stream().anyMatch(s->protectedAt(s.getLocation()))&&(e.getPlayer()==null||!build(e.getPlayer(),e.getBlock().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(protectedAt(e.getBlock().getLocation())||e.getBlocks().stream().anyMatch(b->crosses(b.getLocation(),b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(protectedAt(e.getBlock().getLocation())||e.getBlocks().stream().anyMatch(b->crosses(b.getLocation(),b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mobBlock(EntityChangeBlockEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&protectedAt(e.getTarget().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawn(CreatureSpawnEvent e){if(e.getLocation().getWorld()==p.queue.world||(p.safe(e.getLocation())&&e.getEntity() instanceof Monster))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hang(HangingBreakByEntityEvent e){if(protectedAt(e.getEntity().getLocation())&&(!(e.getRemover() instanceof Player a)||!build(a,e.getEntity().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hanging(HangingBreakEvent e){if(e.getCause()!=HangingBreakEvent.RemoveCause.ENTITY&&protectedAt(e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void vehicle(VehicleDamageEvent e){if(protectedAt(e.getVehicle().getLocation())&&!p.admin.overrides(TravelService.attacker(e.getAttacker())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void vehicleDestroy(VehicleDestroyEvent e){if(protectedAt(e.getVehicle().getLocation())&&!p.admin.overrides(TravelService.attacker(e.getAttacker())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent e){if(protectedAt(e.getFrom()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portalCreate(org.bukkit.event.world.PortalCreateEvent e){if(e.getBlocks().stream().anyMatch(b->protectedAt(b.getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){Player a=e.getPlayer();if(p.travel.inCombat(a)&&!p.safe(e.getFrom())&&p.safe(e.getTo())){e.setTo(e.getFrom());p.tell(a,"전투가 끝난 뒤 스폰 보호구역에 들어갈 수 있습니다.");}if(e.hasChangedPosition()&&!p.queue.waiting(a)&&p.cfg().getBoolean("border.kill-outside",true)&&p.outside(e.getTo())&&!a.isDead())a.setHealth(0);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pearl(PlayerTeleportEvent e){if(e.getCause()==PlayerTeleportEvent.TeleportCause.ENDER_PEARL&&protectedAt(e.getTo())){e.setCancelled(true);p.tell(e.getPlayer(),"스폰 안으로 엔더 진주를 사용할 수 없습니다.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){if(p.queue.waiting(e.getPlayer()))e.setCancelled(true);else if(p.safe(e.getPlayer().getLocation()))e.getItemDrop().setOwner(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player a&&p.queue.waiting(a))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void food(FoodLevelChangeEvent e){if(protectedAt(e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){if(p.queue.waiting(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){if(p.queue.waiting(e.getPlayer()))e.setCancelled(true);else if(e.getPlayer().isSneaking()){e.setCancelled(true);p.gui.main(e.getPlayer());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventory(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player a&&p.queue.waiting(a)&&!(e.getView().getTopInventory().getHolder() instanceof Gui.Menu))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player a&&p.queue.waiting(a))e.setCancelled(true);}
}
