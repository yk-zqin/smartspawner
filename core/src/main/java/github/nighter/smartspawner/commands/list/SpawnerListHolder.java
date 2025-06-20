package github.nighter.smartspawner.commands.list;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@Getter
public class SpawnerListHolder implements InventoryHolder {
    private final int currentPage;
    private final int totalPages;
    private final String worldName;
    private final ListCommand.FilterOption filterOption;
    private final ListCommand.SortOption sortType;

    public SpawnerListHolder(int currentPage, int totalPages, String worldName,
                             ListCommand.FilterOption filterOption, ListCommand.SortOption sortType) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.worldName = worldName;
        this.filterOption = filterOption;
        this.sortType = sortType;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
