package me.nighter.smartSpawner.spawner.gui.stacker;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.SpawnerStackerHolder;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStackerActionUpdater {
    private final SmartSpawner plugin;

    // Map to store update tasks by player UUID
    private final Map<UUID, BukkitTask> updateTasks;

    // Map to track active viewers by spawner ID
    private final Map<String, Set<UUID>> spawnerViewers;

    // Map to track player's last click time to prevent spam clicking
    private final Map<UUID, Long> lastClickTime;

    // Map to track which players are actively interacting (clicked a button)
    private final Map<UUID, Boolean> activeInteractors;

    // GUI slots constants
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};

    // Timing constants
    private static final long UPDATE_DELAY_TICKS = 3L; // Slightly increased delay
    private static final long CLICK_COOLDOWN_MS = 250L; // Anti-spam cooldown (250ms)
    private static final long INTERACTOR_TIMEOUT_MS = 5000L; // Reset active interactor after 5 seconds

    public SpawnerStackerActionUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.updateTasks = new ConcurrentHashMap<>();
        this.spawnerViewers = new ConcurrentHashMap<>();
        this.lastClickTime = new ConcurrentHashMap<>();
        this.activeInteractors = new ConcurrentHashMap<>();

        // Start cleanup task to reset interactor status periodically
        startCleanupTask();
    }

    // Clean up inactive interactors every 5 seconds
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                // Remove players who haven't interacted recently from active interactors list
                lastClickTime.entrySet().removeIf(entry ->
                        currentTime - entry.getValue() > INTERACTOR_TIMEOUT_MS);

                // Reset all active flags after cleanup
                activeInteractors.entrySet().removeIf(entry ->
                        !lastClickTime.containsKey(entry.getKey()));
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    // Track a player viewing a spawner GUI
    public void trackViewer(String spawnerId, Player player) {
        if (player == null || !player.isOnline()) return;

        spawnerViewers.computeIfAbsent(spawnerId, k -> ConcurrentHashMap.newKeySet())
                .add(player.getUniqueId());
    }

    // Stop tracking a player when they close the GUI
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

    // Mark a player as actively interacting with the GUI
    public void markPlayerActive(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        activeInteractors.put(playerId, true);
        lastClickTime.put(playerId, System.currentTimeMillis());
    }

    // Check if a player is on cooldown (to prevent spam clicking)
    public boolean isPlayerOnCooldown(Player player) {
        if (player == null) return false;

        UUID playerId = player.getUniqueId();
        Long lastClick = lastClickTime.get(playerId);

        if (lastClick == null) return false;

        long currentTime = System.currentTimeMillis();
        return (currentTime - lastClick) < CLICK_COOLDOWN_MS;
    }

    // Schedule updates for all active viewers of a spawner
    public void scheduleUpdateForAll(SpawnerData spawner, Player initiator) {
        if (spawner == null) return;

        String spawnerId = spawner.getSpawnerId();
        Set<UUID> viewers = spawnerViewers.getOrDefault(spawnerId, Collections.emptySet());

        // Mark initiator as active
        if (initiator != null) {
            markPlayerActive(initiator);
        }

        // Only schedule updates for active interactors
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID viewerId : viewers) {
                    Player viewer = plugin.getServer().getPlayer(viewerId);
                    // Only update for the initiator and other active players
                    if (viewer != null && viewer.isOnline() &&
                            (viewer.equals(initiator) || Boolean.TRUE.equals(activeInteractors.get(viewerId)))) {
                        scheduleUpdate(viewer, spawner);
                    }
                }
            }
        }.runTask(plugin);
    }

    // Schedule update for a single player's GUI
    private void scheduleUpdate(Player player, SpawnerData spawner) {
        UUID playerId = player.getUniqueId();

        // Cancel any pending updates for this player
        cancelPendingUpdate(playerId);

        // Schedule new update with verification
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup(playerId);
                    return;
                }

                Inventory topInventory = player.getOpenInventory().getTopInventory();

                if (topInventory.getHolder() instanceof SpawnerStackerHolder) {
                    // Update GUI items instead of recreating inventory
                    updateStackerGuiItems(player, topInventory, spawner);
                }
                updateTasks.remove(playerId);
            }
        }.runTaskLater(plugin, UPDATE_DELAY_TICKS);

        updateTasks.put(playerId, task);
    }

    // Update GUI items efficiently without recreating the entire inventory
    private void updateStackerGuiItems(Player player, Inventory inventory, SpawnerData spawner) {
        try {
            // Update spawner info item
            updateSpawnerInfoItem(inventory, spawner);

            // Update decrease buttons
            for (int i = 0; i < STACK_AMOUNTS.length; i++) {
                updateDecreaseButton(inventory, spawner, STACK_AMOUNTS[i], DECREASE_SLOTS[i]);
            }

            // Update increase buttons
            for (int i = 0; i < STACK_AMOUNTS.length; i++) {
                updateIncreaseButton(inventory, spawner, STACK_AMOUNTS[i], INCREASE_SLOTS[i]);
            }

            // Force the client to refresh the inventory view
            player.updateInventory();
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating GUI for player " + player.getName() + ": " + e.getMessage());
        }
    }

    // Update the spawner info item in the GUI
    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner) {
        ItemStack infoButton = inventory.getItem(SPAWNER_INFO_SLOT);
        if (infoButton == null) return;

        ItemMeta meta = infoButton.getItemMeta();
        if (meta == null) return;

        // Get freshly formatted lore from language manager
        String[] lore = plugin.getLanguageManager().getMessage("button.lore.spawner")
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .replace("%max_stack_size%", String.valueOf(plugin.getConfigManager().getMaxStackSize()))
                .replace("%entity%", plugin.getLanguageManager().getFormattedMobName(spawner.getEntityType()))
                .split("\n");

        meta.setLore(Arrays.asList(lore));
        infoButton.setItemMeta(meta);
    }

    // Update decrease button in the GUI
    private void updateDecreaseButton(Inventory inventory, SpawnerData spawner, int amount, int slot) {
        ItemStack button = inventory.getItem(slot);
        if (button == null) return;

        ItemMeta meta = button.getItemMeta();
        if (meta == null) return;

        String[] lore = plugin.getLanguageManager().getMessage("button.lore.remove")
                .replace("%amount%", String.valueOf(amount))
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .split("\n");

        meta.setLore(Arrays.asList(lore));
        button.setItemMeta(meta);
    }

    // Update increase button in the GUI
    private void updateIncreaseButton(Inventory inventory, SpawnerData spawner, int amount, int slot) {
        ItemStack button = inventory.getItem(slot);
        if (button == null) return;

        ItemMeta meta = button.getItemMeta();
        if (meta == null) return;

        String[] lore = plugin.getLanguageManager().getMessage("button.lore.add")
                .replace("%amount%", String.valueOf(amount))
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .split("\n");

        meta.setLore(Arrays.asList(lore));
        button.setItemMeta(meta);
    }

    // Cancel pending update for a player
    private void cancelPendingUpdate(UUID playerId) {
        BukkitTask existingTask = updateTasks.remove(playerId);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }
    }

    // Clean up resources for a player
    public void cleanup(UUID playerId) {
        cancelPendingUpdate(playerId);
        lastClickTime.remove(playerId);
        activeInteractors.remove(playerId);
        spawnerViewers.values().forEach(viewers -> viewers.remove(playerId));
        spawnerViewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    // Clean up all resources
    public void cleanupAll() {
        updateTasks.values().forEach(task -> {
            if (!task.isCancelled()) task.cancel();
        });
        updateTasks.clear();
        lastClickTime.clear();
        activeInteractors.clear();
        spawnerViewers.clear();
    }

    // Get all viewers for a spawner
    public Set<UUID> getSpawnerViewers(String spawnerId) {
        return spawnerViewers.getOrDefault(spawnerId, Collections.emptySet());
    }
}