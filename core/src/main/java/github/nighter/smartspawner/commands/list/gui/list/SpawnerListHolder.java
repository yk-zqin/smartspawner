package github.nighter.smartspawner.commands.list.gui.list;

import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@Getter
public class SpawnerListHolder implements InventoryHolder {
    private final int currentPage;
    private final int totalPages;
    private final String worldName;
    private final FilterOption filterOption;
    private final SortOption sortType;

    public SpawnerListHolder(int currentPage, int totalPages, String worldName,
                             FilterOption filterOption, SortOption sortType) {
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
