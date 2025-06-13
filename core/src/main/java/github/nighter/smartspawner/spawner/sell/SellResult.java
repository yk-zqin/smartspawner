package github.nighter.smartspawner.spawner.sell;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SellResult {
    @Getter
    private final double totalValue;
    @Getter
    private final long itemsSold;
    @Getter
    private final List<ItemStack> itemsToRemove;
    @Getter
    private final long timestamp;
    @Getter
    private final boolean successful;

    public SellResult(double totalValue, long itemsSold, List<ItemStack> itemsToRemove) {
        this.totalValue = totalValue;
        this.itemsSold = itemsSold;
        this.itemsToRemove = new ArrayList<>(itemsToRemove);
        this.timestamp = System.currentTimeMillis();
        this.successful = totalValue > 0.0 && !itemsToRemove.isEmpty();
    }

    public static SellResult empty() {
        return new SellResult(0.0, 0, Collections.emptyList());
    }

    public boolean hasItems() {
        return !itemsToRemove.isEmpty();
    }
}