package kr.blackwiki.smp;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class WeatherJournal {
    private final Path file;

    WeatherJournal(Path file) {
        this.file = file;
    }

    Map<UUID, State> load() throws Exception {
        LinkedHashMap<UUID, State> states = new LinkedHashMap<UUID, State>();
        if (!Files.exists(this.file, new LinkOption[0])) {
            return states;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.load(this.file.toFile());
        if (y.getInt("schema") != 1) {
            throw new IllegalArgumentException("Unknown weather journal schema");
        }
        ConfigurationSection worlds = y.getConfigurationSection("worlds");
        if (worlds != null) {
            for (String id : worlds.getKeys(false)) {
                ConfigurationSection s = worlds.getConfigurationSection(id);
                if (s == null) {
                    throw new IllegalArgumentException("Invalid weather world");
                }
                for (String key : List.of("advance", "storm", "thunder")) {
                    if (s.get(key) instanceof Boolean) continue;
                    throw new IllegalArgumentException("Invalid weather flag");
                }
                for (String key : List.of("rain-ticks", "thunder-ticks", "clear-ticks")) {
                    if (s.get(key) instanceof Integer) continue;
                    throw new IllegalArgumentException("Invalid weather duration type");
                }
                states.put(UUID.fromString(id), new State(s.getBoolean("advance"), s.getBoolean("storm"), s.getBoolean("thunder"), s.getInt("rain-ticks"), s.getInt("thunder-ticks"), s.getInt("clear-ticks")));
            }
        }
        return states;
    }

    void save(Map<UUID, State> states) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.set("schema", 1);
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            String at = "worlds." + String.valueOf(e.getKey()) + ".";
            State s = e.getValue();
            y.set(at + "advance", s.advance);
            y.set(at + "storm", s.storm);
            y.set(at + "thunder", s.thunder);
            y.set(at + "rain-ticks", s.rainTicks);
            y.set(at + "thunder-ticks", s.thunderTicks);
            y.set(at + "clear-ticks", s.clearTicks);
        }
        DataStore.atomic(this.file, y.saveToString());
    }

    record State(boolean advance, boolean storm, boolean thunder, int rainTicks, int thunderTicks, int clearTicks) {
        State {
            if (rainTicks < 0 || thunderTicks < 0 || clearTicks < 0) {
                throw new IllegalArgumentException("Invalid weather duration");
            }
        }
    }
}

