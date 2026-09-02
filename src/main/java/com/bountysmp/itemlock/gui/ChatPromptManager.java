package com.bountysmp.itemlock.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class ChatPromptManager implements Listener {
    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();

    public ChatPromptManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String message, Consumer<String> response) {
        prompts.put(player.getUniqueId(), response);
        player.closeInventory();
        player.sendMessage(message);
        player.sendMessage("Type cancel to abort.");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Consumer<String> response = prompts.remove(event.getPlayer().getUniqueId());
        if (response == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> response.accept(message));
    }
}
