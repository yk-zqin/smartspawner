package github.nighter.smartspawner.commands.list.gui.adminstacker;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory holder for the admin stacker GUI
 */
@Getter
public class AdminStackerHolder implements InventoryHolder {
    private final SpawnerData spawnerData;
    private final String worldName;
    private final int listPage;

    public AdminStackerHolder(SpawnerData spawnerData, String worldName, int listPage) {
        this.spawnerData = spawnerData;
        this.worldName = worldName;
        this.listPage = listPage;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}