package com.bountysmp.itemlock.lock;

import com.bountysmp.itemlock.model.LockDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ItemTracker implements Listener {
    private final Plugin plugin;
    private final LockRegistry registry;
    private final ItemMatcher matcher;
    private final NamespacedKey lockIdKey;
    private final NamespacedKey trackedKey;
    private final Map<UUID, Map<String, Integer>> droppedCounts = new LinkedHashMap<>();

    public ItemTracker(Plugin plugin, LockRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.matcher = new ItemMatcher(plugin);
        this.lockIdKey = new NamespacedKey(plugin, "lock_id");
        this.trackedKey = new NamespacedKey(plugin, "tracked");
    }

    public ItemMatcher matcher() {
        return matcher;
    }

    public void scanOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scanPlayer(player);
        }
    }

    public void snapshotOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            registry.snapshotOffline(player.getUniqueId(), trackedInventoryCounts(player));
        }
    }

    public void scanPlayerNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> scanPlayer(player));
    }

    public void scanPlayer(Player player) {
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            tagIfMatching(itemStack);
        }
        for (ItemStack itemStack : player.getInventory().getArmorContents()) {
            tagIfMatching(itemStack);
        }
        tagIfMatching(player.getInventory().getItemInOffHand());
    }

    public Optional<LockDefinition> definitionFor(ItemStack itemStack) {
        String lockId = lockId(itemStack);
        if (lockId != null) {
            LockDefinition definition = registry.byId(lockId);
            if (definition != null && definition.enabled()) {
                return Optional.of(definition);
            }
        }
        return registry.firstEnabledMatch(itemStack, matcher);
    }

    public Optional<LockDefinition> tagIfMatching(ItemStack itemStack) {
        if (isEmpty(itemStack)) {
            return Optional.empty();
        }
        String existing = lockId(itemStack);
        if (existing != null) {
            LockDefinition definition = registry.byId(existing);
            return definition == null || !definition.enabled() ? Optional.empty() : Optional.of(definition);
        }
        Optional<LockDefinition> match = registry.firstEnabledMatch(itemStack, matcher);
        match.ifPresent(definition -> tag(itemStack, definition.id()));
        return match;
    }

    public boolean hasProtection(ItemStack itemStack, java.util.function.Predicate<LockDefinition> predicate) {
        Optional<LockDefinition> definition = definitionFor(itemStack);
        return definition.isPresent() && predicate.test(definition.get());
    }

    public String lockId(ItemStack itemStack) {
        if (isEmpty(itemStack) || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(lockIdKey, PersistentDataType.STRING);
    }

    public void trackDroppedItem(Item item) {
        Optional<LockDefinition> definition = tagIfMatching(item.getItemStack());
        if (definition.isEmpty()) {
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(definition.get().id(), item.getItemStack().getAmount());
        droppedCounts.put(item.getUniqueId(), counts);
    }

    public void untrackDroppedItem(Item item) {
        droppedCounts.remove(item.getUniqueId());
    }

    public int onlineCount(String lockId) {
        int total = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            total += trackedInventoryCounts(player).getOrDefault(lockId, 0);
        }
        return total;
    }

    public int droppedCount(String lockId) {
        int total = 0;
        for (Map<String, Integer> counts : droppedCounts.values()) {
            total += counts.getOrDefault(lockId, 0);
        }
        return total;
    }

    public int activeCount(String lockId) {
        return registry.offlineCount(lockId) + onlineCount(lockId) + droppedCount(lockId);
    }

    public Map<String, Integer> trackedInventoryCounts(Player player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        countContents(counts, player.getInventory().getStorageContents());
        countContents(counts, player.getInventory().getArmorContents());
        countItem(counts, player.getInventory().getItemInOffHand());
        return counts;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        registry.clearOffline(event.getPlayer().getUniqueId());
        scanPlayerNextTick(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        registry.snapshotOffline(event.getPlayer().getUniqueId(), trackedInventoryCounts(event.getPlayer()));
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        registry.snapshotOffline(event.getPlayer().getUniqueId(), trackedInventoryCounts(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            untrackDroppedItem(event.getItem());
            scanPlayerNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scanPlayerNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scanPlayerNextTick(player);
        }
    }

    private void tag(ItemStack itemStack, String lockId) {
        itemStack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(lockIdKey, PersistentDataType.STRING, lockId);
            meta.getPersistentDataContainer().set(trackedKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void countContents(Map<String, Integer> counts, ItemStack[] contents) {
        for (ItemStack itemStack : contents) {
            countItem(counts, itemStack);
        }
    }

    private void countItem(Map<String, Integer> counts, ItemStack itemStack) {
        String lockId = lockId(itemStack);
        if (lockId != null && registry.byId(lockId) != null) {
            counts.merge(lockId, itemStack.getAmount(), Integer::sum);
        }
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }
}
