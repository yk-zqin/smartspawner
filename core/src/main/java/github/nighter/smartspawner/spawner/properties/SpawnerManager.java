package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.utils.SpawnerFileHandler;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
            Location loc = spawner.getSpawnerLocation();
            // Run hologram removal on location thread
            Scheduler.runLocationTask(loc, spawner::removeHologram);

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
        if (spawners.isEmpty()) {
            return;
        }

        Map<String, SpawnerData> spawnersToCheck = new HashMap<>(spawners);
        AtomicInteger removedCount = new AtomicInteger(0);
        AtomicInteger checkedCount = new AtomicInteger(0);
        int totalToCheck = spawnersToCheck.size();

        // Use a concurrent set to collect ghost spawners safely across threads
        Set<String> ghostSpawnerIds = ConcurrentHashMap.newKeySet();

        // Process spawners in batches to avoid overwhelming the server
        List<CompletableFuture<Void>> checks = new ArrayList<>();

        for (Map.Entry<String, SpawnerData> entry : spawnersToCheck.entrySet()) {
            String spawnerId = entry.getKey();
            SpawnerData spawner = entry.getValue();
            Location loc = spawner.getSpawnerLocation();

            // Create a future for each location check
            CompletableFuture<Void> future = new CompletableFuture<>();
            checks.add(future);

            Scheduler.runLocationTask(loc, () -> {
                try {
                    boolean wasLoaded = loc.getChunk().isLoaded();
                    boolean isGhost = false;

                    // Load chunk temporarily if needed
                    if (!wasLoaded) {
                        loc.getChunk().load(false);
                    }

                    try {
                        // Check if block is not a spawner
                        if (loc.getBlock().getType() != Material.SPAWNER) {
                            ghostSpawnerIds.add(spawnerId);
                            isGhost = true;
                        }
                    } finally {
                        // Always unload if we loaded it
                        if (!wasLoaded) {
                            loc.getChunk().unload(true);
                        }
                    }

                    checkedCount.incrementAndGet();
                    if (checkedCount.get() % 100 == 0 || checkedCount.get() == totalToCheck) {
                        plugin.debug(String.format("Ghost spawner check progress: %d/%d",
                                checkedCount.get(), totalToCheck));
                    }

                    future.complete(null);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error checking spawner " + spawnerId + ": " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        }

        // Process results after all checks are complete
        CompletableFuture.allOf(checks.toArray(new CompletableFuture[0]))
                .thenRunAsync(() -> {
                    if(!ghostSpawnerIds.isEmpty()) {
                        plugin.getLogger().info("Found " + ghostSpawnerIds.size() + " ghost spawners");
                    }

                    // Process ghost spawners in batches
                    List<CompletableFuture<Void>> removals = new ArrayList<>();
                    for (String ghostId : ghostSpawnerIds) {
                        SpawnerData spawner = spawners.get(ghostId);
                        if (spawner != null) {
                            Location loc = spawner.getSpawnerLocation();
                            CompletableFuture<Void> removal = new CompletableFuture<>();
                            removals.add(removal);

                            Scheduler.runLocationTask(loc, () -> {
                                try {
                                    spawner.removeHologram();

                                    Scheduler.runTask(() -> {
                                        removeSpawner(ghostId);
                                        spawnerFileHandler.markSpawnerDeleted(ghostId);
                                        removedCount.incrementAndGet();
                                        removal.complete(null);
                                    });
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Error removing ghost spawner " + ghostId + ": " + e.getMessage());
                                    removal.completeExceptionally(e);
                                }
                            });
                        } else {
                            CompletableFuture<Void> removal = new CompletableFuture<>();
                            removals.add(removal);
                            removal.complete(null);
                        }
                    }

                    // Final cleanup after all removals are done
                    CompletableFuture.allOf(removals.toArray(new CompletableFuture[0]))
                            .thenRunAsync(() -> {
                                if (removedCount.get() > 0) {
                                    spawnerFileHandler.flushChanges();
                                    plugin.getLogger().info("Successfully removed " + removedCount.get() + " ghost spawners");
                                }
                            });
                });
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

    public void cleanupAllSpawners() {
        spawners.clear();
        locationIndex.clear();
        worldIndex.clear();
    }
}