package github.nighter.smartspawner.commands.prices.holders;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@Getter
public class PricesHolder implements InventoryHolder {
    private final int currentPage;
    private final int totalPages;

    public PricesHolder(int currentPage, int totalPages) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}