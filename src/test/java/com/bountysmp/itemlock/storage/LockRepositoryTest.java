package com.bountysmp.itemlock.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.itemlock.BukkitTest;
import com.bountysmp.itemlock.model.LockDefinition;
import com.bountysmp.itemlock.model.MatchType;
import java.io.File;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LockRepositoryTest extends BukkitTest {
    @TempDir
    File tempDir;

    @Test
    void savesAndLoadsDefinition() {
        LockRepository repository = new LockRepository(new File(tempDir, "locks.yml"));
        LockDefinition definition = LockDefinition.create(new ItemStack(Material.NETHER_STAR), MatchType.MATERIAL);
        definition.setDropProtection(true);
        definition.setDestructionProtection(true);
        definition.addDestroyed(3);

        repository.save(java.util.List.of(definition), Map.of());
        LockRepository.Snapshot snapshot = repository.load();

        assertEquals(1, snapshot.definitions().size());
        assertEquals(Material.NETHER_STAR, snapshot.definitions().getFirst().sample().getType());
        assertTrue(snapshot.definitions().getFirst().dropProtection());
        assertTrue(snapshot.definitions().getFirst().destructionProtection());
        assertEquals(3L, snapshot.definitions().getFirst().destroyedCount());
        assertTrue(snapshot.definitions().getFirst().placeProtection());
        assertTrue(snapshot.definitions().getFirst().destructionSoundEnabled());
        assertEquals("minecraft:entity.ender_dragon.growl", snapshot.definitions().getFirst().destructionSoundKey());
    }

    @Test
    void legacyDefinitionLoadsNewDefaults() throws Exception {
        File file = new File(tempDir, "locks.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("locks.old.enabled", true);
        yaml.set("locks.old.sample", new ItemStack(Material.DRAGON_EGG));
        yaml.save(file);

        LockRepository.Snapshot snapshot = new LockRepository(file).load();

        assertEquals(1, snapshot.definitions().size());
        assertTrue(snapshot.definitions().getFirst().placeProtection());
        assertTrue(snapshot.definitions().getFirst().destructionSoundEnabled());
        assertEquals("minecraft:entity.ender_dragon.growl", snapshot.definitions().getFirst().destructionSoundKey());
    }
}
