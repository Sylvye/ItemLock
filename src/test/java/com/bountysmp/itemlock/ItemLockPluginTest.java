package com.bountysmp.itemlock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class ItemLockPluginTest extends BukkitTest {
    @Test
    void pluginEnablesWithNoDataAndCommandOpensGui() {
        ItemLockPlugin plugin = MockBukkit.load(ItemLockPlugin.class);
        assertNotNull(plugin.registry());

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "itemlock.admin", true);
        server.dispatchCommand(player, "itemlock");

        assertEquals("ItemLock", player.getOpenInventory().getTitle());
    }

    @Test
    void aliasOpensGui() {
        ItemLockPlugin plugin = MockBukkit.load(ItemLockPlugin.class);
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "itemlock.admin", true);

        server.dispatchCommand(player, "il");

        assertEquals("ItemLock", player.getOpenInventory().getTitle());
    }

    @Test
    void commandWithoutPermissionSendsRedDenial() {
        MockBukkit.load(ItemLockPlugin.class);
        PlayerMock player = server.addPlayer();

        server.dispatchCommand(player, "itemlock");

        assertEquals(ChatColor.RED + "You do not have permission to use ItemLock", player.nextMessage());
    }

    @Test
    void disableWithOnlinePlayerDoesNotScheduleSave() {
        ItemLockPlugin plugin = MockBukkit.load(ItemLockPlugin.class);
        server.addPlayer();

        assertDoesNotThrow(() -> server.getPluginManager().disablePlugin(plugin));
    }
}
