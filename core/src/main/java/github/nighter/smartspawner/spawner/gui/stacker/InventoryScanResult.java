package github.nighter.smartspawner.spawner.gui.stacker;

import java.util.List;

public class InventoryScanResult {
    final int availableSpawners;
    final boolean hasDifferentType;
    final List<SpawnerSlot> spawnerSlots;

    InventoryScanResult(int availableSpawners, boolean hasDifferentType, List<SpawnerSlot> spawnerSlots) {
        this.availableSpawners = availableSpawners;
        this.hasDifferentType = hasDifferentType;
        this.spawnerSlots = spawnerSlots;
    }
}
