package com.bountysmp.itemlock.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemDisplay {
    private ItemDisplay() {
    }

    public static Component itemComponent(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return Component.text("Unknown Item", NamedTextColor.YELLOW);
        }
        return itemStack.effectiveName()
            .colorIfAbsent(NamedTextColor.YELLOW)
            .hoverEvent(itemStack.asHoverEvent(showItem -> showItem));
    }

    public static Component countedItemComponent(ItemStack itemStack, int amount) {
        return Component.text(Math.max(1, amount) + "x ", NamedTextColor.YELLOW)
            .append(itemComponent(itemStack));
    }

    public static String plainName(ItemStack itemStack) {
        return PlainTextComponentSerializer.plainText().serialize(itemComponent(itemStack));
    }
}
