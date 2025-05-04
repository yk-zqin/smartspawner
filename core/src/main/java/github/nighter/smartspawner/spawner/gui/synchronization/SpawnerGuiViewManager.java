package github.nighter.smartspawner.spawner.gui.synchronization;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.SpawnerMenuHolder;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.utils.SpawnerMobHeadTexture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Unified controller responsible for managing all spawner GUI interactions and updates.
 * Handles the tracking, updating, and synchronization of open spawner GUI interfaces.
 */
public class SpawnerGuiViewManager implements Listener {
    private static final int TICKS_PER_SECOND = 20;
    private static final long UPDATE_INTERVAL_TICKS = 10L;
    private static final long INITIAL_DELAY_TICKS = 10L;
    private static final int ITEMS_PER_PAGE = 45; // Standard chest inventory size minus navigation items

    // GUI slot constants
    private static final int CHEST_SLOT = 11;
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int EXP_SLOT = 15;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerMenuUI spawnerMenuUI;

    // Data structures to track viewers
    private final Map<UUID, SpawnerData> playerToSpawnerMap; // Player UUID -> SpawnerData
    private final Map<String, Set<UUID>> spawnerToPlayersMap; // SpawnerID -> Set of Player UUIDs
    private final Set<Class<? extends InventoryHolder>> validHolderTypes;

    private Scheduler.Task updateTask;
    private volatile boolean isTaskRunning;
    private long previousExpValue = 0;

    public SpawnerGuiViewManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.playerToSpawnerMap = new ConcurrentHashMap<>();
        this.spawnerToPlayersMap = new ConcurrentHashMap<>();
        this.isTaskRunning = false;
        this.validHolderTypes = Set.of(
                SpawnerMenuHolder.class,
                StoragePageHolder.class
        );
    }

    // ===============================================================
    //                      Task Management
    // ===============================================================

    private synchronized void startUpdateTask() {
        if (isTaskRunning) {
            return;
        }

        updateTask = Scheduler.runTaskTimer(this::updateGuiForSpawnerInfo,
                INITIAL_DELAY_TICKS, UPDATE_INTERVAL_TICKS);
        isTaskRunning = true;
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
    }

    private boolean isValidGuiSession(Player player) {
        return player != null && player.isOnline();
    }

    private boolean isValidHolder(InventoryHolder holder) {
        return holder != null && validHolderTypes.contains(holder.getClass());
    }

    // ===============================================================
    //                      Viewer Tracking
    // ===============================================================

    public void trackViewer(UUID playerId, SpawnerData spawner) {
        playerToSpawnerMap.put(playerId, spawner);
        spawnerToPlayersMap.computeIfAbsent(spawner.getSpawnerId(), k -> ConcurrentHashMap.newKeySet())
                .add(playerId);

        if (!isTaskRunning) {
            startUpdateTask();
        }
    }

    public void untrackViewer(UUID playerId) {
        SpawnerData spawner = playerToSpawnerMap.remove(playerId);
        if (spawner != null) {
            Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
            if (viewers != null) {
                viewers.remove(playerId);
                if (viewers.isEmpty()) {
                    spawnerToPlayersMap.remove(spawner.getSpawnerId());
                }
            }
        }

        // Check if we need to stop the update task
        if (playerToSpawnerMap.isEmpty()) {
            stopUpdateTask();
        }
    }

    public Set<Player> getViewers(String spawnerId) {
        Set<UUID> viewerIds = spawnerToPlayersMap.get(spawnerId);
        if (viewerIds == null || viewerIds.isEmpty()) {
            return Collections.emptySet();
        }

        return viewerIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toSet());
    }

    public boolean hasViewers(SpawnerData spawner) {
        Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
        return viewers != null && !viewers.isEmpty();
    }

    public void clearAllTrackedGuis() {
        playerToSpawnerMap.clear();
        spawnerToPlayersMap.clear();
        // plugin.getLogger().info("Cleared all tracked spawner GUIs");
    }

    // ===============================================================
    //                      Event Handlers
    // ===============================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!isValidHolder(holder)) return;

        UUID playerId = player.getUniqueId();
        SpawnerData spawnerData = null;

        if (holder instanceof SpawnerMenuHolder spawnerHolder) {
            spawnerData = spawnerHolder.getSpawnerData();
        } else if (holder instanceof StoragePageHolder storageHolder) {
            spawnerData = storageHolder.getSpawnerData();
        }

        if (spawnerData != null) {
            trackViewer(playerId, spawnerData);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (!player.isOnline()) return;

        // Capture necessary info before passing to scheduler
        final UUID playerId = player.getUniqueId();
        final Inventory closedInventory = event.getInventory();
        final InventoryHolder holder = closedInventory.getHolder();

        // Check if it's a valid holder without accessing block data
        if (isValidHolder(holder)) {
            // Safe to untrack the viewer immediately
            untrackViewer(playerId);

            // Any other cleanup or follow-up actions should happen in the player's region
            // This prevents the "Cannot read world asynchronously" error
            if (player.getLocation() != null) {
                // Use region scheduler for the player's specific region
                Scheduler.runLocationTask(player.getLocation(), () -> {
                    // By this point we're in the correct region thread
                    // Any world access can be safely done here
                    // However, we've already untracked the viewer, so no additional work needed
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        untrackViewer(event.getPlayer().getUniqueId());
    }

    // ===============================================================
    //                      Update Logic
    // ===============================================================

    private void updateGuiForSpawnerInfo() {
        if (playerToSpawnerMap.isEmpty()) {
            stopUpdateTask();
            return;
        }

        // Use iterator to avoid ConcurrentModificationException
        Iterator<Map.Entry<UUID, SpawnerData>> iterator = playerToSpawnerMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnerData> entry = iterator.next();
            UUID playerId = entry.getKey();
            SpawnerData spawner = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);

            if (!isValidGuiSession(player)) {
                iterator.remove();
                untrackViewer(playerId);
                continue;
            }

            // Using location to make sure we're on the correct region thread
            Location playerLocation = player.getLocation();
            if (playerLocation != null) {
                // This is the key fix: use location-based scheduling to ensure we're on the right thread
                Scheduler.runLocationTask(playerLocation, () -> {
                    if (!player.isOnline()) return;

                    Inventory openInventory = player.getOpenInventory().getTopInventory();
                    if (openInventory == null) return;

                    InventoryHolder holder = openInventory.getHolder();

                    if (holder instanceof SpawnerMenuHolder) {
                        if (!spawner.getIsAtCapacity()) {
                            Inventory openInv = player.getOpenInventory().getTopInventory();
                            updateSpawnerInfoItemTimer(openInv, spawner);
                        }
                    } else if (!(holder instanceof StoragePageHolder)) {
                        // If inventory is neither SpawnerMenuHolder nor StoragePageHolder, untrack
                        untrackViewer(playerId);
                    }
                });
            }
        }
    }

    // ===============================================================
    //                      Main GUI Update
    // ===============================================================

    public void updateSpawnerMenuViewers(SpawnerData spawner) {
        Set<Player> viewers = getViewers(spawner.getSpawnerId());
        if (viewers.isEmpty()) return;
        plugin.debug(viewers.size() + " spawner menu viewers to update for " + spawner.getSpawnerId());

        for (Player viewer : viewers) {
            if (!viewer.isOnline()) continue;

            // Schedule update on the correct thread for this player
            Scheduler.runLocationTask(viewer.getLocation(), () -> {
                if (!viewer.isOnline()) return;

                Inventory openInv = viewer.getOpenInventory().getTopInventory();
                if (openInv == null) return;

                InventoryHolder holder = openInv.getHolder();
                if (holder instanceof SpawnerMenuHolder) {
                    updateSpawnerMenuGui(viewer, spawner, true);
                } else if (holder instanceof StoragePageHolder storageHolder) {
                    int oldTotalPages = calculateTotalPages(storageHolder.getOldUsedSlots());
                    int newTotalPages = calculateTotalPages(spawner.getVirtualInventory().getUsedSlots());
                    updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
                }
            });
        }
    }

    private int calculateTotalPages(int totalItems) {
        if (totalItems <= 0) {
            return 1; // At least one page for empty inventory
        }
        return (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
    }

    public void updateSpawnerMenuGui(Player player, SpawnerData spawner, boolean forceUpdate) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof SpawnerMenuHolder) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) openInv.getHolder();
            if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId()) || forceUpdate) {
                updateExpItem(openInv, spawner);
                updateChestItem(openInv, spawner);
                player.updateInventory();
            }
        }
    }

    private void updateSpawnerInfoItemTimer(Inventory inventory, SpawnerData spawner) {
        ItemStack spawnerItem = inventory.getItem(SPAWNER_INFO_SLOT);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) return;

        ItemMeta meta = spawnerItem.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Get the template string for the next spawn time
        String nextSpawnTemplate = languageManager.getGuiItemName("spawner_info_item.lore_change");

        // If template is empty or just whitespace, skip countdown update entirely
        if (nextSpawnTemplate == null || nextSpawnTemplate.trim().isEmpty()) {
            return;
        }

        // Calculate time until next spawn
        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        String timeDisplay = getTimeDisplay(timeUntilNextSpawn);

        // Check if spawner is at capacity and override display message
        if (spawner.getIsAtCapacity()) {
            timeDisplay = languageManager.getGuiItemName("spawner_info_item.lore_full");
        }

        // Create the new line with template and time
        String newLine = nextSpawnTemplate + timeDisplay;

        // Find existing line in lore if it exists
        String strippedTemplate = ChatColor.stripColor(nextSpawnTemplate);
        int lineIndex = -1;

        for (int i = 0; i < lore.size(); i++) {
            String strippedLine = ChatColor.stripColor(lore.get(i));
            if (strippedLine.startsWith(ChatColor.stripColor(strippedTemplate))) {
                lineIndex = i;
                break;
            }
        }

        // Update or add the line
        if (lineIndex >= 0) {
            // Only update if the line has changed to avoid unnecessary inventory updates
            if (!lore.get(lineIndex).equals(newLine)) {
                ItemUpdater.updateLoreLine(spawnerItem, lineIndex, newLine);
            }
        } else {
            // If line doesn't exist yet, add it
            lore.add(newLine);

            ItemUpdater.updateLore(spawnerItem, lore);
        }
    }

    private String getTimeDisplay(long timeUntilNextSpawn) {
        if (timeUntilNextSpawn == -1) {
            return languageManager.getGuiItemName("spawner_info_item.lore_inactive");
        } else if (timeUntilNextSpawn <= 0) {
            return formatTime(timeUntilNextSpawn);
        }
        return formatTime(timeUntilNextSpawn);
    }

    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop()) {
            return -1;
        }

        // Cache spawn delay calculation outside of lock
        long cachedDelay = spawner.getCachedSpawnDelay();
        if (cachedDelay == 0) {
            cachedDelay = spawner.getSpawnDelay() * 50L;
            spawner.setCachedSpawnDelay(cachedDelay);
        }

        long currentTime = System.currentTimeMillis();
        long lastSpawnTime = spawner.getLastSpawnTime();
        long timeUntilNextSpawn = lastSpawnTime + cachedDelay - currentTime;

        // If time is negative, handle it carefully with proper locking
        if (timeUntilNextSpawn < 0) {
            try {
                // Try to acquire lock with timeout to prevent deadlock
                if (spawner.getLock().tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        // Re-check conditions after acquiring lock
                        currentTime = System.currentTimeMillis();
                        lastSpawnTime = spawner.getLastSpawnTime();
                        timeUntilNextSpawn = lastSpawnTime + cachedDelay - currentTime;

                        if (timeUntilNextSpawn < 0) {
                            spawner.setLastSpawnTime(currentTime - cachedDelay);

                            // Get the spawner location to schedule on the right region
                            Location spawnerLocation = spawner.getSpawnerLocation();
                            if (spawnerLocation != null) {
                                // Schedule activation on the appropriate region thread for Folia compatibility
                                Scheduler.runLocationTask(spawnerLocation, () -> {
                                    plugin.getRangeChecker().activateSpawner(spawner);
                                });
                            }
                            return 0;
                        }
                    } finally {
                        spawner.getLock().unlock();
                    }
                } else {
                    // If can't acquire lock, just return current calculation without modifying state
                    return Math.max(0, timeUntilNextSpawn);
                }
            } catch (InterruptedException e) {
                // Handle interruption
                Thread.currentThread().interrupt();
                return Math.max(0, timeUntilNextSpawn);
            }
        }

        return timeUntilNextSpawn;
    }

    private String formatTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "00:00";
        }
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateChestItem(Inventory inventory, SpawnerData spawner) {
        // Get the chest item from the inventory
        ItemStack currentChestItem = inventory.getItem(CHEST_SLOT);
        if (currentChestItem == null || !currentChestItem.hasItemMeta()) return;

        // Create a freshly generated chest item using the optimized method from SpawnerMenuUI
        ItemStack newChestItem = spawnerMenuUI.createLootStorageItem(spawner);

        // If the new item is different from current item, update it
        if (!ItemUpdater.areItemsEqual(currentChestItem, newChestItem)) {
            inventory.setItem(CHEST_SLOT, newChestItem);
        }
    }

    private void updateExpItem(Inventory inventory, SpawnerData spawner) {
        ItemStack expItem = inventory.getItem(EXP_SLOT);
        if (expItem == null || !expItem.hasItemMeta()) return;

        long currentExp = spawner.getSpawnerExp();
        if (currentExp != previousExpValue) {
            Map<String, String> nameReplacements = new HashMap<>();
            int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

            nameReplacements.put("current_exp", String.valueOf(spawner.getSpawnerExp()));
            String name = languageManager.getGuiItemName("exp_info_item.name", nameReplacements);

            Map<String, String> loreReplacements = new HashMap<>();
            loreReplacements.put("current_exp", languageManager.formatNumber(currentExp));
            loreReplacements.put("raw_current_exp", String.valueOf(currentExp));
            loreReplacements.put("max_exp", languageManager.formatNumber(spawner.getMaxStoredExp()));
            loreReplacements.put("percent_exp", String.valueOf(percentExp));
            loreReplacements.put("u_max_exp", String.valueOf(spawner.getMaxStoredExp()));
            List<String> expLore = languageManager.getGuiItemLoreAsList("exp_info_item.lore", loreReplacements);

            ItemUpdater.updateItemMeta(expItem, name, expLore);
            previousExpValue = currentExp;
        }
    }

    // ===============================================================
    //                      Storage GUI Update
    // ===============================================================

    private record UpdateAction(Player player, SpawnerData spawner, int page, int totalPages, boolean requiresNewInventory) {}

    public void updateStorageGuiViewers(SpawnerData spawner, int oldTotalPages, int newTotalPages) {
        // Check if total pages changed
        boolean pagesChanged = oldTotalPages != newTotalPages;

        // Batch all viewers that need updates
        List<UpdateAction> updateQueue = new ArrayList<>();

        Set<Player> viewers = getViewers(spawner.getSpawnerId());
        for (Player player : viewers) {
            if (!player.isOnline()) continue;

            final Player currentPlayer = player;

            // Execute within the player's region thread
            Scheduler.runLocationTask(player.getLocation(), () -> {
                if (!currentPlayer.isOnline()) return;

                Inventory currentInv = currentPlayer.getOpenInventory().getTopInventory();
                if (currentInv == null) return;

                if (currentInv.getHolder() instanceof StoragePageHolder) {
                    StoragePageHolder holder = (StoragePageHolder) currentInv.getHolder();
                    int currentPage = holder.getCurrentPage();

                    boolean needsNewInventory = false;
                    int targetPage = currentPage;

                    // Determine if we need a new inventory
                    if (currentPage > newTotalPages) {
                        // if current page is out of bounds, set to last page
                        targetPage = newTotalPages;
                        holder.setCurrentPage(targetPage);
                        needsNewInventory = true;
                    } else if (pagesChanged) {
                        // If total pages changed but current page is still valid, update current page
                        needsNewInventory = true;
                    }

                    if (needsNewInventory) {
                        try {
                            // Update inventory title and contents
                            String newTitle = languageManager.getGuiTitle("gui_title_storage") + " - [" + targetPage + "/" + newTotalPages + "]";
                            currentPlayer.getOpenInventory().setTitle(newTitle);
                            spawnerStorageUI.updateDisplay(currentInv, spawner, targetPage, newTotalPages);
                        } catch (Exception e) {
                            // Fall back to creating a new inventory
                            Inventory newInv = spawnerStorageUI.createInventory(
                                    spawner,
                                    languageManager.getGuiTitle("gui_title_storage"),
                                    targetPage,
                                    newTotalPages
                            );
                            currentPlayer.closeInventory();
                            currentPlayer.openInventory(newInv);
                        }
                    } else {
                        // Just update contents of current inventory
                        spawnerStorageUI.updateDisplay(currentInv, spawner, targetPage, newTotalPages);
                        currentPlayer.updateInventory();
                    }
                }
            });
        }
    }

    // ===============================================================
    //                      Utility Methods
    // ===============================================================

    public void closeAllViewersInventory(SpawnerData spawner) {
        String spawnerId = spawner.getSpawnerId();
        Set<Player> viewers = getViewers(spawnerId);
        if (!viewers.isEmpty()) {
            for (Player viewer : viewers) {
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                }
            }
        }

        // Check for stacker viewers if that functionality exists
        if (plugin.getSpawnerStackerHandler() != null) {
            plugin.getSpawnerStackerHandler().closeAllViewersInventory(spawnerId);
        }
    }

    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }

    public void cleanup() {
        stopUpdateTask();
        clearAllTrackedGuis();
    }
}