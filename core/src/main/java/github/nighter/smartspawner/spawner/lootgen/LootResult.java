package github.nighter.smartspawner.spawner.lootgen;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@Getter
public class LootResult {
    private final List<ItemStack> items;
    private final int experience;

    public LootResult(List<ItemStack> items, int experience) {
        this.items = items;
        this.experience = experience;
    }
}