package github.nighter.smartspawner.spawner.gui.synchronization;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

/**
 * SpawnerGuiViewManager responsible for managing all spawner GUI interactions and updates.
 * Handles the tracking, updating, and synchronization of open spawner GUI interfaces with improved performance.
 */
public class SpawnerGuiViewManager implements Listener {
    private static final long UPDATE_INTERVAL_TICKS = 20L; // Changed to 20 ticks (1 second) as requested
    private static final long INITIAL_DELAY_TICKS = 20L;   // Match the update interval
    private static final int ITEMS_PER_PAGE = 45;

    // GUI slot constants
    private static final int CHEST_SLOT = 11;
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int EXP_SLOT = 15;

    // Update flags - using bit flags for efficient state tracking
    private static final int UPDATE_CHEST = 1;
    private static final int UPDATE_INFO = 2;
    private static final int UPDATE_EXP = 4;
    private static final int UPDATE_ALL = UPDATE_CHEST | UPDATE_INFO | UPDATE_EXP;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerMenuUI spawnerMenuUI;

    // Optimized data structures to track viewers
    private final Map<UUID, SpawnerViewerInfo> playerToSpawnerMap;
    private final Map<String, Set<UUID>> spawnerToPlayersMap;
    private final Set<Class<? extends InventoryHolder>> validHolderTypes;

    // Batched update tracking to reduce inventory updates
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> updateFlags = new ConcurrentHashMap<>();

    private Scheduler.Task updateTask;
    private volatile boolean isTaskRunning;

    // For timer optimizations - these avoid constant string lookups
    private String cachedInactiveText;
    private String cachedFullText;

    // Static class to hold viewer info more efficiently
    private static class SpawnerViewerInfo {
        final SpawnerData spawnerData;
        final long lastUpdateTime;

        SpawnerViewerInfo(SpawnerData spawnerData) {
            this.spawnerData = spawnerData;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

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

        // Preload commonly used strings to avoid repeated lookups
        initCachedStrings();
    }

    private void initCachedStrings() {
        // Cache status text messages for timer display
        this.cachedInactiveText = languageManager.getGuiItemName("spawner_info_item.lore_inactive");
        this.cachedFullText = languageManager.getGuiItemName("spawner_info_item.lore_full");
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
        playerToSpawnerMap.put(playerId, new SpawnerViewerInfo(spawner));
        spawnerToPlayersMap.computeIfAbsent(spawner.getSpawnerId(), k -> ConcurrentHashMap.newKeySet())
                .add(playerId);

        if (!isTaskRunning) {
            startUpdateTask();
        }
    }

    public void untrackViewer(UUID playerId) {
        SpawnerViewerInfo info = playerToSpawnerMap.remove(playerId);
        if (info != null) {
            SpawnerData spawner = info.spawnerData;
            Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
            if (viewers != null) {
                viewers.remove(playerId);
                if (viewers.isEmpty()) {
                    spawnerToPlayersMap.remove(spawner.getSpawnerId());
                }
            }
        }

        // Also remove from pending updates
        pendingUpdates.remove(playerId);
        updateFlags.remove(playerId);

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

        // More efficient collection of online players
        Set<Player> onlineViewers = new HashSet<>(viewerIds.size());
        for (UUID id : viewerIds) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                onlineViewers.add(player);
            }
        }
        return onlineViewers;
    }

    public boolean hasViewers(SpawnerData spawner) {
        Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
        return viewers != null && !viewers.isEmpty();
    }

    public void clearAllTrackedGuis() {
        playerToSpawnerMap.clear();
        spawnerToPlayersMap.clear();
        pendingUpdates.clear();
        updateFlags.clear();
    }

    // ===============================================================
    //                      Event Handlers
    // ===============================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder(false);
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

        // Simply untrack - no need for complex location-based logic here
        untrackViewer(player.getUniqueId());
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

        // Process batched updates first
        processPendingUpdates();

        // Then handle regular timer updates
        for (Map.Entry<UUID, SpawnerViewerInfo> entry : playerToSpawnerMap.entrySet()) {
            UUID playerId = entry.getKey();
            SpawnerViewerInfo viewerInfo = entry.getValue();
            SpawnerData spawner = viewerInfo.spawnerData;
            Player player = Bukkit.getPlayer(playerId);

            if (!isValidGuiSession(player)) {
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

                    InventoryHolder holder = openInventory.getHolder(false);

                    if (holder instanceof SpawnerMenuHolder) {
                        if (!spawner.getIsAtCapacity()) {
                            updateSpawnerInfoItemTimer(openInventory, spawner);
                            // Force inventory update to ensure changes are visible to the player
                            player.updateInventory();
                        }
                    } else if (!(holder instanceof StoragePageHolder)) {
                        // If inventory is neither SpawnerMenuHolder nor StoragePageHolder, untrack
                        untrackViewer(playerId);
                    }
                });
            }
        }
    }

    private void processPendingUpdates() {
        if (pendingUpdates.isEmpty()) return;

        // Create a copy to avoid concurrent modification
        Set<UUID> currentUpdates = new HashSet<>(pendingUpdates);
        pendingUpdates.clear();

        for (UUID playerId : currentUpdates) {
            Player player = Bukkit.getPlayer(playerId);
            if (!isValidGuiSession(player)) {
                untrackViewer(playerId);
                updateFlags.remove(playerId);
                continue;
            }

            SpawnerViewerInfo info = playerToSpawnerMap.get(playerId);
            if (info == null) {
                updateFlags.remove(playerId);
                continue;
            }

            int flags = updateFlags.getOrDefault(playerId, UPDATE_ALL);
            updateFlags.remove(playerId);

            Location loc = player.getLocation();
            if (loc != null) {
                final int finalFlags = flags;
                final SpawnerData spawner = info.spawnerData;

                Scheduler.runLocationTask(loc, () -> {
                    if (!player.isOnline()) return;

                    Inventory openInv = player.getOpenInventory().getTopInventory();
                    if (openInv == null || !(openInv.getHolder(false) instanceof SpawnerMenuHolder)) return;

                    processInventoryUpdate(player, openInv, spawner, finalFlags);
                });
            }
        }
    }

    private void processInventoryUpdate(Player player, Inventory inventory, SpawnerData spawner, int flags) {
        boolean needsUpdate = false;

        if ((flags & UPDATE_CHEST) != 0) {
            updateChestItem(inventory, spawner);
            needsUpdate = true;
        }

        if ((flags & UPDATE_INFO) != 0) {
            updateSpawnerInfoItem(inventory, spawner, player);
            needsUpdate = true;
        }

        if ((flags & UPDATE_EXP) != 0) {
            updateExpItem(inventory, spawner);
            needsUpdate = true;
        }

        if (needsUpdate) {
            player.updateInventory();
        }
    }

    // ===============================================================
    //                      Public Timer Update Method
    // ===============================================================

    /**
     * Forces an immediate timer update for a specific player's spawner GUI.
     * This is used when opening a new GUI to ensure the timer displays immediately.
     * 
     * @param player The player whose GUI should be updated
     * @param spawner The spawner data for the timer calculation
     */
    public void forceTimerUpdate(Player player, SpawnerData spawner) {
        if (!isValidGuiSession(player)) return;
        
        Location playerLocation = player.getLocation();
        if (playerLocation == null) return;
        
        // Schedule the timer update on the appropriate thread
        Scheduler.runLocationTask(playerLocation, () -> {
            if (!player.isOnline()) return;
            
            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (openInventory == null) return;
            
            InventoryHolder holder = openInventory.getHolder(false);
            if (holder instanceof SpawnerMenuHolder) {
                updateSpawnerInfoItemTimer(openInventory, spawner);
                // Force inventory update to ensure changes are visible immediately
                player.updateInventory();
            }
        });
    }

    // ===============================================================
    //                      Main GUI Update
    // ===============================================================

    /**
     * Optimized version of updateSpawnerMenuViewers - The main entry point for other classes
     * to trigger updates to spawner menus.
     *
     * @param spawner The spawner data that has been updated
     */
    public void updateSpawnerMenuViewers(SpawnerData spawner) {
        Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
        if (viewers == null || viewers.isEmpty()) return;

        int viewerCount = viewers.size();
        if (viewerCount > 10) {
            plugin.debug(viewerCount + " spawner menu viewers to update for " + spawner.getSpawnerId() + " (batch update)");
        }

        // For storage viewers we need to calculate pages
        int oldTotalPages = -1;
        int newTotalPages = -1;

        // Schedule updates for all viewers - but batch them efficiently
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (!isValidGuiSession(viewer)) {
                untrackViewer(viewerId);
                continue;
            }

            // Add to batch update with appropriate flags
            pendingUpdates.add(viewerId);
            updateFlags.put(viewerId, UPDATE_ALL);

            // For efficiency, we'll check holder types once in a separate thread for large batches
            if (viewerCount <= 5) {
                // For small numbers of viewers, check inventory type now to determine storage page updates
                Inventory openInv = viewer.getOpenInventory().getTopInventory();
                if (openInv != null && openInv.getHolder(false) instanceof StoragePageHolder) {
                    // Calculate pages only when needed
                    if (oldTotalPages == -1) {
                        StoragePageHolder holder = (StoragePageHolder) openInv.getHolder(false);
                        oldTotalPages = calculateTotalPages(holder.getOldUsedSlots());
                        newTotalPages = calculateTotalPages(spawner.getVirtualInventory().getUsedSlots());
                    }

                    // Schedule storage update on player's thread
                    processStorageUpdate(viewer, spawner, oldTotalPages, newTotalPages);
                }
            }
        }

        // For larger batches, we'll handle storage viewers in a separate pass
        if (viewerCount > 5) {
            for (UUID viewerId : viewers) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (!isValidGuiSession(viewer)) continue;

                // Run on player's thread to check inventory type
                Location loc = viewer.getLocation();
                if (loc != null) {
                    Scheduler.runLocationTask(loc, () -> {
                        if (!viewer.isOnline()) return;

                        Inventory openInv = viewer.getOpenInventory().getTopInventory();
                        if (openInv != null && openInv.getHolder(false) instanceof StoragePageHolder) {
                            // Calculate pages only when needed (but within player's thread)
                            StoragePageHolder holder = (StoragePageHolder) openInv.getHolder(false);
                            int oldPages = calculateTotalPages(holder.getOldUsedSlots());
                            int newPages = calculateTotalPages(spawner.getVirtualInventory().getUsedSlots());

                            // Process the storage update directly since we're already in player's thread
                            processStorageUpdateDirect(viewer, openInv, spawner, holder, oldPages, newPages);
                        }
                    });
                }
            }
        }
    }

    public void processStorageUpdate(Player viewer, SpawnerData spawner, int oldTotalPages, int newTotalPages) {
        Location loc = viewer.getLocation();
        if (loc != null) {
            Scheduler.runLocationTask(loc, () -> {
                if (!viewer.isOnline()) return;

                Inventory openInv = viewer.getOpenInventory().getTopInventory();
                if (openInv == null || !(openInv.getHolder(false) instanceof StoragePageHolder)) return;

                StoragePageHolder holder = (StoragePageHolder) openInv.getHolder(false);
                processStorageUpdateDirect(viewer, openInv, spawner, holder, oldTotalPages, newTotalPages);
            });
        }
    }

    private void processStorageUpdateDirect(Player viewer, Inventory inventory, SpawnerData spawner,
                                           StoragePageHolder holder, int oldTotalPages, int newTotalPages) {
        // Check if we need a new inventory
        boolean pagesChanged = oldTotalPages != newTotalPages;
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
                viewer.getOpenInventory().setTitle(newTitle);
                spawnerStorageUI.updateDisplay(inventory, spawner, targetPage, newTotalPages);
            } catch (Exception e) {
                // Fall back to creating a new inventory
                Inventory newInv = spawnerStorageUI.createInventory(
                        spawner,
                        languageManager.getGuiTitle("gui_title_storage"),
                        targetPage,
                        newTotalPages
                );
                viewer.closeInventory();
                viewer.openInventory(newInv);
            }
        } else {
            // Just update contents of current inventory
            spawnerStorageUI.updateDisplay(inventory, spawner, targetPage, newTotalPages);
            viewer.updateInventory();
        }
    }

    private int calculateTotalPages(int totalItems) {
        return totalItems <= 0 ? 1 : (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
    }

    public void updateSpawnerMenuGui(Player player, SpawnerData spawner, boolean forceUpdate) {
        // Add to batch update instead of immediate processing
        pendingUpdates.add(player.getUniqueId());
        updateFlags.put(player.getUniqueId(), UPDATE_ALL);
    }

    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner, Player player) {
        // Get the current spawner info item from the inventory
        ItemStack currentSpawnerItem = inventory.getItem(SPAWNER_INFO_SLOT);
        if (currentSpawnerItem == null || !currentSpawnerItem.hasItemMeta()) return;

        // Create a freshly generated spawner info item using the method from SpawnerMenuUI
        ItemStack newSpawnerItem = spawnerMenuUI.createSpawnerInfoItem(player, spawner);

        // If the new item is different from current item, update it
        if (!ItemUpdater.areItemsEqual(currentSpawnerItem, newSpawnerItem)) {
            // Before replacing the item, check if we need to preserve timer info
            preserveTimerInfo(currentSpawnerItem, newSpawnerItem);

            // Update the item in the inventory
            inventory.setItem(SPAWNER_INFO_SLOT, newSpawnerItem);
        }
    }

    private void preserveTimerInfo(ItemStack currentItem, ItemStack newItem) {
        ItemMeta currentMeta = currentItem.getItemMeta();
        ItemMeta newMeta = newItem.getItemMeta();

        if (currentMeta == null || !currentMeta.hasLore() || newMeta == null || !newMeta.hasLore()) {
            return;
        }

        List<String> currentLore = currentMeta.getLore();
        List<String> newLore = newMeta.getLore();

        if (currentLore == null || newLore == null) return;

        // Find the current timer value by looking for lines that don't have the %time% placeholder
        // but were previously processed (meaning they had %time% before)
        String currentTimerValue = null;
        int currentTimerLineIndex = -1;
        
        // First, find the line in new lore that has %time% placeholder
        int newTimerLineIndex = -1;
        for (int i = 0; i < newLore.size(); i++) {
            if (newLore.get(i).contains("%time%")) {
                newTimerLineIndex = i;
                break;
            }
        }
        
        // If there's no %time% placeholder in new lore, nothing to preserve
        if (newTimerLineIndex == -1) return;
        
        // Check if the corresponding line in current lore has been processed (no %time% but same structure)
        if (newTimerLineIndex < currentLore.size()) {
            String currentLine = currentLore.get(newTimerLineIndex);
            String newLine = newLore.get(newTimerLineIndex);
            
            // If current line doesn't have %time% but new line does, extract the timer value
            if (!currentLine.contains("%time%") && newLine.contains("%time%")) {
                // Find the timer value by comparing the structure
                String newLineTemplate = newLine.replace("%time%", "TIMER_PLACEHOLDER");
                String cleanNewTemplate = ChatColor.stripColor(newLineTemplate);
                String cleanCurrentLine = ChatColor.stripColor(currentLine);
                
                // Extract timer value by finding what replaced the placeholder
                int placeholderIndex = cleanNewTemplate.indexOf("TIMER_PLACEHOLDER");
                if (placeholderIndex >= 0 && cleanCurrentLine.length() >= placeholderIndex) {
                    String beforePlaceholder = cleanNewTemplate.substring(0, placeholderIndex);
                    String afterPlaceholder = cleanNewTemplate.substring(placeholderIndex + "TIMER_PLACEHOLDER".length());
                    
                    if (cleanCurrentLine.startsWith(beforePlaceholder) && cleanCurrentLine.endsWith(afterPlaceholder)) {
                        int startIndex = beforePlaceholder.length();
                        int endIndex = cleanCurrentLine.length() - afterPlaceholder.length();
                        if (endIndex > startIndex) {
                            currentTimerValue = cleanCurrentLine.substring(startIndex, endIndex).trim();
                        }
                    }
                }
            }
        }

        // If we found a timer value, apply it to the new item
        if (currentTimerValue != null && !currentTimerValue.isEmpty()) {
            Map<String, String> timerPlaceholder = Collections.singletonMap("time", currentTimerValue);
            List<String> updatedLore = new ArrayList<>(newLore.size());
            
            for (String line : newLore) {
                updatedLore.add(languageManager.applyOnlyPlaceholders(line, timerPlaceholder));
            }
            
            newMeta.setLore(updatedLore);
            newItem.setItemMeta(newMeta);
        }
    }

    private void updateSpawnerInfoItemTimer(Inventory inventory, SpawnerData spawner) {
        ItemStack spawnerItem = inventory.getItem(SPAWNER_INFO_SLOT);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) return;

        ItemMeta meta = spawnerItem.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = meta.getLore();
        if (lore == null) return;

        // Calculate time until next spawn
        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        String timeDisplay;

        // Check if spawner is at capacity and override display message
        if (spawner.getIsAtCapacity()) {
            timeDisplay = cachedFullText;
        } else if (timeUntilNextSpawn == -1) {
            timeDisplay = cachedInactiveText;
        } else {
            timeDisplay = formatTime(timeUntilNextSpawn);
        }

        // Find and update the timer line - handle both placeholder and already processed lines
        boolean needsUpdate = false;
        List<String> updatedLore = new ArrayList<>(lore.size());
        
        for (String line : lore) {
            if (line.contains("%time%")) {
                // This line has the placeholder - replace it with timer display
                String newLine = line.replace("%time%", timeDisplay);
                updatedLore.add(newLine);
                needsUpdate = true;
            } else {
                // Check if this line was previously processed and contains timer info
                // Look for lines that match the timer pattern (contains time format like "01:30", "00:45", etc.)
                String updatedLine = updateExistingTimerLine(line, timeDisplay);
                if (!updatedLine.equals(line)) {
                    updatedLore.add(updatedLine);
                    needsUpdate = true;
                } else {
                    updatedLore.add(line);
                }
            }
        }

        // Only update the inventory item if we actually changed the timer line
        if (needsUpdate) {
            meta.setLore(updatedLore);
            spawnerItem.setItemMeta(meta);
            // Update the inventory directly to ensure changes are applied
            inventory.setItem(SPAWNER_INFO_SLOT, spawnerItem);
        }
    }

    /**
     * Updates an existing timer line by replacing the old timer value with the new one.
     * This handles lines that were previously processed and no longer contain %time% placeholder.
     */
    private String updateExistingTimerLine(String line, String newTimeDisplay) {
        // Pattern to match timer formats: "01:30", "00:45", etc. or status messages
        // Check if line contains what looks like a timer or status message
        String strippedLine = org.bukkit.ChatColor.stripColor(line);
        
        // Look for time patterns (mm:ss format) or our cached status messages
        if (strippedLine.matches(".*\\d{2}:\\d{2}.*") || 
            strippedLine.contains(org.bukkit.ChatColor.stripColor(cachedInactiveText)) ||
            strippedLine.contains(org.bukkit.ChatColor.stripColor(cachedFullText))) {
            
            // This looks like a timer line - we need to replace the timer portion
            // Find the timer part and replace it with the new display
            
            // For time format (mm:ss), replace it
            String updatedLine = line.replaceAll("\\d{2}:\\d{2}", newTimeDisplay);
            if (!updatedLine.equals(line)) {
                return updatedLine;
            }
            
            // For status messages, replace the entire status portion
            String strippedCachedInactive = org.bukkit.ChatColor.stripColor(cachedInactiveText);
            String strippedCachedFull = org.bukkit.ChatColor.stripColor(cachedFullText);
            
            if (strippedLine.contains(strippedCachedInactive)) {
                // Replace the inactive status with new time display
                return line.replace(cachedInactiveText, newTimeDisplay);
            } else if (strippedLine.contains(strippedCachedFull)) {
                // Replace the full status with new time display  
                return line.replace(cachedFullText, newTimeDisplay);
            }
        }
        
        // No timer pattern found, return line unchanged
        return line;
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
        // Get the exp item from the inventory
        ItemStack currentExpItem = inventory.getItem(EXP_SLOT);
        if (currentExpItem == null || !currentExpItem.hasItemMeta()) return;

        // Create a freshly generated exp item using the method from SpawnerMenuUI
        ItemStack newExpItem = spawnerMenuUI.createExpItem(spawner);

        // If the new item is different from current item, update it
        if (!ItemUpdater.areItemsEqual(currentExpItem, newExpItem)) {
            inventory.setItem(EXP_SLOT, newExpItem);
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

    public void cleanup() {
        stopUpdateTask();
        clearAllTrackedGuis();
    }
}
