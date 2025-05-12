package github.nighter.smartspawner.spawner.loot;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class EntityLootConfig {
    private final int experience;
    private final List<LootItem> possibleItems;

    public EntityLootConfig(int experience, List<LootItem> items) {
        this.experience = experience;
        this.possibleItems = items;
    }

    public List<LootItem> getAllItems() {
        return possibleItems;
    }
}