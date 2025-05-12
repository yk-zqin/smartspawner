package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpawnerFileHandler {
    private final SmartSpawner plugin;
    private final Logger logger;
    private File spawnerDataFile;
    private FileConfiguration spawnerData;

    // Data version identifier
    private static final String DATA_VERSION_KEY = "data_version";
    private final int CURRENT_VERSION;

    // Write-behind caching strategy
    private final Set<String> dirtySpawners = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedSpawners = ConcurrentHashMap.newKeySet();

    // Lock to prevent concurrent file operations
    private volatile boolean isSaving = false;

    // Task for periodic save
    private Scheduler.Task saveTask = null;

    public SpawnerFileHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.CURRENT_VERSION = plugin.getDATA_VERSION();
        setupSpawnerDataFile();
        startSaveTask();
    }

    private void setupSpawnerDataFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        spawnerDataFile = new File(plugin.getDataFolder(), "spawners_data.yml");

        if (!spawnerDataFile.exists()) {
            try {
                spawnerDataFile.createNewFile();
                String header = """
                # Warning: Do not modify this file while the server is running!
                # Format:
                #  spawners:
                #    spawnerId:
                #      location: world,x,y,z
                #      entityType: ENTITY_TYPE
                #      settings: exp,active,range,stop,delay,slots,maxExp,minMobs,maxMobs,stack,maxStack,time,atCapacity
                #      filteredItems: MATERIAL1,MATERIAL2,...
                #      inventory:
                #      - ITEM_TYPE:amount
                """;

                Files.write(spawnerDataFile.toPath(), header.getBytes(), StandardOpenOption.WRITE);

                // Set data version
                FileConfiguration config = YamlConfiguration.loadConfiguration(spawnerDataFile);
                config.set(DATA_VERSION_KEY, CURRENT_VERSION);
                config.save(spawnerDataFile);
            } catch (IOException e) {
                logger.severe("Could not create spawners_data.yml!");
                e.printStackTrace();
            }
        }

        spawnerData = YamlConfiguration.loadConfiguration(spawnerDataFile);

        // Check current data version
        int version = spawnerData.getInt(DATA_VERSION_KEY, 1);
        if (version < CURRENT_VERSION) {
            logger.info("Data version " + version + " detected. Current version is " + CURRENT_VERSION + ".");
            logger.info("A migration will be attempted when the plugin fully loads.");
        }
    }

    private void startSaveTask() {
        long intervalTicks = plugin.getTimeFromConfig("data_saving.interval", "5m");

        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        saveTask = Scheduler.runTaskTimerAsync(() -> {
            plugin.debug("Running scheduled save task");
            flushChanges();
        }, intervalTicks, intervalTicks);
    }

    public void markSpawnerModified(String spawnerId) {
        if (spawnerId != null) {
            dirtySpawners.add(spawnerId);
            deletedSpawners.remove(spawnerId); // If it was marked for deletion but modified again
        }
    }

    public void markSpawnerDeleted(String spawnerId) {
        if (spawnerId != null) {
            plugin.debug("Marking spawner " + spawnerId + " for deletion");
            deletedSpawners.add(spawnerId);
            dirtySpawners.remove(spawnerId); // No need to save if it's deleted
        }
    }

    public void flushChanges() {
        if (dirtySpawners.isEmpty() && deletedSpawners.isEmpty()) {
            plugin.debug("No changes to flush");
            return;
        }

        if (isSaving) {
            plugin.debug("Flush operation already in progress");
            return;
        }

        isSaving = true;
        plugin.debug("Flushing " + dirtySpawners.size() + " modified and " + deletedSpawners.size() + " deleted spawners");

        // Process asynchronously
        Scheduler.runTaskAsync(() -> {
            try {
                // Handle updates
                if (!dirtySpawners.isEmpty()) {
                    Set<String> toUpdate = new HashSet<>(dirtySpawners);
                    dirtySpawners.removeAll(toUpdate);

                    Map<String, SpawnerData> batch = new HashMap<>();
                    for (String id : toUpdate) {
                        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(id);
                        if (spawner != null) {
                            batch.put(id, spawner);
                        }
                    }

                    if (!batch.isEmpty()) {
                        saveSpawnerBatch(batch);
                    }
                }

                // Handle deletions
                if (!deletedSpawners.isEmpty()) {
                    Set<String> toDelete = new HashSet<>(deletedSpawners);
                    deletedSpawners.removeAll(toDelete);

                    for (String id : toDelete) {
                        String path = "spawners." + id;
                        spawnerData.set(path, null);
                    }

                    if (!toDelete.isEmpty()) {
                        spawnerData.save(spawnerDataFile);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error during flush: " + e.getMessage());
                e.printStackTrace();

                // Put failed operations back into the queue
                for (String id : dirtySpawners) {
                    dirtySpawners.add(id);
                }
                for (String id : deletedSpawners) {
                    deletedSpawners.add(id);
                }
            } finally {
                isSaving = false;
            }
        });
    }

    private boolean saveSpawnerBatch(Map<String, SpawnerData> spawners) {
        if (spawners.isEmpty()) return true;

        try {
            ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
            if (spawnersSection == null) {
                spawnersSection = spawnerData.createSection("spawners");
            }

            // Ensure data version is up to date
            spawnerData.set(DATA_VERSION_KEY, CURRENT_VERSION);

            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                SpawnerData spawner = entry.getValue();
                String path = "spawners." + spawnerId;
                Location loc = spawner.getSpawnerLocation();

                spawnerData.set(path + ".location", String.format("%s,%d,%d,%d",
                        loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

                spawnerData.set(path + ".entityType", spawner.getEntityType() != null ?
                        spawner.getEntityType().name() : null);

                // Updated settings format for version 3:
                // exp,active,range,stop,delay,slots,maxExp,minMobs,maxMobs,stack,maxStack,time,atCapacity
                String settings = String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%d,%b",
                        spawner.getSpawnerExp(),
                        spawner.getSpawnerActive(),
                        spawner.getSpawnerRange(),
                        spawner.getSpawnerStop(),
                        spawner.getSpawnDelay(),
                        spawner.getMaxSpawnerLootSlots(),
                        spawner.getMaxStoredExp(),
                        spawner.getMinMobs(),
                        spawner.getMaxMobs(),
                        spawner.getStackSize(),
                        spawner.getMaxStackSize(),
                        spawner.getLastSpawnTime(),
                        spawner.getIsAtCapacity());

                spawnerData.set(path + ".settings", settings);

                // Save filtered items
                Set<Material> filteredItems = spawner.getFilteredItems();
                if (filteredItems != null && !filteredItems.isEmpty()) {
                    List<String> materials = filteredItems.stream()
                            .map(Material::name)
                            .collect(Collectors.toList());
                    spawnerData.set(path + ".filteredItems", String.join(",", materials));
                } else {
                    spawnerData.set(path + ".filteredItems", null);
                }

                VirtualInventory virtualInv = spawner.getVirtualInventory();
                if (virtualInv != null) {
                    Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();
                    List<String> serializedItems = ItemStackSerializer.serializeInventory(items);
                    spawnerData.set(path + ".inventory", serializedItems);
                }
            }

            spawnerData.save(spawnerDataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawner batch to file!");
            e.printStackTrace();
            return false;
        }
    }

    public boolean saveAllSpawners(Map<String, SpawnerData> spawners) {
        try {
            // Cancel any pending save task
            if (saveTask != null) {
                saveTask.cancel();
                saveTask = null;
            }

            // Clear any pending operations
            dirtySpawners.clear();
            deletedSpawners.clear();
            isSaving = true;

            // Set data version
            spawnerData.set(DATA_VERSION_KEY, CURRENT_VERSION);

            // Get or create spawners section
            ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
            if (spawnersSection == null) {
                spawnersSection = spawnerData.createSection("spawners");
            } else {
                // Clear existing data
                for (String key : spawnersSection.getKeys(false)) {
                    spawnersSection.set(key, null);
                }
            }

            // Save all current spawners
            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                SpawnerData spawner = entry.getValue();
                Location loc = spawner.getSpawnerLocation();
                String path = "spawners." + spawnerId;

                spawnerData.set(path + ".location", String.format("%s,%d,%d,%d",
                        loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

                spawnerData.set(path + ".entityType", spawner.getEntityType() != null ?
                        spawner.getEntityType().name() : null);

                // Updated settings format for v3
                String settings = String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%d,%b",
                        spawner.getSpawnerExp(),
                        spawner.getSpawnerActive(),
                        spawner.getSpawnerRange(),
                        spawner.getSpawnerStop(),
                        spawner.getSpawnDelay(),
                        spawner.getMaxSpawnerLootSlots(),
                        spawner.getMaxStoredExp(),
                        spawner.getMinMobs(),
                        spawner.getMaxMobs(),
                        spawner.getStackSize(),
                        spawner.getMaxStackSize(),
                        spawner.getLastSpawnTime(),
                        spawner.getIsAtCapacity());

                spawnerData.set(path + ".settings", settings);

                // Save filtered items
                Set<Material> filteredItems = spawner.getFilteredItems();
                if (filteredItems != null && !filteredItems.isEmpty()) {
                    List<String> materials = filteredItems.stream()
                            .map(Material::name)
                            .collect(Collectors.toList());
                    spawnerData.set(path + ".filteredItems", String.join(",", materials));
                }

                VirtualInventory virtualInv = spawner.getVirtualInventory();
                if (virtualInv != null) {
                    Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();
                    List<String> serializedItems = ItemStackSerializer.serializeInventory(items);
                    spawnerData.set(path + ".inventory", serializedItems);
                }
            }

            spawnerData.save(spawnerDataFile);
            isSaving = false;

            // Only restart save task if plugin is still enabled
            if (plugin.isEnabled()) {
                startSaveTask();
            }
            return true;
        } catch (IOException e) {
            logger.severe("Could not save spawners_data.yml!");
            e.printStackTrace();
            isSaving = false;
            // Only restart save task if plugin is still enabled
            if (plugin.isEnabled()) {
                startSaveTask();
            }
            return false;
        }
    }

    public boolean deleteSpawnerFromFile(String spawnerId) {
        markSpawnerDeleted(spawnerId);
        flushChanges();
        return true;
    }

    public Map<String, SpawnerData> loadAllSpawners() {
        Map<String, SpawnerData> loadedSpawners = new HashMap<>();

        ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
        if (spawnersSection == null) return loadedSpawners;

        int loadedCount = 0;
        int errorCount = 0;

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                SpawnerData spawner = loadSpawnerFromConfig(spawnerId);
                if (spawner != null) {
                    loadedSpawners.put(spawnerId, spawner);
                    loadedCount++;
                }
            } catch (Exception e) {
                logger.severe("Error loading spawner " + spawnerId);
                e.printStackTrace();
                errorCount++;
            }
        }

        logger.info("Loaded " + loadedCount + " spawners. Errors: " + errorCount);
        return loadedSpawners;
    }

    private SpawnerData loadSpawnerFromConfig(String spawnerId) {
        String path = "spawners." + spawnerId;

        String locationString = spawnerData.getString(path + ".location");
        if (locationString == null) {
            logger.warning("Invalid location for spawner " + spawnerId);
            return null;
        }

        String[] locParts = locationString.split(",");
        if (locParts.length != 4) {
            logger.warning("Invalid location format for spawner " + spawnerId);
            return null;
        }

        org.bukkit.World world = Bukkit.getWorld(locParts[0]);
        if (world == null) {
            logger.warning("World not found for spawner " + spawnerId + ": " + locParts[0]);
            return null;
        }

        Location location = new Location(world,
                Integer.parseInt(locParts[1]),
                Integer.parseInt(locParts[2]),
                Integer.parseInt(locParts[3]));

        String entityTypeString = spawnerData.getString(path + ".entityType");
        if (entityTypeString == null) {
            logger.warning("Missing entity type for spawner " + spawnerId);
            return null;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeString);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid entity type for spawner " + spawnerId + ": " + entityTypeString);
            return null;
        }

        SpawnerData spawner = new SpawnerData(spawnerId, location, entityType, plugin);

        String settingsString = spawnerData.getString(path + ".settings");
        if (settingsString != null) {
            String[] settings = settingsString.split(",");

            // Handle version differences based on data version
            int version = spawnerData.getInt(DATA_VERSION_KEY, 1);

            try {
                if (version >= 3) {
                    // Version 3 format
                    if (settings.length >= 13) {
                        spawner.setSpawnerExp(Integer.parseInt(settings[0]));
                        spawner.setSpawnerActive(Boolean.parseBoolean(settings[1]));
                        spawner.setSpawnerRange(Integer.parseInt(settings[2]));
                        spawner.setSpawnerStop(Boolean.parseBoolean(settings[3]));
                        spawner.setSpawnDelay(Integer.parseInt(settings[4]));
                        spawner.setMaxSpawnerLootSlots(Integer.parseInt(settings[5]));
                        spawner.setMaxStoredExp(Integer.parseInt(settings[6]));
                        spawner.setMinMobs(Integer.parseInt(settings[7]));
                        spawner.setMaxMobs(Integer.parseInt(settings[8]));
                        spawner.setStackSize(Integer.parseInt(settings[9]));
                        spawner.setMaxStackSize(Integer.parseInt(settings[10]));
                        spawner.setLastSpawnTime(Long.parseLong(settings[11]));
                        spawner.setIsAtCapacity(Boolean.parseBoolean(settings[12]));
                    }
                } else {
                    // Version 1-2 format (handle missing fields with defaults)
                    spawner.setSpawnerExp(Integer.parseInt(settings[0]));
                    spawner.setSpawnerActive(Boolean.parseBoolean(settings[1]));
                    spawner.setSpawnerRange(Integer.parseInt(settings[2]));
                    spawner.setSpawnerStop(Boolean.parseBoolean(settings[3]));
                    spawner.setSpawnDelay(Integer.parseInt(settings[4]));
                    spawner.setMaxSpawnerLootSlots(Integer.parseInt(settings[5]));
                    spawner.setMaxStoredExp(Integer.parseInt(settings[6]));
                    spawner.setMinMobs(Integer.parseInt(settings[7]));
                    spawner.setMaxMobs(Integer.parseInt(settings[8]));
                    spawner.setStackSize(Integer.parseInt(settings[9]));
                    spawner.setLastSpawnTime(Long.parseLong(settings[10]));
                    // Default values for new fields
                    spawner.setIsAtCapacity(false);
                }
            } catch (NumberFormatException e) {
                logger.warning("Invalid settings format for spawner " + spawnerId);
                logger.warning("Settings: " + settingsString);
                e.printStackTrace();
                return null;
            }
        }

        // Load filtered items
        String filteredItemsStr = spawnerData.getString(path + ".filteredItems");
        if (filteredItemsStr != null && !filteredItemsStr.isEmpty()) {
            String[] materialNames = filteredItemsStr.split(",");
            for (String materialName : materialNames) {
                try {
                    Material material = Material.valueOf(materialName.trim());
                    spawner.getFilteredItems().add(material);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid material in filtered items for spawner " + spawnerId + ": " + materialName);
                }
            }
        }

        List<String> inventoryData = spawnerData.getStringList(path + ".inventory");
        VirtualInventory virtualInv = new VirtualInventory(spawner.getMaxSpawnerLootSlots());

        if (inventoryData != null && !inventoryData.isEmpty()) {
            try {
                Map<ItemStack, Integer> items = ItemStackSerializer.deserializeInventory(inventoryData);
                for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
                    ItemStack item = entry.getKey();
                    int amount = entry.getValue();

                    if (item != null && amount > 0) {
                        while (amount > 0) {
                            int batchSize = Math.min(amount, item.getMaxStackSize());
                            ItemStack batch = item.clone();
                            batch.setAmount(batchSize);
                            virtualInv.addItems(Collections.singletonList(batch));
                            amount -= batchSize;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading inventory for spawner " + spawnerId);
                e.printStackTrace();
            }
        }

        spawner.setVirtualInventory(virtualInv);
        return spawner;
    }

    public void queueSpawnerForSaving(String spawnerId) {
        markSpawnerModified(spawnerId);
    }

    public int getDataVersion() {
        return spawnerData.getInt(DATA_VERSION_KEY, 1);
    }

    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        // Synchronously flush any pending changes on shutdown
        if (!dirtySpawners.isEmpty() || !deletedSpawners.isEmpty()) {
            try {
                isSaving = true;

                // Process updates
                if (!dirtySpawners.isEmpty()) {
                    Map<String, SpawnerData> batch = new HashMap<>();
                    for (String id : dirtySpawners) {
                        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(id);
                        if (spawner != null) {
                            batch.put(id, spawner);
                        }
                    }

                    if (!batch.isEmpty()) {
                        saveSpawnerBatch(batch);
                    }
                }

                // Process deletions
                if (!deletedSpawners.isEmpty()) {
                    for (String id : deletedSpawners) {
                        String path = "spawners." + id;
                        spawnerData.set(path, null);
                    }

                    spawnerData.save(spawnerDataFile);
                }

                dirtySpawners.clear();
                deletedSpawners.clear();
            } catch (Exception e) {
                logger.severe("Error during shutdown flush: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isSaving = false;
            }
        }
    }
}