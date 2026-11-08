package kr.blackwiki.smp;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class SpawnLightingService
implements AutoCloseable {
    private final BlackWikiSMP p;
    private final LampJournal journal;
    private final Map<SpawnPlan.Pos, Material> original = new HashMap<SpawnPlan.Pos, Material>();
    private final List<String> placed = new ArrayList<String>();
    private int x;
    private int y;
    private int z;
    private int index;
    private int skipped;
    private long generation;
    private boolean done;
    private boolean busy;
    private boolean closed;
    private boolean started;
    private String problem = "";
    private BukkitTask task;

    SpawnLightingService(BlackWikiSMP p) throws Exception {
        this.p = p;
        this.journal = new LampJournal(p.getDataFolder().toPath(), p.spawnWorld.getUID());
        for (SpawnPlan.Voxel v : SpawnPlan.build()) {
            this.original.put(new SpawnPlan.Pos(v.x(), v.y(), v.z()), v.type());
        }
        LampJournal.State s = this.journal.load();
        if (s != null) {
            this.x = s.x();
            this.y = s.y();
            this.z = s.z();
            this.index = s.index();
            this.skipped = s.skipped();
            this.placed.addAll(s.placed());
            this.done = s.done();
            this.started = true;
            if (!this.done) {
                this.index = 0;
                this.skipped = 0;
                this.placed.clear();
            }
        }
    }

    void tick() {
        if (!(this.closed || this.busy || !this.p.cfg().getBoolean("spawn-lighting.auto-install", true) || this.done || this.task != null || !this.p.spawnDesign.done() || this.p.backups.running() || this.p.resets.busy())) {
            this.start();
        }
    }

    void start() {
        if (this.closed || this.busy || this.done || this.task != null || !this.p.spawnDesign.done() || this.p.backups.running() || this.p.resets.busy()) {
            return;
        }
        try {
            if (!this.started) {
                YamlConfiguration garden = YamlConfiguration.loadConfiguration((File)this.p.getDataFolder().toPath().resolve("spawn-build.yml").toFile());
                this.x = garden.getInt("x");
                this.y = garden.getInt("y");
                this.z = garden.getInt("z");
                this.started = true;
            }
            if (this.y < this.p.spawnWorld.getMinHeight() || this.y + 5 >= this.p.spawnWorld.getMaxHeight()) {
                throw new IllegalArgumentException("Invalid lamp height");
            }
            ++this.generation;
            this.save();
            this.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.p, this::step, 1L, 1L);
        }
        catch (Exception ex) {
            this.fail(ex);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void step() {
        if (this.closed || this.busy || this.done || this.p.backups.running() || this.p.resets.busy()) {
            return;
        }
        this.busy = true;
        long token = this.generation;
        int expected = this.index;
        if (this.index >= LampPlan.sites().size()) {
            if (this.task != null) {
                this.task.cancel();
                this.task = null;
            }
            try {
                this.p.spawnWorld.save(true);
                this.done = true;
                this.save();
                this.p.getLogger().info("Spawn glowstone lighting finished: " + this.placed.size() + " lamps, " + this.skipped + " occupied sites preserved");
            }
            catch (Exception ex) {
                this.done = false;
                this.fail(ex);
            }
            finally {
                this.busy = false;
            }
            return;
        }
        LampPlan.Site site = LampPlan.sites().get(this.index);
        this.p.spawnWorld.getChunkAtAsync(this.x + site.x() >> 4, this.z + site.z() >> 4, true).whenComplete((chunk, error) -> this.p.sync(() -> {
            if (this.closed || token != this.generation) {
                this.busy = false;
                return;
            }
            if (error != null) {
                this.busy = false;
                this.fail((Throwable)error);
                return;
            }
            chunk.addPluginChunkTicket((Plugin)this.p);
            try {
                if (this.index != expected || this.done) {
                    throw new IllegalStateException("Stale lamp callback");
                }
                boolean built = false;
                block5: for (int radius = 0; radius <= 3; ++radius) {
                    for (int dx = -radius; dx <= radius; ++dx) {
                        for (int dz = -radius; dz <= radius; ++dz) {
                            int sz;
                            int sx;
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius || !this.fit(sx = site.x() + dx, sz = site.z() + dz)) continue;
                            for (int h = 1; h <= LampPlan.COLUMN.size(); ++h) {
                                Block block = this.p.spawnWorld.getBlockAt(this.x + sx, this.y + h, this.z + sz);
                                if (block.getType() == LampPlan.COLUMN.get(h - 1)) continue;
                                block.setType(LampPlan.COLUMN.get(h - 1), false);
                            }
                            this.placed.add(sx + "," + sz);
                            built = true;
                            break block5;
                        }
                    }
                }
                if (!built) {
                    ++this.skipped;
                }
                ++this.index;
                this.save();
            }
            catch (Exception ex) {
                this.fail(ex);
            }
            finally {
                chunk.removePluginChunkTicket((Plugin)this.p);
                this.busy = false;
            }
        }));
    }

    private boolean fit(int sx, int sz) {
        if (sx < -48 || sx > 48 || sz < -48 || sz > 48 || !this.p.spawnWorld.getBlockAt(this.x + sx, this.y, this.z + sz).getType().isSolid()) {
            return false;
        }
        for (int h = 1; h <= LampPlan.COLUMN.size(); ++h) {
            Material before;
            Material at = this.p.spawnWorld.getBlockAt(this.x + sx, this.y + h, this.z + sz).getType();
            Material want = LampPlan.COLUMN.get(h - 1);
            if (at.isAir() || at == want || ((before = this.original.get(new SpawnPlan.Pos(sx, h, sz))) == Material.PINK_TULIP || before == Material.WHITE_TULIP || before == Material.LANTERN) && at == before) continue;
            return false;
        }
        return true;
    }

    private void save() throws Exception {
        this.journal.save(new LampJournal.State(this.p.spawnWorld.getUID(), this.x, this.y, this.z, this.index, this.skipped, this.placed, this.done));
    }

    private void fail(Throwable ex) {
        this.problem = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        this.closed = true;
        ++this.generation;
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.p.getLogger().log(Level.WARNING, "Spawn lighting paused; existing buildings retained", ex);
    }

    String status() {
        return !this.problem.isEmpty() ? "설치 대기: " + this.problem : (this.done ? "발광석 가로등 " + this.placed.size() + "개 · 설치 완료 · 건축 보호 " + this.skipped + "곳" : (this.task != null ? "가로등 설치 " + this.index + "/100" : "정원 완성 후 자동 설치"));
    }

    void command(CommandSender sender, String[] args) {
        if (!BanService.operator(sender)) {
            this.p.tell(sender, "OP만 사용할 수 있습니다.");
            return;
        }
        if (args.length > 0 && args[0].equals("설치")) {
            this.start();
            this.p.tell(sender, this.status());
            return;
        }
        if (sender instanceof Player) {
            Player a = (Player)sender;
            Gui.Menu m = this.p.gui.make(a, "스폰 발광석 조명");
            m.inventory.setItem(13, Gui.item(Material.GLOWSTONE, this.status(), "100×100 정원 전체 · 발광석 가로등", "기존 바닥·건물·나무 보존"));
            this.p.gui.button(m, 20, Material.GLOWSTONE, "설치 시작·확인", b -> {
                if (BanService.operator((CommandSender)b)) {
                    this.start();
                    this.command((CommandSender)b, new String[0]);
                }
            }, new String[0]);
            this.p.gui.show(a, m);
        } else {
            this.p.tell(sender, this.status());
        }
    }

    @Override
    public void close() {
        this.closed = true;
        ++this.generation;
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        if (this.started && !this.busy) {
            try {
                this.save();
            }
            catch (Exception ex) {
                this.p.getLogger().log(Level.WARNING, "Lighting checkpoint save failed", ex);
            }
        }
    }
}

