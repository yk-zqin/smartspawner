package github.nighter.smartspawner.holders;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class FilterConfigHolder implements InventoryHolder {
    private final SpawnerData spawnerData;

    public FilterConfigHolder(SpawnerData spawnerData) {
        this.spawnerData = spawnerData;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }
}