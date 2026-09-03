package com.bountysmp.itemlock.gui;

import com.bountysmp.itemlock.lock.ItemTracker;
import com.bountysmp.itemlock.lock.LockRegistry;
import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.model.MatchType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class GuiManager implements Listener {
    private static final int[] LIST_SLOTS = {
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private final LockRegistry registry;
    private final ItemTracker tracker;
    private final ChatPromptManager prompts;
    private final Map<UUID, OpenMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();

    public GuiManager(Plugin plugin, LockRegistry registry, ItemTracker tracker, ChatPromptManager prompts) {
        this.registry = registry;
        this.tracker = tracker;
        this.prompts = prompts;
    }

    public void openMain(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(player, 54, "ItemLock");
        fill(inventory);
        inventory.setItem(10, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Add New", "Create a new item lock."));
        inventory.setItem(12, GuiUtil.item(Material.BOOK, GuiUtil.Tone.INFO, "Locked Items", "Left-click edit. Press Q delete."));
        List<LockDefinition> definitions = registry.definitions();
        int maxPage = maxPage(definitions.size(), LIST_SLOTS.length);
        int safePage = clampPage(page, maxPage);
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            int index = safePage * LIST_SLOTS.length + i;
            if (index >= definitions.size()) {
                break;
            }
            inventory.setItem(LIST_SLOTS[i], lockIcon(definitions.get(index)));
        }
        inventory.setItem(45, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Previous Page"));
        inventory.setItem(49, GuiUtil.item(Material.PAPER, GuiUtil.Tone.NEUTRAL, "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        inventory.setItem(53, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Next Page"));
        open(player, inventory, new OpenMenu(Screen.MAIN, safePage, null, inventory));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenMenu open = openMenus.get(player.getUniqueId());
        if (open == null) {
            return;
        }
        if (event.getView().getTopInventory() != open.inventory()) {
            openMenus.remove(player.getUniqueId());
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        boolean topClick = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (!topClick) {
            if (!canUseBottom(open)) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("itemlock.admin")) {
            return;
        }
        switch (open.screen()) {
            case MAIN -> handleMainClick(player, open, event.getRawSlot(), event.getClick());
            case CAPTURE -> handleCaptureClick(player, event.getRawSlot(), event.getCursor());
            case EDITOR -> handleEditorClick(player, open, event.getRawSlot());
            case CONFIRM_DELETE -> handleDeleteClick(player, open, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && openMenus.containsKey(player.getUniqueId())) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            OpenMenu open = openMenus.get(player.getUniqueId());
            if (open != null && open.inventory() == event.getInventory()) {
                openMenus.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        cleanup(event.getPlayer());
    }

    private void handleMainClick(Player player, OpenMenu open, int slot, ClickType click) {
        if (slot == 10) {
            sessions.put(player.getUniqueId(), new EditorSession((ItemStack) null, null));
            openCapture(player);
            return;
        }
        if (slot == 45) {
            openMain(player, open.page() - 1);
            return;
        }
        if (slot == 53) {
            openMain(player, open.page() + 1);
            return;
        }
        int listIndex = indexOf(LIST_SLOTS, slot);
        if (listIndex < 0) {
            return;
        }
        int lockIndex = open.page() * LIST_SLOTS.length + listIndex;
        List<LockDefinition> definitions = registry.definitions();
        if (lockIndex >= definitions.size()) {
            return;
        }
        LockDefinition definition = definitions.get(lockIndex);
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            openConfirmDelete(player, definition.id(), open.page());
            return;
        }
        sessions.put(player.getUniqueId(), new EditorSession(definition.copy(), definition.id()));
        openEditor(player, open.page());
    }

    private void handleCaptureClick(Player player, int slot, ItemStack cursor) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            openMain(player, 0);
            return;
        }
        if (slot == 22) {
            ItemStack source = !GuiUtil.isEmpty(cursor) ? cursor : player.getInventory().getItemInMainHand();
            if (GuiUtil.isEmpty(source)) {
                player.sendMessage("Hold an item on your cursor or in your main hand.");
                return;
            }
            ItemStack sample = tracker.matcher().stripOwnTags(source);
            sample.setAmount(1);
            sessions.put(player.getUniqueId(), new EditorSession(sample, null));
            openCapture(player);
            return;
        }
        if (slot == 30 || slot == 32) {
            if (GuiUtil.isEmpty(session.sample())) {
                player.sendMessage("Select an item first.");
                return;
            }
            MatchType type = slot == 30 ? MatchType.MATERIAL : MatchType.EXACT;
            LockDefinition definition = LockDefinition.create(session.sample(), type);
            sessions.put(player.getUniqueId(), new EditorSession(definition, definition.id()));
            openEditor(player, 0);
        } else if (slot == 49) {
            sessions.remove(player.getUniqueId());
            openMain(player, 0);
        }
    }

    private void handleEditorClick(Player player, OpenMenu open, int slot) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.definition() == null) {
            openMain(player, 0);
            return;
        }
        LockDefinition definition = session.definition();
        if (slot == 10) {
            definition.setEnabled(!definition.enabled());
        } else if (slot == 12) {
            definition.setMatchType(definition.matchType().next());
        } else if (slot == 19) {
            definition.setDepositProtection(!definition.depositProtection());
        } else if (slot == 21) {
            definition.setDropProtection(!definition.dropProtection());
        } else if (slot == 23) {
            definition.setPlaceProtection(!definition.placeProtection());
        } else if (slot == 25) {
            definition.setPickupProtection(!definition.pickupProtection());
        } else if (slot == 28) {
            definition.setBurnProtection(!definition.burnProtection());
        } else if (slot == 30) {
            definition.setExplosionProtection(!definition.explosionProtection());
        } else if (slot == 32) {
            definition.setDestructionProtection(!definition.destructionProtection());
        } else if (slot == 34) {
            definition.setDestructionMessage(!definition.destructionMessage());
        } else if (slot == 36) {
            definition.setDestructionAudience(definition.destructionAudience().next());
        } else if (slot == 38) {
            definition.setDestructionSoundEnabled(!definition.destructionSoundEnabled());
        } else if (slot == 40) {
            promptSound(player, definition, open.page());
            return;
        } else if (slot == 42) {
            definition.setDestroyedCount(0L);
        } else if (slot == 46) {
            registry.upsert(definition);
            tracker.scanPlayer(player);
            sessions.remove(player.getUniqueId());
            player.sendMessage("Item lock saved.");
            openMain(player, open.page());
            return;
        } else if (slot == 49) {
            sessions.remove(player.getUniqueId());
            openMain(player, open.page());
            return;
        } else if (slot == 52) {
            openConfirmDelete(player, definition.id(), open.page());
            return;
        } else {
            return;
        }
        openEditor(player, open.page());
    }

    private void handleDeleteClick(Player player, OpenMenu open, int slot) {
        if (slot == 11 && open.lockId() != null) {
            registry.remove(open.lockId());
            sessions.remove(player.getUniqueId());
            player.sendMessage("Item lock deleted.");
            openMain(player, open.page());
        } else if (slot == 15) {
            EditorSession session = sessions.get(player.getUniqueId());
            if (session != null && session.definition() != null) {
                openEditor(player, open.page());
            } else {
                openMain(player, open.page());
            }
        }
    }

    private void openCapture(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(player, 54, "New Item Lock");
        fill(inventory);
        ItemStack sample = session == null ? null : session.sample();
        inventory.setItem(22, GuiUtil.namedClone(sample, "Select Item", GuiUtil.Tone.INFO, List.of("Click with cursor item to copy it.", "Or click while holding an item.")));
        inventory.setItem(30, GuiUtil.item(Material.IRON_INGOT, GuiUtil.Tone.WARNING, "MATERIAL", "Match every item of this material."));
        inventory.setItem(32, GuiUtil.item(Material.NAME_TAG, GuiUtil.Tone.WARNING, "EXACT", "Match material and item data, ignoring amount."));
        inventory.setItem(49, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Cancel"));
        open(player, inventory, new OpenMenu(Screen.CAPTURE, 0, null, inventory));
    }

    private void openEditor(Player player, int page) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.definition() == null) {
            openMain(player, page);
            return;
        }
        LockDefinition definition = session.definition();
        Inventory inventory = Bukkit.createInventory(player, 54, "Edit Item Lock");
        fill(inventory);
        inventory.setItem(4, lockIcon(definition));
        inventory.setItem(10, toggleItem("Enabled", definition.enabled()));
        inventory.setItem(12, GuiUtil.item(Material.NAME_TAG, GuiUtil.Tone.INFO, "Match: " + definition.matchType(), "Click to cycle."));
        inventory.setItem(19, toggleItem("Deposit Protection", definition.depositProtection()));
        inventory.setItem(21, toggleItem("Drop Protection", definition.dropProtection()));
        inventory.setItem(23, toggleItem("Place Protection", definition.placeProtection()));
        inventory.setItem(25, toggleItem("Hopper/Entity Pickup Protection", definition.pickupProtection()));
        inventory.setItem(28, toggleItem("Burn Protection", definition.burnProtection()));
        inventory.setItem(30, toggleItem("Explosion Protection", definition.explosionProtection()));
        inventory.setItem(32, toggleItem("Recover on Destruction", definition.destructionProtection()));
        inventory.setItem(34, toggleItem("Destruction Message", definition.destructionMessage()));
        inventory.setItem(36, GuiUtil.item(Material.BELL, GuiUtil.Tone.WARNING, "Audience: " + definition.destructionAudience(), "Click to cycle."));
        inventory.setItem(38, toggleItem("Destruction Sound", definition.destructionSoundEnabled()));
        inventory.setItem(40, GuiUtil.item(Material.NOTE_BLOCK, GuiUtil.Tone.INFO, "Sound: " + definition.destructionSoundKey(), "Click to edit registry key."));
        inventory.setItem(42, GuiUtil.item(Material.CLOCK, GuiUtil.Tone.WARNING, "Reset Destroyed Stat", "Current: " + definition.destroyedCount(), "Click to reset to 0, then save."));
        inventory.setItem(46, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Save"));
        inventory.setItem(49, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Cancel"));
        inventory.setItem(52, GuiUtil.item(Material.BARRIER, GuiUtil.Tone.DANGER, "Delete"));
        open(player, inventory, new OpenMenu(Screen.EDITOR, page, definition.id(), inventory));
    }

    private void openConfirmDelete(Player player, String lockId, int page) {
        Inventory inventory = Bukkit.createInventory(player, 27, "Delete Item Lock");
        fill(inventory);
        inventory.setItem(11, GuiUtil.item(Material.RED_CONCRETE, GuiUtil.Tone.DANGER, "Delete Item Lock"));
        inventory.setItem(15, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Cancel"));
        open(player, inventory, new OpenMenu(Screen.CONFIRM_DELETE, page, lockId, inventory));
    }

    private ItemStack lockIcon(LockDefinition definition) {
        List<String> lore = new ArrayList<>();
        lore.add(definition.enabled() ? "Enabled" : "Disabled");
        lore.add("Match: " + definition.matchType());
        lore.add("Deposit: " + definition.depositProtection());
        lore.add("Place: " + definition.placeProtection());
        lore.add("Drop: " + definition.dropProtection());
        lore.add("Hopper/entity pickup: " + definition.pickupProtection());
        lore.add("Burn: " + definition.burnProtection());
        lore.add("Explosion: " + definition.explosionProtection());
        lore.add("Recover destroyed: " + definition.destructionProtection());
        lore.add("Message: " + definition.destructionMessage() + " / " + definition.destructionAudience());
        lore.add("Sound: " + definition.destructionSoundEnabled());
        lore.add("Destroyed: " + definition.destroyedCount());
        lore.add("Active: " + tracker.activeCount(definition.id()));
        lore.add("Online: " + tracker.onlineCount(definition.id()));
        lore.add("Left-click edit. Press Q delete.");
        return GuiUtil.namedClone(definition.sample(), ItemDisplay.plainName(definition.sample()), definition.enabled() ? GuiUtil.Tone.INFO : GuiUtil.Tone.MUTED, lore);
    }

    private void promptSound(Player player, LockDefinition definition, int page) {
        prompts.prompt(player, "Type a sound registry key, for example minecraft:entity.ender_dragon.growl.", text -> {
            if (!text.equalsIgnoreCase("cancel")) {
                String normalized = normalizeKey(text);
                NamespacedKey key = NamespacedKey.fromString(normalized);
                if (key == null || (Registry.SOUND_EVENT.get(key) == null && Registry.SOUNDS.get(key) == null)) {
                    player.sendMessage("Unknown sound: " + text);
                } else {
                    definition.setDestructionSoundKey(key.toString());
                    player.sendMessage("Sound set to " + key + ".");
                }
            }
            sessions.put(player.getUniqueId(), new EditorSession(definition, definition.id()));
            openEditor(player, page);
        });
    }

    private String normalizeKey(String input) {
        String key = input == null ? "" : input.trim().toLowerCase(java.util.Locale.ROOT);
        if (!key.isBlank() && !key.contains(":")) {
            key = "minecraft:" + key;
        }
        return key;
    }

    private ItemStack toggleItem(String name, boolean enabled) {
        return GuiUtil.item(enabled ? Material.LIME_DYE : Material.GRAY_DYE, enabled ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED, name + ": " + enabled, "Click to toggle.");
    }

    private void open(Player player, Inventory inventory, OpenMenu open) {
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), open);
    }

    private void cleanup(Player player) {
        openMenus.remove(player.getUniqueId());
        sessions.remove(player.getUniqueId());
    }

    private boolean canUseBottom(OpenMenu open) {
        return open.screen() == Screen.CAPTURE;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = GuiUtil.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private int indexOf(int[] slots, int rawSlot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    private int maxPage(int size, int pageSize) {
        return Math.max(0, (int) Math.ceil(size / (double) pageSize) - 1);
    }

    private int clampPage(int page, int maxPage) {
        return Math.max(0, Math.min(page, maxPage));
    }

    private enum Screen {
        MAIN,
        CAPTURE,
        EDITOR,
        CONFIRM_DELETE
    }

    private record OpenMenu(Screen screen, int page, String lockId, Inventory inventory) {
    }

    private record EditorSession(LockDefinition definition, ItemStack sample, String originalId) {
        EditorSession(ItemStack sample, String originalId) {
            this(null, sample, originalId);
        }

        EditorSession(LockDefinition definition, String originalId) {
            this(definition, null, originalId);
        }
    }
}
