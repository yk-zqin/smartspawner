package me.nighter.smartSpawner.spawner.gui.storage;

import me.nighter.smartSpawner.holders.SpawnerHolder;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class StoragePageHolder implements InventoryHolder, SpawnerHolder {
    private final SpawnerData spawnerData;
    private final int currentPage;
    private final int totalPages;

    // Make these public static final for better performance and accessibility
    public static final int ROWS_PER_PAGE = 5;
    public static final int SLOTS_PER_PAGE = ROWS_PER_PAGE * 9;
    public static final int MAX_ITEMS_PER_PAGE = 45; // Pre-calculated value

    // Cache the inventory reference
    private Inventory inventory;

    public StoragePageHolder(SpawnerData spawnerData, int currentPage, int totalPages) {
        this.spawnerData = spawnerData;
        this.currentPage = Math.max(1, Math.min(currentPage, totalPages)); // Ensure valid page range
        this.totalPages = Math.max(1, totalPages);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // Add setter for inventory to cache it
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    // Add utility methods for page calculations
    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    // Calculate slot index for given item position
    public int getSlotIndex(int itemIndex) {
        return itemIndex % MAX_ITEMS_PER_PAGE;
    }

    // Calculate actual item index based on current page
    public int getItemIndex(int slotIndex) {
        return ((currentPage - 1) * MAX_ITEMS_PER_PAGE) + slotIndex;
    }
}