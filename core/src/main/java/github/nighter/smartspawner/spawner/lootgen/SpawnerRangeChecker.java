package github.nighter.smartspawner.spawner.lootgen;

import com.google.common.collect.ImmutableList;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpawnerRangeChecker {
    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerLootGenerator spawnerLootGenerator;
    private final Map<String, Scheduler.Task> spawnerTasks;
    private boolean checkGhostSpawnersOnApproach;
    private final ExecutorService executor;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerLootGenerator = plugin.getSpawnerLootGenerator();
        this.spawnerTasks = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.checkGhostSpawnersOnApproach = plugin.getConfig().getBoolean("ghost_spawners.remove_on_approach", false);
        initializeRangeCheckTask();
    }

    public void reload() {
        this.checkGhostSpawnersOnApproach = plugin.getConfig().getBoolean("ghost_spawners.remove_on_approach", false);
    }

    private void initializeRangeCheckTask() {
        // Using the global scheduler, but only for coordinating region-specific checks
        Scheduler.runTaskTimer(this::scheduleRegionSpecificCheck, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void scheduleRegionSpecificCheck() {
        final List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();
        final List<Player> onlinePlayers = ImmutableList.copyOf(Bukkit.getOnlinePlayers());

        this.executor.execute(() -> {
            final RangeMath rangeCheck = new RangeMath(onlinePlayers, allSpawners);

            rangeCheck.updateActiveSpawners();

            final boolean[] spawnersPlayerFound = rangeCheck.getActiveSpawners();

            for (int i = 0; i < spawnersPlayerFound.length; i++) {
                final boolean shouldStop = !spawnersPlayerFound[i];
                final SpawnerData sd = allSpawners.get(i);

                if (sd.getSpawnerStop().get() != shouldStop) {
                    // Only use the scheduler here
                    Scheduler.runLocationTask(sd.getSpawnerLocation(), () -> {
                        sd.getSpawnerStop().set(shouldStop);
                        handleSpawnerStateChange(sd, shouldStop);
                    });
                }
            }
        });
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
        if (checkGhostSpawnersOnApproach) {
            boolean isGhost = spawnerManager.isGhostSpawner(spawner);
            if (isGhost) {
                plugin.debug("Ghost spawner detected during status update: " + spawner.getSpawnerId());
                spawnerManager.removeGhostSpawner(spawner.getSpawnerId());
                return; // Skip further processing
            }
        }
        if (!shouldStop) {
            activateSpawner(spawner);
        } else {
            deactivateSpawner(spawner);
        }

        // Force GUI update when spawner state changes
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().forceStateChangeUpdate(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        startSpawnerTask(spawner);
        //plugin.debug("Spawner " + spawner.getSpawnerId() + " activated - Player in range");
    }

    private void deactivateSpawner(SpawnerData spawner) {
        stopSpawnerTask(spawner);
        //plugin.debug("Spawner " + spawner.getSpawnerId() + " deactivated - No players in range");
    }

    private void startSpawnerTask(SpawnerData spawner) {
        stopSpawnerTask(spawner);

        // Set lastSpawnTime to current time to start countdown immediately
        // This ensures timer shows full delay countdown when spawner activates
        long currentTime = System.currentTimeMillis();
        spawner.setLastSpawnTime(currentTime);

        Scheduler.Task task = Scheduler.runTaskTimer(() -> {
            if (!spawner.getSpawnerStop().get()) {
                spawnerLootGenerator.spawnLootToSpawner(spawner);
            }
        }, spawner.getSpawnDelay(), spawner.getSpawnDelay()); // Start after one delay period

        spawnerTasks.put(spawner.getSpawnerId(), task);

        // Immediately update any open GUIs to show the countdown
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
        }
    }

    public void stopSpawnerTask(SpawnerData spawner) {
        Scheduler.Task task = spawnerTasks.remove(spawner.getSpawnerId());
        if (task != null) {
            task.cancel();
        }
    }

    public void cleanup() {
        spawnerTasks.values().forEach(Scheduler.Task::cancel);
        spawnerTasks.clear();

        executor.shutdown();
    }
}
