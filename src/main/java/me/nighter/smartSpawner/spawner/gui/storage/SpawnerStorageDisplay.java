package me.nighter.smartSpawner.spawner.gui.storage;

import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;

public interface SpawnerStorageDisplay {
    void updateDisplay(Inventory inventory, SpawnerData spawner, int page);
    Inventory createInventory(SpawnerData spawner, String title, int page);
}