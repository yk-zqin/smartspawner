package github.nighter.smartspawner.spawner.loot;

import lombok.Getter;

import java.util.List;

@Getter
public class EntityLootConfig {
    private final int experience;
    private final List<LootItem> possibleItems;

    public EntityLootConfig(int experience, List<LootItem> items) {
        this.experience = experience;
        this.possibleItems = items;
    }

    /**
     * Gets possible items filtering by equipment allowance
     */
    public List<LootItem> getValidItems(boolean allowEquipment) {
        if (allowEquipment) {
            return possibleItems;
        }

        return possibleItems.stream()
                .filter(item -> item.getMinDurability() == null && item.getMaxDurability() == null)
                .toList();
    }
}