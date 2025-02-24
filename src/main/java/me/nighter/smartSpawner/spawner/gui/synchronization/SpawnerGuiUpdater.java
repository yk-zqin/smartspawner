package me.nighter.smartSpawner.spawner.gui.synchronization;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.holders.StoragePageHolder;
import me.nighter.smartSpawner.spawner.gui.storage.SpawnerStorageUI;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Controller responsible for managing spawner GUI interactions and updates.
 * Handles the tracking, updating, and management of open spawner GUI interfaces.
 */
public class SpawnerGuiUpdater implements Listener {
    private static final long UPDATE_INTERVAL_TICKS = 10L;
    private static final long INITIAL_DELAY_TICKS = 10L;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerStorageUI spawnerStorageUI;
    private final Map<UUID, SpawnerData> openSpawnerGuis;
    private BukkitTask updateTask;
    private volatile boolean isTaskRunning;
    private long previousExpValue = 0;

    public SpawnerGuiUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerStorageUI = new SpawnerStorageUI(plugin);
        this.openSpawnerGuis = new ConcurrentHashMap<>();
        this.isTaskRunning = false;
    }


    private synchronized void startUpdateTask() {
        if (isTaskRunning) {
            return;
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateGuiForSpawnerInfo,
                INITIAL_DELAY_TICKS, UPDATE_INTERVAL_TICKS);
        isTaskRunning = true;

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Started GUI update task");
        }
    }

    private boolean isValidGuiSession(Player player) {
        return player != null && player.isOnline();
    }

    public synchronized void stopUpdateTask() {
        if (!isTaskRunning) {
            return;
        }

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        isTaskRunning = false;

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Stopped GUI update task");
        }
    }

    // ===============================================================
    //                      Spawner Main GUI
    // ===============================================================

    private void updateGuiForSpawnerInfo() {
        if (openSpawnerGuis.isEmpty()) {
            stopUpdateTask();
            return;
        }

        for (Map.Entry<UUID, SpawnerData> entry : openSpawnerGuis.entrySet()) {
            UUID playerId = entry.getKey();
            SpawnerData spawner = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);

            if (!isValidGuiSession(player)) {
                untrackOpenGui(playerId);
                continue;
            }

            // Update the GUI if it's still open
            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (openInventory.getHolder() instanceof SpawnerMenuHolder) {
                updateSpawnerGuiInfo(player, spawner, false);

                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().log(Level.FINE, "Updated GUI for {0}", player.getName());
                }
            } else {
                untrackOpenGui(playerId);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerMenuHolder)) {
            return;
        }

        SpawnerMenuHolder holder = (SpawnerMenuHolder) event.getInventory().getHolder();
        UUID playerId = event.getPlayer().getUniqueId();

        trackOpenGui(playerId, holder.getSpawnerData());
        if (!isTaskRunning) {
            startUpdateTask();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerMenuHolder)) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        untrackOpenGui(playerId);

        // Check if this was the last open GUI
        if (openSpawnerGuis.isEmpty()) {
            stopUpdateTask();
        }
    }

    public void updateSpawnerGuiViewers(SpawnerData spawner) {
        for (Map.Entry<UUID, SpawnerData> entry : openSpawnerGuis.entrySet()) {
            if (entry.getValue().getSpawnerId().equals(spawner.getSpawnerId())) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null && viewer.isOnline()) {
                    updateSpawnerGui(viewer, spawner, true);
                }
            }
        }
    }

    public void updateSpawnerGui(Player player, SpawnerData spawner, boolean forceUpdate) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof SpawnerMenuHolder) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) openInv.getHolder();
            if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId()) || forceUpdate) {
                updateSpawnerInfoItem(openInv, spawner);
                updateExpItem(openInv, spawner);
                updateChestItem(openInv, spawner);
                player.updateInventory();
            }
        }
    }
    public void updateSpawnerGuiInfo(Player player, SpawnerData spawner, boolean forceUpdate) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof SpawnerMenuHolder) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) openInv.getHolder();
            if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId()) || forceUpdate) {
                updateSpawnerInfoItem(openInv, spawner);
                player.updateInventory();
            }
        }
    }


    public void trackOpenGui(UUID playerId, SpawnerData spawner) {
        openSpawnerGuis.put(playerId, spawner);
    }

    public void untrackOpenGui(UUID playerId) {
        openSpawnerGuis.remove(playerId);
    }

    public void clearAllTrackedGuis() {
        openSpawnerGuis.clear();
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Cleared all tracked spawner GUIs");
        }
    }


    public Map<UUID, SpawnerData> getOpenSpawnerGuis() {
        return Collections.unmodifiableMap(openSpawnerGuis);
    }

    private void updateChestItem(Inventory inventory, SpawnerData spawner) {
        ItemStack chestItem = inventory.getItem(11);
        if (chestItem == null || !chestItem.hasItemMeta()) return;

        ItemMeta chestMeta = chestItem.getItemMeta();
        chestMeta.setDisplayName(languageManager.getMessage("spawner-loot-item.name"));

        // Create replacements map
        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = (int) ((double) currentItems / maxSlots * 100);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("%max_slots%", String.valueOf(maxSlots));
        replacements.put("%current_items%", String.valueOf(currentItems));
        replacements.put("%percent_storage%", String.valueOf(percentStorage));

        // Get lore from language file with replacements
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest", replacements);

        List<String> chestLore = Arrays.asList(loreMessageChest.split("\n"));
        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);
        inventory.setItem(11, chestItem);
    }

    private void updateExpItem(Inventory inventory, SpawnerData spawner) {
        ItemStack expItem = inventory.getItem(15);
        if (expItem == null || !expItem.hasItemMeta()) return;

        long currentExp = spawner.getSpawnerExp();
        if (currentExp != previousExpValue) {
            ItemMeta expMeta = expItem.getItemMeta();
            Map<String, String> nameReplacements = new HashMap<>();
            String formattedExp = languageManager.formatNumber(currentExp);
            String formattedMaxExp = languageManager.formatNumber(spawner.getMaxStoredExp());
            int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

            nameReplacements.put("%current_exp%", String.valueOf(spawner.getSpawnerExp()));
            expMeta.setDisplayName(languageManager.getMessage("exp-info-item.name", nameReplacements));

            Map<String, String> loreReplacements = new HashMap<>();
            loreReplacements.put("%current_exp%", formattedExp);
            loreReplacements.put("%max_exp%", formattedMaxExp);
            loreReplacements.put("%percent_exp%", String.valueOf(percentExp));
            loreReplacements.put("%u_max_exp%", String.valueOf(spawner.getMaxStoredExp()));

            String lorePathExp = "exp-info-item.lore.exp-bottle";
            String loreMessageExp = languageManager.getMessage(lorePathExp, loreReplacements);
            List<String> loreEx = Arrays.asList(loreMessageExp.split("\n"));
            expMeta.setLore(loreEx);
            expItem.setItemMeta(expMeta);
            inventory.setItem(15, expItem);
            previousExpValue = currentExp;
        }
    }

    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner) {
        ItemStack spawnerItem = inventory.getItem(13);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) return;

        ItemMeta meta = spawnerItem.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        String timeDisplay = getTimeDisplay(timeUntilNextSpawn);

        // Get template for next spawn line
        String nextSpawnTemplate = languageManager.getMessage("spawner-info-item.lore-change");

        // Strip color for comparison
        String strippedTemplate = ChatColor.stripColor(nextSpawnTemplate);

        // Optimize lore update
        boolean updated = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            // Strip color of current line for comparison
            String strippedLine = ChatColor.stripColor(line);

            // Compare stripped content
            if (strippedLine.startsWith(ChatColor.stripColor(strippedTemplate))) {
                String newLine = nextSpawnTemplate + timeDisplay;
                if (!line.equals(newLine)) {
                    lore.set(i, newLine);
                    updated = true;
                }
                break;
            }
        }

        // Only update ItemMeta if there are changes
        if (updated || !lore.stream()
                .map(ChatColor::stripColor)
                .anyMatch(line -> line.startsWith(ChatColor.stripColor(strippedTemplate)))) {
            if (!updated) {
                lore.add(nextSpawnTemplate + timeDisplay);
            }
            meta.setLore(lore);
            spawnerItem.setItemMeta(meta);
        }
    }

    private String getTimeDisplay(long timeUntilNextSpawn) {
        if (timeUntilNextSpawn == -1) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn == 0) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn < 0) {
            return languageManager.getMessage("spawner-info-item.lore-error");
        }
        return formatTime(timeUntilNextSpawn);
    }

    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop()) {
            return -1;
        }
        final long currentTime = System.currentTimeMillis();
        final long lastSpawnTime = spawner.getLastSpawnTime();
        final long spawnDelay = spawner.getSpawnDelay() * 50L;
        return lastSpawnTime + spawnDelay - currentTime;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ===============================================================
    //                      Spawner Storage GUI
    // ===============================================================

    public List<HumanEntity> getViewersForSpawner(SpawnerData spawner) {
        List<HumanEntity> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory openInv = player.getOpenInventory().getTopInventory();
            if (openInv.getHolder() instanceof StoragePageHolder) {
                StoragePageHolder holder = (StoragePageHolder) openInv.getHolder();
                if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
                    viewers.add(player);
                }
            }
        }
        return viewers;
    }

    private record UpdateAction(Player player, SpawnerData spawner, int page, boolean requiresNewInventory) {
    }

    public void updateLootInventoryViewers(SpawnerData spawner, int oldTotalPages, int newTotalPages) {
        // Check if total pages changed
        boolean pagesChanged = oldTotalPages != newTotalPages;

        // Batch all viewers that need updates
        List<UpdateAction> updateQueue = new ArrayList<>();

        for (HumanEntity viewer : getViewersForSpawner(spawner)) {
            if (viewer instanceof Player) {
                Player player = (Player) viewer;
                Inventory currentInv = player.getOpenInventory().getTopInventory();
                if (currentInv.getHolder() instanceof StoragePageHolder) {
                    StoragePageHolder holder = (StoragePageHolder) currentInv.getHolder();
                    int currentPage = holder.getCurrentPage();

                    boolean needsNewInventory = false;
                    int targetPage = currentPage;

                    // Determine if we need a new inventory
                    if (currentPage > newTotalPages) {
                        targetPage = newTotalPages;
                        needsNewInventory = true;
                    } else if (pagesChanged) {
                        // If total pages changed but current page is still valid,
                        // we need new inventory just to update the title
                        needsNewInventory = true;
                    }

                    updateQueue.add(new UpdateAction(player, spawner, targetPage, needsNewInventory));
                }
            }
        }

        // Process all updates in one server tick
        if (!updateQueue.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UpdateAction action : updateQueue) {
                    if (action.requiresNewInventory) {
                        // Need new inventory for title update
                        Inventory newInv = spawnerStorageUI.createInventory(
                                action.spawner,
                                languageManager.getGuiTitle("gui-title.loot-menu"),
                                action.page
                        );
                        action.player.closeInventory();
                        action.player.openInventory(newInv);
                    } else {
                        // Just update contents of current inventory
                        Inventory currentInv = action.player.getOpenInventory().getTopInventory();
                        spawnerStorageUI.updateDisplay(currentInv, action.spawner, action.page);
                        action.player.updateInventory();
                    }
                }
            });
        }
    }

    public void cleanup() {
        stopUpdateTask();
        clearAllTrackedGuis();
    }
}