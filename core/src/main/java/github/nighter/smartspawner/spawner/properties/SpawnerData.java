package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.hologram.SpawnerHologram;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.EntityLootRegistry;
import github.nighter.smartspawner.spawner.loot.LootItem;
import github.nighter.smartspawner.spawner.sell.SellResult;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpawnerData {
    @Getter
    private final SmartSpawner plugin;
    private final EntityLootRegistry lootRegistry;

    @Getter
    private final String spawnerId;
    @Getter
    private final Location spawnerLocation;
    @Getter
    private final ReentrantLock lock = new ReentrantLock();

    // Base values from config (immutable after load)
    private int baseMaxStoredExp;
    private int baseMaxStoragePages;
    private int baseMinMobs;
    private int baseMaxMobs;

    @Getter
    private Integer spawnerExp;
    @Getter @Setter
    private Boolean spawnerActive;
    @Getter @Setter
    private Integer spawnerRange;
    @Getter @Setter
    private Boolean spawnerStop;
    @Getter @Setter
    private Boolean isAtCapacity;
    @Getter @Setter
    private Long lastSpawnTime;
    @Getter
    private long spawnDelay;

    @Getter
    private EntityType entityType;
    @Getter @Setter
    private EntityLootConfig lootConfig;

    // Calculated values based on stackSize
    @Getter
    private int maxStoragePages;
    @Getter @Setter
    private int maxSpawnerLootSlots;
    @Getter @Setter
    private int maxStoredExp;
    @Getter @Setter
    private int minMobs;
    @Getter @Setter
    private int maxMobs;

    @Getter
    private int stackSize;
    @Getter @Setter
    private int maxStackSize;

    @Getter @Setter
    private VirtualInventory virtualInventory;
    @Getter
    private final Set<Material> filteredItems = new HashSet<>();

    private final AtomicBoolean interacted = new AtomicBoolean(false);
    @Getter @Setter
    private String lastInteractedPlayer;

    @Getter
    private SellResult lastSellResult;
    @Getter
    private boolean lastSellProcessed;

    private SpawnerHologram hologram;
    @Getter @Setter
    private long cachedSpawnDelay = 0;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.lootRegistry = plugin.getEntityLootRegistry();

        initializeDefaults();
        loadConfigurationValues();
        calculateStackBasedValues();
        initializeComponents();
    }

    private void initializeDefaults() {
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = true;
        this.isAtCapacity = false;
        this.stackSize = 1;
        this.lastSpawnTime = System.currentTimeMillis();
    }

    public void loadConfigurationValues() {
        this.baseMaxStoredExp = plugin.getConfig().getInt("spawner_properties.default.max_stored_exp", 1000);
        this.baseMaxStoragePages = plugin.getConfig().getInt("spawner_properties.default.max_storage_pages", 1);
        this.baseMinMobs = plugin.getConfig().getInt("spawner_properties.default.min_mobs", 1);
        this.baseMaxMobs = plugin.getConfig().getInt("spawner_properties.default.max_mobs", 4);
        this.maxStackSize = plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 1000);
        this.spawnDelay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range", 16);
        this.lootConfig = lootRegistry.getLootConfig(entityType);
    }

    public void recalculateAfterConfigReload() {
        calculateStackBasedValues();
        if (virtualInventory != null && virtualInventory.getMaxSlots() != maxSpawnerLootSlots) {
            recreateVirtualInventory();
        }
        updateHologramData();
        
        // Invalidate GUI cache after config reload
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void calculateStackBasedValues() {
        this.maxStoredExp = baseMaxStoredExp * stackSize;
        this.maxStoragePages = baseMaxStoragePages * stackSize;
        this.maxSpawnerLootSlots = maxStoragePages * 45;
        this.minMobs = baseMinMobs * stackSize;
        this.maxMobs = baseMaxMobs * stackSize;
        this.spawnerExp = Math.min(this.spawnerExp, this.maxStoredExp);
    }

    public void setSpawnDelay(long baseSpawnerDelay) {
        this.spawnDelay = baseSpawnerDelay > 0 ? baseSpawnerDelay : 400;
        if (baseSpawnerDelay <= 0) {
            plugin.getLogger().warning("Invalid delay value. Setting to default: 400");
        }
    }

    private void initializeComponents() {
        this.virtualInventory = new VirtualInventory(maxSpawnerLootSlots);
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            createHologram();
        }
    }

    private void createHologram() {
        this.hologram = new SpawnerHologram(spawnerLocation);
        this.hologram.createHologram();
        updateHologramData();
    }

    public void setStackSize(int stackSize) {
        lock.lock();
        try {
            updateStackSize(stackSize);
        } finally {
            lock.unlock();
        }
    }

    private void updateStackSize(int newStackSize) {
        if (newStackSize <= 0) {
            this.stackSize = 1;
            plugin.getLogger().warning("Invalid stack size. Setting to 1");
            return;
        }

        if (newStackSize > this.maxStackSize) {
            this.stackSize = this.maxStackSize;
            plugin.getLogger().warning("Stack size exceeds maximum. Setting to " + this.stackSize);
            return;
        }

        this.stackSize = newStackSize;
        Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();

        calculateStackBasedValues();

        VirtualInventory newInventory = new VirtualInventory(this.maxSpawnerLootSlots);
        transferItemsToNewInventory(currentItems, newInventory);
        this.virtualInventory = newInventory;

        this.lastSpawnTime = System.currentTimeMillis();
        updateHologramData();
        
        // Invalidate GUI cache when stack size changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void recreateVirtualInventory() {
        if (virtualInventory == null) return;

        Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();
        VirtualInventory newInventory = new VirtualInventory(maxSpawnerLootSlots);
        transferItemsToNewInventory(currentItems, newInventory);
        this.virtualInventory = newInventory;
    }

    private void transferItemsToNewInventory(Map<VirtualInventory.ItemSignature, Long> items,
                                             VirtualInventory newInventory) {
        List<ItemStack> itemsToTransfer = new ArrayList<>();

        items.forEach((signature, amount) -> {
            ItemStack template = signature.getTemplate();
            while (amount > 0) {
                int batchSize = (int) Math.min(amount, Integer.MAX_VALUE);
                ItemStack batch = template.clone();
                batch.setAmount(batchSize);
                itemsToTransfer.add(batch);
                amount -= batchSize;
            }
        });

        newInventory.addItems(itemsToTransfer);
    }

    public void setSpawnerExp(int exp) {
        this.spawnerExp = Math.min(Math.max(0, exp), maxStoredExp);
        updateHologramData();
        
        // Invalidate GUI cache when experience changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    public void setSpawnerExpData(int exp) {
        this.spawnerExp = exp;
    }

    public void updateHologramData() {
        if (hologram != null) {
            hologram.updateData(stackSize, entityType, spawnerExp, maxStoredExp,
                    virtualInventory.getUsedSlots(), maxSpawnerLootSlots);
        }
    }

    public void reloadHologramData() {
        if (hologram != null) {
            hologram.remove();
            createHologram();
        }
    }

    public void refreshHologram() {
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            if (hologram == null) {
                createHologram();
            }
        } else if (hologram != null) {
            removeHologram();
        }
    }

    public void removeHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
    }

    public boolean isCompletelyFull() {
        return virtualInventory.getUsedSlots() >= maxSpawnerLootSlots && spawnerExp >= maxStoredExp;
    }

    public boolean updateCapacityStatus() {
        boolean newStatus = isCompletelyFull();
        if (newStatus != isAtCapacity) {
            isAtCapacity = newStatus;
            return true;
        }
        return false;
    }

    public void setEntityType(EntityType newType) {
        this.entityType = newType;
        this.lootConfig = lootRegistry.getLootConfig(newType);
        updateHologramData();
    }

    public boolean toggleItemFilter(Material material) {
        boolean wasFiltered = filteredItems.contains(material);
        if (wasFiltered) {
            filteredItems.remove(material);
        } else {
            filteredItems.add(material);
        }
        return !wasFiltered;
    }

    public List<LootItem> getValidLootItems() {
        if (lootConfig == null) {
            return Collections.emptyList();
        }
        return lootConfig.getAllItems().stream()
                .filter(this::isLootItemValid)
                .collect(Collectors.toList());
    }

    private boolean isLootItemValid(LootItem item) {
        ItemStack example = item.createItemStack(new Random());
        return example != null && !filteredItems.contains(example.getType());
    }

    public int getEntityExperienceValue() {
        return lootConfig != null ? lootConfig.getExperience() : 0;
    }

    public void setLootConfig() {
        this.lootConfig = lootRegistry.getLootConfig(entityType);
    }

    public void setLastSellResult(SellResult sellResult) {
        this.lastSellResult = sellResult;
        this.lastSellProcessed = false;
    }

    public void markLastSellAsProcessed() {
        this.lastSellProcessed = true;
    }

    public boolean isInteracted() {
        return interacted.get();
    }

    public void markInteracted() {
        interacted.compareAndSet(false, true);
    }

    public void clearInteracted() {
        interacted.compareAndSet(true, false);
    }

    public void updateLastInteractedPlayer(String playerName) {
        this.lastInteractedPlayer = playerName;
        markInteracted();
    }
}