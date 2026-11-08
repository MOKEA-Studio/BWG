package kr.blackwiki.smp;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

final class AfkService
implements Listener,
AutoCloseable {
    final World world;
    private final BlackWikiSMP p;
    private final Location stand;
    private final Location guide;
    private final NamespacedKey marker;
    private final NamespacedKey npcKey;
    private final Map<UUID, Location> permitted = new HashMap<UUID, Location>();
    private final Map<UUID, Long> clicked = new HashMap<UUID, Long>();
    private final Set<Hidden> hidden = new HashSet<Hidden>();
    private int heartbeat;
    private boolean closed;

    AfkService(BlackWikiSMP p) {
        boolean existed;
        this.p = p;
        String name = AfkRules.worldName(p.cfg().getString("worlds.afk", "BlackWikiSMPAFK"));
        AfkRules.distinct(name, p.spawnWorld.getName(), p.wildWorld.getName(), p.queue.world.getName());
        this.marker = new NamespacedKey((Plugin)p, "afk-world");
        this.npcKey = new NamespacedKey((Plugin)p, "afk-guide");
        World found = Bukkit.getWorld((String)name);
        boolean bl = existed = found != null || Files.exists(Bukkit.getWorldContainer().toPath().resolve(name).resolve("level.dat"), new LinkOption[0]);
        if (found == null) {
            found = Bukkit.createWorld((WorldCreator)new WorldCreator(name).environment(World.Environment.NORMAL).generator((ChunkGenerator)new Empty()).generateStructures(false));
        }
        this.world = Objects.requireNonNull(found, "AFK 월드를 만들 수 없습니다.");
        if (existed && !this.world.getPersistentDataContainer().has(this.marker, PersistentDataType.BYTE)) {
            throw new IllegalArgumentException("AFK 월드 이름이 기존 일반 월드와 겹칩니다. worlds.afk를 변경하세요.");
        }
        this.world.getPersistentDataContainer().set(this.marker, PersistentDataType.BYTE, (byte)1);
        int y = p.cfg().getInt("afk.platform-y", 100);
        if (y < this.world.getMinHeight() + 2 || y > this.world.getMaxHeight() - 4) {
            throw new IllegalArgumentException("afk.platform-y 높이를 확인하세요.");
        }
        for (int x = -2; x <= 2; ++x) {
            for (int z = -2; z <= 4; ++z) {
                this.world.getBlockAt(x, y - 1, z).setType(Material.BARRIER, false);
            }
        }
        this.stand = new Location(this.world, 0.5, (double)y, 0.5, 0.0f, 0.0f);
        this.guide = new Location(this.world, 0.5, (double)y, 2.5, 180.0f, 0.0f);
        this.world.setSpawnLocation(this.stand);
        this.world.setPVP(false);
        this.world.setTime(6000L);
        this.world.setStorm(false);
        this.world.setThundering(false);
        this.world.setGameRule(GameRules.ADVANCE_TIME, false);
        this.world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        this.world.getWorldBorder().setCenter(0.0, 0.0);
        this.world.getWorldBorder().setSize(64.0);
        this.world.addPluginChunkTicket(0, 0, (Plugin)p);
        this.ensureGuide();
        this.world.save(true);
    }

    boolean at(Location l) {
        return l != null && l.getWorld() == this.world;
    }

    boolean contains(Player a) {
        return a != null && a.getWorld() == this.world;
    }

    Location stand() {
        return this.stand.clone();
    }

    boolean isGuide(Entity e) {
        return e != null && e.getWorld() == this.world && e.getPersistentDataContainer().has(this.npcKey, PersistentDataType.BYTE);
    }

    int count() {
        return this.world.getPlayers().size();
    }

    void enter(Player a) {
        if (this.contains(a)) {
            this.p.gui.worlds(a);
            return;
        }
        if (this.closed) {
            return;
        }
        a.closeInventory();
        this.p.travel.go(a, this.stand(), 5);
    }

    void command(Player a, String[] args) {
        if (args.length > 0 && Set.of("나가기", "leave", "스폰").contains(args[0].toLowerCase(Locale.ROOT))) {
            this.p.travel.go(a, this.p.spawn());
            return;
        }
        if (args.length > 0 && Set.of("입장", "enter", "이동").contains(args[0].toLowerCase(Locale.ROOT))) {
            this.enter(a);
            return;
        }
        if (this.contains(a)) {
            this.p.gui.worlds(a);
        } else {
            this.menu(a);
        }
    }

    void menu(Player a) {
        Gui.Menu m = this.p.gui.make(a, "구역 3 · 공허 AFK");
        m.afkRead = true;
        m.inventory.setItem(13, Gui.item(Material.LIGHT_BLUE_BED, "움직이지 않는 안전한 AFK 구역", "이동·점프·전투·아이템 상호작용 금지", "앞의 주민 우클릭 → 구역 선택 → 이동", "AFK에서는 0.5초당 1코인 · 접속 슬롯/플레이시간 유지"));
        this.p.gui.button(m, 22, Material.ENDER_EYE, "AFK 구역 입장", this::enter, "/afk 입장 · /월드 3", "전투 중 입장 불가 · 5초 대기");
        this.p.gui.button(m, 31, Material.COMPASS, "구역 선택", this.p.gui::worlds, "/월드");
        this.p.gui.show(a, m);
    }

    CompletableFuture<Boolean> teleport(Player a, Location target) {
        Location approval;
        if (this.closed) {
            return CompletableFuture.completedFuture(false);
        }
        UUID id = a.getUniqueId();
        if (this.permitted.putIfAbsent(id, approval = target.clone()) != null) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            this.p.admin.dropCrown(a);
            CompletableFuture result = a.teleportAsync(approval);
            result.whenComplete((ok, error) -> this.p.sync(() -> this.permitted.remove(id, approval)));
            return result;
        }
        catch (RuntimeException ex) {
            this.permitted.remove(id, approval);
            throw ex;
        }
    }

    private boolean same(Location a, Location b) {
        return a != null && b != null && a.getWorld() == b.getWorld() && a.distanceSquared(b) < 1.0E-4;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void teleportEvent(PlayerTeleportEvent e) {
        boolean queued;
        if (!this.at(e.getFrom()) && !this.at(e.getTo())) {
            return;
        }
        Location approved = this.permitted.get(e.getPlayer().getUniqueId());
        boolean bl = queued = this.p.queue.waiting(e.getPlayer()) && (e.getTo().getWorld() == this.p.queue.world || this.same(e.getTo(), this.stand));
        if (!this.same(approved, e.getTo()) && !queued) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent || !this.contains(e.getPlayer()) || !e.hasChangedPosition()) {
            return;
        }
        Location anchor = this.stand();
        anchor.setYaw(e.getTo().getYaw());
        anchor.setPitch(e.getTo().getPitch());
        e.setTo(anchor);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void changed(PlayerChangedWorldEvent e) {
        Player a = e.getPlayer();
        this.visibility(a);
        if (this.contains(a)) {
            a.setVelocity(new Vector());
            a.setFallDistance(0.0f);
            a.setFireTicks(0);
            this.p.travel.cancel(a, "");
            this.p.tell((CommandSender)a, "구역 3 · AFK 입장. 앞의 주민을 우클릭해 다른 구역으로 이동하세요.");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void joined(PlayerJoinEvent e) {
        Player a = e.getPlayer();
        Bukkit.getScheduler().runTask((Plugin)this.p, () -> {
            if (!a.isOnline()) {
                return;
            }
            this.visibility(a);
            if (this.contains(a) && !this.p.queue.waiting(a)) {
                this.teleport(a, this.stand());
                a.setFallDistance(0.0f);
                this.p.tell((CommandSender)a, "AFK 구역에 복귀했습니다. 주민 우클릭 또는 /월드로 나갈 수 있습니다.");
            }
        });
    }

    private void visibility(Player a) {
        for (Player b : Bukkit.getOnlinePlayers()) {
            if (a == b) continue;
            boolean obscure = this.contains(a) && this.contains(b);
            this.visibility(a, b, obscure);
            this.visibility(b, a, obscure);
        }
    }

    private void visibility(Player viewer, Player target, boolean obscure) {
        Hidden pair = new Hidden(viewer.getUniqueId(), target.getUniqueId());
        if (obscure) {
            if (this.hidden.add(pair)) {
                viewer.hidePlayer((Plugin)this.p, target);
            }
        } else if (this.hidden.remove(pair)) {
            viewer.showPlayer((Plugin)this.p, target);
        }
    }

    private void reveal(Player departing) {
        for (Hidden pair : List.copyOf(this.hidden)) {
            Player target;
            if (departing != null && !pair.viewer().equals(departing.getUniqueId()) && !pair.target().equals(departing.getUniqueId())) continue;
            Player viewer = departing != null && pair.viewer().equals(departing.getUniqueId()) ? departing : Bukkit.getPlayer((UUID)pair.viewer());
            Player player = target = departing != null && pair.target().equals(departing.getUniqueId()) ? departing : Bukkit.getPlayer((UUID)pair.target());
            if (viewer != null && target != null) {
                viewer.showPlayer((Plugin)this.p, target);
            }
            this.hidden.remove(pair);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEntityEvent e) {
        this.click(e);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void interactAt(PlayerInteractAtEntityEvent e) {
        this.click((PlayerInteractEntityEvent)e);
    }

    private void click(PlayerInteractEntityEvent e) {
        if (!this.contains(e.getPlayer()) && !this.isGuide(e.getRightClicked())) {
            return;
        }
        e.setCancelled(true);
        Player a = e.getPlayer();
        if (!this.contains(a) || !this.isGuide(e.getRightClicked()) || e.getHand() != EquipmentSlot.HAND || this.p.queue.waiting(a) || a.getLocation().distanceSquared(e.getRightClicked().getLocation()) > 20.25) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.clicked.getOrDefault(a.getUniqueId(), 0L) < 350000000L) {
            return;
        }
        this.clicked.put(a.getUniqueId(), now);
        this.p.gui.worlds(a);
    }

    private void ensureGuide() {
        List<Villager> found = this.world.getEntitiesByClass(Villager.class).stream().filter(this::isGuide).toList();
        if (!found.isEmpty()) {
            found.stream().skip(1L).forEach(Entity::remove);
            Villager v2 = found.getFirst();
            if (!this.same(v2.getLocation(), this.guide)) {
                v2.teleport(this.guide);
            }
            return;
        }
        this.world.spawn(this.guide, Villager.class, v -> {
            v.getPersistentDataContainer().set(this.npcKey, PersistentDataType.BYTE, (byte)1);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setGravity(false);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.customName(BlackWikiSMP.text("구역 선택 · 우클릭"));
            v.setCustomNameVisible(true);
        });
    }

    void tick() {
        if (this.closed) {
            return;
        }
        if (++this.heartbeat % 5 == 0) {
            this.ensureGuide();
            this.clicked.keySet().removeIf(id -> Bukkit.getPlayer((UUID)id) == null);
        }
        for (Player a : this.world.getPlayers()) {
            if (a.isDead() || this.p.queue.waiting(a)) continue;
            a.setFallDistance(0.0f);
            a.setVelocity(new Vector());
            if (this.same(a.getLocation(), this.stand) || this.permitted.containsKey(a.getUniqueId())) continue;
            Location anchor = this.stand();
            anchor.setYaw(a.getYaw());
            anchor.setPitch(a.getPitch());
            this.teleport(a, anchor);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void command(PlayerCommandPreprocessEvent e) {
        if (this.contains(e.getPlayer()) && !AfkRules.allowedCommand(e.getMessage())) {
            e.setCancelled(true);
            this.p.tell((CommandSender)e.getPlayer(), "AFK에서는 주민·/월드·/스폰·/야생·/명령어를 이용하세요.");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void item(PlayerInteractEvent e) {
        if (this.contains(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        if (this.at(e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent e) {
        if (this.at(e.getLocation()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void vehicle(VehicleEnterEvent e) {
        if (this.at(e.getEntered().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void inventory(InventoryClickEvent e) {
        block2: {
            block3: {
                Player a;
                HumanEntity humanEntity = e.getWhoClicked();
                if (!(humanEntity instanceof Player) || !this.contains(a = (Player)humanEntity)) break block2;
                org.bukkit.inventory.InventoryHolder holder = e.getView().getTopInventory().getHolder();
                if (!(holder instanceof Gui.Menu)) break block3;
                Gui.Menu m = (Gui.Menu)holder;
                if (m.afkRead) break block2;
            }
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        Player a;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && this.contains(a = (Player)humanEntity)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        this.reveal(e.getPlayer());
        this.permitted.remove(e.getPlayer().getUniqueId());
        this.clicked.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        this.closed = true;
        this.reveal(null);
        this.permitted.clear();
        this.clicked.clear();
        this.world.removePluginChunkTickets((Plugin)this.p);
    }

    static final class Empty
    extends ChunkGenerator {
        Empty() {
        }

        public boolean shouldGenerateNoise() {
            return false;
        }

        public boolean shouldGenerateSurface() {
            return false;
        }

        public boolean shouldGenerateCaves() {
            return false;
        }

        public boolean shouldGenerateDecorations() {
            return false;
        }

        public boolean shouldGenerateMobs() {
            return false;
        }

        public boolean shouldGenerateStructures() {
            return false;
        }
    }

    private record Hidden(UUID viewer, UUID target) {
    }
}

