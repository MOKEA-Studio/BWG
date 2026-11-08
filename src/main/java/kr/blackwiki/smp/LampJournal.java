package kr.blackwiki.smp;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

final class LampJournal {
    private final Path progress;
    private final Path receipt;
    private final UUID world;

    LampJournal(Path folder, UUID world) {
        this.world = world;
        this.progress = folder.resolve("spawn-lighting.yml");
        this.receipt = folder.resolve("spawn-lighting-complete.yml");
    }

    State load() throws Exception {
        if (Files.exists(this.receipt, new LinkOption[0])) {
            State full = this.read(this.receipt);
            if (!full.done) {
                throw new IllegalArgumentException("Incomplete lighting receipt");
            }
            return full;
        }
        return Files.exists(this.progress, new LinkOption[0]) ? this.read(this.progress) : null;
    }

    private State read(Path file) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.load(file.toFile());
        if (y.getInt("schema") != 1 || !this.world.toString().equals(y.getString("world-id"))) {
            throw new IllegalArgumentException("Invalid lighting journal world/schema");
        }
        return new State(this.world, y.getInt("origin-x"), y.getInt("origin-y"), y.getInt("origin-z"), y.getInt("index"), y.getInt("skipped"), y.getStringList("placed"), y.getBoolean("done"));
    }

    private String text(State s) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("schema", 1);
        y.set("world-id", s.world.toString());
        y.set("origin-x", s.x);
        y.set("origin-y", s.y);
        y.set("origin-z", s.z);
        y.set("done", s.done);
        y.set("index", s.index);
        y.set("skipped", s.skipped);
        y.set("placed", s.placed);
        return y.saveToString();
    }

    void save(State state) throws Exception {
        if (!this.world.equals(state.world)) {
            throw new IllegalArgumentException("Wrong lighting world");
        }
        if (Files.exists(this.receipt, new LinkOption[0]) && !this.read(this.receipt).equals(state)) {
            throw new IllegalStateException("Lighting completion cannot be replaced by stale progress");
        }
        String text = this.text(state);
        if (state.done && !Files.exists(this.receipt, new LinkOption[0])) {
            DataStore.atomic(this.receipt, text);
        }
        DataStore.atomic(this.progress, text);
    }

    record State(UUID world, int x, int y, int z, int index, int skipped, List<String> placed, boolean done) {
        State {
            placed = List.copyOf(placed);
            if (world == null || index < 0 || index > 100 || skipped < 0 || placed.size() + skipped != index || done && index != 100 || new HashSet<String>(placed).size() != placed.size()) {
                throw new IllegalArgumentException("Invalid lighting progress");
            }
            for (String s : placed) {
                String[] xy = s.split(",", -1);
                if (xy.length == 2 && Math.abs(Integer.parseInt(xy[0])) <= 48 && Math.abs(Integer.parseInt(xy[1])) <= 48) continue;
                throw new IllegalArgumentException("Invalid lighting site");
            }
        }
    }
}

