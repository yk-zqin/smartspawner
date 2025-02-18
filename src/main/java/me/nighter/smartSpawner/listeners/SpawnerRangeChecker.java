package me.nighter.smartSpawner.listeners;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.bukkit.util.BoundingBox;

public class SpawnerRangeChecker {
    private static final long CHECK_INTERVAL = 20L;
    private static final int BATCH_SIZE = 20;
    private static final long RANGE_CHECK_THROTTLE = 500L;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final SpawnerManager spawnerManager;
    private final Map<String, BukkitTask> spawnerTasks;
    private final Map<String, Set<UUID>> playersInRange;
    private final Map<String, Long> lastRangeCheckTime;
    private final Cache<String, Boolean> rangeCheckCache;

    // Spatial partitioning for spawners
    private final Map<ChunkCoord, Set<SpawnerData>> spawnersByChunk;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerTasks = new ConcurrentHashMap<>();
        this.playersInRange = new ConcurrentHashMap<>();
        this.lastRangeCheckTime = new ConcurrentHashMap<>();
        this.spawnersByChunk = new ConcurrentHashMap<>();

        this.rangeCheckCache = CacheBuilder.newBuilder()
                .expireAfterWrite(500, TimeUnit.MILLISECONDS)
                .concurrencyLevel(4)
                .initialCapacity(100)
                .build();

        initializeRangeCheckTask();
        initializeSpatialIndex();
    }

    private void initializeSpatialIndex() {
        // Initialize spatial index for all spawners
        spawnerManager.getAllSpawners().forEach(this::addToSpatialIndex);
    }
    private void addToSpatialIndex(SpawnerData spawner) {
        Location loc = spawner.getSpawnerLocation();
        ChunkCoord coord = new ChunkCoord(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, loc.getWorld().getName());
        spawnersByChunk.computeIfAbsent(coord, k -> new ObjectOpenHashSet<>()).add(spawner);
    }

    private static class ChunkCoord {
        final int x, z;
        final String world;

        ChunkCoord(int x, int z, String world) {
            this.x = x;
            this.z = z;
            this.world = world;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoord)) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z && world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z, world);
        }
    }

    private void initializeRangeCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Map<World, Set<Player>> playersByWorld = new HashMap<>();

            // Group players by world
            for (Player player : Bukkit.getOnlinePlayers()) {
                playersByWorld.computeIfAbsent(player.getWorld(), k -> new HashSet<>()).add(player);
            }

            // Process each world's spawners
            playersByWorld.forEach((world, players) -> {
                if (players.isEmpty()) return;

                // Get relevant chunks based on player locations
                Set<ChunkCoord> relevantChunks = new HashSet<>();
                players.forEach(player -> {
                    Location loc = player.getLocation();
                    int chunkX = loc.getBlockX() >> 4;
                    int chunkZ = loc.getBlockZ() >> 4;

                    // Add chunks in view distance
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            relevantChunks.add(new ChunkCoord(chunkX + x, chunkZ + z, world.getName()));
                        }
                    }
                });

                // Process spawners in relevant chunks
                relevantChunks.forEach(chunk -> {
                    Set<SpawnerData> spawners = spawnersByChunk.get(chunk);
                    if (spawners != null) {
                        spawners.forEach(spawner -> updateSpawnerStatus(spawner, players));
                    }
                });
            });
        }, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void updateSpawnerStatus(SpawnerData spawner, Set<Player> nearbyPlayers) {
        String spawnerId = spawner.getSpawnerId();
        Location spawnerLoc = spawner.getSpawnerLocation();

        // Check cache
        Boolean cachedResult = rangeCheckCache.getIfPresent(spawnerId);
        if (cachedResult != null) {
            if (spawner.getSpawnerStop() == cachedResult) {
                spawner.setSpawnerStop(!cachedResult);
                Bukkit.getScheduler().runTask(plugin, () -> handleSpawnerStateChange(spawner, !cachedResult));
            }
            return;
        }

        // Throttle checks
        long now = System.currentTimeMillis();
        Long lastCheck = lastRangeCheckTime.get(spawnerId);
        if (lastCheck != null && now - lastCheck < RANGE_CHECK_THROTTLE) {
            return;
        }
        lastRangeCheckTime.put(spawnerId, now);

        int range = spawner.getSpawnerRange();
        double rangeSquared = range * range;

        // Create bounding box for more efficient distance checks
        BoundingBox spawnerBox = new BoundingBox(
                spawnerLoc.getX() - range,
                spawnerLoc.getY() - range,
                spawnerLoc.getZ() - range,
                spawnerLoc.getX() + range,
                spawnerLoc.getY() + range,
                spawnerLoc.getZ() + range
        );

        boolean playerFound = nearbyPlayers.stream()
                .anyMatch(player -> spawnerBox.contains(player.getLocation().toVector()) &&
                        player.getLocation().distanceSquared(spawnerLoc) <= rangeSquared);

        rangeCheckCache.put(spawnerId, playerFound);

        boolean shouldStop = !playerFound;
        if (spawner.getSpawnerStop() != shouldStop) {
            spawner.setSpawnerStop(shouldStop);
            Bukkit.getScheduler().runTask(plugin, () -> handleSpawnerStateChange(spawner, shouldStop));
        }
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
        if (!shouldStop) {
            activateSpawner(spawner);
        } else {
            deactivateSpawner(spawner);
        }
        updateGuiForSpawner(spawner);
    }

    private void activateSpawner(SpawnerData spawner) {
        startSpawnerTask(spawner);
        spawner.refreshHologram();
        configManager.debug("Spawner " + spawner.getSpawnerId() + " activated - Player in range");
    }

    private void deactivateSpawner(SpawnerData spawner) {
        stopSpawnerTask(spawner);
        spawner.removeHologram();
        configManager.debug("Spawner " + spawner.getSpawnerId() + " deactivated - No players in range");
    }

    private void startSpawnerTask(SpawnerData spawner) {
        stopSpawnerTask(spawner);

        spawner.setLastSpawnTime(System.currentTimeMillis() + spawner.getSpawnDelay());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> {
                    if (!spawner.getSpawnerStop()) {
                        spawnerManager.spawnLoot(spawner);
                    }
                },
                0L, spawner.getSpawnDelay()
        );

        spawnerTasks.put(spawner.getSpawnerId(), task);
    }

    public void stopSpawnerTask(SpawnerData spawner) {
        BukkitTask task = spawnerTasks.remove(spawner.getSpawnerId());
        if (task != null) {
            task.cancel();
        }
    }

    private void updateGuiForSpawner(SpawnerData spawner) {
        Bukkit.getScheduler().runTask(plugin, () ->
                spawnerManager.getOpenSpawnerGuis().entrySet().stream()
                        .filter(entry -> entry.getValue().getSpawnerId().equals(spawner.getSpawnerId()))
                        .forEach(entry -> {
                            Player viewer = Bukkit.getPlayer(entry.getKey());
                            if (viewer != null && viewer.isOnline()) {
                                spawnerManager.updateSpawnerGui(viewer, spawner, true);
                            }
                        })
        );
    }

    public Set<UUID> getPlayersInRange(String spawnerId) {
        return playersInRange.getOrDefault(spawnerId, Collections.emptySet());
    }

    public void cleanup() {
        spawnerTasks.values().forEach(BukkitTask::cancel);
        spawnerTasks.clear();
        playersInRange.clear();
        spawnersByChunk.clear();
    }
}
