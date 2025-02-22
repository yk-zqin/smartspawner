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

public class SpawnerStackerUpdater {
    private final SmartSpawner plugin;
    private final Map<UUID, BukkitTask> updateTasks;
    private final Map<UUID, Long> lastUpdateTime;
    private final Map<String, Set<UUID>> spawnerViewers; // SpawnerID -> Set of Viewer UUIDs
    private static final long UPDATE_DELAY_TICKS = 2L;
    private static final long MIN_UPDATE_INTERVAL = 100L;

    public SpawnerStackerUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.updateTasks = new ConcurrentHashMap<>();
        this.lastUpdateTime = new ConcurrentHashMap<>();
        this.spawnerViewers = new ConcurrentHashMap<>();
    }

    /**
     * Track a viewer for a specific spawner
     */
    public void trackViewer(String spawnerId, Player player) {
        spawnerViewers.computeIfAbsent(spawnerId, k -> ConcurrentHashMap.newKeySet())
                .add(player.getUniqueId());
    }

    /**
     * Remove a viewer from tracking
     */
    public void untrackViewer(String spawnerId, Player player) {
        Set<UUID> viewers = spawnerViewers.get(spawnerId);
        if (viewers != null) {
            viewers.remove(player.getUniqueId());
            if (viewers.isEmpty()) {
                spawnerViewers.remove(spawnerId);
            }
        }
    }

    /**
     * Schedule updates for all viewers of a spawner
     */
    public void scheduleUpdateForAll(SpawnerData spawner, Player initiator) {
        String spawnerId = spawner.getSpawnerId();
        Set<UUID> viewers = spawnerViewers.getOrDefault(spawnerId, Collections.emptySet());

        for (UUID viewerId : viewers) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                // Don't throttle updates for other viewers, only for the initiator
                if (viewer.equals(initiator)) {
                    scheduleUpdate(viewer, spawner, true);
                } else {
                    scheduleUpdate(viewer, spawner, false);
                }
            }
        }
    }

    /**
     * Schedule a GUI update with optional throttling
     */
    private void scheduleUpdate(Player player, SpawnerData spawner, boolean applyThrottle) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Cancel any pending update for this player
        cancelPendingUpdate(playerId);

        // Check if we need to throttle updates
        if (applyThrottle && shouldThrottleUpdate(playerId, currentTime)) {
            return;
        }

        // Schedule new update
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    updateGUI(player, spawner);
                }
                updateTasks.remove(playerId);
            }
        }.runTaskLater(plugin, UPDATE_DELAY_TICKS);

        updateTasks.put(playerId, task);
        lastUpdateTime.put(playerId, currentTime);
    }

    /**
     * Perform the actual GUI update
     */
    private void updateGUI(Player player, SpawnerData spawner) {
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (topInventory == null) return;

        if (topInventory.getHolder() instanceof SpawnerStackerHolder) {
            plugin.getSpawnerStackerUI().openStackerGui(player, spawner);
            plugin.getSpawnerViewUpdater().updateViewersIncludeTitle(spawner);
        }
    }

    private boolean shouldThrottleUpdate(UUID playerId, long currentTime) {
        Long lastUpdate = lastUpdateTime.get(playerId);
        if (lastUpdate == null) {
            return false;
        }
        return (currentTime - lastUpdate) < MIN_UPDATE_INTERVAL;
    }

    private void cancelPendingUpdate(UUID playerId) {
        BukkitTask existingTask = updateTasks.remove(playerId);
        if (existingTask != null) {
            existingTask.cancel();
        }
    }

    public Set<UUID> getSpawnerViewers(String spawnerId) {
        return spawnerViewers.getOrDefault(spawnerId, Collections.emptySet());
    }

    public void cleanup(UUID playerId) {
        cancelPendingUpdate(playerId);
        lastUpdateTime.remove(playerId);
        // Remove player from all spawner viewers
        spawnerViewers.values().forEach(viewers -> viewers.remove(playerId));
        // Clean up empty spawner viewer sets
        spawnerViewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void cleanupAll() {
        updateTasks.values().forEach(BukkitTask::cancel);
        updateTasks.clear();
        lastUpdateTime.clear();
        spawnerViewers.clear();
    }
}