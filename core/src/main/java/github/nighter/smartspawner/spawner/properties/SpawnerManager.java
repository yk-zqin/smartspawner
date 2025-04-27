package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.utils.SpawnerFileHandler;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages all spawner data and interactions, delegating file operations
 * to SpawnerFileHandler for improved performance.
 */
public class SpawnerManager {
    private final SmartSpawner plugin;
    private final Map<String, SpawnerData> spawners = new HashMap<>();
    private final Map<LocationKey, SpawnerData> locationIndex = new HashMap<>();
    private final Map<String, Set<SpawnerData>> worldIndex = new HashMap<>();
    private final SpawnerFileHandler spawnerFileHandler;
    private final Logger logger;

    /**
     * Constructor for SpawnerManager
     *
     * @param plugin The SmartSpawner plugin instance
     */
    public SpawnerManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Initialize file handler
        this.spawnerFileHandler = plugin.getSpawnerFileHandler();

        // Load spawners from file
        loadSpawnerData();
    }

    /**
     * Key class for efficient location-based spawner lookups
     */
    private static class LocationKey {
        private final String world;
        private final int x, y, z;

        public LocationKey(Location location) {
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LocationKey)) return false;
            LocationKey that = (LocationKey) o;
            return x == that.x &&
                    y == that.y &&
                    z == that.z &&
                    world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, y, z);
        }
    }

    /**
     * Adds a spawner to the manager and indexes it
     *
     * @param id The spawner ID
     * @param spawner The spawner data object
     */
    public void addSpawner(String id, SpawnerData spawner) {
        spawners.put(id, spawner);
        locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);

        // Add to world index
        String worldName = spawner.getSpawnerLocation().getWorld().getName();
        worldIndex.computeIfAbsent(worldName, k -> new HashSet<>()).add(spawner);

        // Queue for saving
        spawnerFileHandler.queueSpawnerForSaving(id);
    }

    /**
     * Removes a spawner from the manager and file storage
     *
     * @param id The spawner ID to remove
     */
    public void removeSpawner(String id) {
        SpawnerData spawner = spawners.get(id);
        if (spawner != null) {
            spawner.removeHologram();
            locationIndex.remove(new LocationKey(spawner.getSpawnerLocation()));

            // Remove from world index
            String worldName = spawner.getSpawnerLocation().getWorld().getName();
            Set<SpawnerData> worldSpawners = worldIndex.get(worldName);
            if (worldSpawners != null) {
                worldSpawners.remove(spawner);
                if (worldSpawners.isEmpty()) {
                    worldIndex.remove(worldName);
                }
            }

            spawners.remove(id);
        }
    }

    /**
     * Counts spawners in a specific world
     *
     * @param worldName The name of the world
     * @return Number of spawners in that world
     */
    public int countSpawnersInWorld(String worldName) {
        Set<SpawnerData> worldSpawners = worldIndex.get(worldName);
        return worldSpawners != null ? worldSpawners.size() : 0;
    }

    /**
     * Counts total spawners including stacks in a world
     *
     * @param worldName The name of the world
     * @return Total count including stacked spawners
     */
    public int countTotalSpawnersWithStacks(String worldName) {
        Set<SpawnerData> worldSpawners = worldIndex.get(worldName);
        if (worldSpawners == null) return 0;

        return worldSpawners.stream()
                .mapToInt(SpawnerData::getStackSize)
                .sum();
    }

    /**
     * Gets a spawner by its location in the world
     *
     * @param location The location to check
     * @return The spawner at that location, or null if none exists
     */
    public SpawnerData getSpawnerByLocation(Location location) {
        return locationIndex.get(new LocationKey(location));
    }

    /**
     * Gets a spawner by its unique ID
     *
     * @param id The spawner ID
     * @return The spawner with that ID, or null if none exists
     */
    public SpawnerData getSpawnerById(String id) {
        return spawners.get(id);
    }

    /**
     * Gets all spawners currently managed
     *
     * @return List of all spawner data objects
     */
    public List<SpawnerData> getAllSpawners() {
        return new ArrayList<>(spawners.values());
    }

    /**
     * Loads all spawner data from file storage and removes ghost spawners
     */
    public void loadSpawnerData() {
        // Clear existing data
        spawners.clear();
        locationIndex.clear();
        worldIndex.clear();

        // Load spawners from file handler
        Map<String, SpawnerData> loadedSpawners = spawnerFileHandler.loadAllSpawners();

        // Add all loaded spawners to our indexes
        for (Map.Entry<String, SpawnerData> entry : loadedSpawners.entrySet()) {
            String spawnerId = entry.getKey();
            SpawnerData spawner = entry.getValue();

            spawners.put(spawnerId, spawner);
            locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);

            // Add to world index
            World world = spawner.getSpawnerLocation().getWorld();
            if (world != null) {
                String worldName = world.getName();
                worldIndex.computeIfAbsent(worldName, k -> new HashSet<>()).add(spawner);
            }
        }

        // Check for ghost spawners after initial load using Scheduler
        Scheduler.runTaskLater(this::removeGhostSpawners, 20L * 5); // Run after 5 seconds
    }

    /**
     * Checks for and removes ghost spawners (spawners without physical blocks)
     */
    public void removeGhostSpawners() {
        int removedCount = 0;
        List<String> ghostSpawnerIds = new ArrayList<>();

        // Find ghost spawners
        for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
            String spawnerId = entry.getKey();
            SpawnerData spawner = entry.getValue();
            Location loc = spawner.getSpawnerLocation();

            // Process each location with proper scheduling
            Scheduler.runLocationTask(loc, () -> {
                // Check if the chunk is loaded, if not, load it temporarily
                if (!loc.getChunk().isLoaded()) {
                    loc.getChunk().load(true);
                }

                // Check if the block at the location is a spawner
                if (loc.getBlock().getType() != Material.SPAWNER) {
                    synchronized (ghostSpawnerIds) {
                        ghostSpawnerIds.add(spawnerId);
                    }
                }
            });
        }

        // Use a delay to ensure all location checks have completed
        Scheduler.runTaskLater(() -> {
            // Remove ghost spawners
            int removed = 0;
            for (String ghostId : ghostSpawnerIds) {
                logger.info("Removing ghost spawner with ID: " + ghostId);
                removeSpawner(ghostId);
                spawnerFileHandler.deleteSpawnerFromFile(ghostId);
                removed++;
            }

            if (removed > 0) {
                logger.info("Removed " + removed + " ghost spawners.");
            }
        }, 10L); // Short delay to ensure location tasks complete
    }

    /**
     * Marks a spawner as modified for batch saving
     *
     * @param spawnerId The ID of the modified spawner
     */
    public void markSpawnerModified(String spawnerId) {
        spawnerFileHandler.markSpawnerModified(spawnerId);
    }

    /**
     * Immediately queues a spawner for saving
     *
     * @param spawnerId The ID of the spawner to save
     */
    public void queueSpawnerForSaving(String spawnerId) {
        spawnerFileHandler.queueSpawnerForSaving(spawnerId);
    }

    // ===============================================================
    //                    Spawner Hologram
    // ===============================================================

    public void refreshAllHolograms() {
        for (SpawnerData spawner : spawners.values()) {
            Location loc = spawner.getSpawnerLocation();
            Scheduler.runLocationTask(loc, spawner::refreshHologram);
        }
    }

    public void reloadAllHolograms() {
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            for (SpawnerData spawner : spawners.values()) {
                Location loc = spawner.getSpawnerLocation();
                Scheduler.runLocationTask(loc, spawner::reloadHologramData);
            }
        }
    }

    public void removeAllGhostsHolograms() {
        for (SpawnerData spawner : spawners.values()) {
            Location loc = spawner.getSpawnerLocation();
            Scheduler.runLocationTask(loc, spawner::removeGhostHologram);
        }
    }

    public void cleanupAllSpawners() {
        spawners.clear();
        locationIndex.clear();
        worldIndex.clear();
    }
}