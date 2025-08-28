package github.nighter.smartspawner.commands.list;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory holder for the admin stacker GUI
 */
public class AdminStackerHolder implements InventoryHolder {
    private final SpawnerData spawnerData;
    private final String worldName;
    private final int listPage;

    public AdminStackerHolder(SpawnerData spawnerData, String worldName, int listPage) {
        this.spawnerData = spawnerData;
        this.worldName = worldName;
        this.listPage = listPage;
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getListPage() {
        return listPage;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}