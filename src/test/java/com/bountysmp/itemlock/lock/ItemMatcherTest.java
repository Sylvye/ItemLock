package com.bountysmp.itemlock.lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.itemlock.BukkitTest;
import com.bountysmp.itemlock.ItemLockPlugin;
import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.model.MatchType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class ItemMatcherTest extends BukkitTest {
    @Test
    void exactMatchIgnoresAmountAndOwnTags() {
        ItemLockPlugin plugin = MockBukkit.load(ItemLockPlugin.class);
        ItemStack sample = namedDiamond("Key");
        LockDefinition definition = LockDefinition.create(sample, MatchType.EXACT);
        plugin.registry().upsert(definition);
        ItemStack candidate = namedDiamond("Key");
        candidate.setAmount(42);

        plugin.tracker().tagIfMatching(candidate);

        assertTrue(plugin.tracker().matcher().matches(definition, candidate));
    }

    @Test
    void exactMatchRejectsDifferentMeta() {
        ItemLockPlugin plugin = MockBukkit.load(ItemLockPlugin.class);
        LockDefinition definition = LockDefinition.create(namedDiamond("Key"), MatchType.EXACT);

        assertFalse(plugin.tracker().matcher().matches(definition, namedDiamond("Other")));
    }

    @Test
    void onlineCountIncludesNewUntaggedItems() {
        ItemLockPlugin plugin = MockBukkit.load(ItemLockPlugin.class);
        LockDefinition definition = LockDefinition.create(new ItemStack(Material.DIAMOND), MatchType.MATERIAL);
        plugin.registry().upsert(definition);
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 7));

        assertEquals(7, plugin.tracker().onlineCount(definition.id()));
    }

    private ItemStack namedDiamond(String name) {
        ItemStack itemStack = new ItemStack(Material.DIAMOND);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(name);
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
