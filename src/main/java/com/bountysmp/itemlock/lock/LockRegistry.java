package com.bountysmp.itemlock.lock;

import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.storage.LockRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class LockRegistry {
    private final LockRepository repository;
    private final Plugin plugin;
    private final List<LockDefinition> definitions = new ArrayList<>();
    private final Map<UUID, Map<String, Integer>> offlineSnapshots = new LinkedHashMap<>();
    private BukkitTask pendingSave;

    public LockRegistry(LockRepository repository) {
        this(null, repository);
    }

    public LockRegistry(Plugin plugin, LockRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public synchronized void load() {
        LockRepository.Snapshot snapshot = repository.load();
        definitions.clear();
        definitions.addAll(snapshot.definitions());
        offlineSnapshots.clear();
        offlineSnapshots.putAll(snapshot.offlineSnapshots());
    }

    public synchronized void save() {
        if (plugin != null && plugin.isEnabled()) {
            scheduleSave();
            return;
        }
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        saveNow();
    }

    public synchronized void flush() {
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        saveNow();
    }

    private void saveNow() {
        repository.save(definitions, offlineSnapshots);
    }

    private void scheduleSave() {
        if (pendingSave != null) {
            return;
        }
        pendingSave = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (LockRegistry.this) {
                pendingSave = null;
                saveNow();
            }
        }, 20L);
    }

    public synchronized List<LockDefinition> definitions() {
        return definitions.stream().map(LockDefinition::copy).toList();
    }

    public synchronized List<LockDefinition> internalDefinitions() {
        return definitions;
    }

    public synchronized LockDefinition byId(String id) {
        for (LockDefinition definition : definitions) {
            if (definition.id().equals(id)) {
                return definition;
            }
        }
        return null;
    }

    public synchronized void upsert(LockDefinition definition) {
        if (definition == null || !definition.valid()) {
            return;
        }
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).id().equals(definition.id())) {
                LockDefinition copy = definition.copy();
                copy.setOrder(definitions.get(i).order());
                definitions.set(i, copy);
                normalizeOrder();
                save();
                return;
            }
        }
        LockDefinition copy = definition.copy();
        copy.setOrder(definitions.size());
        definitions.add(copy);
        normalizeOrder();
        save();
    }

    public synchronized void remove(String id) {
        definitions.removeIf(definition -> definition.id().equals(id));
        for (Map<String, Integer> counts : offlineSnapshots.values()) {
            counts.remove(id);
        }
        normalizeOrder();
        save();
    }

    public synchronized Optional<LockDefinition> firstEnabledMatch(org.bukkit.inventory.ItemStack itemStack, ItemMatcher matcher) {
        return definitions.stream()
            .filter(LockDefinition::enabled)
            .filter(definition -> matcher.matches(definition, itemStack))
            .findFirst();
    }

    public synchronized void snapshotOffline(UUID playerId, Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            offlineSnapshots.remove(playerId);
        } else {
            offlineSnapshots.put(playerId, new LinkedHashMap<>(counts));
        }
        save();
    }

    public synchronized void clearOffline(UUID playerId) {
        offlineSnapshots.remove(playerId);
        save();
    }

    public synchronized int offlineCount(String lockId) {
        int total = 0;
        for (Map<String, Integer> counts : offlineSnapshots.values()) {
            total += counts.getOrDefault(lockId, 0);
        }
        return total;
    }

    private void normalizeOrder() {
        definitions.sort(Comparator.comparingInt(LockDefinition::order));
        for (int i = 0; i < definitions.size(); i++) {
            definitions.get(i).setOrder(i);
        }
    }
}
