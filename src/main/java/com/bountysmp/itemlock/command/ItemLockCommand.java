package com.bountysmp.itemlock.command;

import com.bountysmp.itemlock.gui.GuiManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ItemLockCommand implements CommandExecutor {
    private final GuiManager guiManager;

    public ItemLockCommand(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open ItemLock.");
            return true;
        }
        if (!player.hasPermission("itemlock.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use ItemLock");
            return true;
        }
        guiManager.openMain(player, 0);
        return true;
    }
}
