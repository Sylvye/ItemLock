package com.bountysmp.itemlock;

import com.bountysmp.itemlock.command.ItemLockCommand;
import com.bountysmp.itemlock.gui.ChatPromptManager;
import com.bountysmp.itemlock.gui.GuiManager;
import com.bountysmp.itemlock.lock.ItemTracker;
import com.bountysmp.itemlock.lock.ProtectionListener;
import com.bountysmp.itemlock.lock.LockRegistry;
import com.bountysmp.itemlock.storage.LockRepository;
import java.io.File;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemLockPlugin extends JavaPlugin {
    private LockRegistry registry;
    private ItemTracker tracker;
    private ChatPromptManager prompts;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        this.registry = new LockRegistry(this, new LockRepository(new File(getDataFolder(), "locks.yml")));
        this.tracker = new ItemTracker(this, registry);
        this.prompts = new ChatPromptManager(this);
        this.guiManager = new GuiManager(this, registry, tracker, prompts);

        registry.load();
        tracker.scanOnlinePlayers();

        ItemLockCommand commandExecutor = new ItemLockCommand(guiManager);
        PluginCommand command = Objects.requireNonNull(getCommand("itemlock"), "itemlock command");
        command.setExecutor(commandExecutor);

        getServer().getPluginManager().registerEvents(prompts, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(tracker, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, registry, tracker), this);
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.snapshotOnlinePlayers();
        }
        if (registry != null) {
            registry.flush();
        }
    }

    public LockRegistry registry() {
        return registry;
    }

    public ItemTracker tracker() {
        return tracker;
    }
}
