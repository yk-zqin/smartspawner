package me.nighter.smartSpawner.spawner.gui.synchronization;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.SpawnerStackerHolder;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnerStackerUpdater {
    private final SmartSpawner plugin;
    private final Map<UUID, BukkitTask> updateTasks;
    private final Map<UUID, AtomicInteger> updateCounter;
    private final Map<String, Set<UUID>> spawnerViewers;
    private final Cache<UUID, Long> lastUpdateTimeCache;

    private static final long UPDATE_DELAY_TICKS = 2L;
    private static final long MIN_UPDATE_INTERVAL = 100L;
    private static final int MAX_UPDATES_PER_SECOND = 10;
    private static final int CACHE_EXPIRATION_SECONDS = 60;

    public SpawnerStackerUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.updateTasks = new ConcurrentHashMap<>();
        this.updateCounter = new ConcurrentHashMap<>();
        this.spawnerViewers = new ConcurrentHashMap<>();
        this.lastUpdateTimeCache = new Cache<>(CACHE_EXPIRATION_SECONDS);

        // Start cleanup task
        startCleanupTask();
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                lastUpdateTimeCache.cleanup();
                updateCounter.forEach((uuid, counter) -> counter.set(0));
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void trackViewer(String spawnerId, Player player) {
        if (player == null || !player.isOnline()) return;

        spawnerViewers.computeIfAbsent(spawnerId, k -> ConcurrentHashMap.newKeySet())
                .add(player.getUniqueId());
        updateCounter.putIfAbsent(player.getUniqueId(), new AtomicInteger(0));
    }

    public void untrackViewer(String spawnerId, Player player) {
        if (spawnerId == null || player == null) return;

        Set<UUID> viewers = spawnerViewers.get(spawnerId);
        if (viewers != null) {
            viewers.remove(player.getUniqueId());
            if (viewers.isEmpty()) {
                spawnerViewers.remove(spawnerId);
            }
        }
        cleanup(player.getUniqueId());
    }

    public void scheduleUpdateForAll(SpawnerData spawner, Player initiator) {
        if (spawner == null) return;

        String spawnerId = spawner.getSpawnerId();
        Set<UUID> viewers = spawnerViewers.getOrDefault(spawnerId, Collections.emptySet());

        // Batch update for all viewers
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID viewerId : viewers) {
                    Player viewer = plugin.getServer().getPlayer(viewerId);
                    if (viewer != null && viewer.isOnline()) {
                        boolean isInitiator = viewer.equals(initiator);
                        scheduleUpdate(viewer, spawner, isInitiator);
                    }
                }
            }
        }.runTask(plugin);
    }

    private void scheduleUpdate(Player player, SpawnerData spawner, boolean applyThrottle) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Rate limiting check
        AtomicInteger counter = updateCounter.get(playerId);
        if (counter != null && counter.incrementAndGet() > MAX_UPDATES_PER_SECOND) {
            return;
        }

        // Throttling check
        if (applyThrottle && shouldThrottleUpdate(playerId, currentTime)) {
            return;
        }

        cancelPendingUpdate(playerId);

        // Schedule update with verification
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup(playerId);
                    return;
                }

                Inventory topInventory = player.getOpenInventory().getTopInventory();
                if (topInventory != null && topInventory.getHolder() instanceof SpawnerStackerHolder) {
                    updateGUI(player, spawner);
                }
                updateTasks.remove(playerId);
            }
        }.runTaskLater(plugin, UPDATE_DELAY_TICKS);

        updateTasks.put(playerId, task);
        lastUpdateTimeCache.put(playerId, currentTime);
    }

    private void updateGUI(Player player, SpawnerData spawner) {
        try {
            plugin.getSpawnerStackerUI().openStackerGui(player, spawner);
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating GUI for player " + player.getName() + ": " + e.getMessage());
        }
    }

    private boolean shouldThrottleUpdate(UUID playerId, long currentTime) {
        Long lastUpdate = lastUpdateTimeCache.get(playerId);
        return lastUpdate != null && (currentTime - lastUpdate) < MIN_UPDATE_INTERVAL;
    }

    private void cancelPendingUpdate(UUID playerId) {
        BukkitTask existingTask = updateTasks.remove(playerId);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }
    }

    public void cleanup(UUID playerId) {
        cancelPendingUpdate(playerId);
        lastUpdateTimeCache.remove(playerId);
        updateCounter.remove(playerId);
        spawnerViewers.values().forEach(viewers -> viewers.remove(playerId));
        spawnerViewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void cleanupAll() {
        updateTasks.values().forEach(task -> {
            if (!task.isCancelled()) task.cancel();
        });
        updateTasks.clear();
        lastUpdateTimeCache.clear();
        updateCounter.clear();
        spawnerViewers.clear();
    }

    // getSpawnerViewers
    public Set<UUID> getSpawnerViewers(String spawnerId) {
        return spawnerViewers.getOrDefault(spawnerId, Collections.emptySet());
    }

    // Simple cache implementation with expiration
    private static class Cache<K, V> {
        private final Map<K, CacheEntry<V>> map = new ConcurrentHashMap<>();
        private final long expirationSeconds;

        public Cache(long expirationSeconds) {
            this.expirationSeconds = expirationSeconds;
        }

        public void put(K key, V value) {
            map.put(key, new CacheEntry<>(value));
        }

        public V get(K key) {
            CacheEntry<V> entry = map.get(key);
            if (entry != null && !entry.isExpired(expirationSeconds)) {
                return entry.getValue();
            }
            map.remove(key);
            return null;
        }

        public void remove(K key) {
            map.remove(key);
        }

        public void clear() {
            map.clear();
        }

        public void cleanup() {
            map.entrySet().removeIf(entry -> entry.getValue().isExpired(expirationSeconds));
        }

        private static class CacheEntry<V> {
            private final V value;
            private final long timestamp;

            public CacheEntry(V value) {
                this.value = value;
                this.timestamp = System.currentTimeMillis();
            }

            public V getValue() {
                return value;
            }

            public boolean isExpired(long expirationSeconds) {
                return System.currentTimeMillis() - timestamp > expirationSeconds * 1000;
            }
        }
    }
}