package com.bountysmp.itemlock.storage;

import com.bountysmp.itemlock.model.DestructionAudience;
import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.model.MatchType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class LockRepository {
    private static final int SCHEMA_VERSION = 1;
    private final File file;

    public LockRepository(File file) {
        this.file = file;
    }

    public Snapshot load() {
        List<LockDefinition> definitions = new ArrayList<>();
        Map<UUID, Map<String, Integer>> offlineSnapshots = new HashMap<>();
        if (!file.exists()) {
            return new Snapshot(definitions, offlineSnapshots);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection locksSection = yaml.getConfigurationSection("locks");
        if (locksSection != null) {
            int fallbackOrder = 0;
            for (String id : locksSection.getKeys(false)) {
                ConfigurationSection section = locksSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                LockDefinition definition = readDefinition(id, section, fallbackOrder++);
                if (definition.valid()) {
                    definitions.add(definition);
                }
            }
        }
        definitions.sort(Comparator.comparingInt(LockDefinition::order));
        normalizeOrder(definitions);

        ConfigurationSection offlineSection = yaml.getConfigurationSection("offline-snapshots");
        if (offlineSection != null) {
            for (String uuidText : offlineSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    ConfigurationSection playerSection = offlineSection.getConfigurationSection(uuidText);
                    if (playerSection == null) {
                        continue;
                    }
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    for (String lockId : playerSection.getKeys(false)) {
                        int amount = playerSection.getInt(lockId, 0);
                        if (amount > 0) {
                            counts.put(lockId, amount);
                        }
                    }
                    if (!counts.isEmpty()) {
                        offlineSnapshots.put(uuid, counts);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed persisted UUID keys.
                }
            }
        }
        return new Snapshot(definitions, offlineSnapshots);
    }

    public void save(Iterable<LockDefinition> definitions, Map<UUID, Map<String, Integer>> offlineSnapshots) {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA_VERSION);
        ConfigurationSection locksSection = yaml.createSection("locks");
        for (LockDefinition definition : definitions) {
            writeDefinition(locksSection.createSection(definition.id()), definition);
        }
        ConfigurationSection offlineSection = yaml.createSection("offline-snapshots");
        for (Map.Entry<UUID, Map<String, Integer>> entry : offlineSnapshots.entrySet()) {
            ConfigurationSection playerSection = offlineSection.createSection(entry.getKey().toString());
            for (Map.Entry<String, Integer> count : entry.getValue().entrySet()) {
                if (count.getValue() != null && count.getValue() > 0) {
                    playerSection.set(count.getKey(), count.getValue());
                }
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save " + file.getAbsolutePath(), exception);
        }
    }

    private LockDefinition readDefinition(String id, ConfigurationSection section, int fallbackOrder) {
        LockDefinition definition = new LockDefinition(id);
        definition.setEnabled(section.getBoolean("enabled", true));
        definition.setSample(section.getItemStack("sample"));
        definition.setMatchType(MatchType.parse(section.getString("match-type"), MatchType.MATERIAL));
        definition.setDepositProtection(section.getBoolean("deposit-protection", true));
        definition.setPlaceProtection(section.getBoolean("place-protection", true));
        definition.setDropProtection(section.getBoolean("drop-protection", false));
        definition.setBurnProtection(section.getBoolean("burn-protection", false));
        definition.setExplosionProtection(section.getBoolean("explosion-protection", false));
        definition.setDestructionProtection(section.getBoolean("destruction-protection", false));
        definition.setDestructionMessage(section.getBoolean("destruction-message.enabled", false));
        definition.setDestructionAudience(DestructionAudience.parse(section.getString("destruction-message.audience"), DestructionAudience.OPERATOR));
        definition.setDestructionSoundEnabled(section.getBoolean("destruction-message.sound-enabled", true));
        definition.setDestructionSoundKey(section.getString("destruction-message.sound", "minecraft:entity.ender_dragon.growl"));
        definition.setDestroyedCount(section.getLong("stats.destroyed", 0L));
        definition.setOrder(section.getInt("order", fallbackOrder));
        return definition;
    }

    private void writeDefinition(ConfigurationSection section, LockDefinition definition) {
        section.set("enabled", definition.enabled());
        ItemStack sample = definition.sample();
        if (sample != null) {
        section.set("sample", sample);
        }
        section.set("match-type", definition.matchType().name());
        section.set("deposit-protection", definition.depositProtection());
        section.set("place-protection", definition.placeProtection());
        section.set("drop-protection", definition.dropProtection());
        section.set("burn-protection", definition.burnProtection());
        section.set("explosion-protection", definition.explosionProtection());
        section.set("destruction-protection", definition.destructionProtection());
        section.set("destruction-message.enabled", definition.destructionMessage());
        section.set("destruction-message.audience", definition.destructionAudience().name());
        section.set("destruction-message.sound-enabled", definition.destructionSoundEnabled());
        section.set("destruction-message.sound", definition.destructionSoundKey());
        section.set("stats.destroyed", definition.destroyedCount());
        section.set("order", definition.order());
    }

    private void normalizeOrder(List<LockDefinition> definitions) {
        for (int i = 0; i < definitions.size(); i++) {
            definitions.get(i).setOrder(i);
        }
    }

    public record Snapshot(List<LockDefinition> definitions, Map<UUID, Map<String, Integer>> offlineSnapshots) {
    }
}
