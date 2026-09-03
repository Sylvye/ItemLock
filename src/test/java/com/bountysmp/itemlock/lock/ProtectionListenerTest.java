package com.bountysmp.itemlock.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.itemlock.BukkitTest;
import com.bountysmp.itemlock.ItemLockPlugin;
import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.model.MatchType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

final class ProtectionListenerTest extends BukkitTest {
    @Test
    void giveFeedbackItemIsNotReportedAsDestroyed() {
        ItemLockPlugin plugin = MockBukkit.load(ItemLockPlugin.class);
        LockDefinition definition = LockDefinition.create(new ItemStack(Material.DRAGON_EGG), MatchType.MATERIAL);
        definition.setDestructionMessage(true);
        plugin.registry().upsert(definition);
        WorldMock world = server.addSimpleWorld("world");
        Location location = new Location(world, 0, 64, 0);
        Item feedbackItem = world.dropItem(location, new ItemStack(Material.DRAGON_EGG));
        feedbackItem.setPickupDelay(Short.MAX_VALUE);

        server.getPluginManager().callEvent(new ItemDespawnEvent(feedbackItem, location));

        assertEquals(0L, plugin.registry().byId(definition.id()).destroyedCount());
    }
}
