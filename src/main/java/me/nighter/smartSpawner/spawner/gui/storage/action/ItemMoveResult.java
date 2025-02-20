package me.nighter.smartSpawner.spawner.gui.storage.action;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public class ItemMoveResult {
    private final int amountMoved;
    private final List<ItemStack> movedItems;

    public ItemMoveResult(int amountMoved, List<ItemStack> movedItems) {
        this.amountMoved = amountMoved;
        this.movedItems = movedItems;
    }

    public int getAmountMoved() {
        return amountMoved;
    }

    public List<ItemStack> getMovedItems() {
        return movedItems;
    }
}
