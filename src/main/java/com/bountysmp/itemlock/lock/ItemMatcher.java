package com.bountysmp.itemlock.lock;

import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.model.MatchType;
import java.util.Base64;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class ItemMatcher {
    private final List<NamespacedKey> ownKeys;

    public ItemMatcher(Plugin plugin) {
        this.ownKeys = List.of(
            new NamespacedKey(plugin, "lock_id"),
            new NamespacedKey(plugin, "tracked")
        );
    }

    public boolean matches(LockDefinition definition, ItemStack itemStack) {
        if (definition == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemStack sample = definition.sample();
        if (sample == null || sample.getType().isAir() || sample.getType() != itemStack.getType()) {
            return false;
        }
        if (definition.matchType() == MatchType.MATERIAL) {
            return true;
        }
        return exactFingerprint(sample).equals(exactFingerprint(itemStack));
    }

    public ItemStack stripOwnTags(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        ItemStack copy = itemStack.clone();
        copy.setAmount(1);
        if (copy.hasItemMeta()) {
            ItemMeta meta = copy.getItemMeta();
            for (NamespacedKey key : ownKeys) {
                meta.getPersistentDataContainer().remove(key);
            }
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private String exactFingerprint(ItemStack itemStack) {
        return Base64.getEncoder().encodeToString(stripOwnTags(itemStack).serializeAsBytes());
    }
}
