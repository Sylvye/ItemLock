package com.bountysmp.itemlock.lock;

import com.bountysmp.itemlock.model.DestructionAudience;
import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.gui.ItemDisplay;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class ProtectionListener implements Listener {
    private final Plugin plugin;
    private final LockRegistry registry;
    private final ItemTracker tracker;
    private final Set<UUID> reportedDestroyedEntities = new HashSet<>();

    public ProtectionListener(Plugin plugin, LockRegistry registry, ItemTracker tracker) {
        this.plugin = plugin;
        this.registry = registry;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Optional<LockDefinition> definition = tracker.definitionFor(event.getItemInHand());
        if (definition.isPresent() && definition.get().placeProtection()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!hasNonPlayerTop(event.getView().getTopInventory())) {
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && protectedForDeposit(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        boolean topClick = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && !topClick && protectedForDeposit(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (topClick && placesCursorIntoSlot(event.getAction()) && protectedForDeposit(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (topClick && swapsHotbarIntoSlot(event.getAction())) {
            ItemStack hotbar = hotbarItem(event);
            if (protectedForDeposit(hotbar)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!hasNonPlayerTop(event.getView().getTopInventory())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize);
        if (touchesTop && protectedForDeposit(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!isPlayerInventory(event.getDestination()) && protectedForDeposit(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        Optional<LockDefinition> definition = tracker.tagIfMatching(item.getItemStack());
        if (definition.isPresent() && definition.get().dropProtection() && !isDeathDrop(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        tracker.trackDroppedItem(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDrop(EntityDropItemEvent event) {
        tracker.trackDroppedItem(event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }
        Optional<LockDefinition> definition = tracker.definitionFor(item.getItemStack());
        if (definition.isEmpty()) {
            return;
        }
        if (isBurn(event.getCause()) && definition.get().burnProtection()) {
            event.setCancelled(true);
            return;
        }
        if (isExplosion(event.getCause()) && definition.get().explosionProtection()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        Optional<LockDefinition> definition = tracker.definitionFor(event.getEntity().getItemStack());
        if (definition.isPresent() && definition.get().destructionMessage()) {
            reportDestroyed(event.getEntity(), definition.get());
        }
        tracker.untrackDroppedItem(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item) || !isDestructiveRemoval(event.getCause())) {
            return;
        }
        Optional<LockDefinition> definition = tracker.definitionFor(item.getItemStack());
        if (definition.isPresent() && definition.get().destructionMessage()) {
            reportDestroyed(item, definition.get());
        }
        tracker.untrackDroppedItem(item);
    }

    private boolean protectedForDeposit(ItemStack itemStack) {
        return tracker.hasProtection(itemStack, LockDefinition::depositProtection);
    }

    private boolean isDeathDrop(Player player) {
        return player.isDead() || player.getHealth() <= 0.0;
    }

    private void reportDestroyed(Item item, LockDefinition definition) {
        if (!reportedDestroyedEntities.add(item.getUniqueId())) {
            return;
        }
        ItemStack stack = item.getItemStack();
        int amount = Math.max(1, stack.getAmount());
        definition.addDestroyed(amount);
        registry.save();
        tracker.untrackDroppedItem(item);

        Component message = Component.text("[ItemLock] ", NamedTextColor.RED)
            .append(ItemDisplay.countedItemComponent(stack, amount))
            .append(Component.text(" was destroyed", NamedTextColor.YELLOW));
        List<Player> recipients = recipients(definition);
        for (Player player : recipients) {
            player.sendMessage(message);
        }
        if (definition.destructionSoundEnabled()) {
            playSound(definition, recipients);
        }
        plugin.getLogger().warning(amount + "x " + ItemDisplay.plainName(stack) + " was destroyed.");
    }

    private List<Player> recipients(LockDefinition definition) {
        List<Player> recipients = new ArrayList<>();
        if (definition.destructionAudience() == DestructionAudience.PUBLIC) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                recipients.add(player);
            }
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("itemlock.admin")) {
                    recipients.add(player);
                }
            }
        }
        return recipients;
    }

    private void playSound(LockDefinition definition, List<Player> recipients) {
        NamespacedKey key = NamespacedKey.fromString(definition.destructionSoundKey());
        Sound sound = key == null ? null : Registry.SOUND_EVENT.get(key);
        if (sound == null && key != null) {
            sound = Registry.SOUNDS.get(key);
        }
        if (sound == null) {
            return;
        }
        for (Player player : recipients) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    private boolean placesCursorIntoSlot(InventoryAction action) {
        return action == InventoryAction.PLACE_ALL
            || action == InventoryAction.PLACE_SOME
            || action == InventoryAction.PLACE_ONE
            || action == InventoryAction.SWAP_WITH_CURSOR;
    }

    private boolean swapsHotbarIntoSlot(InventoryAction action) {
        return action == InventoryAction.HOTBAR_SWAP;
    }

    private ItemStack hotbarItem(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getHotbarButton() < 0) {
            return null;
        }
        return player.getInventory().getItem(event.getHotbarButton());
    }

    private boolean hasNonPlayerTop(Inventory inventory) {
        return inventory != null && !isPlayerInventory(inventory);
    }

    private boolean isPlayerInventory(Inventory inventory) {
        return inventory != null && (inventory.getType() == InventoryType.PLAYER || inventory.getType() == InventoryType.CRAFTING);
    }

    private boolean isBurn(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
            || cause == EntityDamageEvent.DamageCause.FIRE_TICK
            || cause == EntityDamageEvent.DamageCause.LAVA
            || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private boolean isExplosion(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }

    private boolean isDestructiveRemoval(EntityRemoveEvent.Cause cause) {
        return switch (cause) {
            case DEATH, DESPAWN, ENTER_BLOCK, EXPLODE, HIT, OUT_OF_WORLD, PLUGIN, DISCARD -> true;
            case DROP, MERGE, PICKUP, PLAYER_QUIT, TRANSFORMATION, UNLOAD -> false;
        };
    }
}
