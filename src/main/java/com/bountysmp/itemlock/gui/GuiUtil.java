package com.bountysmp.itemlock.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class GuiUtil {
    enum Tone {
        NEUTRAL(NamedTextColor.WHITE),
        INFO(NamedTextColor.AQUA),
        SUCCESS(NamedTextColor.GREEN),
        WARNING(NamedTextColor.YELLOW),
        DANGER(NamedTextColor.RED),
        MUTED(NamedTextColor.GRAY);

        private final NamedTextColor color;

        Tone(NamedTextColor color) {
            this.color = color;
        }
    }

    private GuiUtil() {
    }

    static ItemStack item(Material material, Tone tone, String name, String... lore) {
        return item(material, tone, name, List.of(lore));
    }

    static ItemStack item(Material material, Tone tone, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.text(name == null ? "" : name, tone.color).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line == null ? "" : line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.values());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    static ItemStack namedClone(ItemStack source, String name, Tone tone, List<String> lore) {
        ItemStack itemStack = isEmpty(source) ? item(Material.BARRIER, tone, name, lore) : source.clone();
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.text(name == null ? displayName(source) : name, tone.color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line == null ? "" : line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    static ItemStack filler() {
        return item(Material.BLACK_STAINED_GLASS_PANE, Tone.MUTED, " ");
    }

    static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }

    static String displayName(ItemStack itemStack) {
        if (isEmpty(itemStack)) {
            return "Empty";
        }
        return itemStack.getType().name().toLowerCase().replace('_', ' ');
    }
}
