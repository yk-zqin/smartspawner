package me.nighter.smartSpawner.utils;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class VirtualInventory {
    private final Map<Integer, ItemStack> items;
    private final int size;

    public VirtualInventory(int size) {
        this.size = size;
        this.items = new HashMap<>();
    }

    public void setItem(int slot, ItemStack item) {
        if (slot >= 0 && slot < size) {
            if (item == null) {
                items.remove(slot);
            } else {
                items.put(slot, item.clone());
            }
        }
    }

    public ItemStack getItem(int slot) {
        return items.containsKey(slot) ? items.get(slot).clone() : null;
    }

    public int getSize() {
        return size;
    }

    public Map<Integer, ItemStack> getAllItems() {
        Map<Integer, ItemStack> result = new HashMap<>();
        items.forEach((slot, item) -> result.put(slot, item.clone()));
        return result;
    }

    // Add to SpawnerData class
    public void setItems(Map<Integer, ItemStack> items) {
        this.items.clear();
        items.forEach(this::setItem);
    }
}
